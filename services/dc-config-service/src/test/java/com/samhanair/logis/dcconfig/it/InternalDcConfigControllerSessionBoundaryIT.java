package com.samhanair.logis.dcconfig.it;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.dcconfig.DcConfigServiceApplication;
import com.samhanair.logis.dcconfig.domain.DcConfig;
import com.samhanair.logis.dcconfig.domain.DcConfigSource;
import com.samhanair.logis.dcconfig.domain.Partner;
import com.samhanair.logis.dcconfig.domain.PartnerGroup;
import com.samhanair.logis.dcconfig.domain.UnitRoundMode;
import com.samhanair.logis.dcconfig.repository.DcConfigRepository;
import com.samhanair.logis.dcconfig.repository.PartnerRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 단건 internal endpoint 의 웹 계층 세션 경계를 재현한다.
 *
 * <p>의도적으로 {@code @Transactional} 을 붙이지 않는다. 테스트 트랜잭션이 열려 있으면
 * controller 반환 뒤 DTO 변환 시점의 LazyInitializationException 을 가릴 수 있다.
 */
@SpringBootTest(classes = DcConfigServiceApplication.class)
@AutoConfigureMockMvc
class InternalDcConfigControllerSessionBoundaryIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private DcConfigRepository dcConfigRepository;

    @Test
    void internalDcConfig_singleEndpoint_returnsPartnerCodeOutsideTestTransaction() throws Exception {
        String partnerCode = "P-LAZY-" + UUID.randomUUID().toString().substring(0, 8);
        Partner partner = partnerRepository.saveAndFlush(Partner.create(
                partnerCode,
                "1234567890",
                "LazyInit 회귀 거래처",
                "부산광역시 해운대구",
                "051-111-2222",
                "김담당",
                PartnerGroup.WHOLESALE,
                new BigDecimal("50000000"),
                null));

        DcConfig config = DcConfig.create(partner, DcConfigSource.LEGACY_CSV);
        config.changeRates(new BigDecimal("0.0500"), new BigDecimal("0.0800"));
        config.changeShowIHose(false);
        config.changeOptionAmounts(
                new BigDecimal("10000"), new BigDecimal("20000"), null, null, null, null);
        config.changeRounding(1000, UnitRoundMode.FLOOR);
        dcConfigRepository.saveAndFlush(config);

        mockMvc.perform(get("/internal/partner-dc-configs/" + partnerCode)
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.partnerCode").value(partnerCode))
                .andExpect(jsonPath("$.data.homeDiscountRate").value(0.05))
                .andExpect(jsonPath("$.data.discount360Amount").value(10000))
                .andExpect(jsonPath("$.data.unitRoundMode").value("FLOOR"));
    }
}
