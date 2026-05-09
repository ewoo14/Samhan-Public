package com.samhanair.logis.notification.client;

import java.util.Optional;

/**
 * partner-service 의 거래처 lookup client (PR-D Part 2-3 의존성).
 *
 * <p>본 PR-D Part 2-3 (CHAT 단톡방 매핑 import) 가 동시 진행 중인 BE-E 의 신규 endpoint
 * {@code GET /api/v1/partners/by-name?name=<사업자명>} 및 기존
 * {@code GET /api/v1/partners/{partnerCode}} 에 의존한다. BE-E 가 endpoint 를 발행하기 전까지는
 * 본 interface 는 implementation 없이 정의만 존재 — IT 에서 {@code @MockBean} 으로 격리하여 본 PR 단독
 * 빌드 / 테스트가 가능하다 (memory feedback_it_mockbean_external_clients).
 *
 * <p>BE-E 의 endpoint 가 머지된 시점에 RestClient/WebClient 기반 구현체를 본 패키지에 추가하여
 * Spring 이 자동 주입 — 본 interface 는 변경 없음 (계약 안정성 보장).
 *
 * <p>Lookup 정책 (TM PR-D Part 3 정정 — partner_code source-of-truth):
 * <ul>
 *   <li><strong>partner_code 우선</strong> — CSV 에 거래처코드 컬럼이 있으면 {@link
 *       #verifyPartnerCode(String)} 로 존재 검증 후 즉시 사용 (사업자명 lookup 회피).</li>
 *   <li>partner_code 미공급 시 {@link #findPartnerCodeByName(String)} fallback (정확 일치 우선,
 *       미발견 시 partner-service 측 LIKE 1건 허용 정책).</li>
 *   <li>not found 시 {@link Optional#empty()} 반환 — 호출자(import service) 가 reject 누적 처리.</li>
 *   <li>사업자명 끝의 "-담당자명" 등 변형은 BE-E 측에서 정규화. 본 client 는 raw 문자열을 그대로 전달.</li>
 * </ul>
 */
public interface PartnerLookupClient {

    /**
     * 사업자명으로 partner_code 조회 — 거래처코드 미공급 시 fallback 경로.
     *
     * @param businessName 거래처 사업자명 (Notion CSV 의 "이카운트 사업자명" 원문)
     * @return 매칭 partner_code (예: "P-2026-0001") 또는 미매칭 시 {@link Optional#empty()}
     */
    Optional<String> findPartnerCodeByName(String businessName);

    /**
     * partner_code 직접 검증 — CSV 에 거래처코드 컬럼이 있을 때 우선 사용.
     *
     * <p>partner-service 측에서 활성 partner 가 존재하면 그대로 입력 코드를 반환,
     * 미존재 시 {@link Optional#empty()} 반환. 본 메서드는 사업자명 lookup 보다 우선되며,
     * 모호한 LIKE 매칭이 없으므로 매핑 정확도가 높다.
     *
     * @param partnerCode 거래처코드 (예: "P-2026-0001")
     * @return 활성 partner 존재 시 동일 코드, 미존재 시 {@link Optional#empty()}
     */
    Optional<String> verifyPartnerCode(String partnerCode);
}
