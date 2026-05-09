package com.samhanair.logis.partner.dto;

import com.samhanair.logis.partner.domain.BlockedPartner;
import java.time.LocalDateTime;

/**
 * Phase 10 PR-D Part B — BLOCK 발송금지 응답 DTO.
 *
 * <p>UUID 비공개 가드 (memory feedback_uuid_no_user_visibility) — 응답은 partnerCode +
 * snapshot 상호 + 차단 사유 + 차단 시점 + source 만 노출. 본 row 의 UUID 식별자 (id) 는
 * admin 화면에서도 사용하지 않음 — 차단 해제 endpoint 는 별도로 path variable 로 id 사용
 * (admin 운영 측이 row 단위 조작 필요).
 *
 * @param id 본 BLOCK row UUID (admin 차단 해제 path variable 용 — 사용자 화면 노출 X)
 * @param partnerCode 차단 대상 partnerCode (사용자 노출 식별자)
 * @param businessNameSnapshot 차단 시점 거래처 상호 snapshot
 * @param blockReason 차단 사유 (nullable)
 * @param blockedAt 차단 시점
 * @param source NOTION_IMPORT / MANUAL / LEGACY_GAS
 */
public record BlockedPartnerResponse(
        java.util.UUID id,
        String partnerCode,
        String businessNameSnapshot,
        String blockReason,
        LocalDateTime blockedAt,
        String source
) {

    public static BlockedPartnerResponse from(BlockedPartner b) {
        return new BlockedPartnerResponse(
                b.getId(),
                b.getPartnerCode(),
                b.getPartnerBusinessNameSnapshot(),
                b.getBlockReason(),
                b.getBlockedAt(),
                b.getSource());
    }
}
