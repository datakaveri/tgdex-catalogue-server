package org.cdpg.dx.tgdex.list.service;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.tgdex.search.util.ResponseModel;

public interface ListService {
    Future<ResponseModel> getAvailableFilters(JsonObject jsonObject);
}
