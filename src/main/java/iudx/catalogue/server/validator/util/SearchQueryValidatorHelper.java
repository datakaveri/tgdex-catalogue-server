package iudx.catalogue.server.validator.util;

import static iudx.catalogue.server.util.Constants.BBOX;
import static iudx.catalogue.server.util.Constants.BETWEEN;
import static iudx.catalogue.server.util.Constants.COORDINATES;
import static iudx.catalogue.server.util.Constants.COORDINATES_PRECISION;
import static iudx.catalogue.server.util.Constants.COORDINATES_SIZE;
import static iudx.catalogue.server.util.Constants.DESC;
import static iudx.catalogue.server.util.Constants.DURING;
import static iudx.catalogue.server.util.Constants.END_RANGE;
import static iudx.catalogue.server.util.Constants.END_TIME;
import static iudx.catalogue.server.util.Constants.FAILED;
import static iudx.catalogue.server.util.Constants.FILTER_VALUE_SIZE;
import static iudx.catalogue.server.util.Constants.GEOMETRY;
import static iudx.catalogue.server.util.Constants.LINESTRING;
import static iudx.catalogue.server.util.Constants.MAX_DISTANCE;
import static iudx.catalogue.server.util.Constants.POINT;
import static iudx.catalogue.server.util.Constants.POLYGON;
import static iudx.catalogue.server.util.Constants.PROPERTY;
import static iudx.catalogue.server.util.Constants.PROPERTY_SIZE;
import static iudx.catalogue.server.util.Constants.RANGE;
import static iudx.catalogue.server.util.Constants.RANGE_REL;
import static iudx.catalogue.server.util.Constants.STATUS;
import static iudx.catalogue.server.util.Constants.STRING_SIZE;
import static iudx.catalogue.server.util.Constants.TIME;
import static iudx.catalogue.server.util.Constants.TIME_REL;
import static iudx.catalogue.server.util.Constants.TITLE;
import static iudx.catalogue.server.util.Constants.TITLE_INVALID_PROPERTY_VALUE;
import static iudx.catalogue.server.util.Constants.TITLE_INVALID_SYNTAX;
import static iudx.catalogue.server.util.Constants.TYPE;
import static iudx.catalogue.server.util.Constants.TYPE_BAD_FILTER;
import static iudx.catalogue.server.util.Constants.TYPE_INTERNAL_SERVER_ERROR;
import static iudx.catalogue.server.util.Constants.TYPE_INVALID_PROPERTY_VALUE;
import static iudx.catalogue.server.util.Constants.TYPE_INVALID_SYNTAX;
import static iudx.catalogue.server.util.Constants.VALUE;
import static iudx.catalogue.server.util.Constants.VALUE_SIZE;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.catalogue.server.apiserver.util.RespBuilder;
import iudx.catalogue.server.validator.ValidatorServiceImpl;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SearchQueryValidatorHelper {
  private static final Logger LOGGER = LogManager.getLogger(ValidatorServiceImpl.class);

  public static void handleAttributeSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    JsonObject errResponse = new JsonObject()
        .put(STATUS, FAILED)
        .put(TYPE, TYPE_INVALID_PROPERTY_VALUE);
    validationFuture
        .onFailure(x -> {
          String errorMsg = x.getMessage();
          LOGGER.error("Fail: Invalid Schema; {}", errorMsg);

          if (errorMsg.contains("is too long")) {
            if (request.getJsonArray(PROPERTY).size() > 4) {
              errResponse.put(DESC, "The max number of 'property' should be " + PROPERTY_SIZE);
            } else if (request.getJsonArray(VALUE).size() > 4) {
              errResponse.put(DESC, "The max number of 'value' should be " + VALUE_SIZE);
            } else {
              errResponse.put(DESC, "Array field exceeded allowed size");
            }
          } else if (errorMsg.contains("does not match input string")) {
            errResponse.put(DESC, "Invalid 'value' format");
          } else if (errorMsg.contains("has missing required properties")) {
            errResponse.put(DESC, "Mandatory field(s) not provided; " + errorMsg);
          } else {
            errResponse.put(DESC, errorMsg);
          }

          handler.handle(Future.failedFuture(errResponse.encode()));
        });
  }

  public static void handleGeoSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    JsonObject errResponse = new JsonObject().put(STATUS, FAILED);
    validationFuture
        .onSuccess(x -> {
          try {
            // Coordinates precision check
            List<Double> allCoOrds = extractAllCoordinates(request.getJsonArray(COORDINATES));
            if (allCoOrds.size() <= COORDINATES_SIZE * 2) {
              for (Double coOrd : allCoOrds) {
                if (Double.isFinite(coOrd)) {
                  if (!isPrecisionValid(coOrd)) {
                    LOGGER.error("Error: Overflow coordinate precision");
                    handler.handle(Future.failedFuture(
                        errResponse
                            .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                            .put(DESC,
                                "The max point of 'coordinates' precision is "
                                    + COORDINATES_PRECISION)
                            .encodePrettily()));
                    return;
                  }
                } else {
                  LOGGER.error("Error: Overflow coordinate value");
                  handler.handle(Future.failedFuture(
                      errResponse
                          .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                          .put(DESC, "Unable to parse 'coordinates'; value is " + coOrd)
                          .encodePrettily()));
                  return;
                }
              }
            } else {
              LOGGER.error("Error: Overflow coordinate values");
              handler.handle(Future.failedFuture(
                  errResponse
                      .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                      .put(DESC, "The max number of 'coordinates' value is " + COORDINATES_SIZE)
                      .encodePrettily()));
              return;
            }

            // Geometry-specific coordinate structure check
            String geometry = request.getString(GEOMETRY, "");
            int nesting = countNesting(request.getJsonArray(COORDINATES).toString());
            if (!(geometry.equalsIgnoreCase(POLYGON) && nesting == 3)
                && !(geometry.equalsIgnoreCase(POINT) && nesting == 1)
                && !((geometry.equalsIgnoreCase(LINESTRING)
                || geometry.equalsIgnoreCase(BBOX)) && nesting == 2)) {
              LOGGER.error("Error: Invalid coordinate format");
              handler.handle(Future.failedFuture(
                  errResponse
                      .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                      .put(DESC, "Invalid coordinate format")
                      .encode()));
              return;
            }

            // maxDistance must be present if geometry = Point
            if (geometry.equalsIgnoreCase(POINT) && !request.containsKey(MAX_DISTANCE)) {
              handler.handle(Future.failedFuture(
                  new RespBuilder()
                      .withType(TYPE_INVALID_SYNTAX)
                      .withTitle(TITLE_INVALID_SYNTAX)
                      .getJsonResponse()
                      .encode()));
            }
          } catch (Exception e) {
            LOGGER.error("Geo validation failed", e);
            handler.handle(Future.failedFuture(
                new JsonObject().put(TYPE, TYPE_INTERNAL_SERVER_ERROR)
                    .put(DESC, "Unexpected server error").encode()));
          }
        })
        .onFailure(x -> {
          String errorMsg = x.getMessage();
          LOGGER.error("Geo validation schema failure: {}", errorMsg);

          JsonObject errorResponse = new JsonObject().put(TYPE, TYPE_INVALID_PROPERTY_VALUE);

          if (errorMsg.contains("maxItems")) {
            errorResponse.put(DESC, "The max number of 'coordinates' value is " + COORDINATES_SIZE);
          } else if (errorMsg.contains("enum")) {
            errorResponse.put(DESC, "Invalid 'geometry' type");
          } else if (errorMsg.contains("minimum") || errorMsg.contains("maximum")) {
            errorResponse.put(DESC, "Error: The 'maxDistance' should range between 0-10000m");
          } else if (errorMsg.contains("has missing required properties")) {
            errorResponse.put(DESC, "Mandatory field(s) not provided; " + errorMsg);
          } else {
            errorResponse.put(DESC, errorMsg);
          }

          handler.handle(Future.failedFuture(errorResponse.encode()));
        });
  }

  private static List<Double> extractAllCoordinates(JsonArray arr) {
    List<Double> result = new ArrayList<>();
    for (Object obj : arr) {
      if (obj instanceof Number) {
        result.add(((Number) obj).doubleValue());
      } else if (obj instanceof JsonArray) {
        result.addAll(extractAllCoordinates((JsonArray) obj));
      }
    }
    return result;
  }

  private static boolean isPrecisionValid(Double value) {
    return BigDecimal.valueOf(value).scale() >= 0
        && BigDecimal.valueOf(value).scale() <= COORDINATES_PRECISION;
  }

  private static int countNesting(String str) {
    return StringUtils.countMatches(str.substring(0, 5), "[");
  }

  public static void handleTextSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    JsonObject errResponse = new JsonObject()
        .put(STATUS, FAILED)
        .put(TYPE, TYPE_INVALID_PROPERTY_VALUE);
    validationFuture
        .onFailure(x -> {
          String errorMsg = x.getMessage();
          LOGGER.error("Fail: Invalid Schema; {}", errorMsg);

          if (errorMsg.contains("is too long")) {
            errResponse.put(DESC, "The max string(q) size supported is " + STRING_SIZE);
          } else if (errorMsg.contains("pattern")) {
            errResponse.put(DESC, "Invalid 'q' format");
          } else if (errorMsg.contains("has missing required properties")) {
            errResponse.put(DESC, "Mandatory field(s) not provided; " + errorMsg);
          } else {
            errResponse.put(DESC, errorMsg);
          }

          handler.handle(Future.failedFuture(errResponse.encode()));
        });
  }

  public static void handleFilterSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {

    validationFuture.onFailure(x -> {
      LOGGER.error("Fail: Invalid Schema");
      String errorMsg = x.getMessage();
      LOGGER.error(errorMsg);

      String errorDesc;
      if (errorMsg.contains("is too long")) {
        errorDesc = "The max number of 'filter' should be " + FILTER_VALUE_SIZE;
      } else if (errorMsg.contains("pattern")) {
        errorDesc = "Invalid format in 'filter'";
      } else if (errorMsg.contains("has missing required properties")) {
        errorDesc = "Mandatory field(s) not provided";
      } else {
        errorDesc = errorMsg;
      }

      JsonObject errResponse = new JsonObject()
          .put(STATUS, FAILED)
          .put(TYPE, TYPE_BAD_FILTER)
          .put(DESC, errorDesc);

      handler.handle(Future.failedFuture(errResponse.encode()));
    });
  }


  public static void handleTemporalSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    validationFuture
        .onSuccess(
            x -> {
              String timeRel = request.getString(TIME_REL);
              String time = request.getString(TIME);
              String endTime = request.getString(END_TIME);

              // Only check time ordering if 'during' or 'between' is used
              if ((timeRel.equals(DURING) || timeRel.equals(BETWEEN))
                  && time != null && endTime != null
                  && !(time.compareTo(endTime) < 0)) {

                String message = "'endTime' must be after 'time'";
                LOGGER.error("Fail: " + message);
                handler.handle(Future.failedFuture(new JsonArray().add(message).toString()));
              }
            })
        .onFailure(
            x -> {
              String errorMsg = x.getMessage();
              LOGGER.error("Fail: Invalid Schema; {}", errorMsg);

              JsonObject errorResponse = new JsonObject().put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                  .put(TITLE, TITLE_INVALID_PROPERTY_VALUE);

              if (errorMsg.contains("has missing required properties")
                  || errorMsg.contains("failed to match at least one required schema")) {
                errorResponse.put(DESC, "Mandatory field(s) not provided");
              } else {
                errorResponse.put(DESC, errorMsg);
              }

              handler.handle(
                  Future.failedFuture(errorResponse.encodePrettily()));
            });
  }

  public static void handleRangeSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    validationFuture
        .onSuccess(x -> {
          String rangeRel = request.getString(RANGE_REL);

          if (rangeRel.equalsIgnoreCase(DURING) || rangeRel.equalsIgnoreCase(BETWEEN)) {
            int startRange = request.getInteger(RANGE);
            int endRange = request.getInteger(END_RANGE);

            if (startRange > endRange) {
              LOGGER.error("Error: startRange must be before endRange for BETWEEN relation");
              JsonObject errorResponse = new JsonObject()
                  .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                  .put(DESC, "startRange must be before endRange for BETWEEN relation");
              handler.handle(Future.failedFuture(errorResponse.encode()));
            }
          }
        })
        .onFailure(x -> {
          String errorMsg = x.getMessage();
          LOGGER.error("Fail: Invalid Schema; {}", errorMsg);

          JsonObject errorResponse = new JsonObject().put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
              .put(TITLE, TITLE_INVALID_PROPERTY_VALUE);

          if (errorMsg.contains("has missing required properties")
              || errorMsg.contains("failed to match at least one required schema")) {
            errorResponse.put(DESC, "Mandatory field(s) not provided");
          } else if (errorMsg.contains(
              "not found in enum (possible values:" +
                  " [\"before\",\"after\",\"during\",\"between\"])")) {
            errorResponse.put(DESC, "Invalid rangerel value; " + errorMsg);
          } else {
            errorResponse.put(DESC, errorMsg);
          }

          handler.handle(Future.failedFuture(errorResponse.encode()));
        });
  }
}

