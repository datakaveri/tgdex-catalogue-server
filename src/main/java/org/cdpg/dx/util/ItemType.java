package org.cdpg.dx.util;

import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_AI_MODEL;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_APPS;
import static org.cdpg.dx.tgdex.util.Constants.ITEM_TYPE_DATA_BANK;

public enum ItemType {
  AI_MODEL(ITEM_TYPE_AI_MODEL),
  DATA_BANK(ITEM_TYPE_DATA_BANK),
  APPS(ITEM_TYPE_APPS);

  private final String typeValue;

  ItemType(String typeValue) {
    this.typeValue = typeValue;
  }

  public static ItemType fromTypeValue(String value) {
    for (ItemType type : ItemType.values()) {
      if (type.getTypeValue().equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("No matching ItemType for: " + value);
  }

  public String getTypeValue() {
    return typeValue;
  }
}
