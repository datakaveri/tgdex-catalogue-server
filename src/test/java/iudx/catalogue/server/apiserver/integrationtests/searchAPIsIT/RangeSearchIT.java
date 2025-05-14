package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static iudx.catalogue.server.util.Constants.*;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using range relations (before, after, between).
 */
@ExtendWith(RestAssuredConfiguration.class)
public class RangeSearchIT {

  @Test
  @Order(1)
  @DisplayName("Testing Range Search - BEFORE relation - 200 Success")
  void GetRangeSearchBefore() {
    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, BEFORE_RANGE)
        .put(FIELD, DATA_READINESS)
        .put(VALUES, new JsonArray().add(80));
    JsonObject requestBody =
        new JsonObject()
            .put(SEARCH_TYPE, SEARCH_TYPE_CRITERIA)
            .put(SEARCH_CRITERIA_KEY, new JsonArray().add(criterion));

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

  @Test
  @Order(2)
  @DisplayName("Range Search - AFTER relation - 200 Success")
  void GetRangeSearchAfter() {
    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, AFTER_RANGE)
        .put(FIELD, DATA_READINESS)
        .put(VALUES, new JsonArray().add(20));
    JsonObject requestBody =
        new JsonObject()
            .put(SEARCH_TYPE, SEARCH_TYPE_CRITERIA)
            .put(SEARCH_CRITERIA_KEY, new JsonArray().add(criterion));

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

  @Test
  @Order(3)
  @DisplayName("Range Search - BETWEEN relation - 200 Success")
  void GetRangeSearchBetween() {
    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, BETWEEN_RANGE)
        .put(FIELD, DATA_READINESS)
        .put(VALUES, new JsonArray().add(20).add(80));
    JsonObject requestBody =
        new JsonObject()
            .put(SEARCH_TYPE, SEARCH_TYPE_CRITERIA)
            .put(SEARCH_CRITERIA_KEY, new JsonArray().add(criterion));

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body(TYPE, is(TYPE_SUCCESS));
  }

  @Test
  @Order(4)
  @DisplayName("Range Search - Invalid range format (string) - 400")
  void GetRangeSearchInvalidFormat() {

    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, BETWEEN_RANGE)
        .put(FIELD, DATA_READINESS)
        .put(VALUES, new JsonArray().add("20-81"));
    JsonObject requestBody =
        new JsonObject()
            .put(SEARCH_TYPE, SEARCH_TYPE_CRITERIA)
            .put(SEARCH_CRITERIA_KEY, new JsonArray().add(criterion));

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body(TYPE, is(TYPE_INVALID_PROPERTY_VALUE));
  }

  @Test
  @Order(5)
  @DisplayName("Range Search - Invalid searchType - 400")
  void GetRangeSearchInvalidRangerel() {
    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, "nearbyRange")
        .put(FIELD, DATA_READINESS)
        .put(VALUES, new JsonArray().add(50));
    JsonObject requestBody =
        new JsonObject()
            .put(SEARCH_TYPE, SEARCH_TYPE_CRITERIA)
            .put(SEARCH_CRITERIA_KEY, new JsonArray().add(criterion));

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body(TYPE, is(TYPE_INVALID_PROPERTY_VALUE));
  }

}
