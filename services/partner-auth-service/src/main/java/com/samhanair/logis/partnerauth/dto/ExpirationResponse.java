package com.samhanair.logis.partnerauth.dto;

import java.time.LocalDateTime;

/**
 * GET /api/v1/auth/partner-expiration 응답.
 *
 * <p>30일 슬라이딩 만료 일시 (lastLoginAt 또는 passwordChangedAt 기준 +30일).
 * {@code expiredAlready} = true 면 LONG_UNUSED 단계로 전환 가능.
 */
public record ExpirationResponse(
        String bizNo,
        LocalDateTime expiresAt,
        boolean expiredAlready,
        long remainingDays
) {}
