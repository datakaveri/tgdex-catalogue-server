package org.cdpg.dx.common.request;

import static org.cdpg.dx.database.elastic.util.Constants.*;
import static org.cdpg.dx.database.elastic.util.Constants.FILTER;
import static org.cdpg.dx.database.elastic.util.Constants.Q_VALUE;
import static org.cdpg.dx.database.elastic.util.Constants.RESPONSE_FILTER;
import static org.cdpg.dx.database.elastic.util.Constants.SEARCH_CRITERIA_KEY;
import static org.cdpg.dx.database.elastic.util.Constants.SEARCH_TYPE_CRITERIA;
import static org.cdpg.dx.database.elastic.util.Constants.SEARCH_TYPE_TEXT;

import io.vertx.core.MultiMap;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.exception.DxBadRequestException;
import org.cdpg.dx.database.elastic.model.*;

public class PostSearchRequestBuilder {
  private static final Logger LOGGER = LogManager.getLogger(PostSearchRequestBuilder.class);
  boolean isCountApi = false;
  boolean isAssetSearch = false;
  private RoutingContext routingContext;

  public PostSearchRequestBuilder(RoutingContext routingContext) {
    this.routingContext = routingContext;
  }

  public static PostSearchRequestBuilder fromRoutingContext(RoutingContext routingContext) {
    return new PostSearchRequestBuilder(routingContext);
  }

  public PostSearchRequestBuilder setCountApi(boolean countApi) {
    isCountApi = countApi;
    return this;
  }

  public PostSearchRequestBuilder setAssetSearch(boolean assetSearch) {
    isAssetSearch = assetSearch;
    return this;
  }

  public QueryDecoderRequestDTO build() {
    JsonObject requestBody = routingContext.getBodyAsJson();
    MultiMap params = routingContext.queryParams();
    return new QueryDecoderRequestDTO(
        buildSearchType(requestBody),
        getSize(params),
        getPage(params),
        getId(requestBody),
        getFilters(requestBody),
        getTextSearchRequest(requestBody),
        getSearchCriteriaRequest(requestBody),
        getAccessPolicyRequest(isAssetSearch, getSub(routingContext)),
        getInstanceFilterRequest(requestBody),
        getResponseFilterRequest(requestBody));
  }

  public int getSize(MultiMap params) {
    return params.get(SIZE_KEY) != null ? Integer.parseInt(params.get(SIZE_KEY)) : 100;
  }

  public int getPage(MultiMap params) {
    return params.get(PAGE_KEY) != null ? Integer.parseInt(params.get(PAGE_KEY)) : 1;
  }

  private String getSub(RoutingContext ctx) {
    try {
      if (ctx.user() != null) {
        return ctx.user().subject();
      }
    } catch (Exception e) {

    }
    return null;
  }

  private List<String> getFilters(JsonObject requestBody) {
    LOGGER.info(requestBody.getJsonArray("filter"));
    if (!requestBody.containsKey("filter")) {
      return new ArrayList<>();
    }
    JsonArray filterArray = requestBody.getJsonArray("filter");
    return filterArray.getList();
  }

  private String getId(JsonObject requestBody) {
    if (requestBody.containsKey("id")) {
      return requestBody.getString("id");
    }
    return null;
  }

  private String buildSearchType(JsonObject body) {
    boolean hasFilter = false;
    StringBuilder typeBuilder = new StringBuilder();

    if (body.getJsonArray(SEARCH_CRITERIA_KEY) != null
        && !body.getJsonArray(SEARCH_CRITERIA_KEY).isEmpty()) {
      typeBuilder.append(SEARCH_TYPE_CRITERIA);
      hasFilter = true;
    }
    if (body.getString(Q_VALUE) != null && !body.getString(Q_VALUE).isBlank()) {
      typeBuilder.append(SEARCH_TYPE_TEXT);
      hasFilter = true;
    }
    if (body.containsKey(FILTER)
        && body.getJsonArray(FILTER) != null
        && !body.getJsonArray(FILTER).isEmpty()) {
      typeBuilder.append(RESPONSE_FILTER);
      hasFilter = true;
    }
    if (!hasFilter) {
      throw new DxBadRequestException("Mandatory field(s) not provided");
    }
    return typeBuilder.toString();
  }

  private TextSearchRequestDTO getTextSearchRequest(JsonObject requestBody) {
    String qValue = requestBody.getString(Q_VALUE);
    boolean fuzzy = requestBody.getBoolean("fuzzy", false);
    boolean autoComplete = requestBody.getBoolean("autoComplete", false);
    return new TextSearchRequestDTO(qValue, fuzzy, autoComplete);
  }

  private SearchCriteriaRequestDTO getSearchCriteriaRequest(JsonObject requestBody) {
    if (requestBody.containsKey(SEARCH_CRITERIA_KEY)) {
      JsonArray searchCriteriaArray = requestBody.getJsonArray(SEARCH_CRITERIA_KEY);
      if (searchCriteriaArray == null || searchCriteriaArray.isEmpty()) {
        throw new DxBadRequestException("Search criteria cannot be empty");
      }

      List<SearchCriteriaDTO> searchCriteria =
          searchCriteriaArray.stream()
              .map(obj -> SearchCriteriaDTO.fromJson((JsonObject) obj))
              .toList();

      List<String> filter =
          requestBody.getJsonArray("filter") != null
              ? requestBody.getJsonArray("filter").getList()
              : new ArrayList<>();

      return new SearchCriteriaRequestDTO(searchCriteria, filter);
    }
    return null;
  }

  private AccessPolicyRequestDTO getAccessPolicyRequest(boolean isAssetSearch, String sub) {
    return new AccessPolicyRequestDTO(sub, isAssetSearch);
  }

  private InstanceFilterRequestDTO getInstanceFilterRequest(JsonObject requestBody) {
    return new InstanceFilterRequestDTO(requestBody.getString(INSTANCE));
  }

  private ResponseFilterRequestDTO getResponseFilterRequest(JsonObject requestBody) {
    return new ResponseFilterRequestDTO(
        buildSearchType(requestBody),
        isCountApi,
        requestBody.getJsonArray(ATTRIBUTE, new JsonArray()).getList(),
        requestBody.getJsonArray(FILTER, new JsonArray()).getList());
  }
}
