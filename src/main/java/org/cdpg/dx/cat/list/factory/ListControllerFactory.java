package org.cdpg.dx.cat.list.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.cat.list.controller.ListController;
import org.cdpg.dx.cat.list.service.ListService;
import org.cdpg.dx.cat.list.service.ListServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public  class ListControllerFactory {

    public static ListController createListController(ElasticsearchService elasticsearchService, AuditingHandler auditingHandler) {
        ListService listService = new ListServiceImpl(elasticsearchService);
        return new ListController(auditingHandler,listService);
    }
}
