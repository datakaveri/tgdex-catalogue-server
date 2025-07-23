package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_DATA_BANK;

import java.util.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxEsException;
import org.cdpg.dx.database.elastic.util.AggregationType;
import org.cdpg.dx.database.elastic.util.QueryType;

public class QueryDecoder {
  private static final Logger LOGGER = LogManager.getLogger(QueryDecoder.class);

  public QueryModel getQueryModel(QueryDecoderRequestDTO request) {
    String searchType = request.getSearchType();
    boolean isValidQuery = false;

    if ("getParentObjectInfo".equalsIgnoreCase(searchType)) {
      LOGGER.info("getParentObjectInfo query");
      return buildGetParentObjectInfoQuery(request);
    }

    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    if (searchType != null && searchType.matches(SEARCH_CRITERIA_REGEX)) {
      LOGGER.debug("Info: searchCriteria block");
      new SearchCriteriaQueryDecorator(queryMap, request.getSearchCriteriaRequest()).add();
      isValidQuery = true;
    }

    if (searchType != null && searchType.matches(TEXTSEARCH_REGEX)) {
      LOGGER.debug("Info: Text search block");
      new TextSearchQueryDecorator(queryMap, request.getTextSearchRequest()).add();
      isValidQuery = true;
    }

    new AccessPolicyQueryDecorator(queryMap, request.getAccessPolicyRequest()).add();
    QueryModel excludeDatabankFalse = buildUploadStatusExclusion(ITEM_TYPE_DATA_BANK);
    QueryModel excludeAiModelFalse = buildUploadStatusExclusion(ITEM_TYPE_AI_MODEL);
    queryMap.get(FilterType.MUST_NOT).add(excludeDatabankFalse);
    queryMap.get(FilterType.MUST_NOT).add(excludeAiModelFalse);

    if (searchType != null && searchType.matches(RESPONSE_FILTER_REGEX)) {
      new ResponseFilterDecorator(queryMap, request.getResponseFilterRequest()).add();
      isValidQuery = true;
    }

    if (!isValidQuery) {
      throw new DxEsException("Invalid search query");
    }

    QueryModel q = new QueryModel();
    q.setQueries(getBoolQuery(queryMap));
    // Optional pagination support
    if (request.getSize() != null) {
      int size = request.getSize();
      q.setLimit(String.valueOf(size));
      if (request.getPage() != null) {
        int offset = (request.getPage() - 1) * size;
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

  public QueryModel listMultipleItemTypesQuery(QueryDecoderRequestDTO request) {
    LOGGER.debug("listMultipleItemTypesQuery - {}", request);
    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    new AccessPolicyQueryDecorator(queryMap, request.getAccessPolicyRequest()).add();
    new SearchCriteriaQueryDecorator(queryMap, request.getSearchCriteriaRequest()).add();
    new InstanceFilterQueryDecorator(queryMap, request.getInstanceFilterRequest()).add();

    QueryModel excludeDatabankFalse = buildUploadStatusExclusion(ITEM_TYPE_DATA_BANK);
    QueryModel excludeAiModelFalse = buildUploadStatusExclusion(ITEM_TYPE_AI_MODEL);
    queryMap.get(FilterType.MUST_NOT).add(excludeDatabankFalse);
    queryMap.get(FilterType.MUST_NOT).add(excludeAiModelFalse);

    QueryModel finalQuery = new QueryModel();
    finalQuery.setQueries(getBoolQuery(queryMap));

    List<String> filters = request.getFilter();
    int size =
        request.getSize() != null
            ? request.getSize()
            : FILTER_PAGINATION_SIZE - (request.getPage() != null ? request.getPage() : 1);
    List<QueryModel> aggs = new ArrayList<>();

    if (filters != null) {
      for (String filter : filters) {
        Map<String, Object> aggParams = Map.of(FIELD, filter + KEYWORD_KEY, SIZE_KEY, size);
        QueryModel agg = new QueryModel();
        agg.setAggregationType(AggregationType.TERMS);
        agg.setAggregationName(filter);
        agg.setAggregationParameters(aggParams);
        aggs.add(agg);
      }
      finalQuery.setAggregations(aggs);
    }

    if (request.getPage() != null) {
      finalQuery.setLimit(String.valueOf(size));
    }

    return finalQuery;
  }

  private QueryModel buildUploadStatusExclusion(String itemType) {
    return new QueryModel(QueryType.BOOL)
        .setMustQueries(
            List.of(
                new QueryModel(QueryType.TERM)
                    .setQueryParameters(
                        Map.of(
                            FIELD, TYPE_KEYWORD,
                            VALUE, itemType)),
                new QueryModel(QueryType.TERM)
                    .setQueryParameters(Map.of(FIELD, DATA_UPLOAD_STATUS, VALUE, false))));
  }

  private QueryModel buildGetParentObjectInfoQuery(QueryDecoderRequestDTO request) {
    String id = request.getId();
    String[] fields = {
      "type",
      "provider",
      "ownerUserId",
      "resourceGroup",
      "name",
      "organizationId",
      "shortDescription",
      "resourceServer",
      "resourceServerRegURL",
      "cos",
      "cos_admin"
    };
    List<QueryModel> mustQueries =
        List.of(
            new QueryModel(QueryType.TERM)
                .setQueryParameters(Map.of(FIELD, ID_KEYWORD, VALUE, id)));
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
    Map<String, Object> aggParams = Map.of(FIELD, TYPE_KEYWORD);
    agg.setAggregationParameters(aggParams);
    return agg;
  }
}
