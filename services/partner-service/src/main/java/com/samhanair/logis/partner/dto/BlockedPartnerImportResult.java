package com.samhanair.logis.partner.dto;

import java.util.List;

/**
 * Phase 10 PR-D Part B — CSV import 결과.
 *
 * <p>총 row 수 / 신규 등록 / 이미 차단 (skip) / reject (lookup miss 또는 파싱 실패) 4 카테고리.
 *
 * @param totalRows CSV 헤더 제외 데이터 row 수
 * @param imported 신규 BLOCK 등록 (partnerCode 매칭 성공 + 미차단)
 * @param alreadyBlocked 이미 차단된 partnerCode (skip — idempotent)
 * @param rejected reject 항목 (lookup miss 또는 파싱 실패)
 */
public record BlockedPartnerImportResult(
        int totalRows,
        int imported,
        int alreadyBlocked,
        List<RejectedRow> rejected
) {

    /**
     * Reject 항목 — row 번호 + 입력 사업자명 + reason.
     *
     * @param rowNumber CSV row 번호 (헤더 제외, 1-based)
     * @param inputBusinessName CSV 의 "이카운트 사업자명" 컬럼 값
     * @param reason reject 사유 (LOOKUP_MISS / LOOKUP_AMBIGUOUS / PARSE_ERROR / DUPLICATE)
     */
    public record RejectedRow(
            int rowNumber,
            String inputBusinessName,
            String reason
    ) {
    }
}
