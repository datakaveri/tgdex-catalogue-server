package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.QueryType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.LESS_THAN;

public class SearchCriteriaQueryDecorator implements ElasticsearchQueryDecorator {
  private static final Logger LOGGER = LogManager.getLogger(SearchCriteriaQueryDecorator.class);

  private final Map<FilterType, List<QueryModel>> queryMap;
  private final JsonObject request;

  public SearchCriteriaQueryDecorator(Map<FilterType, List<QueryModel>> queryMap,
                                      JsonObject request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    if (!request.containsKey(SEARCH_CRITERIA_KEY)) {
      return queryMap;
    }
    LOGGER.info("Adding searchCriteria query decorator "+request) ;
    JsonArray criteria = request.getJsonArray(SEARCH_CRITERIA_KEY);
    if (criteria == null || criteria.isEmpty()) {
      throw new DxEsException("Invalid Property Value: Empty searchCriteria");
    }

    List<QueryModel> mustList = new ArrayList<>();

    for (int i = 0; i < criteria.size(); i++) {
      JsonObject criterion = criteria.getJsonObject(i);
      String field = criterion.getString(FIELD);
      JsonArray values = criterion.getJsonArray(VALUES);
      String type = criterion.getString(SEARCH_TYPE, TERM);
      LOGGER.info("Searchtype, field, values, cST {},{},{}",field,values,type);

      switch (type) {
        case TERM:
          mustList.add(buildTermQuery(field, values));
          break;

        case BETWEEN_RANGE:
        case BETWEEN_TEMPORAL:
          if (values.size() != 2) {
            throw new DxEsException("Expected 2 values for between-type search");
          }
          mustList.add(buildRangeQuery(field, Map.of(
              GREATER_THAN_EQUALS, values.getString(0),
              LESS_THAN_EQUALS, values.getString(1)
          )));
          break;

        case BEFORE_RANGE:
        case BEFORE_TEMPORAL:
          mustList.add(buildRangeQuery(field, Map.of(LESS_THAN, values.getString(0))));
          break;

        case AFTER_RANGE:
        case AFTER_TEMPORAL:
          mustList.add(buildRangeQuery(field, Map.of(GREATER_THAN, values.getString(0))));
          break;

        default:
          throw new DxEsException("Unsupported searchType: " + type);
      }
    }

    queryMap.computeIfAbsent(FilterType.FILTER, k -> new ArrayList<>())
        .add(new QueryModel(QueryType.BOOL).setMustQueries(mustList));
    LOGGER.info("queryMap "+queryMap);
    {
      LOGGER.info("Size "+queryMap.get(FilterType.FILTER).size());
      for(QueryModel queryModel:queryMap.get(FilterType.FILTER)){
        LOGGER.info("query " +queryModel.toJson());

      }
    }
    return queryMap;
  }

  private QueryModel buildTermQuery(String field, JsonArray values) {
    List<QueryModel> shouldQueries = new ArrayList<>();

    for (int i = 0; i < values.size(); i++) {
      String value = values.getString(i);

      // Case: description or location (match/fuzzy match)
      if (DESCRIPTION_ATTR.equals(field) || field.startsWith(LOCATION)) {
        shouldQueries.add(
            new QueryModel(QueryType.MATCH).setQueryParameters(Map.of(FIELD, field, VALUE, value)));

        if ("true".equals(request.getString(FUZZY)) && DESCRIPTION_ATTR.equals(field)) {
          shouldQueries.add(new QueryModel(QueryType.MATCH)
              .setQueryParameters(
                  Map.of(FIELD, field, VALUE, value, FUZZY, "AUTO", OPERATOR, "or")));
        }

      } else if (TAGS.equals(field)) {
        // Case: tags → match_phrase
        shouldQueries.add(new QueryModel(QueryType.MATCH_PHRASE).setQueryParameters(
            Map.of(FIELD, field, VALUE, value)));

        if ("true".equals(request.getString(FUZZY))) {
          shouldQueries.add(new QueryModel(QueryType.MATCH)
              .setQueryParameters(
                  Map.of(FIELD, field, VALUE, value, FUZZY, "AUTO", OPERATOR, "or")));
        }

      } else if (FILE_FORMAT.equals(field)) {
        // Case: fileFormat → wildcard (case-insensitive)
        shouldQueries.add(new QueryModel(QueryType.WILDCARD).setQueryParameters(Map.of(
            FIELD, field + KEYWORD_KEY,
            VALUE, value.toLowerCase(),
            CASE_INSENSITIVE, true
        )));

      } else {
        // Fallback: .keyword match or raw match
        String searchField = field.endsWith(KEYWORD_KEY) ? field : field + KEYWORD_KEY;
        shouldQueries.add(new QueryModel(QueryType.TERM).setQueryParameters(
            Map.of(FIELD, searchField, VALUE, value)));

        if ("true".equals(request.getString(FUZZY)) &&
            (LABEL.equals(field) || INSTANCE.equals(field))) {
          shouldQueries.add(new QueryModel(QueryType.MATCH)
              .setQueryParameters(
                  Map.of(FIELD, field, VALUE, value, FUZZY, "AUTO", OPERATOR, "or")));
        }
      }
    }

    QueryModel queryModel = new QueryModel(QueryType.BOOL);
    queryModel.setShouldQueries(shouldQueries);
    queryModel.setMinimumShouldMatch("1");
    return queryModel;
  }

  private QueryModel buildRangeQuery(String field, Map<String, String> operators) {
    Map<String, Object> rangeParams = new HashMap<>();
    rangeParams.put(FIELD, field);
    rangeParams.putAll(operators);
    return new QueryModel(QueryType.RANGE).setQueryParameters(rangeParams);
  }

}
