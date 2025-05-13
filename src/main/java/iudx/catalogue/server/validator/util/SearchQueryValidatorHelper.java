package iudx.catalogue.server.validator.util;

import static iudx.catalogue.server.util.Constants.*;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import iudx.catalogue.server.apiserver.util.RespBuilder;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SearchQueryValidatorHelper {
  private static final Logger LOGGER = LogManager.getLogger(SearchQueryValidatorHelper.class);

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

  public static boolean isValidDateTimeOrder(String start, String end) {
    try {
      // Convert offset like +0530 to +05:30 using regex
      start = start.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");
      end = end.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");

      OffsetDateTime startTime = OffsetDateTime.parse(start);
      OffsetDateTime endTime = OffsetDateTime.parse(end);

      if (!startTime.isBefore(endTime)) {
        LOGGER.error("Temporal check failed: 'endTime' must be after 'startTime'");
        return false;
      }

      return true;
    } catch (DateTimeParseException e) {
      LOGGER.error("Date parsing error: {}", e.getMessage());
      return false;
    }
  }

  public static boolean isValidRangeOrder(int start, int end) {
    return start <= end;
  }

  public static void handleSearchCriteriaResult(
      Future<String> validationFuture, JsonObject request,
      Handler<AsyncResult<JsonObject>> handler) {
    LOGGER.debug("Post schema validation: " + request);

    JsonObject errorResponse = new JsonObject().put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
        .put(TITLE, TITLE_INVALID_PROPERTY_VALUE);

    validationFuture
        .onSuccess(result -> {
          JsonArray criteriaArray = request.getJsonArray(SEARCH_CRITERIA);

          for (int i = 0; i < criteriaArray.size(); i++) {
            JsonObject criterion = criteriaArray.getJsonObject(i);
            String searchType = criterion.getString(SEARCH_TYPE);

            switch (searchType) {
              case TERM:
                // No additional post-schema checks for term
                break;

              case BETWEEN_TEMPORAL:
                if (criterion.getJsonArray(VALUES).size() != 2) {
                  String message = "'betweenTemporal' must contain exactly two values";
                  LOGGER.error("Value count check failed: {}", message);
                  handler.handle(Future.failedFuture(
                      errorResponse.put(DESC, message).encode()));
                  return;
                }
                if (!isValidDateTimeOrder(criterion.getJsonArray(VALUES).getString(0),
                    criterion.getJsonArray(VALUES).getString(1))) {
                  String message = "'endTime' must be after 'time'";
                  LOGGER.error("Temporal check failed: {}", message);
                  handler.handle(Future.failedFuture(
                      errorResponse.put(DESC, message).encode()));
                  return;
                }
                break;

              case BETWEEN_RANGE:
                if (criterion.getJsonArray(VALUES).size() != 2) {
                  String message = "'betweenRange' must contain exactly two values";
                  LOGGER.error("Value count check failed: {}", message);
                  handler.handle(Future.failedFuture(
                      errorResponse.put(DESC, message).encode()));
                  return;
                }
                if (!isValidRangeOrder(
                    criterion.getJsonArray(VALUES).getInteger(0),
                    criterion.getJsonArray(VALUES).getInteger(1))) {
                  String message = "'endRange' must be after 'range'";
                  LOGGER.error("Range check failed: {}", message);
                  handler.handle(Future.failedFuture(
                      errorResponse.put(DESC, message).encode()));
                  return;
                }
                break;

              case BEFORE_TEMPORAL:
              case AFTER_TEMPORAL:
              case BEFORE_RANGE:
              case AFTER_RANGE:
                if (criterion.getJsonArray(VALUES).size() != 1) {
                  String message = String.format(
                      "'%s' must contain exactly one value", searchType);
                  LOGGER.error("Single value check failed: {}", message);
                  handler.handle(Future.failedFuture(
                      errorResponse.put(DESC, message).encode()));
                  return;
                }
                break;

              default:
                String message = "Unsupported searchType: " + searchType;
                LOGGER.error(message);
                handler.handle(Future.failedFuture(
                    errorResponse.put(DESC, message).encode()));
                return;
            }
          }

          // If all criteria pass
          handler.handle(Future.succeededFuture(new JsonObject().put(STATUS, SUCCESS)));
        })
        .onFailure(err -> {
          LOGGER.error("Composite schema validation failed: {}", err.getMessage());
          errorResponse.put(DESC, err.getMessage());
          handler.handle(Future.failedFuture(errorResponse.encode()));
        });
  }


}

