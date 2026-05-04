package com.samhanair.logis.slip.delivery.web.dto;

import java.time.LocalDateTime;

/**
 * 공개 모바일 배송기사 서명 등록 응답 — Slice C2.
 */
public record PublicDriverSignatureResponse(
        LocalDateTime driverSignedAt,
        String driverSignatureHash) {
}
