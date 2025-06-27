package org.cdpg.dx.tgdex.list.controller;

import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.common.response.ResponseBuilder;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.list.service.ListService;

import static org.cdpg.dx.tgdex.util.Constants.RESULTS;
import static org.cdpg.dx.util.Constants.*;

public class ListController implements ApiController {
    AuditingHandler auditingHandler;
    ListService listService;
    private static final Logger LOGGER = LogManager.getLogger(ListController.class);

    public ListController(AuditingHandler auditingHandler, ListService listService){
        this.auditingHandler=auditingHandler;
        this.listService=listService;
    }

    @Override
    public void register(RouterBuilder builder) {
        builder
                .operation(LIST_AVAILABLE_FILTER)
                .handler(this::handleGetAvailableFilters)
                .handler(auditingHandler::handleApiAudit);
        LOGGER.debug("List Controller registered");
    }
    private void handleGetAvailableFilters(RoutingContext routingContext) {
        JsonObject requestBody = routingContext.body().asJsonObject();

        if (requestBody == null) {
            routingContext.response().setStatusCode(400).end("Invalid request body");
            return;
        }
    listService.getAvailableFilters(requestBody).onSuccess(successHandler -> {
        ResponseBuilder.sendSuccess(routingContext, successHandler.getResponse().getJsonArray(
            RESULTS),
            successHandler.getPaginationInfo());
    }).onFailure(failureHandler -> {
        LOGGER.error("Failed to fetch activity logs: {}", failureHandler.getMessage(), failureHandler);
        routingContext.fail(failureHandler);
    });
    }
}
