package org.cdpg.dx.tgdex.search.controller;


import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.search.service.SearchService;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.*;
import static org.cdpg.dx.util.Constants.*;

/**
 * Controller for handling search endpoints.
 */
public class SearchController implements ApiController {
    private static final Logger LOGGER = LogManager.getLogger(SearchController.class);

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
                .handler(this::handlePostSearch)
                .handler(auditingHandler::handleApiAudit);
        builder
                .operation(POST_COUNT_SEARCH)
                .handler(this::handlePostCount)
                .handler(auditingHandler::handleApiAudit);

        builder
                .operation(ASSET_SEARCH)
                .handler(this::handlePostCount)
                .handler(auditingHandler::handleApiAudit);


        LOGGER.debug("Registered SearchController for operation '{}'.", POST_SEARCH);
    }

    private void handlePostSearch(RoutingContext ctx) {
        LOGGER.debug("Received POST request on '{}'", POST_SEARCH);
        JsonObject body = prepareRequestBody(ctx);
        if (body == null) return;

        searchService.postSearch(body)
                .onSuccess(response -> {
                    LOGGER.info("TOTAL HITS {}", response.getTotalHits());
                    ResponseBuilder.sendSuccess(ctx, response.getElasticsearchResponses(), response.getPaginationInfo(), response.getTotalHits());
                })
                .onFailure(err -> handleSearchFailure(ctx, err));
    }

    private void handlePostCount(RoutingContext ctx) {
        LOGGER.debug("Received POST Count request on '{}'", POST_COUNT_SEARCH);
        JsonObject body = prepareRequestBody(ctx);
        if (body == null) return;

        searchService.postCount(body)
                .onSuccess(response -> ResponseBuilder.sendSuccess(ctx, response.getResults()))
                .onFailure(err -> handleSearchFailure(ctx, err));
    }

    private JsonObject prepareRequestBody(RoutingContext ctx) {
        JsonObject body = ctx.getBodyAsJson();
        if (body == null) {
            LOGGER.error("Invalid or missing Request body");
            ctx.fail(400);
            return null;
        }

        injectSubjectClaim(ctx, body);
        addPaginationParams(ctx, body);
        return body;
    }

    private void injectSubjectClaim(RoutingContext ctx, JsonObject body) {
        try {
            if (ctx.user() != null) {
                String subject = ctx.user().principal().getString(SUB);
                body.put(SUB, subject);
            }
        } catch (Exception e) {
            LOGGER.warn("User subject not available: {}", e.getMessage());
        }
    }

    private void addPaginationParams(RoutingContext ctx, JsonObject body) {
        int size = parseIntOrDefault(ctx.request().getParam(SIZE_KEY), DEFAULT_MAX_PAGE_SIZE);
        int page = parseIntOrDefault(ctx.request().getParam(PAGE_KEY), DEFAULT_PAGE_NUMBER);
        body.put(SIZE_KEY, size);
        body.put(PAGE_KEY, page);
    }

    private void handleSearchFailure(RoutingContext ctx, Throwable err) {
        LOGGER.error("Search request failed: {}", err.getMessage(), err);
        ctx.fail(err);
    }

    /**
     * Parses a string to int, falling back to a default value if parsing fails or input is null.
     */
    private int parseIntOrDefault(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid integer '{}', using default {}", value, defaultValue);
            return defaultValue;
        }
    }
}