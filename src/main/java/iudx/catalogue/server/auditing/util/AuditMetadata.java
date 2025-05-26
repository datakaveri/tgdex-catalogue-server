package iudx.catalogue.server.auditing.util;

public class AuditMetadata {
  public final String itemId;
  public final String apiEndpoint;
  public final String httpMethod;
  public final String itemType;
  public final String itemName;

  public AuditMetadata(String itemId, String apiEndpoint, String httpMethod, String itemType,
                       String itemName) {
    this.itemId = itemId;
    this.apiEndpoint = apiEndpoint;
    this.httpMethod = httpMethod;
    this.itemType = itemType;
    this.itemName = itemName;
  }
}

