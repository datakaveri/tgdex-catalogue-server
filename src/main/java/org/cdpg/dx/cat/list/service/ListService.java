package org.cdpg.dx.cat.list.service;

import io.vertx.ext.web.RoutingContext;

public interface ListService {
    void getList(RoutingContext routingContext);

    void getAvailableFilters(RoutingContext routingContext);
}
