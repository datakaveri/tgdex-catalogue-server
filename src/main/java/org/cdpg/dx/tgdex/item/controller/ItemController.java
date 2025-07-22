package org.cdpg.dx.tgdex.item.controller;

import static org.cdpg.dx.database.elastic.util.Constants.DATA_UPLOAD_STATUS;
import static org.cdpg.dx.database.elastic.util.Constants.MEDIA_URL;
import static org.cdpg.dx.database.elastic.util.Constants.PENDING;
import static org.cdpg.dx.database.elastic.util.Constants.PUBLISH_STATUS;
import static org.cdpg.dx.database.elastic.util.Constants.TYPE_KEY;
import static org.cdpg.dx.tgdex.util.Constants.*;
import static org.cdpg.dx.tgdex.util.Constants.ID;
import static org.cdpg.dx.tgdex.validator.Constants.ACTIVE;
import static org.cdpg.dx.tgdex.validator.Constants.CONTEXT;
import static org.cdpg.dx.tgdex.validator.Constants.ITEM_CREATED_AT;
import static org.cdpg.dx.tgdex.validator.Constants.ITEM_STATUS;
import static org.cdpg.dx.tgdex.validator.Constants.LAST_UPDATED;
import static org.cdpg.dx.tgdex.validator.Constants.VALIDATION_FAILURE_MSG;
import static org.cdpg.dx.util.Constants.*;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auditing.handler.AuditingHandler;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;
import org.cdpg.dx.database.elastic.model.QueryDecoder;
import org.cdpg.dx.database.elastic.model.QueryModel;
import org.cdpg.dx.database.elastic.service.ElasticsearchService;
import org.cdpg.dx.tgdex.apiserver.ApiController;
import org.cdpg.dx.tgdex.item.model.Item;
import org.cdpg.dx.tgdex.item.service.ItemService;
import org.cdpg.dx.tgdex.item.util.ItemFactory;
import org.cdpg.dx.tgdex.item.util.RespBuilder;
import org.cdpg.dx.util.CheckIfTokenPresent;
import org.cdpg.dx.util.VerifyItemTypeAndRole;

import java.util.HashSet;
import java.util.Set;

public class ItemController implements ApiController {
  private static final Logger LOGGER = LogManager.getLogger(ItemController.class);

  private final AuditingHandler auditingHandler;
  private final ItemService itemService;
  private final QueryDecoder queryDecoder;
  private final ElasticsearchService elasticsearchService;
  private final String vocContext;
  private final String docIndex;

  private final CheckIfTokenPresent checkIfTokenPresent = new CheckIfTokenPresent();
  private final VerifyItemTypeAndRole verifyItemTypeAndRole = new VerifyItemTypeAndRole();

  public ItemController(AuditingHandler auditingHandler, ItemService itemService,
                        ElasticsearchService elasticsearchService, String docIndex,
                        String vocContext) {
    this.auditingHandler = auditingHandler;
    this.itemService = itemService;
    this.vocContext = vocContext;
    this.docIndex = docIndex;
    this.queryDecoder = new QueryDecoder();
    this.elasticsearchService = elasticsearchService;
  }

  @Override
  public void register(RouterBuilder builder) {
    builder.operation(CREATE_ITEM)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem)
        .handler(auditingHandler::handleApiAudit);

    builder.operation(GET_ITEM)
        .handler(this::handleGetItem)
        .handler(auditingHandler::handleApiAudit);

