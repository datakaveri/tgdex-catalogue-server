package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static iudx.catalogue.server.util.Constants.*;
import static org.hamcrest.Matchers.is;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using range relations (before, after, between).
 */
@ExtendWith(RestAssuredConfiguration.class)
public class RangeSearchIT {

  private JsonObject baseSearchCriteria(String searchType, String field, JsonArray values) {
    JsonObject criterion = new JsonObject()
        .put(SEARCH_TYPE, searchType)
        .put(FIELD, field)
        .put(VALUES, values);

    return new JsonObject()
        .put(SEARCH_TYPE, SEARCH_CRITERIA)
        .put(SEARCH_CRITERIA, new JsonArray().add(criterion));
  }

  @Test
  @DisplayName("Testing Range Search - BEFORE relation - 200 Success")
  void GetRangeSearchBefore() {
    JsonObject requestBody =
        baseSearchCriteria(BEFORE_RANGE, DATA_READINESS, new JsonArray().add(80));

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
  @DisplayName("Range Search - AFTER relation - 200 Success")
  void GetRangeSearchAfter() {
    JsonObject requestBody =
        baseSearchCriteria(AFTER_RANGE, DATA_READINESS, new JsonArray().add(20));

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
  @DisplayName("Range Search - BETWEEN relation - 200 Success")
  void GetRangeSearchBetween() {
    JsonObject requestBody = baseSearchCriteria(BETWEEN_RANGE, DATA_READINESS,
        new JsonArray().add(20).add(80));

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
  @DisplayName("Range Search - Invalid range format (string) - 400")
  void GetRangeSearchInvalidFormat() {
    JsonObject requestBody =
        baseSearchCriteria(BETWEEN_RANGE, DATA_READINESS, new JsonArray().add("20-81"));

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
  @DisplayName("Range Search - Invalid searchType - 400")
  void GetRangeSearchInvalidRangerel() {
    JsonObject requestBody =
        baseSearchCriteria("nearbyRange", DATA_READINESS, new JsonArray().add(50));

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
