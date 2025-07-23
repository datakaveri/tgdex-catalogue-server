package org.cdpg.dx.tgdex.search.util;

import static org.cdpg.dx.tgdex.util.Constants.RESULTS;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

public class ResponseModel {
  JsonObject response;
  private List<JsonObject> elasticsearchResponses;
  private int totalHits;
  private PaginationInfo paginationInfo;

  public ResponseModel(List<ElasticsearchResponse> elasticsearchResponses, int size, int page) {
    this.response = new JsonObject();
    this.response.put(RESULTS, elasticsearchResponses);
    setTotalHits(ElasticsearchResponse.getTotalHits());
    this.elasticsearchResponses =
        getJsonObjectList(Objects.requireNonNullElse(elasticsearchResponses, List.of()));
    setPaginationInfo(page, size);
    setResponseJson();
  }

  public ResponseModel(List<ElasticsearchResponse> elasticsearchResponses) {
    this.elasticsearchResponses =
        getJsonObjectList(Objects.requireNonNullElse(elasticsearchResponses, List.of()));
    this.response = new JsonObject();
    this.response.put(RESULTS, setAggregationsList());
  }

  public ResponseModel (ElasticsearchResponse elasticsearchResponse){
    this.response= new JsonObject();
    this.response.put(RESULTS, elasticsearchResponse.getSource());
    setTotalHits(ElasticsearchResponse.getTotalHits());

  }
  private JsonArray setAggregationsList() {
    JsonArray results = new JsonArray();
    // Fetch all aggregations from the response JSON
    JsonObject aggregations = ElasticsearchResponse.getAggregations();
    results.add(aggregations);
    return results;
  }

  public List<JsonObject> getElasticsearchResponses() {
    return elasticsearchResponses;
  }

  private void setPaginationInfo(int page, int size) {
    int totalPages = (int) Math.ceil((double) this.totalHits / size);
    boolean hasNext = page < totalPages;
    boolean hasPrevious = page > 1;
    this.paginationInfo =
        new PaginationInfo(page, size, this.totalHits, totalPages, hasNext, hasPrevious);
  }

  private List<JsonObject> getJsonObjectList(List<ElasticsearchResponse> elasticsearchResponses) {
    List<JsonObject> jsonObjectList = new ArrayList<>();
    for (ElasticsearchResponse elasticsearchResponse : elasticsearchResponses) {
      jsonObjectList.add(elasticsearchResponse.getSource());
    }
    return jsonObjectList;
  }

  public JsonObject getResponse() {
    return response;
  }

  public void setResponseJson() {
    response = new JsonObject().put(RESULTS, elasticsearchResponses);
  }

  public int getTotalHits() {
    return totalHits;
  }

  public void setTotalHits(int totalHits) {
    this.totalHits = totalHits;
  }

  @Override
  public String toString() {
    return "ResponseModel{" +
        "elasticsearchResponses=" + elasticsearchResponses +
        '}';
  }

  public PaginationInfo getPaginationInfo() {
    return paginationInfo;
  }
}
