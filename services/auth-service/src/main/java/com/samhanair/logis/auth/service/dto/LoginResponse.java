package com.samhanair.logis.auth.service.dto;

/** JWT issued on successful login plus minimal profile context for the SPA. */
public record LoginResponse(String token, String userId, String role, String displayName) {
}
