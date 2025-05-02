package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static iudx.catalogue.server.util.Constants.AFTER;
import static iudx.catalogue.server.util.Constants.ATTRIBUTE_KEY;
import static iudx.catalogue.server.util.Constants.BEFORE;
import static iudx.catalogue.server.util.Constants.BETWEEN;
import static iudx.catalogue.server.util.Constants.DATA_READINESS;
import static iudx.catalogue.server.util.Constants.ITEM_TYPE_DATA_BANK;
import static iudx.catalogue.server.util.Constants.PROPERTY;
import static iudx.catalogue.server.util.Constants.RANGE;
import static iudx.catalogue.server.util.Constants.RANGE_REL;
import static iudx.catalogue.server.util.Constants.TYPE_INVALID_PROPERTY_VALUE;
import static iudx.catalogue.server.util.Constants.VALUE;
import static org.hamcrest.Matchers.is;

import io.restassured.response.Response;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using range relations (before, after, between).
 */
@ExtendWith(RestAssuredConfiguration.class)
public class RangeSearchIT {

  private JsonObject baseRequest(String rangerel, String attribute, Object range) {
    return new JsonObject()
        .put(RANGE_REL, rangerel)
        .put(ATTRIBUTE_KEY, attribute)
        .put(RANGE, range)
        .put(PROPERTY, new JsonArray().add("type"))
        .put(VALUE, new JsonArray().add(new JsonArray().add(ITEM_TYPE_DATA_BANK)));
  }

  @Test
  @DisplayName("Testing Range Search - BEFORE relation - 200 Success")
  void GetRangeSearchBefore() {
    JsonObject requestBody = baseRequest(BEFORE, DATA_READINESS, 80);

    Response response = given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Range Search - AFTER relation - 200 Success")
  void GetRangeSearchAfter() {
    JsonObject requestBody = baseRequest(AFTER, DATA_READINESS, 20);

    Response response = given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Range Search - BETWEEN relation - 200 Success")
  void GetRangeSearchBetween() {
    JsonObject requestBody = baseRequest(BETWEEN, DATA_READINESS, 20)
        .put("endRange", 80);

    Response response = given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Range Search - Invalid range format - 400 Invalid Syntax")
  void GetRangeSearchInvalidFormat() {
    JsonObject requestBody = baseRequest(BETWEEN, DATA_READINESS, "20-81");

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:InvalidPropertyValue"));
  }

  @Test
  @DisplayName("Testing Range Search - Invalid rangerel value - 400 Invalid Syntax")
  void GetRangeSearchInvalidRangerel() {
    JsonObject requestBody = baseRequest("nearby", DATA_READINESS, 50);

    given()
        .contentType("application/json")
        .body(requestBody.encode())
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body("type", is(TYPE_INVALID_PROPERTY_VALUE));
  }
}
