package com.samhanair.logis.partnerauth.it;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.partnerauth.client.DcConfigClient;
import com.samhanair.logis.partnerauth.client.SmsClient;
import com.samhanair.logis.partnerauth.domain.PartnerAuth;
import com.samhanair.logis.partnerauth.domain.PartnerStatus;
import com.samhanair.logis.partnerauth.repository.PartnerAuthRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

/**
 * Partner Auth Service — 7 endpoint × happy/edge IT.
 *
 * <p>외부 client (DcConfigClient + SmsClient) 는 {@code @MockBean} 으로 격리
 * (memory feedback_it_mockbean_external_clients.md 의무 — Eureka 비활성으로
 * 인한 500 회피).
 */
@SpringBootTest
@DirtiesContext
@WithMockUser(username = "test-user")
@Transactional
class PartnerAuthControllerIT extends AbstractPostgresIT {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PartnerAuthRepository authRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private DcConfigClient dcConfigClient;

    @MockBean
    private SmsClient smsClient;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        // lenient default: M3 미응답 — IT 별 override 가능
        lenient().when(dcConfigClient.findByBizNo(anyString())).thenReturn(Optional.empty());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 1) GET /api/v1/auth/partner-status
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void GET_partner_status_NOT_FOUND_SYSTEM() throws Exception {
        mvc.perform(get("/api/v1/auth/partner-status?bizNo=9999999990"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NOT_FOUND_SYSTEM"));
    }

    @Test
    void GET_partner_status_PENDING() throws Exception {
        authRepository.save(PartnerAuth.register("1234567891", "P-IT-001", "test memo"));
        mvc.perform(get("/api/v1/auth/partner-status?bizNo=1234567891"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 2) POST /api/v1/auth/partner-register
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void POST_partner_register_201_PENDING() throws Exception {
        mvc.perform(post("/api/v1/auth/partner-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"1234567892","partnerCode":"P-IT-002","memo":"new"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.bizNo").value("1234567892"));
    }

    @Test
    void POST_partner_register_409_중복() throws Exception {
        authRepository.save(PartnerAuth.register("1234567893", "P-IT-003", null));
        mvc.perform(post("/api/v1/auth/partner-register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"1234567893","partnerCode":"P-IT-003","memo":null}
                                """))
                .andExpect(status().isConflict());
    }

    // ─────────────────────────────────────────────────────────────────────
    // 3) PATCH /api/v1/auth/partner-password
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void PATCH_partner_password_OK() throws Exception {
        PartnerAuth pa = PartnerAuth.seedFromLegacy(
                "1234567894", "P-IT-004", passwordEncoder.encode("oldPw!1A"), PartnerStatus.NEED_PW_INPUT);
        authRepository.save(pa);

        mvc.perform(patch("/api/v1/auth/partner-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"1234567894","newPassword":"newPw!1AB","currentPassword":"oldPw!1A"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.result").value("OK"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 4) POST /api/v1/auth/partner-login
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void POST_partner_login_OK_및_token_발급() throws Exception {
        PartnerAuth pa = PartnerAuth.seedFromLegacy(
                "1234567895", "P-IT-005", passwordEncoder.encode("rightPw!1A"), PartnerStatus.NEED_PW_INPUT);
        authRepository.save(pa);

        mvc.perform(post("/api/v1/auth/partner-login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"1234567895","password":"rightPw!1A","mobile":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OK"))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void POST_partner_login_3회_fail_시_LOCKED() throws Exception {
        PartnerAuth pa = PartnerAuth.seedFromLegacy(
                "1234567896", "P-IT-006", passwordEncoder.encode("rightPw!1A"), PartnerStatus.NEED_PW_INPUT);
        authRepository.save(pa);

        String wrongJson = """
                {"bizNo":"1234567896","password":"wrongPw!9X","mobile":false}
                """;
        mvc.perform(post("/api/v1/auth/partner-login").contentType(MediaType.APPLICATION_JSON).content(wrongJson))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/partner-login").contentType(MediaType.APPLICATION_JSON).content(wrongJson))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/partner-login").contentType(MediaType.APPLICATION_JSON).content(wrongJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOCKED"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 5) POST /api/v1/auth/partner-temp-password
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void POST_partner_temp_password_202_및_SMS_큐잉() throws Exception {
        PartnerAuth pa = PartnerAuth.seedFromLegacy(
                "1234567897", "P-IT-007", passwordEncoder.encode("oldPw!1A"), PartnerStatus.NEED_PW_INPUT);
        authRepository.save(pa);

        mvc.perform(post("/api/v1/auth/partner-temp-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"1234567897","mobileNo":"01012345678"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.maskedMobileNo").value("010****5678"));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 6) GET /api/v1/auth/partner-expiration
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void GET_partner_expiration_30일_슬라이딩() throws Exception {
        PartnerAuth pa = PartnerAuth.seedFromLegacy(
                "1234567898", "P-IT-008", passwordEncoder.encode("oldPw!1A"), PartnerStatus.NEED_PW_INPUT);
        authRepository.save(pa);

        mvc.perform(get("/api/v1/auth/partner-expiration?bizNo=1234567898"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bizNo").value("1234567898"))
                .andExpect(jsonPath("$.data.expiredAlready").value(false));
    }

    // ─────────────────────────────────────────────────────────────────────
    // 7) PATCH /api/v1/auth/partner-tutorial
    // ─────────────────────────────────────────────────────────────────────
    @Test
    void PATCH_partner_tutorial_PC_완료() throws Exception {
        PartnerAuth pa = PartnerAuth.seedFromLegacy(
                "1234567899", "P-IT-009", passwordEncoder.encode("oldPw!1A"), PartnerStatus.NEED_PW_INPUT);
        authRepository.save(pa);

        mvc.perform(patch("/api/v1/auth/partner-tutorial")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bizNo":"1234567899","platform":"PC","done":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tutorialPcDone").value(true))
                .andExpect(jsonPath("$.data.tutorialMobileDone").value(false));
    }
}
