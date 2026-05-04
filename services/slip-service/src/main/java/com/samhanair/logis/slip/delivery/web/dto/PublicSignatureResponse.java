package com.samhanair.logis.slip.delivery.web.dto;

import java.time.LocalDateTime;

/**
 * 공개 모바일 서명 등록 응답 — Slice C (signature-slice-C Plan §2 + design mobile-spec.md §2.1).
 *
 * <p>UUID 비공개 가드 (memory {@code feedback_uuid_no_user_visibility.md}): slip.id 미노출.
 * 클라이언트는 응답 후 {@code shareToken} 으로 인수자 view ({@code /share/{shareToken}}) 이동.
 *
 * @param signedAt 서명 등록 시각 (서버 timestamp)
 * @param shareToken 인수자 share 토큰 (base64url 64자)
 * @param shareTokenExpiresAt share 토큰 만료 시각 ({@code signedAt + 30일})
 * @param signatureHash 서버 재계산 확정 SHA-256 hex 64자 (client hash 와 동일 — 미스매치 시 400 으로 본 응답 미발생)
 */
public record PublicSignatureResponse(
        LocalDateTime signedAt,
        String shareToken,
        LocalDateTime shareTokenExpiresAt,
        String signatureHash) {
}
