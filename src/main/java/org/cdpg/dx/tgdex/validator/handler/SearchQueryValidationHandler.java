package org.cdpg.dx.tgdex.validator.handler;

import io.vertx.core.CompositeFuture;
import io.vertx.core.Handler;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.tgdex.validator.Validator;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import static org.cdpg.dx.tgdex.util.Constants.*;

public class SearchQueryValidationHandler implements Handler<RoutingContext> {
    private static final Logger LOGGER = LogManager.getLogger(SearchQueryValidationHandler.class);

    private final Validator textSearchQueryValidator;
    private final Validator filterSearchQueryValidator;
    private final Validator termValidator;
    private final Validator rangeValidator;
    private final Validator temporalValidator;

    public SearchQueryValidationHandler() throws IOException, ProcessingException {
        textSearchQueryValidator = new Validator("/textSearchQuerySchema.json");
        filterSearchQueryValidator = new Validator("/filterSearchQuerySchema.json");
        termValidator = new Validator("/attributeSearchQuerySchema.json");
        rangeValidator = new Validator("/rangeSearchQuerySchema.json");
        temporalValidator = new Validator("/temporalSearchQuerySchema.json");
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject request = ctx.getBodyAsJson();
        LOGGER.debug("Validating search query: {}", request);

        if (!validateLimitAndOffset(request)) {
            ctx.fail(400);
            return;
        }


        String searchType = request.getString(SEARCH_TYPE, "");
        List<Future> validations = new ArrayList<>();

        if (searchType.contains(SEARCH_TYPE_TEXT)) {
            validations.add(validateTextSearchQuery(request));
        }
        if (searchType.contains(SEARCH_TYPE_CRITERIA)) {
            validations.add(validateSearchCriteria(request));
        }
        if (searchType.contains(RESPONSE_FILTER)) {
            validations.add(validateFilterSearchQuery(request));
        }

        if (validations.isEmpty()) {
            ctx.response().setStatusCode(400)
                    .end(new JsonObject().put(STATUS, FAILED).put(DESC, "No search type provided").encode());
            return;
        }

        CompositeFuture.all(validations)
                .onSuccess(res -> {
                    LOGGER.debug("Search query validation successful.");
                    ctx.next();
                })
                .onFailure(err -> {
                    LOGGER.error("Validation failed: {}", err.getMessage());
                    ctx.response().setStatusCode(400).end(err.getMessage());
                });
    }

    private boolean validateLimitAndOffset(JsonObject request) {
        if (request.containsKey(LIMIT) || request.containsKey(OFFSET)) {
            int limit = request.getInteger(LIMIT, 0);
            int offset = request.getInteger(OFFSET, 0);
            int totalSize = limit + offset;
            if (totalSize <= 0 || totalSize > MAX_RESULT_WINDOW) {
                LOGGER.error("limit+offset param exceeded limit");
                JsonObject error = new JsonObject()
                        .put(STATUS, FAILED)
                        .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                        .put(DESC, "The limit + offset should be between 1 to " + MAX_RESULT_WINDOW);
                return false;
            }
        }
        return true;
    }

    private Future validateTextSearchQuery(JsonObject request) {
        return textSearchQueryValidator.validateSearchCriteria(request.encode())
                .mapEmpty()
                .recover(err -> Future.failedFuture(
                        new JsonObject()
                                .put(STATUS, FAILED)
                                .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                                .put(DESC, getTextSearchErrorMsg(err.getMessage()))
                                .encode()
                ));
    }

    private Future validateFilterSearchQuery(JsonObject request) {
        return filterSearchQueryValidator.validateSearchCriteria(request.encode())
                .mapEmpty()
                .recover(err -> Future.failedFuture(
                        new JsonObject()
                                .put(STATUS, FAILED)
                                .put(TYPE, TYPE_BAD_FILTER)
                                .put(DESC, getFilterErrorMsg(err.getMessage()))
                                .encode()
                ));
    }

