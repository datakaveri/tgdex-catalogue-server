package org.cdpg.dx.database.elastic.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import co.elastic.clients.json.JsonpMapperFeatures;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import jakarta.json.stream.JsonGenerator;

import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.common.exception.DxInternalServerErrorException;
import org.cdpg.dx.database.elastic.ElasticClient;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryModel;


public class ElasticsearchServiceImpl implements ElasticsearchService {
    private static final Logger LOGGER = LogManager.getLogger(ElasticsearchServiceImpl.class);

    static ElasticClient client;
    private static ElasticsearchAsyncClient asyncClient;

    public ElasticsearchServiceImpl(ElasticClient client) {
        ElasticsearchServiceImpl.client = client;
        asyncClient = client.getClient();
    }

    @Override
    public Future<List<ElasticsearchResponse>> search(String index, QueryModel queryModel, String options) {
        Promise<List<ElasticsearchResponse>> promise = Promise.promise();

        Map<String, Aggregation> aggregations = new HashMap<>();

        if (queryModel.getAggregations() != null) {
            queryModel.getAggregations().forEach(agg -> aggregations.put(agg.getAggregationName(), agg.toElasticsearchAggregations()));
        }

        SearchRequest.Builder requestBuilder = new SearchRequest.Builder().index(index);
        QueryModel queries = queryModel.getQueries();
        if (queries != null && queries.toElasticsearchQuery() != null) {
            requestBuilder.query(queries.toElasticsearchQuery());
        }

        if (!aggregations.isEmpty()) {
            requestBuilder.aggregations(aggregations);
        }

        int limit = parseSize(options, queryModel);
        requestBuilder.size(limit);

        if (queryModel.getOffset() != null) {
            requestBuilder.from(Integer.parseInt(queryModel.getOffset()));
        }

        if (queryModel.toSourceConfig() != null) {
            requestBuilder.source(queryModel.toSourceConfig());
        }

        if (queryModel.toSortOptions() != null) {
            requestBuilder.sort(queryModel.toSortOptions());
        }

        SearchRequest request = requestBuilder.build();
        LOGGER.debug("Request: " + request.toString());

        asyncClient.search(request, ObjectNode.class).whenComplete((response, error) -> {
            if (error != null) {
                LOGGER.error("Search failed: {}", error.getMessage(), error);
                promise.fail(new DxInternalServerErrorException(error.getMessage(), error));
                return;
            }

            try {
                List<ElasticsearchResponse> esResponses = new ArrayList<>();
                JsonObject aggregationsJson = new JsonObject();

                // 1. Handle hits if needed
                if (!options.startsWith(AGGREGATION_ONLY)) {
                    for (var hit : response.hits().hits()) {
                        String id = hit.id();
                        JsonObject source = hit.source() != null ? new JsonObject(hit.source().toString()) : new JsonObject();
                        JsonObject result = new JsonObject();
                        switch (options) {
                            case DOC_IDS_ONLY:
                                result.put(ID, id);
                                break;
                            case SOURCE_AND_ID:
                                result.put(ID, id).put(SOURCE, source);
                                break;
                            case SOURCE_AND_ID_GEOQUERY:
                                source.put("doc_id", id);
                                result.mergeIn(source);
                                break;
                            case SOURCE_ONLY:
                                source.remove(SUMMARY_KEY);
                                source.remove(WORD_VECTOR_KEY);
                                result = source;
                                break;
                            default:
                                result = source;
                                break;
                        }

                        esResponses.add(new ElasticsearchResponse(id, result));
                    }

                    long totalHits = response.hits().total() != null ? response.hits().total().value() : 0;
                    ElasticsearchResponse.setTotalHits((int) totalHits);
                }

                // 2. Handle aggregations if needed
                if (options.startsWith(AGGREGATION_ONLY) || options.equals(COUNT_AGGREGATION_ONLY)) {
                    aggregationsJson = parseAggregations(response, options);
                }

                if (!aggregationsJson.isEmpty()) {
                    ElasticsearchResponse.setAggregations(aggregationsJson);
                }

                promise.complete(esResponses);
            } catch (Exception e) {
                LOGGER.error("Failed to parse search response", e);
                promise.fail(new DxInternalServerErrorException("Failed to parse search result", e));
            }
        });

        return promise.future();
    }

