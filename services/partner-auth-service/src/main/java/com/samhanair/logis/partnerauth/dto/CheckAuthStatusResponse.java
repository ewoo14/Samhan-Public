package com.samhanair.logis.partnerauth.dto;

import com.samhanair.logis.partnerauth.domain.PartnerStatus;

/**
 * GET /api/v1/auth/partner-status 응답.
 *
 * <p>{@code status} 가 8 enum 중 하나 (NOT_FOUND_SYSTEM/NOT_FOUND_AUTH/PENDING/
 * LOCKED/LONG_UNUSED/ACCESS_DENIED/PW_EXPIRED/NEED_PW_SET/NEED_PW_INPUT) —
 * UUID 비공개 (bizNo 만 응답).
 */
public record CheckAuthStatusResponse(
        String bizNo,
        PartnerStatus status,
        String partnerName,  // M3 RPC 결과 (null 이면 NOT_FOUND_SYSTEM)
        String message
) {}
