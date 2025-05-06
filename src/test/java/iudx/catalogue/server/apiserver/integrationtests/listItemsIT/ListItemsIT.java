package iudx.catalogue.server.apiserver.integrationtests.listItemsIT;

import io.restassured.response.Response;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.catalogue.server.apiserver.integrationtests.RestAssuredConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static io.restassured.RestAssured.given;
import static iudx.catalogue.server.util.Constants.DEPARTMENT;
import static iudx.catalogue.server.util.Constants.FIELD;
import static iudx.catalogue.server.util.Constants.FILE_FORMAT;
import static iudx.catalogue.server.util.Constants.FILTER;
import static iudx.catalogue.server.util.Constants.ITEM_TYPE_DATA_BANK;
import static iudx.catalogue.server.util.Constants.ORGANIZATION_TYPE;
import static iudx.catalogue.server.util.Constants.SEARCH_CRITERIA;
import static iudx.catalogue.server.util.Constants.TAGS;
import static iudx.catalogue.server.util.Constants.TYPE;
import static iudx.catalogue.server.util.Constants.VALUES;
import static org.hamcrest.Matchers.is;

/**
 * Integration tests for the List Items APIs in the Catalog Server.
 * Note: These tests assume the availability of the required authentication tokens and valid
 * configurations for the Catalog Server.
 */
@ExtendWith(RestAssuredConfiguration.class)
public class ListItemsIT {
    private static final Logger LOGGER = LogManager.getLogger(ListItemsIT.class);
    @Test
    @DisplayName("testing list tags - 200 Success")
    void ListTags() {
        Response response = given()
                .when()
                .get("/list/tags")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing list instances - 200 Success")
    void ListInstances() {
        Response response = given()
                .when()
                .get("/list/instance")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing list Resource Group - 200 Success")
    void ListResourceGroup() {
        Response response = given()
                .when()
                .get("/list/resourceGroup")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing list Resource Server - 200 Success")
    void ListResourceServer() {
        Response response = given()
                .when()
                .get("/list/resourceServer")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing list Provider - 200 Success")
    void ListProvider() {
        Response response = given()
                .when()
                .get("/list/provider")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing list Tags - 400 Invalid item type")
    void ListInvalidTags() {
        given()
                .when()
                .get("/list/tag")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing list Instances - 400 Invalid item type")
    void ListInvalidInstance() {
        given()
                .when()
                .get("/list/instanc")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing list Resource Group - 400 Invalid item type")
    void ListInvalidResourceGrp() {
        given()
                .when()
                .get("/list/resourceGrp")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing list Resource Server - 400 Invalid item type")
    void ListInvalidResourceSvr() {
        given()
                .when()
                .get("/list/resourceSvr")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing list Provider - 400 Invalid item type")
    void ListInvalidProvider() {
        given()
                .when()
                .get("/list/rprovider")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing list t- 400 Invalid item type")
    void ListInvalidItemType() {
        given()
                .when()
                .get("/list/resource")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing Exceed limit value - 400 Invalid request")
    void ListTagsExceedLimit() {
        given()
                .queryParam("limit","13323232320")
                .when()
                .get("/list/tags")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing List COS - 200 Success")
    void ListCOS() {
        Response response = given()
                .when()
                .get("/list/cos")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing List Owner - 200 Success")
    void ListOwner() {
        Response response = given()
                .when()
                .get("/list/owner")
                .then()
                .statusCode(200)
                .body("type", is("urn:dx:cat:Success"))
                .extract()
                .response();
    }
    @Test
    @DisplayName("testing List COS - 400 Invalid Item Type")
    void ListCOSInvalid() {
        given()
                .when()
                .get("/list/coss")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("testing List Owner - 400 Invalid Item Type")
    void ListOwnerInvalid() {
        given()
                .when()
                .get("/list/rowner")
                .then()
                .statusCode(400)
                .body("type", is("urn:dx:cat:InvalidSyntax"));
    }
    @Test
    @DisplayName("Testing listMultipleItems for itemType with filter - 200 Success")
    void listMultipleItemsWithItemTypeAndFilter() {
        JsonObject requestBody = new JsonObject()
            .put(SEARCH_CRITERIA, new JsonArray()
                .add(new JsonObject()
                    .put(FIELD, TYPE)
                    .put(VALUES, new JsonArray().add(ITEM_TYPE_DATA_BANK))
                )
            )
            .put(FILTER, new JsonArray()
                .add(TAGS)
                .add(DEPARTMENT)
                .add(ORGANIZATION_TYPE)
                .add(FILE_FORMAT)
            );

        Response response = given()
            .header("Content-Type", "application/json")
            .body(requestBody.toString())
            .when()
            .post("/list")
            .then()
            .statusCode(200)
            .body("type", is("urn:dx:cat:Success"))
            .extract()
            .response();
    }

}
