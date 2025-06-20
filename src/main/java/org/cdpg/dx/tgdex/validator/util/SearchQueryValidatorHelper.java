package org.cdpg.dx.tgdex.validator.util;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

import static org.cdpg.dx.tgdex.util.Constants.*;


public class SearchQueryValidatorHelper {
  private static final Logger LOGGER = LogManager.getLogger(SearchQueryValidatorHelper.class);

  public static void handleTextSearchValidationResult(
          Future<String> validationFuture, Promise<JsonObject> promise) {
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

          promise.fail(errResponse.encode());
        });
  }

  public static void handleFilterSearchValidationResult(
      Future<String> validationFuture,Promise<JsonObject> promise) {

    validationFuture.onFailure(x -> {
      String errorMsg = x.getMessage();
      LOGGER.error("Fail: Invalid Schema: " + errorMsg);

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

      promise.fail(errResponse.encode());
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
      Future<String> validationFuture, JsonObject request,Promise<JsonObject> promise) {

    JsonObject errorResponse = new JsonObject().put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
        .put(TITLE, TITLE_INVALID_PROPERTY_VALUE);

    validationFuture
        .onSuccess(result -> {
          JsonArray criteriaArray = request.getJsonArray(SEARCH_CRITERIA_KEY);

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
                  promise.fail(
                      errorResponse.put(DESC, message).encode());
                  return;
                }
                if (!isValidDateTimeOrder(criterion.getJsonArray(VALUES).getString(0),
                    criterion.getJsonArray(VALUES).getString(1))) {
                  String message = "'endTime' must be after 'time'";
                  LOGGER.error("Temporal check failed: {}", message);
                  promise.fail(
                      errorResponse.put(DESC, message).encode());
                  return;
                }
                break;

              case BETWEEN_RANGE:
                if (criterion.getJsonArray(VALUES).size() != 2) {
                  String message = "'betweenRange' must contain exactly two values";
                  LOGGER.error("Value count check failed: {}", message);
                  promise.fail(
                      errorResponse.put(DESC, message).encode());
                  return;
                }
                if (!isValidRangeOrder(
                    criterion.getJsonArray(VALUES).getInteger(0),
                    criterion.getJsonArray(VALUES).getInteger(1))) {
                  String message = "'endRange' must be after 'range'";
                  LOGGER.error("Range check failed: {}", message);
                  promise.fail(
                      errorResponse.put(DESC, message).encode());
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
                  promise.fail(
                      errorResponse.put(DESC, message).encode());
                  return;
                }
                break;

              default:
                String message = "Unsupported searchType: " + searchType;
                LOGGER.error(message);
                promise.fail(
                    errorResponse.put(DESC, message).encode());
                return;
            }
          }

          // If all criteria pass
          promise.complete(new JsonObject().put(STATUS, SUCCESS));
        })
        .onFailure(err -> {
          LOGGER.error("Composite schema validation failed: {}", err.getMessage());
          errorResponse.put(DESC, err.getMessage());
          promise.fail(errorResponse.encode());
        });
  }


}

