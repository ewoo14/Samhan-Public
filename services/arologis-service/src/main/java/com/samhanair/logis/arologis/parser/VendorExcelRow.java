package com.samhanair.logis.arologis.parser;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 운송사 엑셀 1행 — Phase 10 PR-F1 BE-2.
 *
 * <p>legacy GAS 11번 ("운송사-실배차내역 비교") 의 vendor 별 row 평탄화. vendor 별 양식은 다르지만
 * (CJ대한통운 / 롯데 / 한진 등), 본 DTO 는 공통 4 필드로 정규화한 후 매칭 단계로 넘긴다:
 *
 * <ul>
 *   <li>{@code vendorName} — 사용자 가시 vendor 식별자 (multipart 업로드 시 파일명 또는 매처 결과)</li>
 *   <li>{@code slipNo} — 운송장 번호 또는 슬립 번호 (매칭 키 1)</li>
 *   <li>{@code dispatchDate} — 접수/발송/출고 일자 (매칭 키 2)</li>
 *   <li>{@code expectedTime} — 운송사 기록 접수/발송 시각 (옵션)</li>
 *   <li>{@code partnerName} — 운송사가 기록한 업체명 (옵션, mismatch 진단용)</li>
 * </ul>
 *
 * @param vendorName   vendor 식별자 (예: "CJ대한통운")
 * @param slipNo       슬립/운송장 번호 (매칭 키)
 * @param dispatchDate 접수/발송/출고 일자 (매칭 키)
 * @param expectedTime 접수/발송/출고 시각 (옵션)
 * @param partnerName  운송사 기록 업체명 (옵션)
 */
public record VendorExcelRow(
        String vendorName,
        String slipNo,
        LocalDate dispatchDate,
        LocalTime expectedTime,
        String partnerName) {
}
