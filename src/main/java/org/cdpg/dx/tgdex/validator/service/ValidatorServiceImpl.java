package org.cdpg.dx.tgdex.validator.service;


import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.validator.Validator;
import org.cdpg.dx.tgdex.validator.util.SearchQueryValidatorHelper;

import static org.cdpg.dx.tgdex.util.Constants.*;
import static org.cdpg.dx.tgdex.validator.Constants.*;

/**
 * The Validator Service Implementation.
 *
 * <h1>Validator Service Implementation</h1>
 *
 * <p>The Validator Service implementation in the IUDX Catalogue Server implements the definitions
 * of the {@link ValidatorService}.
 *
 * @version 1.0
 * @since 2020-05-31
 */
public class ValidatorServiceImpl implements ValidatorService {

  private static final Logger LOGGER = LogManager.getLogger(ValidatorServiceImpl.class);

  private final String docIndex;
  private final String vocContext;
  private Future<String> isValidSchema;
  private Validator aiModelValidator;
  private Validator termValidator;
  private Validator rangeValidator;
  private Validator temporalValidator;
  private Validator dataBankResourceValidator;
  private Validator adexAppsValidator;
  private Validator textSearchQueryValidator;
  private Validator filterSearchQueryValidator;
  private QueryDecoder queryDecoder;
  private ElasticsearchService elasticsearchService;
  /**
   * Constructs a new ValidatorServiceImpl object with the specified ElasticClient and docIndex.
   *
   * @param docIndex the index name to use for storing documents in Elasticsearch
   */
  public ValidatorServiceImpl(ElasticsearchService elasticsearchService, String docIndex, String vocContext) {
    this.elasticsearchService=elasticsearchService;

    this.docIndex = docIndex;
    this.vocContext = vocContext;
    this.queryDecoder=new QueryDecoder();
    try {
      aiModelValidator = new Validator("/adexAiModelItemSchema.json");
      dataBankResourceValidator = new Validator("/adexDataBankResourceItemSchema.json");
      adexAppsValidator = new Validator("/adexAppsItemSchema.json");
      textSearchQueryValidator = new Validator("/textSearchQuerySchema.json");
      filterSearchQueryValidator = new Validator("/filterSearchQuerySchema.json");
      termValidator = new Validator("/attributeSearchQuerySchema.json");
      rangeValidator = new Validator("/rangeSearchQuerySchema.json");
      temporalValidator = new Validator("/temporalSearchQuerySchema.json");
    } catch (IOException | ProcessingException e) {
      e.printStackTrace();
    }
  }

  /**
   * Generates timestamp with timezone +05:30.
   */
  public static String getUtcDatetimeAsString() {
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ssZ");
    df.setTimeZone(TimeZone.getTimeZone("IST"));
    return df.format(new Date());
  }

  public static String getPrettyLastUpdatedForUI() {
    DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    DateTimeFormatter outputFormatter = DateTimeFormatter
            .ofPattern("dd MMMM, yyyy - hh:mm a", Locale.ENGLISH);

    // Format the current date in IST
    ZonedDateTime nowIst = ZonedDateTime.now(ZoneId.of("Asia/Kolkata"));
    String istTime = nowIst.format(inputFormatter);

    // Parse using OffsetDateTime (handles the +0530 format correctly)
    OffsetDateTime offsetDateTime = OffsetDateTime.parse(istTime, inputFormatter);

    // Format to the desired output
    return offsetDateTime.format(outputFormatter);
  }

  private static String getItemType(JsonObject request,Promise promise) {
      new JsonArray().getList();
      Set<String> type = new HashSet<String>(new JsonArray().getList());
    try {
      type = new HashSet<String>(request.getJsonArray(TYPE).getList());
    } catch (Exception e) {
      LOGGER.error("Item type mismatch");
      promise.fail(VALIDATION_FAILURE_MSG);
    }
    type.retainAll(ITEM_TYPES);
      return type.toString().replaceAll("\\[", "").replaceAll("\\]", "");
  }

