package com.samhanair.logis.notification.client;

/**
 * partner-service 의 BLOCKED 거래처 lookup client (PR-D Part B 의존성, PR-E1 BE-4 사용).
 *
 * <p>배차안내 SMS batch 발송 (PR-E1 BE-4) 의 가드:
 * <pre>
 *   slip 의 partner_code → isBlocked(partnerCode) 가 true 면 발송 제외 (preview 에서는 blocked=true 표시).
 * </pre>
 *
 * <p>partner-service 측 신규 endpoint {@code GET /internal/blocked-partners/{partnerCode}} 에
 * 의존한다 — 미구현 시 본 interface 는 {@code @MockBean} 으로 격리하여 본 PR 단독 빌드 / 테스트가
 * 가능하다 (memory feedback_it_mockbean_external_clients).
 *
 * <p>네트워크 실패 / 5xx 시 fail-soft = false 반환 권장 (운영자가 가드 의도하지 않은 누락을 방지하기
 * 위해 보수적 default 는 차단으로 두는 안도 가능 — 결정은 구현체 수준).
 */
public interface BlockedPartnerLookupClient {

    /**
     * 차단 여부 조회.
     *
     * @param partnerCode 거래처코드 (예: "P-2026-0001")
     * @return 활성 BLOCKED row 가 존재하면 true, 그렇지 않으면 false
     */
    boolean isBlocked(String partnerCode);
}
