package org.cdpg.dx.tgdex.search.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import org.cdpg.dx.database.elastic.model.AggregationResponse;
import org.cdpg.dx.tgdex.search.util.ResponseModel;

public interface SearchService {

    Future<ResponseModel> postSearch(JsonObject requestBody);
    Future<ResponseModel> postCount(JsonObject requestBody);
}
