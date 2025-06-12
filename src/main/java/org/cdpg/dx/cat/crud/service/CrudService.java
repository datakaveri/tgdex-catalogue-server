package org.cdpg.dx.cat.crud.service;

import io.vertx.ext.web.RoutingContext;

public interface CrudService {
    void createItem(RoutingContext ctx);

    void getItem(RoutingContext routingContext);

    void deleteItem(RoutingContext routingContext);

    void updateItem(RoutingContext routingContext);
}
