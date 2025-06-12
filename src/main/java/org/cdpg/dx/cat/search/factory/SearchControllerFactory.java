package org.cdpg.dx.cat.search.factory;

import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.cat.search.controller.SearchController;
import org.cdpg.dx.cat.search.service.SearchService;
import org.cdpg.dx.cat.search.service.SearchServiceImpl;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;

public class SearchControllerFactory {

    public static SearchController createSearchController(ElasticsearchService elasticsearchService, AuditingHandler auditingHandler) {
        SearchService searchService = new SearchServiceImpl(elasticsearchService);
        return new SearchController(searchService, auditingHandler);
    }
}
