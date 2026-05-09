package com.samhanair.logis.arologis.dto;

import java.util.List;

/**
 * 미배차 출고전표 리스트 응답 — Phase 10 PR-E1 BE-A3 (legacy GAS 7번 이식).
 *
 * <p>출고전표 중 dispatch 미할당 (slip_no 가 어떤 VehicleStop 의 카톡 슬립번호와도 매칭 안 됨)
 * 슬립 목록.
 *
 * <p>UUID 비공개 가드 — slipId 응답에 미포함.
 *
 * @param date 조회 기준 일자 (요청 파라미터 echo)
 * @param totalOutbound 기간 OUTBOUND 슬립 총 건수
 * @param unassignedCount 미배차 슬립 건수 (entries 길이와 동일)
 * @param entries 미배차 슬립 entry 리스트
 */
public record UnassignedSlipResponse(
        String date,
        int totalOutbound,
        int unassignedCount,
        List<Entry> entries
) {

    /**
     * 미배차 entry — 슬립 1건 = entry 1건.
     *
     * @param slipNo 전표번호 (사용자 노출 식별자, 필수)
     * @param partnerCode 거래처 코드 (사용자 노출 식별자)
     * @param partnerName 거래처 상호 (사용자 노출)
     * @param address 거래처 주소 (사용자 노출 — admin 화면 보조 정보)
     */
    public record Entry(
            String slipNo,
            String partnerCode,
            String partnerName,
            String address
    ) {}
}
