package org.cdpg.dx.tgdex.item.model;
import io.vertx.core.json.JsonObject;
import java.util.List;
import java.time.LocalDateTime;

/**
 * Common interface for all item types.
 */
public interface Item {

  String getName();
  List<String> getType();
  String getLabel();
  String getShortDescription();
  String getDescription();
  List<String> getTags();
  String getAccessPolicy();
  String getOrganizationType();
  String getOrganizationId();
  String getIndustry();
  String getDepartment();
  String getId();
  String getItemStatus();
  LocalDateTime getItemCreatedAt();
  String getContext();

  // Serialize this object to JsonObject
  JsonObject toJson();
}