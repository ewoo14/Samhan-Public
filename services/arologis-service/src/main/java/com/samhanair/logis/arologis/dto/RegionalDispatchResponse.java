package com.samhanair.logis.arologis.dto;

import java.util.List;
import java.util.Map;

/**
 * 지방 가배차 분류 응답 — Phase 10 PR-E1 BE-A4 (legacy GAS 15번 이식).
 *
 * <p>출고전표 → 거래처 주소의 광역 prefix (서울/부산/대구/...) 추출 → 시도별 그룹핑.
 *
 * <p>BE-A2 ({@link PreClassifyResponse}) 와의 차이:
 * <ul>
 *   <li>BE-A2 — REGION 마스터 (region_dispatch_classifications 테이블, sort_order/keywords) 기반</li>
 *   <li>BE-A4 (본 응답) — 코드 내부 광역 prefix 상수 17 개 (시도 단위) 기반.
 *       legacy GAS 15번 호환 — 지방 광역시도 분류만 단순 매칭.</li>
 * </ul>
 *
 * <p>UUID 비공개 가드 — slipId 응답에 미포함.
 *
 * @param date 조회 기준 일자 (요청 파라미터 echo)
 * @param sidoGroups 시도명 → 슬립 entry 리스트. 사용자 화면에서 시도별 column 노출.
 * @param unmatched 광역 prefix 매칭 실패 슬립 entry (예: "Tokyo" 같은 외국 주소 / NULL)
 */
public record RegionalDispatchResponse(
        String date,
        Map<String, List<Entry>> sidoGroups,
        List<Entry> unmatched
) {

    /**
     * 지방 가배차 entry — 슬립 1건 = entry 1건.
     *
     * @param slipNo 전표번호 (사용자 노출 식별자, 필수)
     * @param partnerCode 거래처 코드 (사용자 노출 식별자)
     * @param partnerName 거래처 상호 (사용자 노출)
     * @param address 거래처 주소 (사용자 노출 — 광역 prefix 매칭 source)
     * @param sido 매칭된 시도명 (예: "서울" / "부산"). 미매칭 시 null.
     */
    public record Entry(
            String slipNo,
            String partnerCode,
            String partnerName,
            String address,
            String sido
    ) {}
}
