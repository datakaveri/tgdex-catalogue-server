package org.cdpg.dx.tgdex.crud.service;

import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class CrudServiceImpl implements CrudService{
    ElasticsearchService elasticsearchService;

    public CrudServiceImpl(ElasticsearchService elasticsearchService) {
        this.elasticsearchService=elasticsearchService;
    }

    @Override
    public void createItem(RoutingContext ctx) {

    }

    @Override
    public void getItem(RoutingContext routingContext) {

    }

    @Override
    public void deleteItem(RoutingContext routingContext) {

    }

    @Override
    public void updateItem(RoutingContext routingContext) {

    }
}
