package com.samhanair.logis.partnerorder.vendor.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * AirDesignerOrderParser — 정상 / 다중 / 단가 누락 / 빈 입력 / partnerCode 부재 5 case.
 */
class AirDesignerOrderParserTest {

    private final AirDesignerOrderParser parser = new AirDesignerOrderParser();

    @Test
    void parse_normal_single_line() {
        String text = """
                에어디자이너 발주서
                거래처: P-A001
                1. 헬로멀티 5kW [HM-5000] 2개 1,000,000원
                합계: 2,000,000원
                """;
        ParsedVendorOrder result = parser.parse(text);
        assertThat(result.vendorName()).isEqualTo("에어디자이너");
        assertThat(result.partnerCode()).isEqualTo("P-A001");
        assertThat(result.lines()).hasSize(1);
        ParsedVendorOrder.Line line = result.lines().get(0);
        assertThat(line.modelCode()).isEqualTo("HM-5000");
        assertThat(line.quantity()).isEqualTo(2);
        assertThat(line.unitPrice()).isEqualByComparingTo("1000000");
        assertThat(result.totalAmount()).isEqualByComparingTo("2000000");
    }

    @Test
    void parse_multiple_lines() {
        String text = """
                에어디자이너 발주서
                거래처: P-A002
                1. 헬로멀티 5kW [HM-5000] 2개 1,000,000원
                2. 헬로멀티 7kW [HM-7000] 1개 1,500,000원
                3. 상업멀티 [CM-9000] 3개 2,500,000원
                합계: 11,000,000원
                """;
        ParsedVendorOrder result = parser.parse(text);
        assertThat(result.lines()).hasSize(3);
        assertThat(result.lines().get(2).modelCode()).isEqualTo("CM-9000");
        assertThat(result.lines().get(2).quantity()).isEqualTo(3);
    }

    @Test
    void parse_missing_price_line_skipped() {
        // 단가 자리에 "단가확인요" 텍스트가 들어가면 정규식 미매칭 → 스킵
        String text = """
                에어디자이너 발주서
                1. 헬로멀티 [HM-5000] 2개 단가확인요
                2. 헬로멀티 [HM-7000] 1개 1,500,000원
                """;
        ParsedVendorOrder result = parser.parse(text);
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).modelCode()).isEqualTo("HM-7000");
    }

    @Test
    void parse_empty_text_returns_empty_lines() {
        ParsedVendorOrder result = parser.parse("");
        assertThat(result.lines()).isEmpty();
        assertThat(result.totalAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(parser.parse(null).lines()).isEmpty();
    }

    @Test
    void parse_no_partner_code_returns_null_partner() {
        String text = """
                에어디자이너 발주서
                1. 헬로멀티 [HM-5000] 1개 500,000원
                """;
        ParsedVendorOrder result = parser.parse(text);
        assertThat(result.partnerCode()).isNull();
        assertThat(result.lines()).hasSize(1);
    }

    @Test
    void matches_keyword_detection() {
        assertThat(parser.matches("에어디자이너 발주서")).isTrue();
        assertThat(parser.matches("AIR DESIGNER ORDER")).isTrue();
        assertThat(parser.matches("Air Designer 견적")).isTrue();
        assertThat(parser.matches("제이시스템 발주서")).isFalse();
        assertThat(parser.matches(null)).isFalse();
        assertThat(parser.matches("")).isFalse();
    }
}
