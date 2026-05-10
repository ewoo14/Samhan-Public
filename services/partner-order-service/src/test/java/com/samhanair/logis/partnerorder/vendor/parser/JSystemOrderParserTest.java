package com.samhanair.logis.partnerorder.vendor.parser;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * JSystemOrderParser — 정상 / 다중 / 단가 누락 / 빈 입력 / matches 5 case.
 */
class JSystemOrderParserTest {

    private final JSystemOrderParser parser = new JSystemOrderParser();

    @Test
    void parse_normal_table_row() {
        String text = """
                제이시스템 발주서
                거래처코드: P-J001
                HM-7000 헬로멀티 7kW 3 EA 1,500,000
                TOTAL 4,500,000
                """;
        ParsedVendorOrder result = parser.parse(text);
        assertThat(result.vendorName()).isEqualTo("제이시스템");
        assertThat(result.partnerCode()).isEqualTo("P-J001");
        assertThat(result.lines()).hasSize(1);
        ParsedVendorOrder.Line line = result.lines().get(0);
        assertThat(line.modelCode()).isEqualTo("HM-7000");
        assertThat(line.quantity()).isEqualTo(3);
        assertThat(line.unitPrice()).isEqualByComparingTo("1500000");
        assertThat(result.totalAmount()).isEqualByComparingTo("4500000");
    }

    @Test
    void parse_multiple_rows() {
        String text = """
                JSYSTEM Order Sheet
                Partner: P-J002
                HM-5000 헬로멀티 5kW 2 EA 1,000,000
                CM-9000 상업멀티 9kW 1 EA 2,500,000
                TOTAL 4,500,000
                """;
        ParsedVendorOrder result = parser.parse(text);
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).modelCode()).isEqualTo("HM-5000");
        assertThat(result.lines().get(1).modelCode()).isEqualTo("CM-9000");
    }

    @Test
    void parse_missing_quantity_skipped() {
        String text = """
                제이시스템 발주서
                HM-5000 헬로멀티 5kW abc EA 1,000,000
                HM-7000 헬로멀티 7kW 2 EA 1,500,000
                """;
        ParsedVendorOrder result = parser.parse(text);
        // 첫 라인은 qty 가 abc 이므로 매칭 실패. 두 번째 라인만 인식.
        assertThat(result.lines()).hasSize(1);
        assertThat(result.lines().get(0).modelCode()).isEqualTo("HM-7000");
    }

    @Test
    void parse_empty_text() {
        assertThat(parser.parse("").lines()).isEmpty();
        assertThat(parser.parse(null).lines()).isEmpty();
    }

    @Test
    void matches_keyword_detection() {
        assertThat(parser.matches("제이시스템 발주서")).isTrue();
        assertThat(parser.matches("JSYSTEM order")).isTrue();
        assertThat(parser.matches("J-SYSTEM purchase")).isTrue();
        assertThat(parser.matches("J SYSTEM")).isTrue();
        assertThat(parser.matches("에어디자이너")).isFalse();
        assertThat(parser.matches(null)).isFalse();
    }
}
