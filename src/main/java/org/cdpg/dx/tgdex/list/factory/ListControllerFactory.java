package org.cdpg.dx.tgdex.list.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.list.controller.ListController;
import org.cdpg.dx.tgdex.list.service.ListService;
import org.cdpg.dx.tgdex.list.service.ListServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;

public  class ListControllerFactory {

    public static ListController createListController(ElasticsearchService elasticsearchService, AuditingHandler auditingHandler, String docIndex ) {
        ListService listService = new ListServiceImpl(elasticsearchService,docIndex);
        return new ListController(auditingHandler,listService);
    }
}
