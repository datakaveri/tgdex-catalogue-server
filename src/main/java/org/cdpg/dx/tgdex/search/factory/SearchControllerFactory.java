package org.cdpg.dx.tgdex.search.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.tgdex.search.controller.SearchController;
import org.cdpg.dx.tgdex.search.service.SearchService;
import org.cdpg.dx.tgdex.search.service.SearchServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;
import org.cdpg.dx.tgdex.validator.service.ValidatorServiceImpl;

public class SearchControllerFactory {

    public static SearchController createSearchController(ElasticsearchService elasticsearchService, AuditingHandler auditingHandler, String docIndex, ValidatorService validatorService) {
        SearchService searchService = new SearchServiceImpl(elasticsearchService,docIndex,validatorService);
        return new SearchController(searchService, auditingHandler);
    }
}
