package iudx.catalogue.server.apiserver.integrationtests.searchAPIsIT;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

import io.restassured.response.Response;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Integration tests for the search APIs using range relations (before, after, between).
 */
@ExtendWith(RestAssuredConfiguration.class)
public class RangeSearchIT {

  @Test
  @DisplayName("Testing Range Search - BEFORE relation - 200 Success")
  void GetRangeSearchBefore() {
    Response response = given()
        .param("rangerel", "before")
        .param("attribute", "dataReadiness")
        .param("range", "80")
        .param("property", "[type]")
        .param("value", "[[adex:DataBank]]")
        .when()
        .get("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Range Search - AFTER relation - 200 Success")
  void GetRangeSearchAfter() {
    Response response = given()
        .param("rangerel", "after")
        .param("attribute", "dataReadiness")
        .param("range", "20")
        .param("property", "[type]")
        .param("value", "[[adex:DataBank]]")
        .when()
        .get("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Range Search - BETWEEN relation - 200 Success")
  void GetRangeSearchBetween() {
    Response response = given()
        .param("rangerel", "between")
        .param("attribute", "dataReadiness")
        .param("range", "20")
        .param("endRange", "80")
        .param("property", "[type]")
        .param("value", "[[adex:DataBank]]")
        .when()
        .get("/search")
        .then()
        .statusCode(200)
        .body("type", is("urn:dx:cat:Success"))
        .extract()
        .response();
  }

  @Test
  @DisplayName("Testing Range Search - Invalid range format - 400 Invalid Syntax")
  void GetRangeSearchInvalidFormat() {
    given()
        .param("rangerel", "between")
        .param("attribute", "dataReadiness")
        .param("range", "20-81") // Invalid format
        .param("property", "[type]")
        .param("value", "[[adex:DataBank]]")
        .when()
        .get("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:InvalidPropertyValue"));
  }

  @Test
  @DisplayName("Testing Range Search - Invalid rangerel value - 400 Invalid Syntax")
  void GetRangeSearchInvalidRangerel() {
    given()
        .param("rangerel", "nearby")
        .param("attribute", "dataReadiness")
        .param("range", "50")
        .param("property", "[type]")
        .param("value", "[[adex:DataBank]]")
        .when()
        .get("/search")
        .then()
        .statusCode(400)
        .body("type", is("urn:dx:cat:BadRangeQuery"));
  }
}
