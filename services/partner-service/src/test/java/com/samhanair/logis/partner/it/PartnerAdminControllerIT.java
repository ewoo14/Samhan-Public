package com.samhanair.logis.partner.it;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.samhanair.logis.partner.PartnerServiceApplication;
import com.samhanair.logis.partner.dto.PartnerAdminRequest;
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
 * Admin CRUD endpoint 권한 / 흐름 시나리오.
 *
 * <p>커버:
 * <ol>
 *   <li>인증 미적재 → 403 (Spring Security 기본 — protected endpoint)</li>
 *   <li>X-User-Role = SALES (관리자 아님) → 403 FORBIDDEN</li>
 *   <li>X-User-Role = MANAGER → 200, 신규 거래처 등록 OK</li>
 *   <li>중복 partnerCode → 409 CONFLICT</li>
 *   <li>X-User-Role = MASTER + DELETE → 200 (soft-delete)</li>
 * </ol>
 */
@SpringBootTest(classes = PartnerServiceApplication.class)
@AutoConfigureMockMvc
class PartnerAdminControllerIT extends AbstractPostgresIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanup() {
        partnerRepository.deleteAll();
    }

    @Test
    void create_without_authentication_returns_403() throws Exception {
        // Spring Security 기본 — 인증 미적재 + protected endpoint 시 AccessDeniedException → 403
        PartnerAdminRequest req = sampleRequest("P-2026-0010", "999-88-77777");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/partners")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void create_with_sales_role_returns_403() throws Exception {
        PartnerAdminRequest req = sampleRequest("P-2026-0011", "999-88-77778");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/partners")
                        .header("X-User-Id", "user-sales")
                        .header("X-User-Role", "SALES")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isForbidden());
    }

    @Test
    void create_with_manager_role_returns_200_and_persists_active_partner() throws Exception {
        PartnerAdminRequest req = sampleRequest("P-2026-0012", "999-88-77779");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/partners")
                        .header("X-User-Id", "user-manager")
                        .header("X-User-Role", "MANAGER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.partnerCode").value("P-2026-0012"))
                .andExpect(MockMvcResultMatchers.jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void create_duplicate_partner_code_returns_409() throws Exception {
        PartnerAdminRequest first = sampleRequest("P-2026-0013", "999-88-77780");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/partners")
                        .header("X-User-Id", "user-master")
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        PartnerAdminRequest dup = new PartnerAdminRequest("P-2026-0013", "999-88-99999",
                "(주)다른상호", null, null, BigDecimal.ZERO);
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/partners")
                        .header("X-User-Id", "user-master")
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dup)))
                .andExpect(MockMvcResultMatchers.status().isConflict())
                .andExpect(MockMvcResultMatchers.jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void delete_with_master_role_returns_200_and_soft_deletes() throws Exception {
        PartnerAdminRequest req = sampleRequest("P-2026-0014", "999-88-77781");
        mockMvc.perform(MockMvcRequestBuilders.post("/admin/partners")
                        .header("X-User-Id", "user-master")
                        .header("X-User-Role", "MASTER")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(MockMvcResultMatchers.status().isOk());

        mockMvc.perform(MockMvcRequestBuilders.delete("/admin/partners/P-2026-0014")
                        .header("X-User-Id", "user-master")
                        .header("X-User-Role", "MASTER"))
                .andExpect(MockMvcResultMatchers.status().isOk());

        // soft-delete 후 SQLRestriction 으로 미조회 → 후속 GET 은 404
        mockMvc.perform(MockMvcRequestBuilders.get("/admin/partners/P-2026-0014")
                        .header("X-User-Id", "user-master")
                        .header("X-User-Role", "MASTER"))
                .andExpect(MockMvcResultMatchers.status().isNotFound());
    }

    private PartnerAdminRequest sampleRequest(String partnerCode, String bizNo) {
        return new PartnerAdminRequest(
                partnerCode,
                bizNo,
                "(주)샘플",
                "서울 종로구 종로 1",
                "02-9999-0000",
                new BigDecimal("3000000"));
    }
}
