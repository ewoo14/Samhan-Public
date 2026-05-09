package com.samhanair.logis.arologis.parser;

import com.samhanair.logis.arologis.domain.DispatchType;
import com.samhanair.logis.arologis.domain.VehicleTonnage;
import java.time.LocalDate;
import java.util.List;

/**
 * 카톡 메시지 파싱 결과 — Phase 10 W10-1.
 *
 * <p>저장 전 미리보기 응답 형식. 실 저장은 {@code POST /admin/arologis/dispatches} 별도 endpoint
 * (수동 보정 후 저장).
 *
 * @param dispatchDate 도착 일자 (헤더 "8일착")
 * @param dispatchType 배차 유형
 * @param vehicles 차량 목록 (사용자 제공 13 차량 = 13 element)
 * @param totalLines 전체 라인 수 (정확도 계산용)
 * @param parsedLines 파싱 성공한 라인 수
 */
public record ParsedDispatch(
        LocalDate dispatchDate,
        DispatchType dispatchType,
        List<ParsedVehicle> vehicles,
        int totalLines,
        int parsedLines
) {

    /** 파싱 정확도 (= parsedLines / totalLines, 0.0 ~ 1.0). */
    public double accuracy() {
        if (totalLines <= 0) {
            return 0.0;
        }
        return (double) parsedLines / (double) totalLines;
    }

    /**
     * 차량 1대 파싱 결과.
     *
     * @param sequence 카톡 그룹 번호 (1, 2, 3, ...)
     * @param tonnage 톤수 (그룹 끝 라인 "1톤" 추출)
     * @param label 헤더 옆 텍스트 (예: "상일+초월")
     * @param stops 정차 목록
     */
    public record ParsedVehicle(
            int sequence,
            VehicleTonnage tonnage,
            String label,
            List<ParsedStop> stops
    ) {}

    /**
     * 정차 1건 파싱 결과.
     *
     * @param sequence 차량 내 정차 순서
     * @param rawText 카톡 원본 라인
     * @param parsedAddress 파싱된 주소 (옵션)
     * @param parsedPartnerName 사업자명 (옵션)
     * @param parsedPartnerCode 전표번호 (옵션)
     * @param notes 특이사항 (옵션)
     * @param unparsed 미해석 라인 여부 ("상일상차" 등 group label)
     * @param regionGroup 가배차 지역 분류 그룹명 (PR-D 2-1 — RegionClassifier 매칭, 미매칭 시 null)
     */
    public record ParsedStop(
            int sequence,
            String rawText,
            String parsedAddress,
            String parsedPartnerName,
            Long parsedPartnerCode,
            String notes,
            boolean unparsed,
            String regionGroup
    ) {

        /** 7-인자 호환 생성자 — RegionClassifier 미주입 환경 (단위 테스트) regionGroup=null. */
        public ParsedStop(int sequence, String rawText, String parsedAddress,
                          String parsedPartnerName, Long parsedPartnerCode,
                          String notes, boolean unparsed) {
            this(sequence, rawText, parsedAddress, parsedPartnerName, parsedPartnerCode,
                    notes, unparsed, null);
        }
    }
}
