package org.cdpg.dx.cat.search.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.cat.apiserver.ApiController;
import org.cdpg.dx.cat.search.service.SearchService;

import static org.cdpg.dx.util.Constants.*;

public class SearchController implements ApiController {
    SearchService searchService;
    AuditingHandler  auditingHandler;
    public SearchController(SearchService searchService, AuditingHandler auditingHandler) {
        this.searchService=searchService;
        this.auditingHandler=auditingHandler;
    }

    @Override
    public void register(RouterBuilder builder) {
        builder
                .operation(POST_SEARCH)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handlePostSearch);
    }

    private void handlePostSearch(RoutingContext routingContext) {
        searchService.postSearch(routingContext);
    }
}
