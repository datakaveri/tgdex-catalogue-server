package org.cdpg.dx.tgdex.item.service;

import io.vertx.core.Future;
<<<<<<< HEAD
import org.cdpg.dx.tgdex.item.model.Item;
import org.cdpg.dx.tgdex.item.util.GetItemRequest;
import org.cdpg.dx.tgdex.search.util.ResponseModel;

public interface ItemService {
    public Future<Void> createItem(Item item);
    public Future<Void> updateItem(Item item);
    public Future<Void> deleteItem(String id);
    Future<Item> itemWithTheNameExists(String type, String name);
    Future<ResponseModel> getItem(GetItemRequest request);
}
