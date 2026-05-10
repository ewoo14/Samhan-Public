package com.samhanair.logis.partnerorder.vendor.parser;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * VendorParserRegistry — name 매칭 / autoDetect / 미등록 vendor 케이스.
 */
class VendorParserRegistryTest {

    private final VendorParserRegistry registry = new VendorParserRegistry(
            List.of(new AirDesignerOrderParser(), new JSystemOrderParser()));

    @Test
    void resolveByName_returns_matching_parser() {
        assertThat(registry.resolveByName("에어디자이너"))
                .get().isInstanceOf(AirDesignerOrderParser.class);
        assertThat(registry.resolveByName("제이시스템"))
                .get().isInstanceOf(JSystemOrderParser.class);
        assertThat(registry.resolveByName("미등록")).isEmpty();
        assertThat(registry.resolveByName(null)).isEmpty();
        assertThat(registry.resolveByName("")).isEmpty();
    }

    @Test
    void autoDetect_uses_keyword_heuristic() {
        assertThat(registry.autoDetect("에어디자이너 발주서"))
                .get().isInstanceOf(AirDesignerOrderParser.class);
        assertThat(registry.autoDetect("JSYSTEM Order"))
                .get().isInstanceOf(JSystemOrderParser.class);
        assertThat(registry.autoDetect("아무 텍스트")).isEmpty();
        assertThat(registry.autoDetect(null)).isEmpty();
    }

    @Test
    void registeredVendors_lists_all_names() {
        assertThat(registry.registeredVendors())
                .containsExactly("에어디자이너", "제이시스템");
    }
}
