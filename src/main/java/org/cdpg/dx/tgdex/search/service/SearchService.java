package org.cdpg.dx.tgdex.search.service;

import io.vertx.ext.web.RoutingContext;

public interface SearchService {

    void postSearch(RoutingContext routingContext);
}
