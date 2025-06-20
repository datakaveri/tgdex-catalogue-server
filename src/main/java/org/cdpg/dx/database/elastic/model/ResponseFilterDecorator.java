package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.QueryType;

import java.util.List;
import java.util.Map;

import static org.cdpg.dx.database.elastic.util.Constants.*;

public class ResponseFilterDecorator implements ElasticsearchQueryDecorator {
  private static final Logger LOGGER = LogManager.getLogger(ResponseFilterDecorator.class);

  private final Map<FilterType, List<QueryModel>> queryMap;
  private final JsonObject request;

  public ResponseFilterDecorator(Map<FilterType, List<QueryModel>> queryMap, JsonObject request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    String searchType = request.getString(SEARCH_TYPE);
    LOGGER.info("Search type: {}", request);
    if (!searchType.matches(RESPONSE_FILTER_REGEX)) {
      return queryMap;
    }

    if (!request.getBoolean(SEARCH, false)) {
      throw new DxEsException("Operation not allowed: 'search' must be true for filtering");
    }

    JsonArray sourceFilter = request.getJsonArray(ATTRIBUTE);
    if (sourceFilter == null || sourceFilter.isEmpty()) {
      sourceFilter = request.getJsonArray(FILTER);
    }

    if (sourceFilter == null || sourceFilter.isEmpty()) {
      throw new DxEsException("Missing response filter: 'attribute' or 'filter' is required");
    }
    
    QueryModel sourceConfigModel = new QueryModel(QueryType.BOOL);
    sourceConfigModel.setIncludeFields(sourceFilter.getList());

    queryMap.get(FilterType.FILTER).add(sourceConfigModel);

    for(FilterType f:queryMap.keySet()){
      LOGGER.info("Key, Value {},{} ",f,queryMap.get(f));
    }
    return queryMap;
  }
}

