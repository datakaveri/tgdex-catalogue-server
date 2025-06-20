package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.database.elastic.util.QueryType;

import java.util.List;
import java.util.Map;

import static org.cdpg.dx.database.elastic.util.Constants.*;

public class InstanceFilterQueryDecorator implements ElasticsearchQueryDecorator {

  private final Map<FilterType, List<QueryModel>> queryMap;
  private final JsonObject request;

  public InstanceFilterQueryDecorator(Map<FilterType, List<QueryModel>> queryMap,
                                      JsonObject request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {


    if (request.containsKey(INSTANCE)) {
      String instanceId = request.getString(INSTANCE);
      QueryModel instanceFilter = new QueryModel(QueryType.TERM)
          .setQueryParameters(Map.of(
              FIELD, INSTANCE + KEYWORD_KEY,
              VALUE, instanceId
          ));
      queryMap.get(FilterType.FILTER).add(instanceFilter);
    }
    return queryMap;
  }
}
