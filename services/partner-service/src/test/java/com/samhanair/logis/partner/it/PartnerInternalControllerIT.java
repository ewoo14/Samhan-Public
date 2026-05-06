package com.samhanair.logis.partner.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.partner.PartnerServiceApplication;
import com.samhanair.logis.partner.domain.Partner;
import com.samhanair.logis.partner.domain.PartnerStatus;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

/**
 * Internal endpoint 인증 / lookup 시나리오.
 *
 * <p>커버:
 * <ol>
 *   <li>X-Internal-Token 누락 → 401</li>
 *   <li>X-Internal-Token 불일치 → 401</li>
 *   <li>X-Internal-Token 일치 + 존재하는 partnerCode → 200, partnerId / 마스터 / 신용 정보</li>
 *   <li>X-Internal-Token 일치 + 미존재 partnerCode → 404</li>
 * </ol>
 *
 * <p>외부 client 의존성 없음 (self-contained service) — {@code @MockBean} 격리 불요.
 * (memory feedback_it_mockbean_external_clients 가드 = 외부 client 가 있을 때만 적용)
 */
@SpringBootTest(classes = PartnerServiceApplication.class)
@AutoConfigureMockMvc
class PartnerInternalControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @BeforeEach
    void seedFixturePartner() {
        partnerRepository.deleteAll();
        Partner p = Partner.register("P-2026-0001", "111-22-33333", "(주)테스트거래처",
                "서울 강남구 테스트로 1", "02-1234-5678", new BigDecimal("5000000"));
        partnerRepository.save(p);
    }

    @Test
    void lookup_without_internal_token_returns_401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/partners/P-2026-0001"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void lookup_with_invalid_internal_token_returns_401() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/partners/P-2026-0001")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test
    void lookup_with_valid_token_returns_partner_master_with_uuid() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/partners/P-2026-0001")
                        .header("X-Internal-Token", "test-internal-token")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(true))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.partnerCode").value("P-2026-0001"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.name").value("(주)테스트거래처"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.partnerId").exists())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.creditLimit").value(5000000))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status")
                        .value(PartnerStatus.ACTIVE.name()));

        // 부수 효과 검증 — 본 lookup 은 read-only, balance / status 변경 X
        Partner reloaded = partnerRepository.findByPartnerCode("P-2026-0001").orElseThrow();
        assertThat(reloaded.getOutstandingBalance()).isEqualByComparingTo("0");
    }

    @Test
    void lookup_with_valid_token_but_missing_code_returns_404() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/internal/partners/P-NOT-EXIST")
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("$.success").value(false))
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("NOT_FOUND"));
    }
}
