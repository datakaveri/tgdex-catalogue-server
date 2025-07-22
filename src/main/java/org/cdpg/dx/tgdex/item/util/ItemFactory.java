package org.cdpg.dx.tgdex.item.util;

import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_DATA_BANK;
import static org.cdpg.dx.tgdex.util.Constants.TYPE;

import io.vertx.core.json.JsonObject;
import java.util.List;
import org.cdpg.dx.tgdex.item.model.AiModelItem;
import org.cdpg.dx.tgdex.item.model.AppsItem;
import org.cdpg.dx.tgdex.item.model.DataBankItem;
import org.cdpg.dx.tgdex.item.model.Item;

public class ItemFactory {
  public static Item parse(JsonObject body) {
    List<String> typeList = body.getJsonArray(TYPE).getList();

    if (typeList == null || typeList.isEmpty()) {
      throw new IllegalArgumentException("Missing 'type' field");
    }

    String type = typeList.getFirst();

    return switch (type) {
      case ITEM_TYPE_DATA_BANK -> body.mapTo(DataBankItem.class);
      case ITEM_TYPE_APPS -> body.mapTo(AppsItem.class);
      case ITEM_TYPE_AI_MODEL -> body.mapTo(AiModelItem.class);
      default -> throw new IllegalArgumentException("Unsupported type: " + type);
    };
  }

  public static Item from(JsonObject json) {
    List<String> types = json.getJsonArray(TYPE).getList();
    if (types.contains(ITEM_TYPE_AI_MODEL)) {
      return AiModelItem.fromJson(json);
    } else if (types.contains(ITEM_TYPE_APPS)) {
      return AppsItem.fromJson(json);
    } else if (types.contains(ITEM_TYPE_DATA_BANK)) {
      return DataBankItem.fromJson(json);
    }
    throw new IllegalArgumentException("Unknown item type: " + types);
  }
}