    private int parseSize(String options, QueryModel model) {
        if (options.startsWith(AGGREGATION_ONLY)) {
            return 0;
        }
        try {
            return Optional.ofNullable(model.getLimit()).map(Integer::parseInt).orElse(STRING_SIZE);
        } catch (NumberFormatException e) {
            throw new DxBadRequestException("Invalid 'limit' format");
        }
    }

    private JsonObject parseAggregations(SearchResponse<ObjectNode> response, String options) {
        JsonObject aggResult = new JsonObject();
        JsonpMapper mapper = asyncClient._jsonpMapper().withAttribute(JsonpMapperFeatures.SERIALIZE_TYPED_KEYS, false);
        StringWriter writer = new StringWriter();
        try (JsonGenerator generator = mapper.jsonProvider().createGenerator(writer)) {
            mapper.serialize(response, generator);
        } catch (Exception e) {
            LOGGER.error("Error serializing aggregations: ", e);
            throw new DxInternalServerErrorException("Failed to process aggregations", e);
        }
        String result = writer.toString();

        // Parse the aggregations object from the serialized result
        JsonObject rawAggs = new JsonObject(result).getJsonObject(AGGREGATIONS);
        if (rawAggs == null) {
            return aggResult;
        }

        if (AGGREGATION_LIST.equals(options)) {
            for (String aggKey : rawAggs.fieldNames()) {
                JsonArray keys = new JsonArray();
                JsonObject agg = rawAggs.getJsonObject(aggKey);
                if (agg.containsKey(BUCKETS)) {
                    JsonArray buckets = agg.getJsonArray(BUCKETS);
                    for (int i = 0; i < buckets.size(); i++) {
                        keys.add(buckets.getJsonObject(i).getString(KEY));
                    }
                }
                aggResult.put(aggKey, keys);
            }
        } else if (COUNT_AGGREGATION_ONLY.equals(options)) {
            JsonObject resultsAgg = rawAggs.getJsonObject(RESULTS);

            if (resultsAgg != null && resultsAgg.containsKey(BUCKETS)) {
                JsonArray buckets = resultsAgg.getJsonArray(BUCKETS);
                for (int i = 0; i < buckets.size(); i++) {
                    JsonObject bucket = buckets.getJsonObject(i);
                    aggResult.put(bucket.getString(KEY), bucket.getInteger(DOC_COUNT));
                }
            }
        } else {
            aggResult.mergeIn(rawAggs);
        }

        return aggResult;
    }


    @Override
    public Future<Integer> count(String index, QueryModel queryModel) {
        // Convert QueryModel into Elasticsearch Query
        Query query = queryModel.getQueries() == null ? null : queryModel.getQueries().toElasticsearchQuery();
        LOGGER.debug("Count query {}", query);
        // Create a CountRequest.Builder for the count query
        CountRequest.Builder requestBuilder = new CountRequest.Builder().index(index);
        // Add query if present
        if (query != null) {
            requestBuilder.query(query);
        }

        CountRequest request = requestBuilder.build();
        LOGGER.debug("Final CountRequest: {}", request);

        // Execute the count query
        return executeCount(request);
    }

    private Future<Integer> executeCount(CountRequest request) {
        Promise<Integer> promise = Promise.promise();
        LOGGER.debug("REQUEST {}", request);
        asyncClient.count(request).whenComplete((response, error) -> {
            if (error != null) {
                // Log specific error type for better debugging
                LOGGER.error("Count operation failed. Error type: {}, Message: {}", error.getClass().getSimpleName(), error.getMessage());

                // You might want to handle specific exceptions differently
                {
                    LOGGER.error("Elasticsearch cluster is unreachable");
                    promise.fail(new DxInternalServerErrorException("Elasticsearch cluster is unreachable"));
                }
            } else {
                try {
                    LOGGER.debug("COUNT: " + response);
                    Integer count = Math.toIntExact(response.count());
                    LOGGER.debug("Total document count: {}", count);
                    promise.complete(count);
                } catch (ArithmeticException e) {
                    LOGGER.error("Count value too large for Integer conversion");
                    promise.fail(new DxBadRequestException("Count value too large for Integer conversion"));
                }
            }
        });

        return promise.future();
    }
        // Public API methods

