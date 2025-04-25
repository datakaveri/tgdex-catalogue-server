package iudx.catalogue.server.validator.util;

import static iudx.catalogue.server.util.Constants.*;

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
                                "The max point of 'coordinates' precision is " + COORDINATES_PRECISION)
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

    JsonObject errResponse = new JsonObject()
        .put(STATUS, FAILED)
        .put(TYPE, TYPE_BAD_FILTER);
    validationFuture
        .onFailure(x -> {
          LOGGER.error("Fail: Invalid Schema");
          String errorMsg = x.getMessage();
          LOGGER.error(errorMsg);

          if (errorMsg.contains("is too long")) {
            errResponse.put(DESC, "The max number of 'filter' should be " + FILTER_VALUE_SIZE);
          } else if (errorMsg.contains("pattern")) {
            errResponse.put(DESC, "Invalid format in 'filter'");
          } else if (errorMsg.contains("has missing required properties")) {
            errResponse.put(DESC, "Mandatory field(s) not provided; " + errorMsg);
          } else {
            errResponse.put(DESC, errorMsg);
          }

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
                  && time != null && endTime != null) {
                if (!(time.compareTo(endTime) < 0)) {
                  String message = "'endTime' must be after 'time'";
                  LOGGER.error("Fail: " + message);
                  handler.handle(Future.failedFuture(new JsonArray().add(message).toString()));
                }
              }
            })
        .onFailure(
            x -> {
              LOGGER.error("Fail: Invalid Schema; {}", x.getMessage());
              handler.handle(
                  Future.failedFuture(new JsonArray().add(x.getMessage()).toString()));
            });
  }

  public static void handleRangeSearchValidationResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    validationFuture
        .onSuccess(x -> {
          String rangeRel = request.getString(RANGE_REL);

          if ((rangeRel.equalsIgnoreCase(DURING) || rangeRel.equalsIgnoreCase(BETWEEN))) {
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
          LOGGER.error("Fail: Invalid Schema");
          String errorMsg = x.getMessage();
          LOGGER.error(errorMsg);

          JsonObject errorResponse = new JsonObject().put(TYPE, TYPE_INVALID_PROPERTY_VALUE);

          if (errorMsg.contains("has missing required properties")) {
            errorResponse.put(DESC, "Mandatory field(s) not provided; " + errorMsg);
          } else if (errorMsg.contains("type")) {
            errorResponse.put(DESC, "Invalid datatype for 'range' or 'endRange'");
          } else {
            errorResponse.put(DESC, errorMsg);
          }

          handler.handle(Future.failedFuture(errorResponse.encode()));
        });
  }
}

