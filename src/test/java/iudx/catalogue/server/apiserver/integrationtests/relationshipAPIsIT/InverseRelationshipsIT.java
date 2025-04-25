package iudx.catalogue.server.apiserver.integrationtests.relationshipAPIsIT;

import io.restassured.response.Response;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import iudx.catalogue.server.apiserver.integrationtests.instanceAPIsIT.InstanceAPIsIT;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests for the Inverse Relationships APIs in the Catalog Server.
 */
@ExtendWith(RestAssuredConfiguration.class)
public class InverseRelationshipsIT {
    private static final Logger LOGGER = LogManager.getLogger(InstanceAPIsIT.class);
    @Test
    @DisplayName("testing get resources for resource group - 200 Success")
    void GetResourcesForRG() {
        Response response= given()
                .queryParam("id","a4f83b5d-4431-4193-9c33-41f6fc1557b7")
                .queryParam("rel","resource")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get Resource for provider - 200 Success")
    void GetResourcesForProvider() {
        Response response = given()
                .queryParam("id","411df492-45f6-4345-a12d-e3207f2b8623")
                .queryParam("rel","resource")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get RGs for provider - 200 Success")
    void GetRGsForProvide() {
        Response response = given()
                .queryParam("id","3897a41c-83f7-37e7-9194-374d5278dff5")
                .queryParam("rel","resourceGroup")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get resources for resource server - 200 Success")
    void GetResourceForRS() {
        Response response = given()
                .queryParam("id","411df492-45f6-4345-a12d-e3207f2b8623")
                .queryParam("rel","resource")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get RGs for resource server - 200 Success")
    void GetRGsForRS() {
        Response response = given()
                .queryParam("id","8e901b91-5bf1-4ad2-bf8f-d59dc139cc29")
                .queryParam("rel","resourceGroup")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get providers for resource server - 200 Success")
    void GetProvidersForRS() {
        Response response = given()
                .queryParam("id","8e901b91-5bf1-4ad2-bf8f-d59dc139cc29")
                .queryParam("rel","provider")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get resources for cos - 200 Success")
    void GetResourcesForCos() {
        Response response = given()
                .queryParam("id","5484ddce-d372-4962-8a07-3e72149716b3")
                .queryParam("rel","resource")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get RGs for cos - 200 Success")
    void GetRGsForCos() {
        Response response = given()
                .queryParam("id","5484ddce-d372-4962-8a07-3e72149716b3")
                .queryParam("rel","resourceGroup")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get providers for cos - 200 Success")
    void GetProvidersForCos() {
        Response response = given()
                .queryParam("id","5484ddce-d372-4962-8a07-3e72149716b3")
                .queryParam("rel","provider")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing get RSs for cos - 200 Success")
    void GetRSsForCos() {
        Response response = given()
                .queryParam("id","5484ddce-d372-4962-8a07-3e72149716b3")
                .queryParam("rel","resourceServer")
                .when()
                .get("/relationship")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
}
