package org.cdpg.dx.database.elastic.model;

import static org.cdpg.dx.database.elastic.util.Constants.ACCESS_POLICY;
import static org.cdpg.dx.tgdex.search.controller.SearchController.CLAIM_SUBJECT;
import static org.cdpg.dx.tgdex.util.Constants.*;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import org.cdpg.dx.database.elastic.util.QueryType;

public class AccessPolicyQueryDecorator implements ElasticsearchQueryDecorator {

  private final Map<FilterType, List<QueryModel>> queryMap;
  private final JsonObject request;

  public AccessPolicyQueryDecorator(Map<FilterType, List<QueryModel>> queryMap, JsonObject request) {
    this.queryMap = queryMap;
    this.request = request;
  }

  @Override
  public Map<FilterType, List<QueryModel>> add() {
    String sub = request.getString(CLAIM_SUBJECT);

    if (sub != null && !sub.isEmpty()) {
      // User is authenticated → allow: PUBLIC, RESTRICTED, PRIVATE owned

      // public
      QueryModel publicAccess = new QueryModel(QueryType.MATCH)
          .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, OPEN));

      // restricted
      QueryModel restrictedAccess = new QueryModel(QueryType.MATCH)
          .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, RESTRICTED));

      // private
      QueryModel privateAccess = new QueryModel(QueryType.MATCH)
          .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PRIVATE));

      // owner match
      QueryModel ownerMatch = new QueryModel(QueryType.MATCH)
          .setQueryParameters(Map.of(FIELD, PROVIDER_USER_ID, VALUE, sub));

      // private AND owned
      QueryModel privateOwned = new QueryModel(QueryType.BOOL)
          .setMustQueries(List.of(privateAccess, ownerMatch));

      // OR clause: public OR restricted OR private+owned
      QueryModel accessFilter = new QueryModel(QueryType.BOOL);
      accessFilter.setShouldQueries(List.of(publicAccess, restrictedAccess, privateOwned));
      accessFilter.setMinimumShouldMatch("1");

      queryMap.get(FilterType.MUST).add(accessFilter);

    } else {
      // Not authenticated → exclude PRIVATE
      QueryModel excludePrivate = new QueryModel(QueryType.MATCH)
          .setQueryParameters(Map.of(FIELD, ACCESS_POLICY, VALUE, PRIVATE));

      queryMap.get(FilterType.MUST_NOT).add(excludePrivate);
    }

    return queryMap;
  }
}

