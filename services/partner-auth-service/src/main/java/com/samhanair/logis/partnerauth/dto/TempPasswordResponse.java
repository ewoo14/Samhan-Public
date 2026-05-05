package com.samhanair.logis.partnerauth.dto;

/** POST /api/v1/auth/partner-temp-password 응답 — 202 Accepted (sms-service 큐잉). */
public record TempPasswordResponse(String message, String maskedMobileNo) {}
