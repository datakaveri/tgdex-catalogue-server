package org.cdpg.dx.tgdex.item.service;

import io.vertx.ext.web.RoutingContext;

public interface ItemService {
    void createItem(RoutingContext ctx);

    void getItem(RoutingContext routingContext);

    void deleteItem(RoutingContext routingContext);

    void updateItem(RoutingContext routingContext);
}
