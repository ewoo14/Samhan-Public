package com.samhanair.logis.slip.price.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** PartnerProductPriceMemory — 거래처+품목 최근 VAT 포함 입력단가 기억 도메인 테스트. */
class PartnerProductPriceMemoryTest {

    @Test
    void create_storesVatInclusiveInputPriceForRoundTrip() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        PartnerProductPriceMemory memory = PartnerProductPriceMemory.create(
                partnerId, productId, new BigDecimal("123456.78"), "LINE_SAVE",
                LocalDateTime.of(2026, 7, 15, 10, 0));

        assertThat(memory.getPartnerId()).isEqualTo(partnerId);
        assertThat(memory.getProductId()).isEqualTo(productId);
        assertThat(memory.getUnitPrice()).isEqualByComparingTo("123456.78");
        assertThat(memory.getSource()).isEqualTo("LINE_SAVE");
        assertThat(memory.getRememberedAt()).isEqualTo(LocalDateTime.of(2026, 7, 15, 10, 0));
    }
}
