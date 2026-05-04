package com.samhanair.logis.auth.service.dto;

/** Echo of the persisted account after registration (no secrets). */
public record RegisterResponse(String userId, String loginId, String role) {
}
