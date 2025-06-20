package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.QueryType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.cdpg.dx.database.elastic.util.Constants.*;

public class TextSearchQueryDecorator implements ElasticsearchQueryDecorator {

  private final Map<FilterType, List<QueryModel>> queryMap;
  private final JsonObject request;

  public TextSearchQueryDecorator(Map<FilterType, List<QueryModel>> queryMap,
                                  JsonObject request) {
    this.queryMap = queryMap;
    this.request = request;
  }
//  {
//    "query": {
//    "bool": {
//      "must": [
//      { "match": { "type": "dataset" }},
//      { "match": { "name.keyword": "water-quality" }}
//      ]
//    }
//  }
//  }


  @Override
  public Map<FilterType, List<QueryModel>> add() {

    if (!request.containsKey(Q_VALUE) || request.getString(Q_VALUE).isBlank()) {
      throw new DxEsException("bad text query values");
    }

    String textAttr = request.getString(Q_VALUE);
    boolean isFuzzy = "true".equals(request.getString(FUZZY));
    boolean isAutoComplete = "true".equals(request.getString(AUTO_COMPLETE));

    List<QueryModel> shouldQueries = new ArrayList<>();

    if (isFuzzy) {
      shouldQueries.add(
          new QueryModel(QueryType.MULTI_MATCH)
              .setQueryParameters(Map.of(
                  "fields", List.of("label", "tags", "description"),
                  "query", textAttr,
                  "fuzziness", "AUTO",
                  "boost", "1.0"
              )));
    }

    if (isAutoComplete) {
      shouldQueries.add(
          new QueryModel(QueryType.MULTI_MATCH)
              .setQueryParameters(Map.of(
                  "fields", List.of("label", "tags", "description"),
                  "query", textAttr,
                  "type", "BoolPrefix",
                  "boost", "5.0"
              )));
    }

    if (!isFuzzy && !isAutoComplete) {
      shouldQueries.add(
          new QueryModel(QueryType.TEXT)
              .setQueryParameters(Map.of(Q_VALUE, textAttr)));
    }

    QueryModel boolModel = new QueryModel(QueryType.BOOL);
    boolModel.setShouldQueries(shouldQueries);
    boolModel.setMinimumShouldMatch("1");

    queryMap.computeIfAbsent(FilterType.MUST, k -> new ArrayList<>()).add(boolModel);
    return queryMap;
  }
}