        @Override
        public Future<Void> deleteByQuery(String index, QueryModel queryModel) {
            return validateIndex(index)
                    .compose(v -> validateQueryModel(queryModel))
                    .compose(v -> executeDeleteByQuery(index, queryModel));
        }

        @Override
        public Future<ElasticsearchResponse> getSingleDocument(String index, QueryModel queryModel) {
            return validateIndex(index)
                    .compose(v -> performSingleSearch(index, queryModel));
        }

        @Override
        public Future<List<String>> createDocuments(String index, List<QueryModel> documentModels) {
            return validateIndex(index)
                    .compose(v -> validateDocumentModels(documentModels))
                    .compose(v -> executeBulkIndex(index, documentModels));
        }

        @Override
        public Future<Void> deleteDocument(String index, String id) {
            return validateIndex(index)
                    .compose(v -> validateId(id))
                    .compose(v -> executeDeleteDocument(index, id));
        }

        @Override
        public Future<Void> updateDocument(String index, String id, QueryModel queryModel) {
        LOGGER.debug("Update document with index: {}, id: {}, queryModel: {}", index, id, queryModel);
            return validateIndex(index)
                    .compose(v -> validateId(id))
                    .compose(v -> validateQueryModel(queryModel))
                    .compose(v -> executeExistenceCheck(index, id))
                    .compose(v -> executeUpdate(index, id, queryModel));
        }

        @Override
        public Future<Void> updateDocumentsByQuery(QueryModel queryModel, String index) {
            return validateIndex(index)
                    .compose(v -> validateQueryModel(queryModel))
                    .compose(v -> executeUpdateByQuery(index, queryModel));
        }

        // Validation helpers

        private Future<Void> validateIndex(String index) {
            if (index == null || index.trim().isEmpty()) {
                String msg = "Index cannot be null or empty";
                LOGGER.error(msg);
                return Future.failedFuture(new IllegalArgumentException(msg));
            }
            return Future.succeededFuture();
        }

        private Future<Void> validateId(String id) {
            if (id == null || id.trim().isEmpty()) {
                String msg = "Document ID cannot be null or empty";
                LOGGER.error(msg);
                return Future.failedFuture(new IllegalArgumentException(msg));
            }
            return Future.succeededFuture();
        }

        private Future<Void> validateQueryModel(QueryModel model) {
            if (model == null) {
                String msg = "QueryModel cannot be null";
                LOGGER.error(msg);
                return Future.failedFuture(new IllegalArgumentException(msg));
            }
            return Future.succeededFuture();
        }

        private Future<Void> validateDocumentModels(List<QueryModel> models) {
            if (models == null || models.isEmpty()) {
                String msg = "DocumentModels list cannot be null or empty";
                LOGGER.error(msg);
                return Future.failedFuture(new IllegalArgumentException(msg));
            }
            return Future.succeededFuture();
        }

        // Execution logic

        private Future<Void> executeDeleteByQuery(String indices, QueryModel queryModel) {
            Promise<Void> promise = Promise.promise();
            DeleteByQueryRequest request = new DeleteByQueryRequest.Builder()
                    .index(indices)
                    .query(queryModel.toElasticsearchQuery())
                    .build();
            LOGGER.debug("DeleteByQuery Request: {}", request);
            asyncClient.deleteByQuery(request).whenComplete((resp, err) -> {
                if (err != null) {
                    LOGGER.error("deleteByQuery failed {}", err.getMessage());
                    promise.fail(new RuntimeException("Failed to execute deleteByQuery", err));
                } else {
                    LOGGER.info("Deleted {} documents", resp.deleted());
                    promise.complete();
                }
            });
            return promise.future();
        }

        private Future<ElasticsearchResponse> performSingleSearch(String index, QueryModel model) {
            Promise<ElasticsearchResponse> promise = Promise.promise();
            SearchRequest.Builder builder = new SearchRequest.Builder()
                    .index(index)
                    .query(model.toElasticsearchQuery())
                    .size(1)
                    .from(0);
            asyncClient.search(builder.build(), ObjectNode.class)
                    .whenComplete((resp, err) -> {
                        if (err != null) {
                            promise.fail(new RuntimeException("Search error", err));
                        } else if (resp.hits().total().value()==0) {
                            LOGGER.debug("No documents found ");
                            promise.complete();
                        } else {
                            Hit <ObjectNode> hit = resp.hits().hits().getFirst();
                            LOGGER.debug("Single document found with ID: {}", hit.id());
                            ElasticsearchResponse response = new ElasticsearchResponse(hit.id(), new JsonObject(hit.source().toString()));
                            ElasticsearchResponse.setTotalHits((int) resp.hits().total().value());
                            promise.complete(response);
                        }
                    });
            return promise.future();
        }

