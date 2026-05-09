package com.samhanair.logis.inventory.service;

import java.time.LocalDate;

/**
 * DPS 입고 엑셀의 한 row — {@link DpsExcelParser} 가 생성.
 *
 * <p>legacy GAS 1번/16번 의 DPS 엑셀 컬럼 5종 (품번, 입고일자, 수량, 거래처코드, 거래처명) 만
 * 추출. 그 외 metadata (품목명/규격/단위) 는 매칭에 사용하지 않으므로 무시.
 *
 * <p>UUID 비공개 — DPS 엑셀에는 애초에 UUID 가 없다. productCode / partnerCode 가 매칭 키.
 *
 * @param productCode  품번 (필수)
 * @param inboundDate  입고 일자 (nullable — 일부 엑셀이 빈 값 허용)
 * @param quantity     입고 수량
 * @param partnerCode  거래처 코드 (nullable)
 * @param partnerName  거래처명 (nullable)
 */
public record DpsExcelRow(
        String productCode,
        LocalDate inboundDate,
        int quantity,
        String partnerCode,
        String partnerName) {
}
