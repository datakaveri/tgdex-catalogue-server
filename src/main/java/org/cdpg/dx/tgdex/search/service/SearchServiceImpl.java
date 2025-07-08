package org.cdpg.dx.tgdex.search.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.AggregationResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.search.util.ResponseModel;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;

import java.util.List;

import static org.cdpg.dx.database.elastic.util.Constants.*;

public class SearchServiceImpl implements SearchService {
    private static final Logger LOGGER = LogManager.getLogger(SearchServiceImpl.class);

    private final ElasticsearchService elasticsearchService;
    private final QueryDecoder queryDecoder;
    private final String docIndex;
    private final ValidatorService validatorService;

    public SearchServiceImpl(ElasticsearchService elasticsearchService, String docIndex, ValidatorService validatorService) {
        this.elasticsearchService = elasticsearchService;
        this.queryDecoder = new QueryDecoder();
        this.docIndex = docIndex;
        this.validatorService = validatorService;
    }

    @Override
    public Future<ResponseModel> postSearch(JsonObject requestBody) {
        try {
            setSearchType(requestBody);
        } catch (DxBadRequestException e) {
            LOGGER.error("Invalid search request: {}", e.getMessage());
            return Future.failedFuture(e);
        }

        return validatorService.validateSearchQuery(requestBody).compose(validated -> {
            requestBody.put(SEARCH, true);
            QueryModel queryModel = queryDecoder.getQueryModel(requestBody);
            return elasticsearchService.search(docIndex, queryModel, SOURCE_ONLY);
        }).map(results -> new ResponseModel(results, getIntValue(requestBody, SIZE_KEY), getIntValue(requestBody, PAGE_KEY))).onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));
    }

    @Override
    public Future<AggregationResponse> postCount(JsonObject requestBody) {
        try {
            setSearchType(requestBody);
        } catch (DxBadRequestException e) {
            LOGGER.error("Invalid search request: {}", e.getMessage());
            return Future.failedFuture(e);
        }

        return validatorService.validateSearchQuery(requestBody).compose(validated -> {
            QueryModel queryModel = queryDecoder.getQueryModel(requestBody);
            queryModel.setAggregations(List.of(queryDecoder.setCountAggregations()));
            return elasticsearchService.countByAggregation(docIndex, queryModel);
        }).onFailure(err -> LOGGER.error("Count execution failed: {}", err.getMessage()));
    }

    /**
     * Sets the SEARCH_TYPE in the request body based on present filters.
     *
     * @throws DxBadRequestException if no valid filter is provided.
     */
    private void setSearchType(JsonObject body) {
        String searchType = buildSearchType(body);
        LOGGER.info("search type {}", searchType);
        body.put(SEARCH_TYPE, searchType);
    }

    /**
     * Builds the SEARCH_TYPE string based on present filters in the request body.
     *
     * @throws DxBadRequestException if no valid filter is provided.
     */
    private String buildSearchType(JsonObject body) {
        boolean hasFilter = false;
        StringBuilder typeBuilder = new StringBuilder();

        if (body.getJsonArray(SEARCH_CRITERIA_KEY) != null && !body.getJsonArray(SEARCH_CRITERIA_KEY).isEmpty()) {
            typeBuilder.append(SEARCH_TYPE_CRITERIA);
            hasFilter = true;
        }
        if (body.getString(Q_VALUE) != null && !body.getString(Q_VALUE).isBlank()) {
            typeBuilder.append(SEARCH_TYPE_TEXT);
            hasFilter = true;
        }
        if (body.containsKey(FILTER) && body.getJsonArray(FILTER) != null && !body.getJsonArray(FILTER).isEmpty()) {
            typeBuilder.append(RESPONSE_FILTER);
            hasFilter = true;
        }
        if (!hasFilter) {
            throw new DxBadRequestException("Mandatory field(s) not provided");
        }
        return typeBuilder.toString();
    }

    private Integer getIntValue(JsonObject obj, String key) {
        Object value = obj.getValue(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                LOGGER.warn("Invalid integer format for key {}: {}", key, value);
                return null;
            }
        }
        return null;
    }
}