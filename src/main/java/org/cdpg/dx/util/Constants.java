package org.cdpg.dx.util;

import io.vertx.core.http.HttpMethod;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class Constants {
  /** API Documentation endpoint */
  public static final String ROUTE_STATIC_SPEC = "/apis/spec";

  public static final String ROUTE_DOC = "/apis";

  // request/response params
  public static final String CONTENT_TYPE = "content-type";
  public static final String APPLICATION_JSON = "application/json";

  // Request's operationIds
  public static final String CREATE_ITEM="createItem";
  public static final String GET_ITEM="getItem";
  public static final String UPDATE_ITEM="updateItem";
  public static final String DELETE_ITEM="deleteItem";
  public static final String LIST_AVAILABLE_FILTER="listAvailableFilters";
  public static final String GET_LIST="listTypes";
  public static final String POST_SEARCH="search";

  public static final String ID = "id";
}
