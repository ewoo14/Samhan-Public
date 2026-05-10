package com.samhanair.logis.arologis.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 운송사 실배차 비교 endpoint 응답 — Phase 10 PR-F1 BE-2.
 *
 * <p>POST {@code /admin/arologis/dispatch/reconcile} 응답 본문. legacy GAS 11번
 * ("운송사-실배차내역 비교") 의 비교 결과 통합 응답.
 *
 * <p>UUID 비공개 가드 — 응답 어디에도 dispatchId / vehicleId / stopId 노출 없음.
 *
 * @param from           조회 시작일 (자체 dispatch 자동 조회 기간)
 * @param to             조회 종료일
 * @param vendorCount    업로드된 운송사 엑셀 파일 수 (parse 성공 + 헤더 인식 성공한 vendor 만 카운트)
 * @param dispatchCount  자체 자동 조회된 dispatch 라인 수 (vehicle_stops 평탄화)
 * @param vendorRowCount 운송사 엑셀 라인 합계 (전 vendor 총합)
 * @param matchedCount   양쪽 매칭 성공 행수 (Status.TRUE)
 * @param mismatchedRows FALSE_LEFT + FALSE_RIGHT 행 목록
 */
public record DispatchReconcileResponse(
        LocalDate from,
        LocalDate to,
        int vendorCount,
        int dispatchCount,
        int vendorRowCount,
        int matchedCount,
        List<MismatchedRow> mismatchedRows) {
}
