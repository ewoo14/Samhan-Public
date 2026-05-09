package com.samhanair.logis.notification.dto;

import java.util.List;

/**
 * CSV import 종합 결과.
 *
 * @param inserted 신규 적재된 매핑 수 (활성 중복 없는 정상 row)
 * @param updated 기존 매핑 발견 → snapshot 사업자명 갱신만 수행한 row 수
 * @param rejected 매칭 실패 / 검증 실패 row 의 reject 보고서 (row 번호 + 사유 누적)
 */
public record ChatRoomImportResult(
        int inserted,
        int updated,
        List<RejectedRow> rejected
) {

    /**
     * 단일 reject row 보고.
     *
     * @param rowNumber CSV 1-base 행 번호 (header 제외, 첫 데이터 행 = 1)
     * @param businessName 입력된 사업자명 (감사용)
     * @param chatRoomName 입력된 단톡방 이름 (감사용)
     * @param reason reject 사유 한국어 메시지
     */
    public record RejectedRow(
            int rowNumber,
            String businessName,
            String chatRoomName,
            String reason
    ) {
    }
}
