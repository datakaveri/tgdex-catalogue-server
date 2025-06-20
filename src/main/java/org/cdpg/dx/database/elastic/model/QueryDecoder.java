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

public class QueryDecoder {
  private static final Logger LOGGER = LogManager.getLogger(QueryDecoder.class);


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

  public QueryModel getQueryModel(JsonObject request) {
    LOGGER.info("trying to get the query model");
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

    return q;
  }


  public QueryModel listMultipleItemTypesQuery(JsonObject request) {
    LOGGER.debug("listMultipleItemTypesQuery - {}", request);

    Map<FilterType, List<QueryModel>> queryMap = new HashMap<>();
    for (FilterType filterType : FilterType.values()) {
      queryMap.put(filterType, new ArrayList<>());
    }

    new SearchCriteriaQueryDecorator(queryMap, request).add();
    new InstanceFilterQueryDecorator(queryMap, request).add();

    QueryModel q =new QueryModel();
    q.setQueries(getBoolQuery(queryMap));

    JsonArray itemTypes = request.getJsonArray(TYPE);
    int size = request.getInteger(LIMIT, FILTER_PAGINATION_SIZE - request.getInteger(OFFSET, 0));

    for (int i = 0; i < itemTypes.size(); i++) {
      String itemType = itemTypes.getString(i);
      Map<String, Object> aggParams = Map.of(
              FIELD, itemType + KEYWORD_KEY,
              SIZE_KEY, size
      );
      QueryModel agg = new QueryModel();
      agg.setAggregationType(AggregationType.TERMS);
      agg.setAggregationParameters(aggParams);
      q.addAggregationsMap(Map.of(itemType, agg));
    }

    if (request.containsKey(LIMIT)) {
      q.setLimit(String.valueOf(size));
    }

    return q;
  }

  private QueryModel buildGetParentObjectInfoQuery(JsonObject request) {
    String id = request.getString(ID);

    String[] fields = {
            "type", "provider", "ownerUserId", "resourceGroup", "name", "organizationId",
            "shortDescription", "resourceServer", "resourceServerRegURL", "cos", "cos_admin"
    };

    List<QueryModel> mustQueries = List.of(
            new QueryModel(QueryType.TERM).setQueryParameters(Map.of(FIELD, "id.keyword", VALUE, id))
    );

    QueryModel boolQuery = new QueryModel(QueryType.BOOL);
    boolQuery.setMustQueries(mustQueries);
    boolQuery.setIncludeFields(Arrays.asList(fields));

    return boolQuery;
  }


  public QueryModel getQueryForValidator(JsonObject request){

//    {
//      "query": {
//      "bool": {
//        "should": [
//        { "match": { "id.keyword": "resource-001" }},
//        { "match": { "id.keyword": "resource-002" }},
//        { "match": { "id.keyword": "group-123" }},
//        {
//          "bool": {
//          "must": [
//          { "match": { "type.keyword": "iudx:Resource" }},
//          { "match": { "name.keyword": "Air Quality" }},
//          { "match": { "resourceGroup.keyword": "group-123" }}
//            ]
//        }
//        }
//      ]
//      }
//    },
//      "_source": ["type"]
//    }

//    QueryModel m1=new QueryModel(QueryType.MATCH);
//    m1.setQueryParameters(Map.of("type",""));
//
//    QueryModel m2=new QueryModel(QueryType.MATCH);
//    m2.setQueryParameters(Map.of("type",""));
//    QueryModel m3=new QueryModel(QueryType.MATCH);
//    m3.setQueryParameters(Map.of("type",""));
//
//    QueryModel m4=new QueryModel(QueryType.BOOL);
//    m4.setMustQueries(List.of(m1, m2, m3));
//    QueryModel m5=new QueryModel(QueryType.BOOL);
//    m5.setShouldQueries(List.of(m1,m2,m3,m4));
//
//    QueryModel m6 = new QueryModel();
//    m6.setQueries(m5);
//    m6.setIncludeFields(List.of("type"));
//

    QueryModel matchQuery=new QueryModel(QueryType.MATCH);
    matchQuery.setQueryParameters(Map.of("type","dataset"));
    QueryModel matchQuery1=new QueryModel(QueryType.MATCH);
    matchQuery1.setQueryParameters(Map.of("name.keyword", "water-quality"));
    QueryModel boolQuery=new QueryModel(QueryType.BOOL);
    boolQuery.setMustQueries(List.of(matchQuery, matchQuery1));

    QueryModel q = new QueryModel();
    q.setQueries(boolQuery);
    return q;
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

}