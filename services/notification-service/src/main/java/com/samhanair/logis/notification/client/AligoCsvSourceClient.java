package com.samhanair.logis.notification.client;

import java.util.List;

/**
 * Phase 10 PR-F1 BE-1 — partner-service 의 알리고 CSV export endpoint 를 호출하여
 * {@link AligoAddressBookClient.AligoContact} 리스트로 변환하는 client (계약 정의).
 *
 * <p><b>설계 — 단일 진실의 원천 (SSoT) 보존.</b> partner-service Part A 가 발행한 CSV
 * (그룹명/이름/휴대폰/비고) 를 본 client 가 그대로 fetch + parse 하여 contact 리스트로 변환한다.
 * 별도의 partner list endpoint 를 호출하지 않음으로써 정규화 / 필터 / 그룹 매핑 로직의 중복
 * 구현을 회피 (CSV 가 사실상의 schema).
 *
 * <h2>구현체</h2>
 * <ul>
 *   <li>{@link RestClientAligoCsvSourceClient} — production 용 RestClient 기반 호출 (X-Internal-Token 인증)</li>
 *   <li>test profile — {@code @MockBean AligoCsvSourceClient} 격리
 *       (memory feedback_it_mockbean_external_clients)</li>
 * </ul>
 */
public interface AligoCsvSourceClient {

    /**
     * partner-service {@code GET /admin/partners/export/aligo-csv} 호출 + CSV parsing 후 contact 리스트 반환.
     *
     * <p>호출 측 (sync service) 이 chunk 50 분할 + 알리고 API 호출 책임. 본 client 자체는 fetch +
     * parse 만 수행 — 외부 호출 실패 시 빈 리스트 반환 (fail-soft).
     *
     * @return 정규화된 contact 리스트 (UTF-8 BOM 제거 후 row 파싱)
     */
    List<AligoAddressBookClient.AligoContact> fetchContacts();
}
