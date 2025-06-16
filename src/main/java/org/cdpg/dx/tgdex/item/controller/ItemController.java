package org.cdpg.dx.tgdex.item.controller;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.auth.authorization.handler.AuthorizationHandler;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.item.service.ItemService;
import org.cdpg.dx.util.CheckIfTokenPresent;
import org.cdpg.dx.util.VerifyItemTypeAndRole;

import static org.cdpg.dx.util.Constants.*;

public class ItemController implements ApiController {
    AuditingHandler auditingHandler;
    ItemService itemService;
    CheckIfTokenPresent checkIfTokenPresent = new CheckIfTokenPresent();

    VerifyItemTypeAndRole verifyItemTypeAndRole = new VerifyItemTypeAndRole();
    public ItemController(AuditingHandler auditingHandler, ItemService itemService) {
        this.itemService =itemService;
        this.auditingHandler=auditingHandler;
    }

    @Override
    public void register(RouterBuilder builder) {
        builder
                .operation(CREATE_ITEM)
                .handler(checkIfTokenPresent)
                .handler(verifyItemTypeAndRole)
                .handler(this::handleCreateItem)
                .handler(auditingHandler::handleApiAudit);

        builder
                .operation(GET_ITEM)
                .handler(this::handleGetItem)
                .handler(auditingHandler::handleApiAudit);

        builder
                .operation(DELETE_ITEM)
                .handler(checkIfTokenPresent)
                .handler(verifyItemTypeAndRole)
                .handler(this::handleDeleteItem)
                .handler(auditingHandler::handleApiAudit);

        builder
                .operation(UPDATE_ITEM)
                .handler(checkIfTokenPresent)
                .handler(verifyItemTypeAndRole)
                .handler(auditingHandler::handleApiAudit)
                .handler(this::handleUpdateItem);
    }

    private void handleGetItem(RoutingContext routingContext) {
        itemService.getItem(routingContext);

    }

    private void handleDeleteItem(RoutingContext routingContext) {
        itemService.deleteItem(routingContext);

    }

    private void handleUpdateItem(RoutingContext routingContext) {
        itemService.updateItem(routingContext);

    }

    private void handleCreateItem(RoutingContext routingContext) {
        itemService.createItem(routingContext);
    }
}
