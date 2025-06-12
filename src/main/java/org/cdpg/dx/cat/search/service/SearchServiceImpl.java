package org.cdpg.dx.cat.search.service;

import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class SearchServiceImpl implements SearchService{
    ElasticsearchService elasticsearchService;
    public SearchServiceImpl(ElasticsearchService elasticsearchService) {
        this.elasticsearchService=elasticsearchService;
    }

    @Override
    public void postSearch(RoutingContext routingContext) {

    }
}