    builder.operation(DELETE_ITEM)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleDeleteItem)
        .handler(auditingHandler::handleApiAudit);

    builder.operation(UPDATE_ITEM)
        .handler(checkIfTokenPresent)
        .handler(verifyItemTypeAndRole)
        .handler(this::handleCreateOrUpdateItem)
        .handler(auditingHandler::handleApiAudit);

    LOGGER.debug("Item Controller registered");
  }

  private void handleCreateOrUpdateItem(RoutingContext ctx) {
    LOGGER.debug("Handling create/update item");
    HttpServerResponse response = ctx.response();

    JsonObject body;
    try {
      body = ctx.body().asJsonObject();
    } catch (Exception e) {
      LOGGER.error("Invalid JSON", e);
      response.setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail("Request body is not valid JSON")
              .getResponse());
      return;
    }

    Set<String> type;
    try {
      JsonArray typeArray = body.getJsonArray(TYPE);
      if (typeArray == null || typeArray.isEmpty()) {
        throw new IllegalArgumentException("Missing or empty 'type' field");
      }
      type = new HashSet<>(typeArray.getList());
      type.retainAll(ITEM_TYPES);
      if (type.isEmpty()) {
        throw new IllegalArgumentException("No valid types found in 'type' field");
      }
    } catch (Exception e) {
      LOGGER.error("Invalid 'type' field", e);
      response.setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SCHEMA)
              .withTitle(TITLE_INVALID_SCHEMA)
              .withDetail("Invalid type for item/type not present")
              .getResponse());
      return;
    }

    String itemType = type.iterator().next(); // Only 1 valid item type allowed
    LOGGER.debug("Info: itemType = {}", itemType);
    ctx.put(ITEM_TYPE, itemType);

    // Inject Keycloak info if present
    if (ITEM_TYPE_AI_MODEL.equals(itemType)
        || ITEM_TYPE_DATA_BANK.equals(itemType)
        || ITEM_TYPE_APPS.equals(itemType)) {
      String kcId = ctx.user().principal().getString(SUB);
      String orgName = ctx.user().principal().getString(ORGANIZATION_NAME);
      body.put(PROVIDER_USER_ID, kcId);
      body.put(DEPARTMENT, orgName);
      body.put(UPLOADED_BY, orgName);
    }

    String method = ctx.request().method().name();
    body.put(HTTP_METHOD, method);
    body.put(CONTEXT, vocContext);

    Promise<JsonObject> isItemExistsWithName = Promise.promise();

    switch (itemType) {
      case ITEM_TYPE_AI_MODEL -> checkAiModelItemExists(body, method, isItemExistsWithName);
      case ITEM_TYPE_DATA_BANK -> checkDataBankItemExists(body, method, isItemExistsWithName);
      case ITEM_TYPE_APPS -> checkAppsItemExists(body, method, isItemExistsWithName);
      default -> {
        response.setStatusCode(400).end(
            new RespBuilder()
                .withType(TYPE_INVALID_SCHEMA)
                .withTitle(TITLE_INVALID_SCHEMA)
                .withDetail("Unsupported item type: " + itemType)
                .getResponse());
        return;
      }
    }

    isItemExistsWithName.future().onComplete(validationResult -> {
      if (validationResult.failed()) {
        String msg = validationResult.cause().getMessage();
        LOGGER.error("Item validation failed: {}", msg);

        if ("validation failed. Incorrect id".equalsIgnoreCase(msg)) {
          response.setStatusCode(400).end(
              new RespBuilder()
                  .withType(TYPE_INVALID_UUID)
                  .withTitle(TITLE_INVALID_UUID)
                  .withDetail("Syntax of the UUID is incorrect")
                  .getResponse());
          return;
        }

        response.setStatusCode(400).end(
            new RespBuilder()
                .withType(TYPE_LINK_VALIDATION_FAILED)
                .withTitle(TITLE_LINK_VALIDATION_FAILED)
                .withDetail(msg)
                .getResponse());
        return;
      }

      LOGGER.debug("Item validation passed");

      Item item;
      try {
        item = ItemFactory.parse(body);
      } catch (Exception e) {
        LOGGER.error("Failed to parse item into model", e);
        response.setStatusCode(400).end(
            new RespBuilder()
                .withType(TYPE_INVALID_SYNTAX)
                .withTitle(TITLE_INVALID_SYNTAX)
                .withDetail(e.getMessage())
                .getResponse());
        return;
      }

      if (REQUEST_POST.equalsIgnoreCase(method)) {
        LOGGER.debug("Creating item...");
        itemService.createItem(item)
            .onSuccess(res -> response.setStatusCode(201).end(
                new RespBuilder()
                    .withType(TYPE_SUCCESS)
                    .withTitle(TITLE_SUCCESS)
                    .withDetail("Item created successfully")
                    .getResponse()))
            .onFailure(err -> {
              LOGGER.error("Create item failed", err);
              response.setStatusCode(400).end(
                  new RespBuilder()
                      .withType(TYPE_OPERATION_NOT_ALLOWED)
                      .withTitle(TITLE_OPERATION_NOT_ALLOWED)
                      .withDetail(err.getMessage())
                      .getResponse());
            });
      } else {
        LOGGER.debug("Updating item...");
        itemService.updateItem(item)
            .onSuccess(res -> response.setStatusCode(200).end(
                new RespBuilder()
                    .withType(TYPE_SUCCESS)
                    .withTitle(TITLE_SUCCESS)
                    .withDetail("Item updated successfully")
                    .getResponse()))
            .onFailure(err -> {
              LOGGER.error("Update item failed", err);
              response.setStatusCode(400).end(
                  new RespBuilder()
                      .withType(TYPE_OPERATION_NOT_ALLOWED)
                      .withTitle(TITLE_OPERATION_NOT_ALLOWED)
                      .withDetail(err.getMessage())
                      .getResponse());
            });
      }
    });
  }

  private void checkAppsItemExists(JsonObject request, String method,
                                Promise<JsonObject> promise) {
    validateId(request, promise);
    if (!request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }
    request.put(ITEM_STATUS, ACTIVE).put(LAST_UPDATED, getPrettyLastUpdatedForUI())
        .put(ITEM_CREATED_AT, getUtcDatetimeAsString());

    itemService.itemWithTheNameExists(ITEM_TYPE_APPS, request.getString(NAME))
        .onFailure(err -> {
          LOGGER.debug("Fail: DB Error");
          promise.fail(VALIDATION_FAILURE_MSG);
        })
        .onSuccess(res -> {
          if (method.equalsIgnoreCase(REQUEST_POST) && ElasticsearchResponse.getTotalHits() > 0) {
            LOGGER.debug("potential apps item already exists with the given name");
            promise.fail("Fail: Apps item already exists");
          } else {
            promise.complete(request);
          }
        });
  }

  private void checkAiModelItemExists(JsonObject request, String method,
                                   Promise<JsonObject> promise) {
    validateId(request, promise);
    if (!request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }

    request.put(ITEM_STATUS, ACTIVE)
        .put(LAST_UPDATED, getPrettyLastUpdatedForUI())
        .put(ITEM_CREATED_AT, getUtcDatetimeAsString());

    itemService.itemWithTheNameExists(ITEM_TYPE_AI_MODEL, request.getString(NAME))
        .onFailure(err -> {
          LOGGER.debug("Fail: DB Error");
          promise.fail(VALIDATION_FAILURE_MSG);
        })
        .onSuccess(res -> {
          String returnType = getReturnTypeForValidation(res.toJson());
          LOGGER.debug(returnType);
          if (method.equalsIgnoreCase(REQUEST_POST) && returnType.contains(ITEM_TYPE_AI_MODEL)) {
            LOGGER.debug("AI Model already exists with the name {} in organization {}",
                request.getString(NAME), request.getString(ORGANIZATION_ID));
            promise.fail("Fail: AI Model item already exists");
            return;
          }

          // Handle dataUploadStatus here — after checking existing ES data
          boolean mediaUrlPresent =
              request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();
          if (method.equalsIgnoreCase(REQUEST_POST)) {
            // On POST: Always infer from mediaURL
            request.put(DATA_UPLOAD_STATUS, mediaUrlPresent);
            request.put(PUBLISH_STATUS, PENDING);
          } else if (method.equalsIgnoreCase(REQUEST_PUT)) {
            // On PUT: Preserve previous true if already set
            boolean wasPreviouslyUploaded = extractDataUploadStatusFromES(res.toJson());
            boolean previousMediaUrlPresent = extractMediaUrlFromES(res.toJson());

            if (mediaUrlPresent) {
              request.put(DATA_UPLOAD_STATUS, true);
            } else if (wasPreviouslyUploaded && !previousMediaUrlPresent) {
              // Preserve only if upload was done via the alternate (non-mediaURL) flow
              request.put(DATA_UPLOAD_STATUS, true);
            } else {
              request.put(DATA_UPLOAD_STATUS, false);
            }

            String publishStatus = extractPublishStatusFromES(res.toJson());
            request.put(PUBLISH_STATUS, publishStatus);
          }

          promise.complete(request);
        });
  }

  private boolean extractDataUploadStatusFromES(JsonObject res) {
    try {
      if (res != null && !res.isEmpty()) {
        return res.getBoolean(DATA_UPLOAD_STATUS, false);
      }
    } catch (Exception e) {
      LOGGER.error("Error extracting dataUploadStatus from ES", e);
    }
    return false;
  }

  private boolean extractMediaUrlFromES(JsonObject res) {
    try {
      if (res != null && !res.isEmpty()) {
        String mediaUrl = res.getString(MEDIA_URL, "");
        return !mediaUrl.isBlank();
      }
    } catch (Exception e) {
      LOGGER.error("Error extracting mediaURL from ES", e);
    }
    return false;
  }

  private String extractPublishStatusFromES(JsonObject res) {
    try {
      if (res != null && !res.isEmpty()) {
        return res.getString(PUBLISH_STATUS, PENDING);
      }
    } catch (Exception e) {
      LOGGER.error("Error extracting mediaURL from ES", e);
    }
    return PENDING;
  }


  private void checkDataBankItemExists(JsonObject request, String method,
                                    Promise<JsonObject> promise) {
    validateId(request, promise);
    if (!request.containsKey(ID)) {
      UUID uuid = UUID.randomUUID();
      request.put(ID, uuid.toString());
    }

    request.put(ITEM_STATUS, ACTIVE)
        .put(LAST_UPDATED, getPrettyLastUpdatedForUI())
        .put(ITEM_CREATED_AT, getUtcDatetimeAsString());

    itemService.itemWithTheNameExists(ITEM_TYPE_DATA_BANK, request.getString(NAME))
        .onFailure(err -> {
          LOGGER.debug("Fail: DB Error");
          promise.fail(VALIDATION_FAILURE_MSG);
        })
        .onSuccess(res -> {
          String returnType = getReturnTypeForValidation(res.toJson());
          LOGGER.debug(returnType);
          if (method.equalsIgnoreCase(REQUEST_POST)
              && returnType.contains(ITEM_TYPE_DATA_BANK)) {
            LOGGER.debug("Data Bank item already exists with the name {} in organization {}",
                request.getString(NAME), request.getString(ORGANIZATION_ID));
            promise.fail("Fail: DataBank item already exists");
            return;
          }

          // Handle dataUploadStatus here — after checking existing ES data
          boolean mediaUrlPresent =
              request.containsKey(MEDIA_URL) && !request.getString(MEDIA_URL).isBlank();
          if (method.equalsIgnoreCase(REQUEST_POST)) {
            // On POST: Always infer from mediaURL
            request.put(DATA_UPLOAD_STATUS, mediaUrlPresent);
            request.put(PUBLISH_STATUS, PENDING);
          } else if (method.equalsIgnoreCase(REQUEST_PUT)) {
            // On PUT: Preserve previous true if already set
            boolean wasPreviouslyUploaded = extractDataUploadStatusFromES(res.toJson());
            boolean previousMediaUrlPresent = extractMediaUrlFromES(res.toJson());

            if (mediaUrlPresent) {
              request.put(DATA_UPLOAD_STATUS, true);
            } else if (wasPreviouslyUploaded && !previousMediaUrlPresent) {
              // Preserve only if upload was done via the alternate (non-mediaURL) flow
              request.put(DATA_UPLOAD_STATUS, true);
            } else {
              request.put(DATA_UPLOAD_STATUS, false);
            }

            String publishStatus = extractPublishStatusFromES(res.toJson());
            request.put(PUBLISH_STATUS, publishStatus);
          }

          promise.complete(request);
        });
  }

  String getReturnTypeForValidation(JsonObject result) {
    LOGGER.debug(result);
    return result.getJsonArray(RESULTS).stream()
        .map(JsonObject.class::cast)
        .map(r -> r.getString(TYPE))
        .toList()
        .toString();
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

  private void handleGetItem(RoutingContext ctx) {
    String id = ctx.pathParam("id");
    String type = ctx.request().getParam(TYPE_KEY);

    if (id == null || id.isBlank()) {
      ctx.response().setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_ID_NOT_FOUND)
              .getResponse());
      return;
    }

    itemService.getItem(id, type)
        .onSuccess(item -> ctx.response().setStatusCode(200).end(
            new RespBuilder()
                .withType(TYPE_SUCCESS)
                .withTitle(TITLE_SUCCESS)
                .withResult(JsonObject.mapFrom(item).encode())
                .getResponse()))
        .onFailure(err -> {
          LOGGER.error("Get item failed", err);
          ctx.response().setStatusCode(404).end(
              new RespBuilder()
                  .withType(TYPE_ITEM_NOT_FOUND)
                  .withTitle(TITLE_ITEM_NOT_FOUND)
                  .withDetail(err.getMessage())
                  .getResponse());
        });
  }

  private void handleDeleteItem(RoutingContext ctx) {
    String id = ctx.pathParam("id");

    if (id == null || id.isBlank()) {
      ctx.response().setStatusCode(400).end(
          new RespBuilder()
              .withType(TYPE_INVALID_SYNTAX)
              .withTitle(TITLE_INVALID_SYNTAX)
              .withDetail(DETAIL_ID_NOT_FOUND)
              .getResponse());
      return;
    }

    itemService.deleteItem(id)
        .onSuccess(v -> ctx.response().setStatusCode(200).end(
            new RespBuilder()
                .withType(TYPE_SUCCESS)
                .withTitle(TITLE_SUCCESS)
                .withDetail("Item deleted successfully")
                .getResponse()))
        .onFailure(err -> {
          LOGGER.error("Delete item failed", err);
          ctx.response().setStatusCode(400).end(
              new RespBuilder()
                  .withType(TYPE_OPERATION_NOT_ALLOWED)
                  .withTitle(TITLE_OPERATION_NOT_ALLOWED)
                  .withDetail(err.getMessage())
                  .getResponse());
        });
  }
}
