package com.samhanair.logis.auth.web.dto;

/** Response body for {@code GET /auth/me} — derived from gateway-set headers + DB. */
public record MeResponse(String userId, String loginId, String role, String displayName) {
}
