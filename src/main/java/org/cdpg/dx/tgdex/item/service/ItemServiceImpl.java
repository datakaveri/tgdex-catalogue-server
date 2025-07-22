package org.cdpg.dx.tgdex.item.service;

import static org.cdpg.dx.database.elastic.util.Constants.ID_KEYWORD;
import static org.cdpg.dx.database.elastic.util.Constants.KEYWORD_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE_KEYWORD;
import static org.cdpg.dx.tgdex.util.Constants.*;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.database.elastic.util.QueryType;
import org.cdpg.dx.tgdex.item.util.RespBuilder;

public class ItemServiceImpl implements ItemService {

  private static final Logger LOGGER = LogManager.getLogger(ItemServiceImpl.class);
  private final ElasticsearchService elasticsearchService;
  private final String docIndex;

  public ItemServiceImpl(ElasticsearchService elasticsearchService, String docIndex) {
    this.elasticsearchService = elasticsearchService;
    this.docIndex = docIndex;
  }

  @Override
  public void createItem(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String id = body.getString(ID);

    /* check if the id is present */
    if (id == null || id.isBlank()) {
      LOGGER.error("Fail: ID not present in request body");
      ctx.response().setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_ID_NOT_FOUND)
              .getResponse()
      );
      return;
    }

    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));

    elasticsearchService.getSingleDocument(docIndex, termQuery)
        .onSuccess(existingDoc -> {
          if (existingDoc != null && ElasticsearchResponse.getTotalHits() > 0) {
            LOGGER.warn("Fail: Item with ID already exists");
            ctx.response().setStatusCode(409).end(
                new RespBuilder()
                    .withType(TYPE_ALREADY_EXISTS)
                    .withTitle(TITLE_ALREADY_EXISTS)
                    .withResult(id, INSERT, FAILED, "Fail: Doc Exists")
                    .withDetail("Fail: Doc Exists")
                    .getResponse()
            );
          } else {
            QueryModel queryModel = new QueryModel();
            queryModel.createQueryModelFromDocument(body);
            elasticsearchService.createDocuments(docIndex,
                    Collections.singletonList(queryModel))
                .onSuccess(createResult -> {
                  LOGGER.info("Success: Item created");
                  ctx.response().setStatusCode(201).end(
                      new RespBuilder()
                          .withType(TYPE_SUCCESS)
                          .withTitle(TITLE_SUCCESS)
                          .withDetail("Success: Item created")
                          .getResponse()
                  );
                })
                .onFailure(err -> {
                  LOGGER.error("Fail: createDocument failed", err);
                  ctx.response().setStatusCode(400).end(
                      new RespBuilder()
                          .withType(FAILED)
                          .withResult(id, INSERT, FAILED)
                          .withDetail("Insertion Failed")
                          .getResponse());
                });
          }
        })
        .onFailure(err -> {
          LOGGER.error("Fail: getDocument failed", err);
          ctx.response().setStatusCode(500).end(
              new RespBuilder()
                  .withType(TYPE_INTERNAL_SERVER_ERROR)
                  .withTitle(TITLE_INTERNAL_SERVER_ERROR)
                  .withDetail(err.getMessage())
                  .getResponse()
          );
        });
  }

  @Override
  public void getItem(RoutingContext ctx) {
    String id = ctx.pathParam(ID);

    if (id == null || id.isBlank()) {
      ctx.response().setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_ID_NOT_FOUND)
              .getResponse()
      );
      return;
    }

    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
    elasticsearchService.getSingleDocument(docIndex, termQuery)
        .onSuccess(result -> {
          if (result == null || ElasticsearchResponse.getTotalHits() == 0) {
            ctx.response().setStatusCode(404).end(
                new RespBuilder()
                    .withType(TYPE_ITEM_NOT_FOUND)
                    .withTitle(TITLE_ITEM_NOT_FOUND)
                    .withDetail("Item not found")
                    .getResponse()
            );
          } else {
            ctx.response().setStatusCode(200).end(
                new RespBuilder()
                    .withType(TYPE_SUCCESS)
                    .withTitle(TITLE_SUCCESS)
                    .withResult(result.toString())
                    .getResponse()
            );
          }
        })
        .onFailure(err -> {
          LOGGER.error("Fail: getDocument failed", err);
          ctx.response().setStatusCode(500).end(
              new RespBuilder()
                  .withType(TYPE_INTERNAL_SERVER_ERROR)
                  .withTitle(TITLE_INTERNAL_SERVER_ERROR)
                  .withDetail(err.getMessage())
                  .getResponse()
          );
        });
  }

  @Override
  public void deleteItem(RoutingContext ctx) {
    LOGGER.debug("Info: Deleting item");
    String id = ctx.pathParam(ID);

    if (id == null || id.isBlank()) {
      ctx.response().setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_ID_NOT_FOUND)
              .getResponse()
      );
      return;
    }

        /* the check query checks if any type item is present more than once.
                If it's present then the item cannot be deleted.  */
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
          LOGGER.debug("Success: Check index for doc");
          if (ElasticsearchResponse.getTotalHits() > 1) {
            LOGGER.error("Fail: Can't delete, doc has associated item;");
            ctx.response().setStatusCode(400).end(
                new RespBuilder()
                    .withType(TYPE_OPERATION_NOT_ALLOWED)
                    .withTitle(TITLE_OPERATION_NOT_ALLOWED)
                    .withResult(id, "Fail: Can't delete, doc has associated item")
                    .getResponse());
            return;
          } else if (ElasticsearchResponse.getTotalHits() < 1) {
            LOGGER.error("Fail: Doc doesn't exist, can't delete;");
            ctx.response().setStatusCode(200).end(
                new RespBuilder()
                    .withType(TYPE_ITEM_NOT_FOUND)
                    .withTitle(TITLE_ITEM_NOT_FOUND)
                    .withResult(id, "Fail: Doc doesn't exist, can't delete")
                    .getResponse());
            return;
          }

          String docId = result.getDocId();
          elasticsearchService.deleteDocument(docIndex, docId)
              .onSuccess(v -> ctx.response().setStatusCode(200).end(
                  new RespBuilder()
                      .withType(TYPE_SUCCESS)
                      .withTitle(TITLE_SUCCESS)
                      .withResult(id)
                      .withDetail("Success: Item deleted successfully")
                      .getResponse()
              ))
              .onFailure(err -> {
                LOGGER.error("Fail: deleteDocument failed", err);
                ctx.response().setStatusCode(500).end(
                    new RespBuilder()
                        .withType(TYPE_INTERNAL_SERVER_ERROR)
                        .withTitle(TITLE_INTERNAL_SERVER_ERROR)
                        .withDetail(err.getMessage())
                        .getResponse()
                );
              });
        })
        .onFailure(err -> {
          LOGGER.error("Fail: getDocument failed", err);
          ctx.response().setStatusCode(500).end(
              new RespBuilder()
                  .withType(TYPE_INTERNAL_SERVER_ERROR)
                  .withTitle(TITLE_INTERNAL_SERVER_ERROR)
                  .withDetail(err.getMessage())
                  .getResponse()
          );
        });
  }

  @Override
  public void updateItem(RoutingContext ctx) {
    JsonObject body = ctx.body().asJsonObject();
    String id = body.getString(ID);

    if (id == null || id.isBlank()) {
      ctx.response().setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_ID_NOT_FOUND)
              .getResponse()
      );
      return;
    }

    String type = body.getJsonArray(TYPE_KEY).getString(0);
    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    QueryModel termQuery = new QueryModel(QueryType.TERM);
    termQuery.setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id));
    QueryModel matchQuery = new QueryModel(QueryType.MATCH);
    matchQuery.setQueryParameters(Map.of(FIELD, TYPE_KEYWORD, VALUE, type));
    boolQuery.setMustQueries(List.of(termQuery, matchQuery));

    elasticsearchService.getSingleDocument(docIndex, boolQuery)
        .onSuccess(getRes -> {
          if (getRes == null || ElasticsearchResponse.getTotalHits() == 0) {
            ctx.response().setStatusCode(404).end(
                new RespBuilder()
                    .withType(TYPE_ITEM_NOT_FOUND)
                    .withTitle(TITLE_ITEM_NOT_FOUND)
                    .withDetail("Document not found for update")
                    .getResponse()
            );
          } else {
            QueryModel queryModel = new QueryModel();
            queryModel.createQueryModelFromDocument(body);
            elasticsearchService.updateDocument(docIndex, id, queryModel)
                .onSuccess(v -> {
                  LOGGER.info("Success: Item updated");
                  ctx.response().setStatusCode(200).end(
                      new RespBuilder()
                          .withType(TYPE_SUCCESS)
                          .withTitle(TITLE_SUCCESS)
                          .withDetail("Item updated successfully")
                          .getResponse()
                  );
                })
                .onFailure(err -> {
                  LOGGER.error("Fail: updateDocument failed", err);
                  ctx.response().setStatusCode(400).end(
                      new RespBuilder()
                          .withType(TYPE_OPERATION_NOT_ALLOWED)
                          .withTitle(TITLE_OPERATION_NOT_ALLOWED)
                          .withDetail(err.getMessage())
                          .getResponse()
                  );
                });
          }
        })
        .onFailure(err -> {
          LOGGER.error("Fail: getDocument failed", err);
          ctx.response().setStatusCode(500).end(
              new RespBuilder()
                  .withType(TYPE_INTERNAL_SERVER_ERROR)
                  .withTitle(TITLE_INTERNAL_SERVER_ERROR)
                  .withDetail(err.getMessage())
                  .getResponse()
          );
        });
  }
}
