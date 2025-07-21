package org.cdpg.dx.tgdex.validator.handler;

import io.vertx.core.Handler;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.tgdex.validator.Validator;

import java.util.UUID;

import static org.cdpg.dx.tgdex.util.Constants.*;

public class ItemValidationHandler implements Handler<RoutingContext> {
    private final Validator aiModelValidator;
    private final Validator dataBankResourceValidator;
    private final Validator adexAppsValidator;

    public ItemValidationHandler() throws Exception {
        aiModelValidator = new Validator("/adexAiModelItemSchema.json");
        dataBankResourceValidator = new Validator("/adexDataBankResourceItemSchema.json");
        adexAppsValidator = new Validator("/adexAppsItemSchema.json");
    }

    @Override
    public void handle(RoutingContext ctx) {
        JsonObject request = ctx.body().asJsonObject();

        try {
            // You may add validation logic here as needed (e.g., validateId)
            String itemType = getItemType(request);

            if (itemType.equalsIgnoreCase(ITEM_TYPE_AI_MODEL)) {
                validateAiModelItem(request, ctx);
            } else if (itemType.equalsIgnoreCase(ITEM_TYPE_DATA_BANK)) {
                validateDataBankItem(request, ctx);
            } else if (itemType.equalsIgnoreCase(ITEM_TYPE_APPS)) {
                validateAppsItem(request, ctx);
            } else {
                ctx.response().setStatusCode(400).end(new JsonObject().put("status", "failed").put("error", "Invalid Item Type").encode());
                return;
            }
        } catch (Exception ex) {
            ctx.response().setStatusCode(400).end(new JsonObject().put("status", "failed").put("error", ex.getMessage()).encode());
        }
    }

    private String getItemType(JsonObject request) {
        try {
            if (request.containsKey(TYPE)) {
                return request.getJsonArray(TYPE).getString(0);
            }
        } catch (Exception e) {}
        return "";
    }

    private void validateAppsItem(JsonObject r, RoutingContext ctx) {
        putCommonFields(r);
        ctx.response().setStatusCode(200).end(new JsonObject().put("status", "success").encode());
    }

    private void validateDataBankItem(JsonObject r, RoutingContext ctx) {
        putCommonFields(r);
        ctx.response().setStatusCode(200).end(new JsonObject().put("status", "success").encode());
    }

    private void validateAiModelItem(JsonObject r, RoutingContext ctx) {
        putCommonFields(r);
        ctx.response().setStatusCode(200).end(new JsonObject().put("status", "success").encode());
    }

    private void putCommonFields(JsonObject r) {
        if (!r.containsKey(ID)) {
            r.put(ID, UUID.randomUUID().toString());
        }
        r.put("itemStatus", "active");
        r.put("itemCreatedAt", java.time.OffsetDateTime.now().toString());
        r.put("lastUpdated", java.time.OffsetDateTime.now().toString());
    }
}
