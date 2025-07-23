package org.cdpg.dx.tgdex.item.service;

import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.tgdex.item.model.Item;

public interface ItemService {
    public Future<Void> createItem(Item item);
    public Future<Item> getItem(String id, String type);
    public Future<Void> updateItem(Item item);
    public Future<Void> deleteItem(String id);
    Future<Item> itemWithTheNameExists(String type, String name);

}
