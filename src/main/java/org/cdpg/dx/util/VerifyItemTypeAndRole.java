package org.cdpg.dx.util;

import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.RoutingContext;
import org.cdpg.dx.auth.authorization.model.DxRole;
import org.cdpg.dx.common.exception.DxForbiddenException;
import org.cdpg.dx.common.exception.DxUnauthorizedException;

import java.util.*;

import static org.cdpg.dx.util.ItemType.AI_MODEL;
import static org.cdpg.dx.util.ItemType.APPS;

public class VerifyItemTypeAndRole implements Handler<RoutingContext> {
    HashMap<DxRole, List<ItemType>> roleItemTypeMap = new HashMap<>();

    @Override
    public void handle(RoutingContext routingContext) {
        User user = routingContext.user();
        if (user == null) {
            routingContext.fail(new DxUnauthorizedException("No User Found")); // 401
            return;
        }

        JsonObject principal = user.principal();
        JsonObject realmAccess = principal.getJsonObject("realm_access");
        if (realmAccess == null || !realmAccess.containsKey("roles")) {
            routingContext.fail(new DxForbiddenException("User don't have any assigned")); // 403
            return;
        }
        createMap();
        String itemType = routingContext.body().asJsonObject().getJsonArray("type").getString(0);
        JsonArray userRoles = realmAccess.getJsonArray("roles");
        ItemType requestedType = ItemType.valueOf(itemType);

        boolean allowed = userRoles.stream()
                .map(Object::toString)
                .map(String::toLowerCase)
                .map(roleName -> {
                    return Arrays.stream(DxRole.values())
                            .filter(r -> r.getRole().equalsIgnoreCase(roleName))
                            .findFirst()
                            .orElse(null);
                })
                .filter(Objects::nonNull)        // drop any unknown roles
                .anyMatch(dxRole -> {
                    List<ItemType> allowedTypes = roleItemTypeMap.get(dxRole);
                    return allowedTypes != null && allowedTypes.contains(requestedType);
                });

        if (allowed) {
            routingContext.next();
        } else {
            routingContext.fail(new DxForbiddenException(
                    "User does not have sufficient role for item type: " + requestedType));
        }
    }

    private void createMap(){
        List<ItemType> providerItemTypes = new ArrayList<>();
        List<ItemType> cosAdminItemTypes = new ArrayList<>();
        providerItemTypes.add(AI_MODEL);
        providerItemTypes.add(APPS);
        this.roleItemTypeMap.put(DxRole.PROVIDER, providerItemTypes);
        this.roleItemTypeMap.put(DxRole.COS_ADMIN, cosAdminItemTypes);}
}
