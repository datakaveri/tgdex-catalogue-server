package org.cdpg.dx.common.response;


import com.fasterxml.jackson.annotation.JsonInclude;
import org.cdpg.dx.common.util.PaginationInfo;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DxResponse<T> {
    private String type; // e.g. "urn:dx:acl:success"
    private String title; // e.g. "Success" or "Bad Request"
    private String detail; // Optional detailed message
    private Integer totalHits;
    private T results;
    private PaginationInfo paginationInfo; // Optional payload

    public DxResponse() {}

    public DxResponse(
            String type, String title, String detail, T results, PaginationInfo paginationInfo) {
        this.type = type;
        this.title = title;
        this.detail = detail;
        this.results = results;
        this.paginationInfo = paginationInfo;
    }

    public DxResponse(
            String type, String title, String detail, T results, PaginationInfo paginationInfo,Integer totalHits) {
        this.type = type;
        this.title = title;
        this.totalHits=totalHits;
        this.detail = detail;
        this.results = results;
        this.paginationInfo = paginationInfo;
    }

    // Getters and setters
    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public T getResults() {
        return results;
    }

    public void setResults(T results) {
        this.results = results;
    }

    public Integer getTotalHits() {
        return totalHits;
    }

    public void setTotalHits(Integer totalHits) {
        this.totalHits = totalHits;
    }
    public PaginationInfo getPaginationInfo() {
        return paginationInfo;
    }

    public void setPaginationInfo(PaginationInfo paginationInfo) {
        this.paginationInfo = paginationInfo;
    }
}
