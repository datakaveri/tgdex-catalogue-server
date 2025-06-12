package org.cdpg.dx.cat.crud.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.cat.crud.controller.CrudController;
import org.cdpg.dx.cat.crud.service.CrudService;
import org.cdpg.dx.cat.crud.service.CrudServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class CrudControllerFactory {

    public static CrudController createCrudController(AuditingHandler auditingHandler, ElasticsearchService elasticsearchService) {
        CrudService crudService = new CrudServiceImpl(elasticsearchService);
        return new CrudController(auditingHandler,crudService);
    }
}