        private Future<List<String>> executeBulkIndex(String index, List<QueryModel> models) {
            Promise<List<String>> promise = Promise.promise();
            LOGGER.debug("Index "+index);
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            models.forEach(queryModel -> {
                JsonObject doc = queryModel.extractDocumentFromQueryModel();
                bulkBuilder.operations(operation -> operation.index(docs -> docs.index(index)
                        .id(doc.getString("id"))
                        .document(doc)));
            });

            asyncClient.bulk(bulkBuilder.build()).whenComplete((bulkResponse,error) -> {
                LOGGER.debug("bulk Response "+bulkResponse);
                LOGGER.error("Error "+error.getMessage());
                if (bulkResponse.errors()) {
                    LOGGER.error("bulk index failed");
                    promise.fail(new RuntimeException("Bulk index error"));
                } else {
                    LOGGER.debug("bulk Response "+bulkResponse);
                    List<String> ids = bulkResponse.items().stream().map(BulkResponseItem::id).collect(Collectors.toList());
                    promise.complete(ids);
                }
            });
            return promise.future();
        }

        private Future<Void> executeDeleteDocument(String index, String id) {
            LOGGER.debug("Deleting document with ID: {}", id);
            Promise<Void> promise = Promise.promise();
            DeleteRequest req = DeleteRequest.of(d -> d.index(index).id(id));
            asyncClient.delete(req).whenComplete((resp, err) -> {

                if (err != null) {
                    LOGGER.error("delete failed", err);
                    promise.fail(new RuntimeException("Delete error"));
                } else if (resp.result() == Result.NotFound ){
                    LOGGER.warn("Document not found: {}", id);
                    promise.fail(new RuntimeException("Document not found"));
                } else {
                    promise.complete();
                }
            });
            return promise.future();
        }

        private Future<Void> executeExistenceCheck(String index, String id) {
            Promise<Void> promise = Promise.promise();
            ExistsRequest req = ExistsRequest.of(e -> e.index(index).id(id));
            asyncClient.exists(req).whenComplete((res, err) -> {
                if (err != null || !res.value()) {
                    String msg = err != null ? "Existence check failed" : "Document not found";
                    LOGGER.error(msg, err);
                    promise.fail(new RuntimeException(msg, err));
                } else {
                    promise.complete();
                }
            });
            return promise.future();
        }

        private Future<Void> executeUpdate(String index, String id, QueryModel model) {
            Promise<Void> promise = Promise.promise();
            UpdateRequest<String, JsonObject> req = UpdateRequest.of(u -> u
                    .index(index)
                    .id(id)
                    .doc(model.extractDocumentFromQueryModel()));
            asyncClient.update(req, JsonObject.class).whenComplete((res, err) -> {
                if (err != null) {
                    LOGGER.error("update failed {}", err.getMessage());
                    promise.fail(new RuntimeException("Update error", err));
                } else {
                    promise.complete();
                }
            });
            return promise.future();
        }

        private Future<Void> executeUpdateByQuery(String index, QueryModel model) {
            Promise<Void> promise = Promise.promise();
            UpdateByQueryRequest.Builder builder = new UpdateByQueryRequest.Builder()
                    .index(index)
                    .query(model.toElasticsearchQuery());

            Script script = model.toElasticsearchScript();
            if (script != null) {
                builder.script(script);
            }
            UpdateByQueryRequest request = builder.build();
            LOGGER.debug("UpdateByQuery Request: {}", request);
            asyncClient.updateByQuery(request).whenComplete((res, err) -> {
                if (err != null) {
                    LOGGER.error("updateByQuery failed {}", err.getMessage());
                    promise.fail(new RuntimeException("UpdateByQuery error", err));
                } else {
                    LOGGER.info("Updated {} documents", res);
                    promise.complete();
                }
            });
            return promise.future();
        }
    }
