package org.cdpg.dx.tgdex.search.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.search.util.ResponseModel;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;

import static org.cdpg.dx.database.elastic.util.Constants.*;

/**
 * Implementation of the SearchService, handling request validation, query building, and execution.
 */
public class SearchServiceImpl implements SearchService {
    private static final Logger LOGGER = LogManager.getLogger(SearchServiceImpl.class);

    private final ElasticsearchService elasticsearchService;
    private final QueryDecoder queryDecoder;
    private final String docIndex;
    private final ValidatorService validatorService;

    public SearchServiceImpl(ElasticsearchService elasticsearchService,
                             String docIndex,
                             ValidatorService validatorService) {
        this.elasticsearchService = elasticsearchService;
        this.queryDecoder = new QueryDecoder();
        this.docIndex = docIndex;
        this.validatorService = validatorService;
    }

    @Override
    public Future<ResponseModel> postSearch(JsonObject requestBody) {
        try {
            String searchType = buildSearchType(requestBody);
            LOGGER.info("search type "+searchType);
            requestBody.put(SEARCH_TYPE, searchType);
        } catch (DxBadRequestException e) {
            LOGGER.error("Invalid search request: {}", e.getMessage());
            return Future.failedFuture(e);
        }

        // Validate, then search, then map to ResponseModel
        return validatorService.validateSearchQuery(requestBody)
                .compose(validated -> {
                    requestBody.put(SEARCH,true);
                    QueryModel queryModel = queryDecoder.getQueryModel(requestBody);
                    return elasticsearchService.search(docIndex, queryModel, "SOURCE");
                })
                .map(results -> new ResponseModel(
                        results,
                        requestBody.getInteger(SIZE_KEY),
                        requestBody.getInteger(PAGE_KEY)
                ))
                .onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));
    }

    /**
     * Builds the SEARCH_TYPE string based on present filters in the request body.
     * @throws DxBadRequestException if no valid filter is provided.
     */
    private String buildSearchType(JsonObject body) {
        boolean hasFilter = false;
        StringBuilder typeBuilder = new StringBuilder();

        if (body.getJsonArray(SEARCH_CRITERIA_KEY) != null
                && !body.getJsonArray(SEARCH_CRITERIA_KEY).isEmpty()) {
            typeBuilder.append(SEARCH_TYPE_CRITERIA);
            hasFilter = true;
        }
        if (body.getString(Q_VALUE) != null && !body.getString(Q_VALUE).isBlank()) {
            typeBuilder.append(SEARCH_TYPE_TEXT);
            hasFilter = true;
        }
        if (body.containsKey(FILTER)
                && body.getJsonArray(FILTER) != null
                && !body.getJsonArray(FILTER).isEmpty()) {
            typeBuilder.append(RESPONSE_FILTER);
            hasFilter = true;
        }
        if (!hasFilter) {
            throw new DxBadRequestException("Mandatory field(s) not provided");
        }
        LOGGER.info("search type "+typeBuilder.toString());

        return typeBuilder.toString();
    }
}
