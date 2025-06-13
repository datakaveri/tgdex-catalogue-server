package org.cdpg.dx.util;

import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

public class CheckIfTokenPresent implements Handler<RoutingContext> {

    private static final Logger LOGGER = LogManager.getLogger(CheckIfTokenPresent.class);

    @Override
    public void handle(RoutingContext ctx) {
        String token = BearerTokenExtractor.extract(ctx);
        boolean isTokenNotPresent = token != null && !token.isBlank();

        // only checking if token is present or not
        // if present we will already have the User from the KeycloakJwtAuthHandler

        if (isTokenNotPresent) {
            LOGGER.warn("Missing or invalid Authorization header");
            ctx.fail(new DxUnauthorizedException("Missing Bearer token"));
            return;
        }
        else ctx.next();
    }
}