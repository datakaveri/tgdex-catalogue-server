package org.cdpg.dx.auth.authentication.handler;


import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.jwt.JWTAuth;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.AuthenticationHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.cdpg.dx.auth.authentication.util.BearerTokenExtractor;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

public class KeycloakJwtAuthHandler implements AuthenticationHandler {
    private static final Logger LOGGER = LogManager.getLogger(KeycloakJwtAuthHandler.class);
    private final JWTAuth jwtAuth;
    private final boolean isTokenRequired;
    public KeycloakJwtAuthHandler(JWTAuth jwtAuth,boolean isTokenRequired) {
        this.jwtAuth = jwtAuth;
        this.isTokenRequired=isTokenRequired;
    }

    @Override
    public void handle(RoutingContext ctx) {

        LOGGER.info("Inside token handler");
        String token = BearerTokenExtractor.extract(ctx);
        boolean isTokenPresent = token != null && !token.isBlank();
        if ( !isTokenPresent && !isTokenRequired) {
            LOGGER.warn("Token not present and not required.");
            ctx.next();
            return;
        }

        else if (!isTokenPresent) {
            LOGGER.warn("Missing or invalid Authorization header");
            ctx.fail(new DxUnauthorizedException("Missing Bearer token"));
            return;
        }


        jwtAuth.authenticate(new JsonObject().put("token", token))
                .onSuccess(user -> {
                    LOGGER.info("PRESENT success");
                    ctx.setUser(user);
                    ctx.next();
                })
                .onFailure(err -> {
                    LOGGER.warn("Authentication failed: {}", err.getMessage());
                    ctx.fail(new DxUnauthorizedException("Unauthorized"));
                });
    }
}

