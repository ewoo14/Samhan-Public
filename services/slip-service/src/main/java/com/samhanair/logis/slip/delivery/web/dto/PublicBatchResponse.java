package com.samhanair.logis.slip.delivery.web.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 공개 모바일 endpoint 의 배치 응답 — Plan §4.2.
 * No-auth (토큰만 검증) 진입 후 read-only.
 *
 * <p>UUID 비공개 가드 (memory {@code feedback_uuid_no_user_visibility.md}):
 * slip.id 의 UUID 노출 금지. 슬립 식별은 {@code slipNo} 만.
 * 본 응답에서 batch.id UUID 도 미노출 (모바일 사용자 직접 조회 경로 없음).
 *
 * @param driverName 기사명
 * @param batchDate 배송일
 * @param slips 배치 내 슬립 요약 목록 (slipNo, partnerName, lineCount 등 — UUID 없음)
 */
public record PublicBatchResponse(
        String driverName,
        LocalDate batchDate,
        List<PublicSlipSummary> slips) {

    /**
     * 모바일 슬립 요약 — UUID 없음, slipNo + 거래처명 + 라인 건수만.
     *
     * @param slipNo 전표번호 (yyyy/MM/dd-NNN)
     * @param partnerName 거래처명 snapshot
     * @param lineCount 라인 건수
     * @param status 상태 (기사 화면 progress 표시용)
     */
    public record PublicSlipSummary(
            String slipNo,
            String partnerName,
            int lineCount,
            String status) {
    }
}
