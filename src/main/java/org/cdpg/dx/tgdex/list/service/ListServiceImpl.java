package org.cdpg.dx.tgdex.list.service;

import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ListServiceImpl implements ListService{
    ElasticsearchService elasticsearchService;
    public ListServiceImpl(ElasticsearchService elasticsearchService) {
        this.elasticsearchService=elasticsearchService;
    }

    @Override
    public void getList(RoutingContext routingContext) {

    }

    @Override
    public void getAvailableFilters(RoutingContext routingContext) {

    }
}
