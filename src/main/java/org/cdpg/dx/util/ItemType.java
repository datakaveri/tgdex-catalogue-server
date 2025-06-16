package org.cdpg.dx.util;

import java.util.HashMap;
import java.util.Map;

public enum ItemType {
    DATA_BANK("adex:DataBank"),
    AI_MODEL("adex:AiModel"),
    APPS("adex:Apps");

    public final String itemType;

    ItemType(String itemType) {
        this.itemType = itemType;
    }
}
