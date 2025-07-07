package org.cdpg.dx.database.elastic.model;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.AggregationType;
import org.cdpg.dx.database.elastic.util.QueryType;

import java.util.*;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_DATA_BANK;

public class QueryDecoder {

  private static final Logger LOGGER = LogManager.getLogger(QueryDecoder.class);

  public QueryModel getQueryModel(JsonObject request) {
    String searchType = request.getString(SEARCH_TYPE);
    boolean isValidQuery = false;

    if ("getParentObjectInfo".equalsIgnoreCase(searchType)) {
      return buildGetParentObjectInfoQuery(request);
    }

    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    if (searchType.matches(SEARCH_CRITERIA_REGEX)) {
      LOGGER.debug("Info: searchCriteria block");
      new SearchCriteriaQueryDecorator(queryMap, request).add();
      isValidQuery = true;
    }

    if (searchType.matches(TEXTSEARCH_REGEX)) {
      LOGGER.debug("Info: Text search block");
      new TextSearchQueryDecorator(queryMap, request).add();
      isValidQuery = true;
    }

    new AccessPolicyQueryDecorator(queryMap, request).add();
    QueryModel excludeDatabankFalse = buildUploadStatusExclusion(ITEM_TYPE_DATA_BANK);
    QueryModel excludeAiModelFalse = buildUploadStatusExclusion(ITEM_TYPE_AI_MODEL);
    queryMap.get(FilterType.MUST_NOT).add(excludeDatabankFalse);
    queryMap.get(FilterType.MUST_NOT).add(excludeAiModelFalse);

    if (searchType.matches(RESPONSE_FILTER_REGEX)) {
      new ResponseFilterDecorator(queryMap, request).add();
      isValidQuery = true;
    }

    if (!isValidQuery) {
      throw new DxEsException("Invalid search query");
    }

    QueryModel q= new QueryModel();
    q.setQueries(getBoolQuery(queryMap));
    // Optional pagination support
    if (request.containsKey(LIMIT)) {
      int size = request.getInteger(LIMIT);
      q.setLimit(String.valueOf(size));
      if (request.containsKey(OFFSET)) {
        int offset = request.getInteger(OFFSET);
        q.setOffset(String.valueOf(offset));
      }

      return q;
    }

    for (QueryModel qm : queryMap.get(FilterType.FILTER)) {
      if (qm.getIncludeFields() != null) {
        q.setIncludeFields(qm.getIncludeFields());
      }
    }

    return q;
  }


  public QueryModel listMultipleItemTypesQuery(JsonObject request) {
    LOGGER.debug("listMultipleItemTypesQuery - {}", request);

    // Step 1: Initialize query map with all FilterTypes
    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    // Step 2: Apply decorators
    new AccessPolicyQueryDecorator(queryMap, request).add();
    new SearchCriteriaQueryDecorator(queryMap, request).add();
    new InstanceFilterQueryDecorator(queryMap, request).add();

    // Step 3: Exclude documents with uploadStatus = false for specific item types
    QueryModel excludeDatabankFalse = buildUploadStatusExclusion(ITEM_TYPE_DATA_BANK);
    QueryModel excludeAiModelFalse = buildUploadStatusExclusion(ITEM_TYPE_AI_MODEL);
    queryMap.get(FilterType.MUST_NOT).add(excludeDatabankFalse);
    queryMap.get(FilterType.MUST_NOT).add(excludeAiModelFalse);

    // Step 4: Build bool query
    QueryModel finalQuery = new QueryModel();
    finalQuery.setQueries(getBoolQuery(queryMap));

    // Step 5: Handle aggregations for each item type
    JsonArray filters = request.getJsonArray(FILTER);
    int size = request.getInteger(LIMIT, FILTER_PAGINATION_SIZE - request.getInteger(OFFSET, 0));
    List<QueryModel> aggs = new ArrayList<>();

    if (filters != null) {
      for (int i = 0; i < filters.size(); i++) {
        String filter = filters.getString(i);
        Map<String, Object> aggParams = Map.of(
            FIELD, filter + KEYWORD_KEY,
            SIZE_KEY, size
        );
        QueryModel agg = new QueryModel();
        agg.setAggregationType(AggregationType.TERMS);
        agg.setAggregationName(filter);
        agg.setAggregationParameters(aggParams);
        aggs.add(agg);
      }
      finalQuery.setAggregations(aggs);
    }

    // Step 6: Set pagination limit if present
    if (request.containsKey(LIMIT)) {
      finalQuery.setLimit(String.valueOf(size));
    }

    return finalQuery;
  }
  private QueryModel buildUploadStatusExclusion(String itemType) {
    return new QueryModel(QueryType.BOOL).setMustQueries(List.of(
        new QueryModel(QueryType.TERM).setQueryParameters(Map.of(
            FIELD, TYPE_KEYWORD,
            VALUE, itemType
        )),
        new QueryModel(QueryType.TERM).setQueryParameters(Map.of(
            FIELD, DATA_UPLOAD_STATUS,
            VALUE, false
        ))
    ));
  }

  private QueryModel buildGetParentObjectInfoQuery(JsonObject request) {
    String id = request.getString(ID);

    String[] fields = {
        "type", "provider", "ownerUserId", "resourceGroup", "name", "organizationId",
        "shortDescription", "resourceServer", "resourceServerRegURL", "cos", "cos_admin"
    };

    List<QueryModel> mustQueries = List.of(
        new QueryModel(QueryType.TERM).setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id))
    );

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    boolQuery.setMustQueries(mustQueries);
    boolQuery.setIncludeFields(Arrays.asList(fields));

    return boolQuery;
  }

  private QueryModel getBoolQuery(Map<FilterType, List<QueryModel>> filterQueries) {
    QueryModel boolQuery = new QueryModel(QueryType.BOOL);

    for (Map.Entry<FilterType, List<QueryModel>> entry : filterQueries.entrySet()) {
      switch (entry.getKey()) {
        case MUST -> boolQuery.setMustQueries(entry.getValue());
        case FILTER -> boolQuery.setFilterQueries(entry.getValue());
        case MUST_NOT -> boolQuery.setMustNotQueries(entry.getValue());
        case SHOULD -> boolQuery.setShouldQueries(entry.getValue());
      }
    }
    return boolQuery;
  }

  public QueryModel setCountAggregations() {
    QueryModel agg = new QueryModel();
    agg.setAggregationType(AggregationType.TERMS);
    agg.setAggregationName(RESULTS);
    Map<String, Object> aggParams = Map.of(
            FIELD, TYPE_KEYWORD
    );
    agg.setAggregationParameters(aggParams);
    return agg;
  }

}