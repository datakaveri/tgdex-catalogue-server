package org.cdpg.dx.tgdex.search.controller;

import static org.cdpg.dx.tgdex.util.Constants.RESULTS;
import static org.cdpg.dx.util.Constants.ASSET_SEARCH;
import static org.cdpg.dx.util.Constants.POST_COUNT_SEARCH;
import static org.cdpg.dx.util.Constants.POST_SEARCH;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.request.PostSearchRequestBuilder;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.database.elastic.model.QueryDecoderRequestDTO;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.search.service.SearchService;
import org.cdpg.dx.util.CheckIfTokenPresent;

/** Controller for handling search endpoints. */
public class SearchController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(SearchController.class);
  private static final CheckIfTokenPresent TOKEN_CHECK = new CheckIfTokenPresent();

  private final SearchService searchService;
  private final AuditingHandler auditingHandler;

  public SearchController(SearchService searchService, AuditingHandler auditingHandler) {
    this.searchService = searchService;
    this.auditingHandler = auditingHandler;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder
        .operation(POST_SEARCH)
        .handler(this::handleSearch)
        .handler(auditingHandler::handleApiAudit);

    builder
        .operation(POST_COUNT_SEARCH)
        .handler(this::handleCount)
        .handler(auditingHandler::handleApiAudit);

    builder
        .operation(ASSET_SEARCH)
        .handler(TOKEN_CHECK)
        .handler(this::handleAsset)
        .handler(auditingHandler::handleApiAudit);

    LOGGER.debug(
        "Registered SearchController operations: {}, {}, {}",
        POST_SEARCH,
        POST_COUNT_SEARCH,
        ASSET_SEARCH);
  }

  private void handleSearch(RoutingContext ctx) {
    LOGGER.debug("Received POST request on at search'{}'", POST_SEARCH);
    try {
      QueryDecoderRequestDTO queryDecoder =
          PostSearchRequestBuilder.fromRoutingContext(ctx)
              .setAssetSearch(false)
              .setCountApi(false)
              .build();
      searchService
          .postSearch(queryDecoder)
          .onSuccess(
              searchService -> {
                ResponseBuilder.sendSuccess(
                    ctx,
                    searchService.getElasticsearchResponses(),
                    searchService.getPaginationInfo(),
                    searchService.getTotalHits());
              })
          .onFailure(
              err -> {
                LOGGER.error("Search request failed: {}", err.getMessage(), err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Error processing search request: {}", e.getMessage(), e);
    }
  }

  private void handleCount(RoutingContext ctx) {
    LOGGER.debug("Received POST Count request on '{}'", POST_COUNT_SEARCH);
    QueryDecoderRequestDTO queryDecoderRequestDTO =
        PostSearchRequestBuilder.fromRoutingContext(ctx)
            .setAssetSearch(false)
            .setCountApi(true)
            .build();
    searchService
        .postCount(queryDecoderRequestDTO)
        .onSuccess(
            response -> {
              ResponseBuilder.sendSuccess(ctx, response.getResponse().getJsonArray(RESULTS));
            })
        .onFailure(
            err -> {
              LOGGER.error("Count request failed: {}", err.getMessage(), err);
              ctx.fail(err);
            });
  }

  private void handleAsset(RoutingContext ctx) {
    LOGGER.debug("Received POST Asset request on '{}'", ASSET_SEARCH);
    // Reuse search handler logic
    try {
      var queryDecoder =
          PostSearchRequestBuilder.fromRoutingContext(ctx)
              .setAssetSearch(true)
              .setCountApi(false)
              .build();
      searchService
          .postSearch(queryDecoder)
          .onSuccess(
              searchService -> {
                ResponseBuilder.sendSuccess(
                    ctx,
                    searchService.getElasticsearchResponses(),
                    searchService.getPaginationInfo(),
                    searchService.getTotalHits());
              })
          .onFailure(
              err -> {
                LOGGER.error("Search request failed: {}", err.getMessage(), err);
                ctx.fail(err);
              });

    } catch (Exception e) {
      LOGGER.error("Error processing search request: {}", e.getMessage(), e);
    }
  }
}
