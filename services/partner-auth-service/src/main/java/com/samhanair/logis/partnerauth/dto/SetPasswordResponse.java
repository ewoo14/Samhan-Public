package com.samhanair.logis.partnerauth.dto;

/** PATCH /api/v1/auth/partner-password 응답. result = OK / USED_PW. */
public record SetPasswordResponse(String result, String message) {}
