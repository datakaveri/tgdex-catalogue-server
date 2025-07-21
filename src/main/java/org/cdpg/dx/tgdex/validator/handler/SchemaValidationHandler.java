package org.cdpg.dx.tgdex.validator.handler;

import io.vertx.core.Handler;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.tgdex.validator.Validator;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import static org.cdpg.dx.tgdex.util.Constants.*;

public class SchemaValidationHandler implements Handler<RoutingContext> {
    private final Validator aiModelValidator;
    private final Validator dataBankResourceValidator;
    private final Validator adexAppsValidator;
    private static final Logger LOGGER = LogManager.getLogger(SchemaValidationHandler.class);

    public SchemaValidationHandler() throws IOException, ProcessingException {
        aiModelValidator = new Validator("/adexAiModelItemSchema.json");
        dataBankResourceValidator = new Validator("/adexDataBankResourceItemSchema.json");
        adexAppsValidator = new Validator("/adexAppsItemSchema.json");
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject request = ctx.body().asJsonObject();

        String itemType = getItemType(request);
        Future<String> isValidSchema;
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
                ctx.response().setStatusCode(400).end(new JsonObject().put("status", "failed").put("error", "Invalid Item Type").encode());
                return;
        }

        isValidSchema
            .onSuccess(res -> ctx.next())
            .onFailure(err -> ctx.fail(400));
    }

    private static String getItemType(JsonObject request) {
        Set<String> type = new HashSet<String>();
        try {
            type = new HashSet<String>(request.getJsonArray(TYPE).getList());
        } catch (Exception e) {
            LOGGER.error("Item type mismatch");
        }
        type.retainAll(ITEM_TYPES);
        return type.toString().replaceAll("\\[", "").replaceAll("\\]", "");
    }
}