    private Future<Void> validateSearchCriteria(JsonObject request) {
        JsonArray criteriaArray = request.getJsonArray(SEARCH_CRITERIA_KEY, new JsonArray());
        List<Future> validationFutures = new ArrayList<>();

        for (int i = 0; i < criteriaArray.size(); i++) {
            JsonObject criterion = criteriaArray.getJsonObject(i);
            String criterionType = criterion.getString(SEARCH_TYPE, "");

            Future<String> schemaValidation;
            switch (criterionType) {
                case TERM:
                    schemaValidation = termValidator.validateSearchCriteria(criterion.encode());
                    break;
                case BETWEEN_RANGE:
                case BEFORE_RANGE:
                case AFTER_RANGE:
                    schemaValidation = rangeValidator.validateSearchCriteria(criterion.encode());
                    break;
                case BETWEEN_TEMPORAL:
                case BEFORE_TEMPORAL:
                case AFTER_TEMPORAL:
                    schemaValidation = temporalValidator.validateSearchCriteria(criterion.encode());
                    break;
                default:
                    return Future.failedFuture(new JsonObject()
                            .put(STATUS, FAILED)
                            .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                            .put(DESC, "Invalid searchType: " + criterionType)
                            .encode());
            }

            Future<Void> fullValidation = schemaValidation.compose(res -> {
                switch (criterionType) {
                    case BETWEEN_TEMPORAL: {
                        JsonArray values = criterion.getJsonArray(VALUES, new JsonArray());
                        if (values.size() != 2 || !isValidDateTimeOrder(values.getString(0), values.getString(1))) {
                            String message = (values.size() != 2)
                                    ? "'betweenTemporal' must contain exactly two values"
                                    : "'endTime' must be after 'time'";
                            return Future.failedFuture(
                                    new JsonObject()
                                            .put(STATUS, FAILED)
                                            .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                                            .put(DESC, message)
                                            .encode());
                        }
                        break;
                    }
                    case BETWEEN_RANGE: {
                        JsonArray values = criterion.getJsonArray(VALUES, new JsonArray());
                        if (values.size() != 2 || !isValidRangeOrder(values.getInteger(0), values.getInteger(1))) {
                            String message = (values.size() != 2)
                                    ? "'betweenRange' must contain exactly two values"
                                    : "'endRange' must be after 'range'";
                            return Future.failedFuture(
                                    new JsonObject()
                                            .put(STATUS, FAILED)
                                            .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                                            .put(DESC, message)
                                            .encode());
                        }
                        break;
                    }
                    case BEFORE_TEMPORAL:
                    case AFTER_TEMPORAL:
                    case BEFORE_RANGE:
                    case AFTER_RANGE: {
                        JsonArray values = criterion.getJsonArray(VALUES, new JsonArray());
                        if (values.size() != 1) {
                            String msg = String.format("'%s' must contain exactly one value", criterionType);
                            return Future.failedFuture(
                                    new JsonObject()
                                            .put(STATUS, FAILED)
                                            .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                                            .put(DESC, msg)
                                            .encode());
                        }
                        break;
                    }
                    default:
                        // Term or others: nothing to check.
                        break;
                }
                return Future.succeededFuture();
            }).mapEmpty();
            validationFutures.add(fullValidation);
        }

        if (validationFutures.isEmpty()) {
            return Future.failedFuture(
                    new JsonObject().put(STATUS, FAILED).put(DESC, "No criteria provided").encode());
        }
        return CompositeFuture.all(validationFutures).mapEmpty();
    }


    private String getTextSearchErrorMsg(String errorMsg) {
        if (errorMsg.contains("is too long")) {
            return "The max string(q) size supported is " + STRING_SIZE;
        } else if (errorMsg.contains("pattern")) {
            return "Invalid 'q' format";
        } else if (errorMsg.contains("has missing required properties")) {
            return "Mandatory field(s) not provided; " + errorMsg;
        } else {
            return errorMsg;
        }
    }

    private String getFilterErrorMsg(String errorMsg) {
        if (errorMsg.contains("is too long")) {
            return "The max number of 'filter' should be " + FILTER_VALUE_SIZE;
        } else if (errorMsg.contains("pattern")) {
            return "Invalid format in 'filter'";
        } else if (errorMsg.contains("has missing required properties")) {
            return "Mandatory field(s) not provided";
        } else {
            return errorMsg;
        }
    }

    public static boolean isValidDateTimeOrder(String start, String end) {
        try {
            start = start.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");
            end = end.replaceAll("([+-]\\d{2})(\\d{2})$", "$1:$2");
            OffsetDateTime t1 = OffsetDateTime.parse(start);
            OffsetDateTime t2 = OffsetDateTime.parse(end);
            return t1.isBefore(t2);
        } catch (DateTimeParseException e) {
            LOGGER.error("Date parsing error: " + e.getMessage());
            return false;
        }
    }

    public static boolean isValidRangeOrder(int start, int end) {
        return start <= end;
    }
}
