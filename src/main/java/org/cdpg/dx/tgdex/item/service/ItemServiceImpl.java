package org.cdpg.dx.tgdex.item.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.COS;
import static org.cdpg.dx.tgdex.util.Constants.FIELD;
import static org.cdpg.dx.tgdex.util.Constants.PROVIDER;
import static org.cdpg.dx.tgdex.util.Constants.RESOURCE_GRP;
import static org.cdpg.dx.tgdex.util.Constants.RESOURCE_SVR;
import static org.cdpg.dx.tgdex.util.Constants.VALUE;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.tgdex.item.model.Item;
import org.cdpg.dx.tgdex.item.util.GetItemRequest;
import org.cdpg.dx.tgdex.item.util.ItemFactory;
import org.cdpg.dx.tgdex.search.util.ResponseModel;

public class ItemServiceImpl implements ItemService {
    private final String docIndex;
    ElasticsearchService elasticsearchService;
    QueryDecoder queryDecoder = new QueryDecoder();

    private static final Logger LOGGER = LogManager.getLogger(ItemServiceImpl.class);

    public ItemServiceImpl(ElasticsearchService elasticsearchService,String docIndex) {
        this.elasticsearchService=elasticsearchService;
        this.docIndex = docIndex;
    }

    @Override
    public Future<Void> createItem(Item item) {
        Promise<Void> promise = Promise.promise();
        String id = item.getId();

        if (id == null || id.isBlank()) {
            return Future.failedFuture("ID not present in request");
        }

        QueryModel termQuery = new QueryModel(QueryType.TERM);
        termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));

        elasticsearchService.getSingleDocument(docIndex, termQuery)
            .onSuccess(existingDoc -> {
                if (existingDoc != null && ElasticsearchResponse.getTotalHits() > 0) {
                    promise.fail("Item with ID already exists");
                } else {
                    QueryModel queryModel = new QueryModel();
                    queryModel.createQueryModelFromDocument(item.toJson());
                    elasticsearchService.createDocuments(docIndex, Collections.singletonList(queryModel))
                        .onSuccess(v -> promise.complete())
                        .onFailure(promise::fail);
                }
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    @Override
    public Future<ResponseModel> getItem(GetItemRequest request) {
        Promise<ResponseModel> promise = Promise.promise();

        QueryDecoder queryDecoder = new QueryDecoder();
        QueryModel queryModel = queryDecoder.getItemQueryModel(request.getItemId());

        LOGGER.debug("Retrieving item with ID: {}", queryModel.toJson());

        elasticsearchService.getSingleDocument(docIndex, queryModel.getQueries())
                .onSuccess(response -> {
                    int totalHits = ElasticsearchResponse.getTotalHits();
                    if (totalHits == 0) {
                        LOGGER.warn("Item with ID {} does not exist", request.getItemId());
                        promise.fail("Document does not exist");
                        return;
                    }

                    if (ownershipCheck(response, request.getSubId())) {
                        LOGGER.debug("Ownership check passed for item with ID: {}", request.getItemId());
                        ResponseModel responseModel = new ResponseModel(List.of(response));
                        responseModel.setTotalHits(totalHits);
                        promise.complete(responseModel);
                        return;
                    } else {
                        LOGGER.warn("Ownership check failed for item with ID: {}", request.getItemId());
                        promise.fail("Ownership check failed");
                    }
                })
                .onFailure(err -> {
                    LOGGER.error("Error retrieving item with ID {}: {}", request.getItemId(), err.getMessage());
                    promise.fail("Failed to retrieve item: " + err.getMessage());
                });
        return promise.future();
    }

    @Override
    public Future<Void> deleteItem(String id) {
        Promise<Void> promise = Promise.promise();

        if (id == null || id.isBlank()) {
            return Future.failedFuture("ID not present in request");
        }

        QueryModel boolQuery = new QueryModel(QueryType.BOOL);
        QueryModel idTermQuery = new QueryModel(QueryType.TERM);
        idTermQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
        QueryModel resourceGrpTermQuery = new QueryModel(QueryType.TERM);
        resourceGrpTermQuery.setQueryParameters(Map.of(FIELD, RESOURCE_GRP + KEYWORD_KEY, VALUE, id));
        QueryModel providerTermQuery = new QueryModel(QueryType.TERM);
        providerTermQuery.setQueryParameters(Map.of(FIELD, PROVIDER + KEYWORD_KEY, VALUE, id));
        QueryModel resourceSvrTermQuery = new QueryModel(QueryType.TERM);
        resourceSvrTermQuery.setQueryParameters(Map.of(FIELD, RESOURCE_SVR + KEYWORD_KEY, VALUE, id));
        QueryModel cosTermQuery = new QueryModel(QueryType.TERM);
        cosTermQuery.setQueryParameters(Map.of(FIELD, COS + KEYWORD_KEY, VALUE, id));

        boolQuery.setShouldQueries(List.of(idTermQuery, resourceGrpTermQuery, providerTermQuery,
            resourceSvrTermQuery, cosTermQuery));

        elasticsearchService.getSingleDocument(docIndex, boolQuery)
            .onSuccess(result -> {
                if (ElasticsearchResponse.getTotalHits() > 1) {
                    promise.fail("Item has associated entities and cannot be deleted");
                } else if (ElasticsearchResponse.getTotalHits() < 1) {
                    promise.fail("Item not found for deletion");
                } else {
                    String docId = result.getDocId();
                    elasticsearchService.deleteDocument(docIndex, docId)
                        .onSuccess(v -> promise.complete())
                        .onFailure(promise::fail);
                }
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    @Override
    public Future<Void> updateItem(Item item) {
        Promise<Void> promise = Promise.promise();
        String id = item.getId();
        String type = item.getType().getFirst();

        if (id == null || id.isBlank() || type == null || type.isBlank()) {
            return Future.failedFuture("ID or Type missing in update request");
        }

        QueryModel boolQuery = new QueryModel(QueryType.BOOL);
        QueryModel termQuery = new QueryModel(QueryType.TERM);
        termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
        QueryModel matchQuery = new QueryModel(QueryType.MATCH);
        matchQuery.setQueryParameters(Map.of(FIELD, TYPE_KEYWORD, VALUE, type));

        boolQuery.setMustQueries(List.of(termQuery, matchQuery));

        elasticsearchService.getSingleDocument(docIndex, boolQuery)
            .onSuccess(getRes -> {
                if (getRes == null || ElasticsearchResponse.getTotalHits() == 0) {
                    promise.fail("Item not found for update");
                } else {
                    QueryModel queryModel = new QueryModel();
                    queryModel.createQueryModelFromDocument(item.toJson());
                    elasticsearchService.updateDocument(docIndex, id, queryModel)
                        .onSuccess(v -> promise.complete())
                        .onFailure(promise::fail);
                }
            })
            .onFailure(promise::fail);

        return promise.future();
    }

    @Override
    public Future<Item> itemWithTheNameExists(String type, String name){
            Promise<Item> promise = Promise.promise();

            QueryModel queryModel = queryDecoder.buildGetItemWithNameExistsQuery(type, name);
            elasticsearchService.getSingleDocument(docIndex, queryModel)
                    .onSuccess(result -> {
                        if (result == null || ElasticsearchResponse.getTotalHits() == 0) {
                            promise.fail("Item not found");
                        } else {
                            promise.complete(ItemFactory.from(result.getSource()));
                        }
                    })
                    .onFailure(promise::fail);
            return promise.future();
        }
    private boolean ownershipCheck(ElasticsearchResponse response, String subId) {
        JsonObject source = response.getSource();
        String accessPolicy = source.getString("accessPolicy");
        String ownerUserId = source.getString("ownerUserId");

        if ("private".equalsIgnoreCase(accessPolicy)) {
            if (subId.isEmpty()) {
                LOGGER.warn("Ownership check failed: No subId provided for private access policy");
                return false;
            }
            if (!ownerUserId.equalsIgnoreCase(subId)) {
                LOGGER.warn("Ownership check failed: User {} does not own the item", subId);
                return false;
            }
        } else {
            LOGGER.info("Ownership check not required for access policy '{}'", accessPolicy);
            return true;
        }
        return true;
    }
}