  String getReturnTypeForValidation(JsonObject result) {
    LOGGER.debug(result);
    return result.getJsonArray(RESULTS).stream()
            .map(JsonObject.class::cast)
            .map(r -> r.getString(TYPE))
            .toList()
            .toString();
  }

  /*
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  @Override
  public Future<Void> validateSchema(JsonObject request) {
    Promise<Void> promise=Promise.promise();
    LOGGER.debug("Info: Reached Validator service validate schema");
    String itemType = null;
    itemType =
            request.containsKey("stack_type")
                    ? request.getString("stack_type")
                    : getItemType(request,promise);
    request.remove("api");

    LOGGER.debug("Info: itemType: " + itemType);

    switch (itemType) {
      case ITEM_TYPE_AI_MODEL:
        isValidSchema = aiModelValidator.validate(request.toString());
        break;
      case ITEM_TYPE_DATA_BANK:
        isValidSchema = dataBankResourceValidator.validate(request.toString());
        break;
      case ITEM_TYPE_APPS:
        isValidSchema = adexAppsValidator.validate(request.toString());
        break;
      default:
        promise.fail("Invalid Item Type");
    }

    validateSchema(promise);
    return promise.future();
  }

  private void validateSchema(Promise promise) {
    isValidSchema.onSuccess(promise::complete).onFailure(x -> {
      LOGGER.error("Fail: Invalid Schema");
      LOGGER.error(x.getMessage());
      promise.fail(x.getMessage());
    });
  }

  /*
   * {@inheritDoc}
   */
  @SuppressWarnings("unchecked")
  @Override
  public Future<Void> validateItem(JsonObject request) {

    request.put(CONTEXT, vocContext);
    String method = (String) request.remove(HTTP_METHOD);
    Promise<Void> promise=Promise.promise();
    String itemType = getItemType(request,promise);
    LOGGER.debug("Info: itemType: " + itemType);

    // Validate if Resource
     if (itemType.equalsIgnoreCase(ITEM_TYPE_AI_MODEL)) {
      validateAiModelItem(request, method, promise);
    } else if (itemType.equalsIgnoreCase(ITEM_TYPE_DATA_BANK)) {
      validateDataBankItem(request, method, promise);
    } else if (itemType.equalsIgnoreCase(ITEM_TYPE_APPS)) {
      validateAppsItem(request, method, promise);
    }
    return promise.future();
  }

  private void validateAppsItem(JsonObject request, String method,
                                Promise<Void> promise) {
    validateId(request, promise);
    if (request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }
    request.put(ITEM_STATUS, ACTIVE).put(LAST_UPDATED, getPrettyLastUpdatedForUI())
            .put(ITEM_CREATED_AT, getUtcDatetimeAsString());
    String checkQuery = ITEM_WITH_NAME_EXISTS_QUERY
            .replace("$1", ITEM_TYPE_APPS).replace("$2", request.getString(NAME));
    //QueryModel queryModel = queryDecoder.getQueryModel(request);
    LOGGER.debug(checkQuery);
//    client.searchGetId(
//            checkQuery,
//            docIndex,
//            res -> {
//              if (res.failed()) {
//                LOGGER.debug("Fail: DB Error");
//                handler.handle(Future.failedFuture(VALIDATION_FAILURE_MSG));
//                return;
//              }
//              if (method.equalsIgnoreCase(REQUEST_POST) && res.result().getInteger(TOTAL_HITS) > 0) {
//                LOGGER.debug("potential apps item already exists with the given name");
//                handler.handle(Future.failedFuture("Fail: Apps item already exists"));
//              } else {
//                handler.handle(Future.succeededFuture(request));
//              }
//            });
  }

