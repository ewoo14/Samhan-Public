package com.samhanair.logis.slip.web.dto;

import com.samhanair.logis.slip.domain.SlipStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * PR-E1 BE-A6 응답 — 전표 정리 리스트 (legacy GAS 13번 "전표정리리스트").
 *
 * <p>기간 내 활성 슬립 전체 + 정합성 검증 flag. legacy GAS 는 이카운트 엑셀 업로드 후
 * 누락/중복/금액0 등을 표시했으나, 본 endpoint 는 자체 자동 조회 기반이므로 다음 4 flag 만 의미가 있다:
 *
 * <ul>
 *   <li>{@link #partnerCodeMissing} — partner_code NULL (V15 신규 컬럼 미채움)</li>
 *   <li>{@link #amountZero} — 라인 합계 금액 = 0 (사실상 무료 슬립, 검증 필요)</li>
 *   <li>{@link #linesMissing} — 라인 0건 (DRAFT 단계 외에는 비정상)</li>
 *   <li>{@link #regionMissing} — classified_region_group NULL (다음날자 이미지 그룹핑 누락)</li>
 * </ul>
 *
 * <p>응답 구조:
 * <ul>
 *   <li>{@code totalSlips} = 기간 내 전체 활성 슬립 수</li>
 *   <li>{@code byStatus} = status 별 카운트 그룹핑</li>
 *   <li>{@code byPartner} = partner_code 별 카운트 그룹핑 (partner_code NULL 는 "(미매핑)" key)</li>
 *   <li>{@code entries} = 슬립별 정합성 flag (size 큰 경우 page 분할은 후속 슬라이스)</li>
 * </ul>
 */
public record SlipCleanupResponse(
        LocalDate from,
        LocalDate to,
        int totalSlips,
        List<StatusCount> byStatus,
        List<PartnerCount> byPartner,
        List<CleanupEntry> entries) {

    /** status 별 카운트 — FE 의 status filter dropdown 백킹. */
    public record StatusCount(SlipStatus status, int count) {
    }

    /** partner_code 별 카운트 — FE 의 거래처 filter / 정리 요약 표. */
    public record PartnerCount(String partnerCode, String partnerName, int count) {
    }

    /**
     * 슬립 1건의 정리 entry — 4 flag 포함. ID 는 UUID 노출 가드 — 사용자 화면에서 path variable 로
     * 사용 가능 (admin 화면 한정), 일반 표시는 slipNo 우선.
     */
    public record CleanupEntry(
            UUID id,
            String slipNo,
            LocalDate slipDate,
            SlipStatus status,
            String partnerCode,
            String partnerName,
            String classifiedRegionGroup,
            int lineCount,
            java.math.BigDecimal totalAmount,
            boolean partnerCodeMissing,
            boolean amountZero,
            boolean linesMissing,
            boolean regionMissing) {
    }
}
