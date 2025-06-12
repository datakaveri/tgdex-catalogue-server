package org.cdpg.dx.aaa.models;

import org.cdpg.dx.common.models.DxRole;

import java.util.UUID;

/**
 * Immutable UserInfo model.
 */
public record UserInfo(UUID userId, boolean isDelegate, DxRole role, String audience) {
}
