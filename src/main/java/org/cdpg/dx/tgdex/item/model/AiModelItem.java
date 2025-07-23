package org.cdpg.dx.tgdex.item.model;

import io.vertx.core.json.JsonObject;
import java.time.LocalDateTime;
import java.util.List;

public class AiModelItem implements Item {
  private String name;
  private List<String> type;
  private String label;
  private String shortDescription;
  private String description;
  private List<String> tags;
  private String accessPolicy;
  private String organizationType;
  private String organizationId;
  private String industry;
  private String department;
  private String id;
  private String itemStatus;
  private LocalDateTime itemCreatedAt;
  private String context;

  private String modelType;
  private String fileFormat;
  private String uploadedBy;
  private String license;
  private String fileSize;
  private Boolean dataUploadStatus;
  private String mediaURL;

  // Getters and setters for all fields...

  @Override
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public List<String> getType() {
    return type;
  }

  public void setType(List<String> type) {
    this.type = type;
  }

  @Override
  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  @Override
  public String getShortDescription() {
    return shortDescription;
  }

  public void setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
  }

  @Override
  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  @Override
  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> tags) {
    this.tags = tags;
  }

  @Override
  public String getAccessPolicy() {
    return accessPolicy;
  }

  public void setAccessPolicy(String accessPolicy) {
    this.accessPolicy = accessPolicy;
  }

  @Override
  public String getOrganizationType() {
    return organizationType;
  }

  public void setOrganizationType(String organizationType) {
    this.organizationType = organizationType;
  }

  @Override
  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  @Override
  public String getIndustry() {
    return industry;
  }

  public void setIndustry(String industry) {
    this.industry = industry;
  }

  @Override
  public String getDepartment() {
    return department;
  }

  public void setDepartment(String department) {
    this.department = department;
  }

  @Override
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getItemStatus() {
    return itemStatus;
  }

  public void setItemStatus(String itemStatus) {
    this.itemStatus = itemStatus;
  }

  @Override
  public LocalDateTime getItemCreatedAt() {
    return itemCreatedAt;
  }

  public void setItemCreatedAt(LocalDateTime itemCreatedAt) {
    this.itemCreatedAt = itemCreatedAt;
  }

  @Override
  public String getContext() {
    return context;
  }

  public void setContext(String context) {
    this.context = context;
  }

  public String getModelType() {
    return modelType;
  }

  public void setModelType(String modelType) {
    this.modelType = modelType;
  }

  public String getFileFormat() {
    return fileFormat;
  }

  public void setFileFormat(String fileFormat) {
    this.fileFormat = fileFormat;
  }

  public String getUploadedBy() {
    return uploadedBy;
  }

  public void setUploadedBy(String uploadedBy) {
    this.uploadedBy = uploadedBy;
  }

  public String getLicense() {
    return license;
  }

  public void setLicense(String license) {
    this.license = license;
  }

  public String getFileSize() {
    return fileSize;
  }

  public void setFileSize(String fileSize) {
    this.fileSize = fileSize;
  }

  public Boolean getDataUploadStatus() {
    return dataUploadStatus;
  }

  public void setDataUploadStatus(Boolean dataUploadStatus) {
    this.dataUploadStatus = dataUploadStatus;
  }

  public String getMediaURL() {
    return mediaURL;
  }

  public void setMediaURL(String mediaURL) {
    this.mediaURL = mediaURL;
  }

  @Override
  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("name", name);
    json.put("type", type);
    json.put("label", label);
    json.put("shortDescription", shortDescription);
    json.put("description", description);
    json.put("tags", tags);
    json.put("accessPolicy", accessPolicy);
    json.put("organizationType", organizationType);
    json.put("organizationId", organizationId);
    json.put("industry", industry);
    json.put("department", department);
    json.put("id", id);
    json.put("itemStatus", itemStatus);
    json.put("itemCreatedAt", itemCreatedAt != null ? itemCreatedAt.toString() : null);
    json.put("@context", context);

    json.put("modelType", modelType);
    json.put("fileFormat", fileFormat);
    json.put("uploadedBy", uploadedBy);
    json.put("license", license);
    json.put("fileSize", fileSize);
    json.put("dataUploadStatus", dataUploadStatus);
    json.put("mediaURL", mediaURL);
    return json;
  }

  public static AiModelItem fromJson(JsonObject json) {
    AiModelItem item = new AiModelItem();
    item.setName(json.getString("name"));
    item.setType(json.getJsonArray("type").getList());
    item.setLabel(json.getString("label"));
    item.setShortDescription(json.getString("shortDescription"));
    item.setDescription(json.getString("description"));
    item.setTags(json.getJsonArray("tags").getList());
    item.setAccessPolicy(json.getString("accessPolicy"));
    item.setOrganizationType(json.getString("organizationType"));
    item.setOrganizationId(json.getString("organizationId"));
    item.setIndustry(json.getString("industry"));
    item.setDepartment(json.getString("department"));
    item.setId(json.getString("id"));
    item.setItemStatus(json.getString("itemStatus"));
    item.setItemCreatedAt(json.getString("itemCreatedAt") != null
        ? LocalDateTime.parse(json.getString("itemCreatedAt"))
        : null);
    item.setContext(json.getString("@context"));

    item.setModelType(json.getString("modelType"));
    item.setFileFormat(json.getString("fileFormat"));
    item.setUploadedBy(json.getString("uploadedBy"));
    item.setLicense(json.getString("license"));
    item.setFileSize(json.getString("fileSize"));
    item.setDataUploadStatus(json.getBoolean("dataUploadStatus"));
    item.setMediaURL(json.getString("mediaURL"));

    return item;
  }

}
