package com.samhanair.logis.partnerorder.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PartnerOrderPrintServiceTest {

    @Test
    void categoryLabel_singleSets는_싱글중대형으로_표시한다() {
        PartnerOrderPrintService service = new PartnerOrderPrintService(null, null);

        String label = ReflectionTestUtils.invokeMethod(service, "categoryLabel", "singleSets");

        assertThat(label).isEqualTo("싱글중대형");
    }
}
