package com.samhanair.logis.arologis.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 운송사 실배차 비교 mismatch 1행 — Phase 10 PR-F1 BE-2.
 *
 * <p>legacy GAS 11번 ("운송사-실배차내역 비교") 의 left-join 분류 결과 단일 행. 매칭 키 = (날짜 +
 * 슬립번호). status 는 3 분류:
 *
 * <ul>
 *   <li>{@code TRUE} — 양쪽 모두 존재 (정상). 본 DTO 는 mismatch 응답 source 라 TRUE 행은
 *       포함하지 않음 (matchedCount 로 카운트만).</li>
 *   <li>{@code FALSE_LEFT} — 우리 dispatch 만 존재, 운송사 엑셀 누락 (운송사가 미접수)</li>
 *   <li>{@code FALSE_RIGHT} — 운송사 엑셀만 존재, 우리 dispatch 누락 (자체 등록 누락)</li>
 * </ul>
 *
 * <p>UUID 비공개 가드 — 모든 식별자는 비즈니스 식별자 (slipNo / dispatchDate / vendorName /
 * partnerName) 만 노출. 내부 dispatchId / vehicleId / stopId 는 응답에서 제거.
 *
 * @param status        분류 (FALSE_LEFT / FALSE_RIGHT, TRUE 행은 응답 미포함)
 * @param slipNo        슬립/운송장 번호 (매칭 키 1)
 * @param dispatchDate  배차/접수 일자 (매칭 키 2)
 * @param vendorName    운송사 식별자 (FALSE_RIGHT 시 = 엑셀 vendor, FALSE_LEFT 시 = null)
 * @param expectedTime  운송사 기록 접수/발송 시각 (FALSE_RIGHT 시만)
 * @param actualTime    우리 dispatch 의 실제 도착 시각 (FALSE_LEFT 시만, ARRIVED/DELIVERED 단계에서 채움)
 * @param partnerName   업체명 (진단용, 양쪽 중 알 수 있는 값)
 * @param reason        한국어 사유 ("운송사 엑셀 누락" / "자체 dispatch 누락")
 */
public record MismatchedRow(
        Status status,
        String slipNo,
        LocalDate dispatchDate,
        String vendorName,
        LocalTime expectedTime,
        LocalTime actualTime,
        String partnerName,
        String reason) {

    /** mismatch 분류. TRUE 는 응답 미포함이지만 enum 정의로 명시. */
    public enum Status {
        /** 양쪽 모두 존재 (정상) — 응답 포함되지 않음, matchedCount 로만 카운트. */
        TRUE,
        /** 우리 dispatch 만 존재, 운송사 엑셀 누락. */
        FALSE_LEFT,
        /** 운송사 엑셀만 존재, 우리 dispatch 누락. */
        FALSE_RIGHT
    }
}
