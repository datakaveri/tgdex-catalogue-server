package org.cdpg.dx.tgdex.item.service;

import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ItemServiceImpl implements ItemService {
    ElasticsearchService elasticsearchService;

    public ItemServiceImpl(ElasticsearchService elasticsearchService) {
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
