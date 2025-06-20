package org.cdpg.dx.common.response;


import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.common.HttpStatusCode;
import org.cdpg.dx.common.response.DxResponse;
import org.cdpg.dx.common.util.PaginationInfo;

public class ResponseBuilder {

    public static <T> DxResponse<T> success(String detail, T results, PaginationInfo pageInfo) {
        HttpStatusCode code = HttpStatusCode.SUCCESS;
        return new DxResponse<>(code.getUrn(), code.getDescription(), detail, results, pageInfo);
    }

    public static <T> DxResponse<T> success(String detail, T results) {
        HttpStatusCode code = HttpStatusCode.SUCCESS;
        return new DxResponse<>(code.getUrn(), code.getDescription(), detail, results, null);
    }

    public static DxResponse<Void> success(String detail) {
        return success(detail, null);
    }
    public static <T> void send(
            RoutingContext ctx, HttpStatusCode status, String detail, T results,
            PaginationInfo pageInfo, Integer totalHits) {

        if (status == HttpStatusCode.NO_CONTENT) {
            ctx.response().setStatusCode(status.getValue()).end();
            return;
        }

        DxResponse<T> response = (totalHits == null)
                ? new DxResponse<>(status.getUrn(), status.getDescription(), detail, results, pageInfo)
                : new DxResponse<>(status.getUrn(), status.getDescription(), detail, results, pageInfo, totalHits);
        ctx.response()
                .setStatusCode(status.getValue())
                .putHeader("Content-Type", "application/json")
                .putHeader("Access-Control-Allow-Origin", "*")
                .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
                .putHeader("Access-Control-Allow-Methods", "GET, POST,PUT, DELETE, OPTIONS")
                .end(JsonObject.mapFrom(response).encode());
    }

        public static void sendSuccess(RoutingContext ctx, String detail) {
        send(ctx, HttpStatusCode.SUCCESS, detail, null, null,null);
    }

    public static <R> void sendSuccess(RoutingContext ctx, R results) {
        send(ctx, HttpStatusCode.SUCCESS, null, results, null,null);
    }

    public static <T> void sendSuccess(RoutingContext ctx, T results, PaginationInfo pageInfo) {
        send(ctx, HttpStatusCode.SUCCESS, null, results, pageInfo,null);
    }
    public static <T> void sendSuccess(RoutingContext ctx, T results, PaginationInfo pageInfo,int totalHits) {
        send(ctx, HttpStatusCode.SUCCESS, null, results, pageInfo,totalHits);
    }

    public static <T> void sendCreated(RoutingContext ctx, String detail, T results) {
        send(ctx, HttpStatusCode.CREATED, detail, results, null,null);
    }

    public static void sendCreated(RoutingContext ctx, String detail) {
        send(ctx, HttpStatusCode.CREATED, detail, null, null,null);
    }

    public static void sendNoContent(RoutingContext ctx) {
        send(ctx, HttpStatusCode.NO_CONTENT, null, null, null,null);
    }

    public static void sendProcessing(RoutingContext ctx, String detail) {
        send(ctx, HttpStatusCode.PROCESSING, detail, null, null,null);
    }
}