  private void validateAiModelItem(JsonObject request, String method,
                                   Promise<Void> promise) {
    validateId(request, promise);
    if (!request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }

    request.put(ITEM_STATUS, ACTIVE).put(LAST_UPDATED, getPrettyLastUpdatedForUI())
            .put(ITEM_CREATED_AT, getUtcDatetimeAsString());

    String checkQuery = ITEM_WITH_NAME_EXISTS_QUERY
            .replace("$1", ITEM_TYPE_AI_MODEL).replace("$2", request.getString(NAME));
//    client.searchAsync(
//            checkQuery,
//            docIndex,
//            res -> {
//              if (res.failed()) {
//                LOGGER.debug("Fail: DB Error");
//                handler.handle(Future.failedFuture(VALIDATION_FAILURE_MSG));
//                return;
//              }
//              String returnType = getReturnTypeForValidation(res.result());
//              LOGGER.debug(returnType);
//              if (method.equalsIgnoreCase(REQUEST_POST)
//                      && returnType.contains(ITEM_TYPE_AI_MODEL)) {
//                LOGGER.debug("AI Model already exists with the name {} in organization {}",
//                        request.getString(NAME), request.getString(ORGANIZATION_ID));
//                handler.handle(Future.failedFuture("Fail: AI Model item already exists"));
//              } else {
//                handler.handle(Future.succeededFuture(request));
//              }
//            });
  }

  private void validateDataBankItem(JsonObject request, String method,
                                    Promise<Void> promise) {
    validateId(request, promise);
    if (!request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }

    request.put(ITEM_STATUS, ACTIVE).put(LAST_UPDATED, getPrettyLastUpdatedForUI())
            .put(ITEM_CREATED_AT, getUtcDatetimeAsString());

    String checkQuery = ITEM_WITH_NAME_EXISTS_QUERY
            .replace("$1", ITEM_TYPE_DATA_BANK).replace("$2", request.getString(NAME));

//    client.searchAsync(
//            checkQuery,
//            docIndex,
//            res -> {
//              if (res.failed()) {
//                LOGGER.debug("Fail: DB Error");
//                handler.handle(Future.failedFuture(VALIDATION_FAILURE_MSG));
//                return;
//              }
//              String returnType = getReturnTypeForValidation(res.result());
//              LOGGER.debug(returnType);
//              if (method.equalsIgnoreCase(REQUEST_POST)
//                      && returnType.contains(ITEM_TYPE_AI_MODEL)) {
//                LOGGER.debug("Data Bank item already exists with the name {} in organization {}",
//                        request.getString(NAME), request.getString(ORGANIZATION_ID));
//                handler.handle(Future.failedFuture("Fail: DataBank item already exists"));
//              } else {
//                handler.handle(Future.succeededFuture(request));
//              }
//            });
  }

  private boolean isValidUuid(String uuidString) {
    return UUID_PATTERN.matcher(uuidString).matches();
  }

  private void validateId(
          JsonObject request, Promise promise) {
    if (request.containsKey(ID)) {
      String id = request.getString(ID);
      LOGGER.debug("id in the request body: " + id);

      if (!isValidUuid(id)) {
        promise.fail("validation failed. Incorrect id");
      }
    } else if (!request.containsKey(ID)) {
      promise.fail("mandatory id field not present in request body");
    }
  }

