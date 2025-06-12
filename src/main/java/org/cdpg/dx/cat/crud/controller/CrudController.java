package org.cdpg.dx.cat.crud.controller;

import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.cat.apiserver.ApiController;
import org.cdpg.dx.cat.crud.service.CrudService;

import static org.cdpg.dx.util.Constants.*;

public class CrudController implements ApiController {
    AuditingHandler auditingHandler;
    CrudService crudService;
    public CrudController(AuditingHandler auditingHandler,CrudService crudService) {
        this.crudService=crudService;
        this.auditingHandler=auditingHandler;
    }

    @Override
    public void register(RouterBuilder builder) {
        builder
                .operation(CREATE_ITEM)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handleCreateItem);

        builder
                .operation(GET_ITEM)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handleGetItem);

        builder
                .operation(DELETE_ITEM)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handleDeleteItem);

        builder
                .operation(UPDATE_ITEM)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handleUpdateItem);
    }

    private void handleGetItem(RoutingContext routingContext) {
        crudService.getItem(routingContext);

    }

    private void handleDeleteItem(RoutingContext routingContext) {
        crudService.deleteItem(routingContext);

    }

    private void handleUpdateItem(RoutingContext routingContext) {
        crudService.updateItem(routingContext);

    }

    private void handleCreateItem(RoutingContext routingContext) {
        crudService.createItem(routingContext);
    }
}
