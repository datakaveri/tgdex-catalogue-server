package org.cdpg.dx.tgdex.list.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.list.service.ListService;

import static org.cdpg.dx.util.Constants.*;

public class ListController implements ApiController {
    AuditingHandler auditingHandler;
    ListService listService;
    public ListController(AuditingHandler auditingHandler, ListService listService){
        this.auditingHandler=auditingHandler;
        this.listService=listService;
    }

    @Override
    public void register(RouterBuilder builder) {
        builder
                .operation(LIST_AVAILABLE_FILTER)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handleGetAvailableFilters);
    }

    private void handleGetList(RoutingContext routingContext) {
        listService.getList(routingContext);
    }

    private void handleGetAvailableFilters(RoutingContext routingContext) {
    listService.getAvailableFilters(routingContext);
    }
}
