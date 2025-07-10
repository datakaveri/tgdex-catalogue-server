package org.cdpg.dx.tgdex.search.controller;

import static org.cdpg.dx.database.elastic.util.Constants.PAGE_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.SIZE_KEY;
import static org.cdpg.dx.tgdex.util.Constants.DEFAULT_MAX_PAGE_SIZE;
import static org.cdpg.dx.tgdex.util.Constants.DEFAULT_PAGE_NUMBER;
import static org.cdpg.dx.tgdex.util.Constants.MY_ASSETS_REQ;
import static org.cdpg.dx.tgdex.util.Constants.RESULTS;
import static org.cdpg.dx.tgdex.util.Constants.SUB;
import static org.cdpg.dx.util.Constants.ASSET_SEARCH;
import static org.cdpg.dx.util.Constants.POST_COUNT_SEARCH;
import static org.cdpg.dx.util.Constants.POST_SEARCH;

import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.search.service.SearchService;
import org.cdpg.dx.util.CheckIfTokenPresent;

/**
 * Controller for handling search endpoints.
 */
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
    builder.operation(POST_SEARCH)
        .handler(this::handleSearch)
        .handler(auditingHandler::handleApiAudit);

    builder.operation(POST_COUNT_SEARCH)
        .handler(this::handleCount)
        .handler(auditingHandler::handleApiAudit);

    builder.operation(ASSET_SEARCH)
        .handler(TOKEN_CHECK)
        .handler(this::handleAsset)
        .handler(auditingHandler::handleApiAudit);

    LOGGER.debug("Registered SearchController operations: {}, {}, {}",
        POST_SEARCH, POST_COUNT_SEARCH, ASSET_SEARCH);
  }

  private void handleSearch(RoutingContext ctx) {
    LOGGER.debug("Received POST request on '{}'", POST_SEARCH);
    process(ctx,
        searchService::postSearch,
        (c, resp) -> ResponseBuilder.sendSuccess(c,
            resp.getElasticsearchResponses(),
            resp.getPaginationInfo(),
            resp.getTotalHits()));
  }

  private void handleCount(RoutingContext ctx) {
    LOGGER.debug("Received POST Count request on '{}'", POST_COUNT_SEARCH);
    process(ctx,
        searchService::postCount,
        (c, resp) -> ResponseBuilder.sendSuccess(c,
            resp.getResponse().getJsonArray(RESULTS)));
  }

  private void handleAsset(RoutingContext ctx) {
    LOGGER.debug("Received POST Asset request on '{}'", ASSET_SEARCH);
    JsonObject body = prepareRequestBody(ctx);
      if (body == null) {
          return;
      }

    MultiMap params = ctx.request().params();
    params.forEach(entry -> body.put(entry.getKey(), entry.getValue()));
    body.put(MY_ASSETS_REQ, true);
    LOGGER.debug("Asset request body: {}", body);

    // Reuse search handler logic
    process(ctx,
        searchService::postSearch,
        (c, resp) -> ResponseBuilder.sendSuccess(c,
            resp.getElasticsearchResponses(),
            resp.getPaginationInfo(),
            resp.getTotalHits()));
  }

  /**
   * Generic request processor: prepares body, invokes service, and handles success/failure.
   */
  private <T> void process(RoutingContext ctx,
                           Function<JsonObject, Future<T>> serviceCall,
                           BiConsumer<RoutingContext, T> onSuccess) {
    JsonObject body = prepareRequestBody(ctx);
      if (body == null) {
          return;
      }

    serviceCall.apply(body)
        .onSuccess(result -> onSuccess.accept(ctx, result))
        .onFailure(err -> handleFailure(ctx, err));
  }

  private JsonObject prepareRequestBody(RoutingContext ctx) {
    JsonObject body = ctx.getBodyAsJson();
    if (body == null) {
      LOGGER.error("Invalid or missing Request body");
      ctx.fail(400);
      return null;
    }
    injectSubject(ctx, body);
    addPagination(ctx, body);
    return body;
  }

  private void injectSubject(RoutingContext ctx, JsonObject body) {
    try {
      if (ctx.user() != null) {
        String subject = ctx.user().principal().getString(SUB);
        body.put(SUB, subject);
      }
    } catch (Exception e) {
      LOGGER.warn("User subject not available: {}", e.getMessage());
    }
  }

  private void addPagination(RoutingContext ctx, JsonObject body) {
    int size = parseOrDefault(ctx.request().getParam(SIZE_KEY), DEFAULT_MAX_PAGE_SIZE);
    int page = parseOrDefault(ctx.request().getParam(PAGE_KEY), DEFAULT_PAGE_NUMBER);
    body.put(SIZE_KEY, size).put(PAGE_KEY, page);
  }

  private int parseOrDefault(String val, int defaultVal) {
      if (val == null) {
          return defaultVal;
      }
    try {
      return Integer.parseInt(val);
    } catch (NumberFormatException e) {
      LOGGER.warn("Invalid integer '{}', using default {}", val, defaultVal);
      return defaultVal;
    }
  }

  private void handleFailure(RoutingContext ctx, Throwable err) {
    LOGGER.error("Search request failed: {}", err.getMessage(), err);
    ctx.fail(err);
  }
}
