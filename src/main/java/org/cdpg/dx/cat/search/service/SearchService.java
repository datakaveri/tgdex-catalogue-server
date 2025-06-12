package org.cdpg.dx.cat.search.service;

import io.vertx.ext.web.RoutingContext;

public interface SearchService {

    void postSearch(RoutingContext routingContext);
}
