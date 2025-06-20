package org.cdpg.dx.tgdex.search.util;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.cdpg.dx.common.util.PaginationInfo;
import org.cdpg.dx.database.elastic.model.ElasticsearchResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ResponseModel {
    JsonObject response;

    public List<JsonObject> getElasticsearchResponses() {
        return elasticsearchResponses;
    }

    private List<JsonObject> elasticsearchResponses;
    private int totalHits;
    private PaginationInfo paginationInfo;

    public ResponseModel(List<ElasticsearchResponse> elasticsearchResponses, int size,int page) {
        this.response=new JsonObject();
        this.response.put("results",elasticsearchResponses);
        this.elasticsearchResponses = getJsonObjectList(Objects.requireNonNullElse(elasticsearchResponses, List.of()));
        setPaginationInfo(page,size);
        setResponseJson();
    }

    public ResponseModel(List<ElasticsearchResponse> elasticsearchResponses) {
        this.response=new JsonObject();
        this.response.put("results",elasticsearchResponses);
        this.elasticsearchResponses = getJsonObjectList(Objects.requireNonNullElse(elasticsearchResponses, List.of()));
    }

    private void setPaginationInfo(int page, int size) {
        int totalPages = (int) Math.ceil((double) this.totalHits / size);
        boolean hasNext = page < totalPages;
        boolean hasPrevious = page > 1;
        this.paginationInfo = new PaginationInfo(page, size, this.totalHits, totalPages, hasNext, hasPrevious);
    }

    private List<JsonObject> getJsonObjectList(List<ElasticsearchResponse> elasticsearchResponses) {
        List<JsonObject> jsonObjectList = new ArrayList<>();
        for (ElasticsearchResponse elasticsearchResponse : elasticsearchResponses) {
            jsonObjectList.add(elasticsearchResponse.getSource());
        }
        setTotalHits(elasticsearchResponses.size());
        return jsonObjectList;
    }

    public JsonObject getResponse() {
        return response;
    }

    public void setResponseJson(){
        response = new JsonObject().put("results",elasticsearchResponses);
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
