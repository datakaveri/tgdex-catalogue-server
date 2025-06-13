package org.cdpg.dx.tgdex.item.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.item.controller.ItemController;
import org.cdpg.dx.tgdex.item.service.ItemService;
import org.cdpg.dx.tgdex.item.service.ItemServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class ItemControllerFactory {

    public static ItemController createCrudController(AuditingHandler auditingHandler, ElasticsearchService elasticsearchService) {
        ItemService crudService = new ItemServiceImpl(elasticsearchService);
        return new ItemController(auditingHandler,crudService);
    }
}
