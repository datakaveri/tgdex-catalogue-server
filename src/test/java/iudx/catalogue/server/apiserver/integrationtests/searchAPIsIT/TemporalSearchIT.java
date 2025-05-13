package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static iudx.catalogue.server.apiserver.util.Constants.APPLICATION_JSON;
import static iudx.catalogue.server.util.Constants.AFTER_TEMPORAL;
import static iudx.catalogue.server.util.Constants.BEFORE_TEMPORAL;
import static iudx.catalogue.server.util.Constants.BETWEEN_TEMPORAL;
import static iudx.catalogue.server.util.Constants.FIELD;
import static iudx.catalogue.server.util.Constants.ITEM_TYPE_APPS;
import static iudx.catalogue.server.util.Constants.SEARCH_CRITERIA;
import static iudx.catalogue.server.util.Constants.SEARCH_TYPE;
import static iudx.catalogue.server.util.Constants.TYPE;
import static iudx.catalogue.server.util.Constants.TYPE_INVALID_PROPERTY_VALUE;
import static iudx.catalogue.server.util.Constants.TYPE_SUCCESS;
import static iudx.catalogue.server.util.Constants.VALUES;
import static iudx.catalogue.server.validator.Constants.ITEM_CREATED_AT;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using temporal relations (after, before, between)
 * using POST method with JSON body (JsonObject based).
 */
@ExtendWith(RestAssuredConfiguration.class)
public class TemporalSearchIT {

  private JsonObject baseTemporalCriteria(String searchType, String field, JsonArray values) {
    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, searchType)
        .put(FIELD, field)
        .put(VALUES, values);

    return new JsonObject()
        .put(SEARCH_TYPE, SEARCH_CRITERIA)
        .put(SEARCH_CRITERIA, new JsonArray().add(criterion));
  }

  private JsonObject baseTermCriteria(String field, JsonArray values) {
    JsonObject criterion = new JsonObject()
        .put("searchType", "term")
        .put("field", field)
        .put("values", values);

    return new JsonObject()
        .put("searchType", "searchCriteria")
        .put("searchCriteria", new JsonArray().add(criterion));
  }

  @Test
  @DisplayName("Temporal Search - BETWEEN - 200 Success")
  void PostTemporalSearchBetween() {
    JsonArray values =
        new JsonArray().add("2025-03-20T04:00:00+0530").add("2025-05-02T09:15:27+0530");
    JsonObject requestBody = baseTemporalCriteria(BETWEEN_TEMPORAL, ITEM_CREATED_AT, values);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

  @Test
  @DisplayName("Temporal Search - AFTER - 200 Success")
  void PostTemporalSearchAfter() {
    JsonArray values = new JsonArray().add("2025-03-20T04:00:00+0530");
    JsonObject requestBody = baseTemporalCriteria(AFTER_TEMPORAL, ITEM_CREATED_AT, values);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

  @Test
  @DisplayName("Temporal Search - BEFORE - 200 Success")
  void PostTemporalSearchBefore() {
    JsonArray values = new JsonArray().add("2025-05-02T09:15:27+0530");
    JsonObject requestBody = baseTemporalCriteria(BEFORE_TEMPORAL, ITEM_CREATED_AT, values);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

  @Test
  @DisplayName("Temporal Search - BETWEEN without endTime - 400 Invalid Request")
  void PostTemporalSearchBetweenInvalid() {
    JsonArray values = new JsonArray().add("2025-03-20T04:00:00+0530"); // Missing second value
    JsonObject requestBody = baseTemporalCriteria(BETWEEN_TEMPORAL, ITEM_CREATED_AT, values);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body(TYPE, is(TYPE_INVALID_PROPERTY_VALUE));
  }

  @Test
  @DisplayName("Temporal Search - Invalid timerel value - 400 Invalid Syntax")
  void PostTemporalSearchInvalidTimerel() {
    JsonArray values = new JsonArray().add("2025-03-20T04:00:00+05:30");
    JsonObject requestBody = baseTemporalCriteria("soonTemporal", ITEM_CREATED_AT, values);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body(TYPE, is(TYPE_INVALID_PROPERTY_VALUE));
  }

  @Test
  @DisplayName("Term Search - type = adex:Apps - 200 Success")
  void PostTermSearch() {
    JsonArray values = new JsonArray().add(ITEM_TYPE_APPS);
    JsonObject requestBody = baseTermCriteria(TYPE, values);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

}

