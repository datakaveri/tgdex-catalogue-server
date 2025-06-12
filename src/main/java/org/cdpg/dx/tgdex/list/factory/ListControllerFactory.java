package org.cdpg.dx.tgdex.list.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.list.controller.ListController;
import org.cdpg.dx.tgdex.list.service.ListService;
import org.cdpg.dx.tgdex.list.service.ListServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public  class ListControllerFactory {

    public static ListController createListController(ElasticsearchService elasticsearchService, AuditingHandler auditingHandler) {
        ListService listService = new ListServiceImpl(elasticsearchService);
        return new ListController(auditingHandler,listService);
    }
}
