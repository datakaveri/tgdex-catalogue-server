package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.restassured.response.Response;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using temporal relations (after, before, between)
 */
@ExtendWith(RestAssuredConfiguration.class)
public class TemporalSearchIT {

  @Test
  @DisplayName("Testing Temporal Search - BETWEEN relation - 200 Success")
  void GetTemporalSearchBetween() {
    Response response = given()
        .param("timerel","between")
        .param("time","2025-01-15T10:30:00Z")
        .param("endTime", "2025-04-19T14:20:00Z")
        .param("property", "[type]")
        .param("value", "[[adex:Apps]]")
        .when()
        .get("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }
  @Test
  @DisplayName("Testing Temporal Search - AFTER relation - 200 Success")
  void GetTemporalSearchAfter() {
    Response response = given()
        .param("timerel", "after")
        .param("time", "2024-12-01T00:00:00Z")
        .param("property", "[type]")
        .param("value", "[[adex:Apps]]")
        .when()
        .get("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }
  @Test
  @DisplayName("Testing Temporal Search - BEFORE relation - 200 Success")
  void GetTemporalSearchBefore() {
    Response response = given()
        .param("timerel", "before")
        .param("time", "2026-01-01T00:00:00Z")
        .param("property", "[type]")
        .param("value", "[[adex:Apps]]")
        .when()
        .get("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }
  @Test
  @DisplayName("Testing Temporal Search - BETWEEN without endTime - 400 Invalid Request")
  void GetTemporalSearchBetweenInvalid() {
    given()
        .param("timerel", "between")
        .param("time", "2025-01-15T10:30:00Z")
        .param("property", "[type]")
        .param("value", "[[adex:Apps]]")
        .when()
        .get("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:InvalidPropertyValue"));
  }
  @Test
  @DisplayName("Testing Temporal Search - Invalid timerel value - 400 Invalid Syntax")
  void GetTemporalSearchInvalidTimerel() {
    given()
        .param("timerel", "soon")
        .param("time", "2025-01-15T10:30:00Z")
        .param("property", "[type]")
        .param("value", "[[adex:Apps]]")
        .when()
        .get("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:BadTemporalQuery"));
  }
}
