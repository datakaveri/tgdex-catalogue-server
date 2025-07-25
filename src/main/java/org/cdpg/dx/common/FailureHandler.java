package org.cdpg.dx.common;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.validation.BodyProcessorException;
import io.vertx.ext.web.validation.ParameterProcessorException;
import io.vertx.ext.web.validation.RequestPredicateException;
import io.vertx.json.schema.ValidationException;
import org.apache.http.HttpStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.common.response.DxErrorResponse;
import org.cdpg.dx.common.util.ExceptionHttpStatusMapper;
import org.cdpg.dx.common.util.ThrowableUtils;

import static org.cdpg.dx.util.Constants.*;

public class FailureHandler implements Handler<RoutingContext> {

    private static final Logger LOGGER = LogManager.getLogger(FailureHandler.class);

    public void handle(RoutingContext context) {
        LOGGER.trace("FailureHandler.handle() started");
        Throwable failure = context.failure();
        if (failure == null) {
            failure = new RuntimeException("Unknown server error");
        }
        LOGGER.debug("Failure Instance: {}", failure.getClass());
        if (failure instanceof ValidationException || failure instanceof BodyProcessorException
                || failure instanceof RequestPredicateException || failure instanceof ParameterProcessorException) {
            context.response().putHeader(CONTENT_TYPE, APPLICATION_JSON)
                    .putHeader(HEADER_ALLOW_ORIGIN, "*")
                    .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                    .putHeader("Access-Control-Allow-Headers", "Authorization, Content-Type")
                    .setStatusCode(HttpStatus.SC_BAD_REQUEST)
                    .end(new DxErrorResponse(ResponseUrn.BAD_REQUEST_URN.getUrn(), HttpStatusCode.BAD_REQUEST.getDescription(), failure.getMessage()).toJson().encode());
            return;
        }

        HttpStatusCode statusCode = ExceptionHttpStatusMapper.map(failure);

        // Log complete error with stack trace for diagnostics
        LOGGER.error("Unhandled error: {}, {}", failure.getMessage(), failure);

        // Avoid leaking internal exception messages
        String safeDetail = ThrowableUtils.isSafeToExpose(failure) ? failure.getMessage() : "An unexpected error occurred";

        DxErrorResponse errorResponse = new DxErrorResponse(statusCode.getUrn(), statusCode.getDescription(), safeDetail);

        if (!context.response().ended()) {
            int status = statusCode.getValue();
            if (status < 400 || status > 599) {
                status = 500;
            }

            context.response().putHeader("Content-Type", "application/json").setStatusCode(status).end(errorResponse.toJson().encode());
        }
    }

}
