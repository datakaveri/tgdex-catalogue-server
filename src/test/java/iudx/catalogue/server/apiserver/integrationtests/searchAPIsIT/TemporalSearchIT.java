package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static iudx.catalogue.server.apiserver.util.Constants.APPLICATION_JSON;
import static iudx.catalogue.server.util.Constants.ATTRIBUTE_KEY;
import static iudx.catalogue.server.util.Constants.BETWEEN;
import static iudx.catalogue.server.util.Constants.END_TIME;
import static iudx.catalogue.server.util.Constants.PROPERTY;
import static iudx.catalogue.server.util.Constants.TIME;
import static iudx.catalogue.server.util.Constants.TIME_REL;
import static iudx.catalogue.server.util.Constants.VALUE;
import static iudx.catalogue.server.validator.Constants.ITEM_CREATED_AT;
import static org.hamcrest.Matchers.is;
import io.restassured.response.Response;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using temporal relations (after, before, between)
 * using POST method with JSON body (JsonObject based).
 */
@ExtendWith(RestAssuredConfiguration.class)
public class TemporalSearchIT {

  @Test
  @DisplayName("Testing Temporal Search - BETWEEN relation - 200 Success")
  void PostTemporalSearchBetween() {
    JsonObject requestBody = new JsonObject();
    requestBody.put(TIME_REL, BETWEEN);
    requestBody.put(TIME, "2025-03-20T04:00:00+0530");
    requestBody.put(END_TIME, "2025-05-02T09:15:27+0530");
    requestBody.put(ATTRIBUTE_KEY, ITEM_CREATED_AT);
    requestBody.put(PROPERTY, "[type]");
    requestBody.put(VALUE, "[[adex:Apps]]");

    Response response = given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.toString())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Temporal Search - AFTER relation - 200 Success")
  void PostTemporalSearchAfter() {
    JsonObject requestBody = new JsonObject();
    requestBody.put(TIME_REL, "after");
    requestBody.put(TIME, "2025-03-20T04:00:00+0530");
    requestBody.put(ATTRIBUTE_KEY, ITEM_CREATED_AT);
    requestBody.put(PROPERTY, "[type]");
    requestBody.put(VALUE, "[[adex:Apps]]");

    Response response = given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.toString())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Temporal Search - BEFORE relation - 200 Success")
  void PostTemporalSearchBefore() {
    JsonObject requestBody = new JsonObject();
    requestBody.put(TIME_REL, "before");
    requestBody.put(TIME, "2025-05-02T09:15:27+0530");
    requestBody.put(ATTRIBUTE_KEY, ITEM_CREATED_AT);
    requestBody.put(PROPERTY, "[type]");
    requestBody.put(VALUE, "[[adex:Apps]]");

    Response response = given()
        .contentType(APPLICATION_JSON)
        .body(requestBody.toString())
        .when()
        .post("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Temporal Search - BETWEEN without endTime - 400 Invalid Request")
  void PostTemporalSearchBetweenInvalid() {
    JsonObject requestBody = new JsonObject();
    requestBody.put(TIME_REL, BETWEEN);
    requestBody.put(TIME, "2025-03-20T04:00:00+0530");
    requestBody.put(ATTRIBUTE_KEY, ITEM_CREATED_AT);

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody)
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:InvalidSyntax"));
  }

  @Test
  @DisplayName("Testing Temporal Search - Invalid timerel value - 400 Invalid Syntax")
  void PostTemporalSearchInvalidTimerel() {
    JsonObject requestBody = new JsonObject();
    requestBody.put(TIME_REL, "soon");
    requestBody.put(TIME, "2025-03-20T04:00:00+0530");
    requestBody.put(ATTRIBUTE_KEY, ITEM_CREATED_AT);
    requestBody.put(PROPERTY, "[type]");
    requestBody.put(VALUE, "[[adex:Apps]]");

    given()
        .contentType(APPLICATION_JSON)
        .body(requestBody)
        .when()
        .post("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:InvalidSyntax"));
  }
}