  @Override
  public Future<Void> validateSearchQuery(JsonObject request) {
    LOGGER.debug("Info: Validating attributes limits and  constraints "+request);
    String searchType = request.getString(SEARCH_TYPE, "");
    Promise<Void> promise = Promise.promise();
    List<Future<JsonObject>> validations = new ArrayList<>();

    if (searchType.contains(SEARCH_TYPE_TEXT)) {
      Promise<JsonObject> p = Promise.promise();
      this.validateTextSearchQuery(request, p);
      validations.add(p.future());
    }
    if (searchType.contains(SEARCH_TYPE_CRITERIA)) {
      Promise<JsonObject> p = Promise.promise();
      this.validateSearchCriteria(request, p);
      validations.add(p.future());
    }
    if (searchType.contains(RESPONSE_FILTER)) {
      Promise<JsonObject> p = Promise.promise();
      this.validateFilterSearchQuery(request, p);
      validations.add(p.future());
    }

    Future.all(validations)
            .onFailure(err -> {
              LOGGER.error("Fail: Invalid Schema: {}", err.getMessage());
              promise.fail(err.getLocalizedMessage());
            });

    // Additional validation logic for  limit, and
    // offset fields (similar to your previous implementation)
    // Validating the 'instance' field
    JsonObject errResponse = new JsonObject().put(STATUS, FAILED);
    // Validating the 'limit' and 'offset' fields
    if (request.containsKey(LIMIT) || request.containsKey(OFFSET)) {
      Integer limit = request.getInteger(LIMIT, 0);
      Integer offset = request.getInteger(OFFSET, 0);
      int totalSize = limit + offset;

      if (totalSize <= 0 || totalSize > MAX_RESULT_WINDOW) { // Example max size check
        LOGGER.error("Error: The limit + offset param has exceeded the limit");
        errResponse
                .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                .put(DESC, "The limit + offset should be between 1 to " + MAX_RESULT_WINDOW);
        promise.fail(errResponse.encode());
      }
    }
    LOGGER.debug("Request validation successful.");
    promise.complete();
      return promise.future();
  }

  public void validateSearchCriteria(JsonObject request,Promise<JsonObject> promise) {
    JsonArray criteriaArray = request.getJsonArray(SEARCH_CRITERIA_KEY);
    List<Future<String>> validationFutures = new ArrayList<>();

    for (int i = 0; i < criteriaArray.size(); i++) {
      JsonObject criterion = criteriaArray.getJsonObject(i);
      String searchType = criterion.getString(SEARCH_TYPE);

      if (searchType == null || searchType.isBlank()) {
        JsonObject error = new JsonObject()
                .put(STATUS, FAILED)
                .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                .put(DESC, "'searchType' is missing or empty in searchCriteria at index " + i);
        promise.fail(error.encode());
      }

      Future<String> validationFuture = null;
      switch (searchType) {
        case TERM:
          validationFuture = termValidator.validateSearchCriteria(criterion.encode());
          break;

        case BETWEEN_RANGE:
        case BEFORE_RANGE:
        case AFTER_RANGE:
          validationFuture = rangeValidator.validateSearchCriteria(criterion.encode());
          break;

        case BETWEEN_TEMPORAL:
        case BEFORE_TEMPORAL:
        case AFTER_TEMPORAL:
          validationFuture = temporalValidator.validateSearchCriteria(criterion.encode());
          break;
        default:
          JsonObject error = new JsonObject()
                  .put(STATUS, FAILED)
                  .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                  .put(DESC, "Invalid searchType: " + searchType);
          promise.fail(error.encode());
      }

      // Wrap each validation future to handle individual failure
        assert validationFuture != null;
        Future<String> wrappedFuture = validationFuture.recover(err -> {
        // Fail-fast on any individual failure
        JsonObject errorMsg = new JsonObject()
                .put(STATUS, FAILED)
                .put(TYPE, TYPE_INVALID_PROPERTY_VALUE)
                .put(DESC, err.getMessage());
        return Future.failedFuture(errorMsg.encode()); // stop execution
      });

      validationFutures.add(wrappedFuture);
    }

    // All validations passed
    CompositeFuture.all(new ArrayList<>(validationFutures))
            .onSuccess(res -> {
              isValidSchema = Future.succeededFuture(SUCCESS);
              SearchQueryValidatorHelper.handleSearchCriteriaResult(isValidSchema, request, promise);
            });

    return;
  }

  public void validateTextSearchQuery(JsonObject request,Promise<JsonObject> promise) {
    isValidSchema = textSearchQueryValidator.validateSearchCriteria(request.toString());

    SearchQueryValidatorHelper.handleTextSearchValidationResult(isValidSchema,promise );
  }

  public void validateFilterSearchQuery(JsonObject request,Promise<JsonObject> promise) {
    isValidSchema = filterSearchQueryValidator.validateSearchCriteria(request.toString());

    SearchQueryValidatorHelper.handleFilterSearchValidationResult(isValidSchema, promise);
  }
}
