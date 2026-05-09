package com.samhanair.logis.dcconfig.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * DC 거래처 할인 정보 CSV import 결과.
 *
 * <p>PR-D Part 2-2 — Notion 에서 다운받은 거래처 DC 정보 CSV 의 일괄 import 결과 응답.
 *
 * @param inserted 신규 생성된 dc_configs row 수
 * @param updated  기존 row 갱신 수 (partner_code 매칭 → upsert)
 * @param skipped  CSV 에 등장했으나 변동 없이 통과한 row 수 (예: 빈 라인)
 * @param rejected partner_code 부재 등으로 거부된 row 의 상세 보고
 */
@Schema(description = "DC 거래처 할인 정보 CSV import 결과")
public record DcConfigImportResult(
        @Schema(description = "신규 생성 row 수", example = "120") int inserted,
        @Schema(description = "갱신 row 수", example = "85") int updated,
        @Schema(description = "변동 없이 skip 된 row 수", example = "0") int skipped,
        @Schema(description = "거부된 row 보고") List<RejectedRow> rejected
) {

    /**
     * 거부된 row 상세.
     *
     * @param rowNumber     CSV row 번호 (1-base, header 제외)
     * @param partnerCode   CSV `거래처코드` 값 (없으면 null/empty)
     * @param businessName  CSV `업체명` 값
     * @param reason        거부 사유 (한국어)
     */
    @Schema(description = "거부된 row 상세")
    public record RejectedRow(
            @Schema(description = "CSV row 번호 (1-base)", example = "37") int rowNumber,
            @Schema(description = "거래처코드", example = "6260403108") String partnerCode,
            @Schema(description = "업체명", example = "(주)예전") String businessName,
            @Schema(description = "거부 사유", example = "거래처코드 미존재") String reason
    ) {}
}
