package org.cdpg.dx.database.elastic.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;

import co.elastic.clients.elasticsearch.ElasticsearchAsyncClient;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkResponseItem;
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
    @Override
    public Future<Void> deleteByQuery(String index, QueryModel queryModel) {
        Promise<Void> promise = Promise.promise();

        if (queryModel == null) {
            LOGGER.error("QueryModel cannot be null for delete operation");
            promise.fail(new DxInternalServerErrorException("QueryModel cannot be null"));
            return promise.future();
        }

        if (index == null || index.isEmpty()) {
            LOGGER.error("Indices array cannot be null or empty for delete operation");
            promise.fail(new DxBadRequestException("Indices array cannot be null or empty"));
            return promise.future();
        }

        try {
            Query elasticsearchQuery = queryModel.toElasticsearchQuery();

            LOGGER.info("Executing delete by query for index: {} with query: {}", String.join(",", index), queryModel.toJson());

            // Build the delete by query request for multiple index
            DeleteByQueryRequest.Builder requestBuilder = new DeleteByQueryRequest.Builder().index(index).query(elasticsearchQuery);

            DeleteByQueryRequest request = requestBuilder.build();

            // Execute the delete by query operation asynchronously
            asyncClient.deleteByQuery(request).whenComplete((response, error) -> {
                if (error != null) {
                    LOGGER.error("Error occurred during delete by query operation for index: {} with query: {} ", index, queryModel.toJson(), error);
                    promise.fail(new RuntimeException("Failed to execute delete by query", error));
                } else {
                    LOGGER.info("Delete by query completed successfully for index: {}. Deleted: {},", index, response.deleted());
                    promise.complete();
                }
            });

        } catch (Exception e) {
            LOGGER.error("Error while preparing delete by query for index: {} with QueryModel: {}",index, queryModel.toJson(), e);
            promise.fail(new RuntimeException("Failed to prepare delete by query", e));
        }

        return promise.future();
    }

    @Override
    public Future<ElasticsearchResponse> getSingleDocument(QueryModel queryModel, String docIndex) {

    LOGGER.debug("Executing single document search on index: {} with QueryModel: {}", docIndex, queryModel.toJson());
    Promise<ElasticsearchResponse> promise = Promise.promise();

    // Build the search request optimized for single document retrieval
    SearchRequest.Builder searchRequestBuilder = new SearchRequest.Builder()
            .index(docIndex)
            .size(10)  // Only get one document
            .from(0); // Start from beginning

    // Get the main query from QueryModel
    if (queryModel.getQueries() != null) {
      Query elasticsearchQuery = queryModel.getQueries().toElasticsearchQuery();
      searchRequestBuilder.query(elasticsearchQuery);
      LOGGER.debug("Applied query: {}", elasticsearchQuery);
    }


    SearchRequest request = searchRequestBuilder.build();
    LOGGER.debug("Applied query: {}", request);

    asyncClient.search(request, ObjectNode.class).whenComplete((response, error) -> {
      if (response.hits().hits().isEmpty()) {
        LOGGER.debug("No document found matching the criteria");
        promise.complete(null); // or handle as appropriate for your use case
        return;
      }

      try {
        LOGGER.debug("Response total hits: {}", response.hits().total().value());
        LOGGER.debug("Response "+response);
        // Since size is 1, we expect at most one document
        if (response.hits().hits().isEmpty()) {
          LOGGER.debug("No document found matching the criteria");
          promise.complete(null); // or handle as appropriate for your use case
          return;
        }

        // Extract the single document source
        ObjectNode documentSource = response.hits().hits().get(0).source();

        if (documentSource != null) {
          LOGGER.debug("Document source: {}", documentSource);

          // Convert ObjectNode to JsonObject if needed
          JsonObject result = new JsonObject(documentSource.toString());
          LOGGER.debug("Result source {}",result);
          // Complete the promise with the document
          promise.complete(new ElasticsearchResponse(result));
        } else {
          LOGGER.warn("Document found but source is null");
          promise.complete(null);
        }

        // Handle aggregations if present (though unlikely for single document queries)
        if (response.aggregations() != null && !response.aggregations().isEmpty()) {
          JsonObject aggregationsJson = new JsonObject();
          response.aggregations().forEach((key, aggregation) -> {
            // Process aggregations if needed
            LOGGER.debug("Aggregation {}: {}", key, aggregation);
            // Add aggregation processing logic here if required
          });
        }

      } catch (Exception e) {
        LOGGER.error("Error processing search response", e);
        promise.fail(new DxInternalServerErrorException("Error processing search response", e));
      }

    });
    return promise.future();
  }

    @Override
    public Future<List<String>> createDocuments(String index, List<QueryModel> documentModels) {
        Promise<List<String>> promise = Promise.promise();

        if (index == null || index.isEmpty()) {
            LOGGER.error("Index cannot be null or empty for batch create operation");
            promise.fail(new IllegalArgumentException("Index cannot be null or empty"));
            return promise.future();
        }

        if (documentModels == null || documentModels.isEmpty()) {
            LOGGER.error("DocumentModels list cannot be null or empty for batch create operation");
            promise.fail(new IllegalArgumentException("DocumentModels list cannot be null or empty"));
            return promise.future();
        }

        try {
            LOGGER.debug("Creating {} documents in index: {} from QueryModels", documentModels.size(), index);
            BulkRequest.Builder requestBuilder = new BulkRequest.Builder();

            for (QueryModel documentModel : documentModels) {
                JsonObject document = documentModel.extractDocumentFromQueryModel();
                requestBuilder.operations(op -> op.index(idx -> idx.index(index)
                        .document(document).id(document.getString("id"))));
            }

            BulkRequest request = requestBuilder.build();

            asyncClient.bulk(request).whenComplete((response, error) -> {
                if (error != null) {
                    LOGGER.error("Error occurred during bulk document creation in index: {}", index, error);
                    promise.fail(new RuntimeException("Failed to create documents", error));
                } else {
                    List<String> documentIds = response.items().stream().map(BulkResponseItem::id).collect(Collectors.toList());
                    LOGGER.debug("Bulk document creation completed in index: {}. Created: {}, Errors: {}",
                            index, documentIds.size(), response.errors());
                    promise.complete(documentIds);
                }
            });

        } catch (Exception e) {
            LOGGER.error("Error while preparing bulk document creation for index: {}", index, e);
            promise.fail(new RuntimeException("Failed to prepare bulk document creation", e));
        }

        return promise.future();
    }

    @Override
    public Future<Void> deleteDocument(String index, String id) {
        if (index == null || index.isEmpty()) {
            LOGGER.error("Index cannot be null or empty for delete operation");
            return Future.failedFuture(
                    new IllegalArgumentException("Index cannot be null or empty"));
        }
        if (id == null || id.isEmpty()) {
            LOGGER.error("Document ID cannot be null or empty for delete operation");
            return Future.failedFuture(
                    new IllegalArgumentException("Document ID cannot be null or empty"));
        }

        LOGGER.debug("Deleting document index={}, id={}", index, id);
        DeleteRequest deleteRequest = DeleteRequest.of(d -> d
                .index(index)
                .id(id)
        );

        Promise<Void> promise = Promise.promise();
        asyncClient.delete(deleteRequest)
                .whenComplete((deleteResponse, deleteError) -> {
                    LOGGER.error("Delete error "+deleteError);
                    if (deleteError != null) {
                        LOGGER.error("Error occurred during document delete: index={}, id={}, reason={}", index, id, deleteError.getMessage());
                        promise.fail(new DxInternalServerErrorException("Failed to delete document", deleteError.getCause()));
                    } else {
                        LOGGER.error("Result "+deleteResponse);

                        Result result = deleteResponse.result();
                        if (result == Result.NotFound) {
                            LOGGER.info("Document not found index={}, id={}", index, id);
                            promise.fail("Document not found");
                        } else {
                            LOGGER.info("Document deleted successfully: index={}, id={}", index, id);
                            promise.complete();
                        }
                    }
                });

        return promise.future();
    }


    @Override
    public Future<Void> updateDocument(String index, String id, QueryModel documentModel) {
        Promise<Void> promise = Promise.promise();

        if (index == null || index.isEmpty()) {
            LOGGER.error("Index cannot be null or empty for update operation");
            promise.fail(new IllegalArgumentException("Index cannot be null or empty"));
            return promise.future();
        }

        if (id == null || id.isEmpty()) {
            LOGGER.error("Document ID cannot be null or empty for update operation");
            promise.fail(new IllegalArgumentException("Document ID cannot be null or empty"));
            return promise.future();
        }

        if (documentModel == null) {
            LOGGER.error("DocumentModel cannot be null for update operation");
            promise.fail(new IllegalArgumentException("DocumentModel cannot be null"));
            return promise.future();
        }

        try {
            LOGGER.debug("Checking if document exists before update: index={}, id={}", index, id);

            // First, check if the document exists
            ExistsRequest existsRequest = ExistsRequest.of(e -> e.index(index).id(id));

            asyncClient.exists(existsRequest).whenComplete((existsResponse, existsError) -> {
                LOGGER.debug("Exist response "+existsResponse.value());
                if (existsError != null) {
                    LOGGER.error("Error checking document existence: index={}, id={}, reason={}", index, id, existsError);
                    promise.fail(new RuntimeException("Failed to check document existence", existsError));
                    return;
                }

                if (!existsResponse.value()) {
                    LOGGER.warn("Document not found for update: index={}, id={}", index, id);
                    promise.fail(new IllegalArgumentException("Document not found with id: " + id));
                    return;
                }

                // Document exists, proceed with update
                LOGGER.debug("Document exists, proceeding with update: index={}, id={}", index, id);

                try {
                    JsonObject document = documentModel.extractDocumentFromQueryModel();

                    UpdateRequest<String,JsonObject> updateRequest = UpdateRequest.of(u -> u
                            .index(index)
                            .id(id)
                            .doc(document));

                    asyncClient.update(updateRequest, JsonObject.class).whenComplete((updateResponse, updateError) -> {
                        if (updateError != null) {
                            LOGGER.error("Error occurred during document update: index={}, id={}", index, id, updateError);
                            promise.fail(new DxInternalServerErrorException("Failed to update document "+ updateError));
                        } else {
                            LOGGER.debug("Document updated successfully: index={}, id={}, result={}",
                                    index, id, updateResponse.toString());
                            promise.complete();
                        }
                    });

                } catch (Exception e) {
                    LOGGER.error("Error while preparing document update: index={}, id={}", index, id, e.getCause());
                    promise.fail(new RuntimeException("Failed to prepare document update", e));
                }
            });

        } catch (Exception e) {
            LOGGER.error("Error while checking document existence: index={}, id={}", index, id, e);
            promise.fail(new RuntimeException("Failed to check document existence", e));
        }

        return promise.future();
    }

}