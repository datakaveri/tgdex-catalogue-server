package org.cdpg.dx.tgdex.item.service;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.COS;
import static org.cdpg.dx.tgdex.util.Constants.FIELD;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.tgdex.util.Constants.NAME;
import static org.cdpg.dx.tgdex.util.Constants.PROVIDER;
import static org.cdpg.dx.tgdex.util.Constants.RESOURCE_GRP;
import static org.cdpg.dx.tgdex.util.Constants.RESOURCE_SVR;
import static org.cdpg.dx.tgdex.util.Constants.VALUE;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javassist.NotFoundException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.tgdex.item.model.Item;
import org.cdpg.dx.tgdex.item.util.ItemFactory;

public class ItemServiceImpl implements ItemService {

    private static final Logger LOGGER = LogManager.getLogger(ItemServiceImpl.class);
    private final ElasticsearchService elasticsearchService;
    private final String docIndex;
    QueryDecoder queryDecoder = new QueryDecoder();

    public ItemServiceImpl(ElasticsearchService elasticsearchService, String docIndex) {
        this.elasticsearchService = elasticsearchService;
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
    public Future<Item> getItem(String id, String type) {
        Promise<Item> promise = Promise.promise();

        if (id == null || id.isBlank()) {
            return Future.failedFuture("ID not present in request");
        }

        QueryModel termQuery = new QueryModel(QueryType.TERM);
        termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));

        elasticsearchService.getSingleDocument(docIndex, termQuery)
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
    public Future<Item> itemWithTheNameExists(String type, String name) {
        Promise<Item> promise = Promise.promise();

        QueryModel queryModel = queryDecoder.buildGetItemWithNameExistsQuery(type, name);
        elasticsearchService.getSingleDocument(docIndex, queryModel)
            .onSuccess(result -> {
                if (result == null || ElasticsearchResponse.getTotalHits() == 0) {
                    promise.fail(DETAIL_ITEM_NOT_FOUND);
                } else {
                    LOGGER.debug("result: " + result.getSource());
                    promise.complete(ItemFactory.from(result.getSource()));
                }
            })
            .onFailure(err -> {
                LOGGER.debug("Error from elastic service: " + err.getCause());
                promise.fail(err.getLocalizedMessage());
            });
        return promise.future();
    }

}
