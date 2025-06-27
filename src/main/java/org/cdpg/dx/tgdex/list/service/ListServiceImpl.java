package org.cdpg.dx.tgdex.list.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.search.util.ResponseModel;
import org.cdpg.dx.tgdex.validator.service.ValidatorService;

import static org.cdpg.dx.database.elastic.util.Constants.*;

public class ListServiceImpl implements ListService{
    ElasticsearchService elasticsearchService;
    private static final Logger LOGGER = LogManager.getLogger(ListServiceImpl.class);
    private final QueryDecoder queryDecoder;
    String docIndex;
    private final ValidatorService validatorService;

    public ListServiceImpl(ElasticsearchService elasticsearchService , String docIndex,
                            ValidatorService validatorService) {
        this.elasticsearchService=elasticsearchService;
        this.validatorService = validatorService;
        this.queryDecoder = new QueryDecoder();
        this.docIndex=docIndex;
    }


    @Override
    public Future<ResponseModel> getAvailableFilters(JsonObject body) {

        JsonArray filters = body.getJsonArray(FILTER);
        if (filters == null || filters.isEmpty()) {
            return Future.failedFuture( "Missing or empty 'filter' array");
        }

        return validatorService.validateSearchQuery(body)
            .compose(validated -> {
                QueryModel queryModel = queryDecoder.listMultipleItemTypesQuery(body);
                return elasticsearchService.search(docIndex, queryModel, "AGGREGATION_LIST");
            })
            .map(ResponseModel::new)
            .onFailure(err -> LOGGER.error("Search execution failed: {}", err.getMessage()));

    }

}
