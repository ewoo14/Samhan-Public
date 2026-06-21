package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.samhanair.logis.auth.AuthServiceApplication;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 결재라인 내부 인가 endpoint X-Internal-Token 계약 IT. */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "eureka.client.enabled=false",
        "eureka.client.register-with-eureka=false",
        "eureka.client.fetch-registry=false",
        "app.security.jwt.secret=test-secret-key-32-chars-min-aaaaaa",
        "app.security.internal.token=test-internal-token"
})
class ApprovalLineAuthorizeControllerIT extends AbstractPostgresIT {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * cross-service 계약 가드 — 결재자(USER) 1건 seed 후 그 userId 로 allowed=true 와 ApiResponse envelope
     * 중첩($.data.configured/allowed)을 실 컨트롤러 응답으로 단언한다. slip 측 client 가 root.get("data") 로
     * 파싱하므로, 컨트롤러가 envelope 을 깨거나 키를 바꾸면 이 IT 가 CI 에서 차단한다(restclient false-green 방지).
     */
    @Test
    @DisplayName("authorize — USER 결재자 seed 시 allowed=true + envelope($.data.*) 계약")
    void authorize_withSeededUserApprover_returnsAllowedTrue_andEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID roleId = jdbcTemplate.queryForObject("""
                SELECT id FROM approval_line_config
                 WHERE document_type='SLIP_OUTBOUND' AND action_key='OUTBOUND_DISPATCH' AND is_deleted=false
                """, UUID.class);
        jdbcTemplate.update("""
                INSERT INTO approval_line_approver
                    (id, config_role_id, approver_type, approver_ref_id, created_at, created_by, is_deleted)
                VALUES (?, ?, 'USER', ?, now(), 'it-seed', false)
                """, UUID.randomUUID(), roleId, userId);
        try {
            mockMvc.perform(post("/auth/internal/approval-line/authorize")
                            .header(INTERNAL_TOKEN_HEADER, "test-internal-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"documentType":"SLIP_OUTBOUND","actionKey":"OUTBOUND_DISPATCH","userId":"%s"}
                                    """.formatted(userId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.configured").value(true))
                    .andExpect(jsonPath("$.data.allowed").value(true));
        } finally {
            jdbcTemplate.update(
                    "DELETE FROM approval_line_approver WHERE config_role_id = ? AND approver_ref_id = ?",
                    roleId, userId);
        }
    }

    @Test
    @DisplayName("POST /auth/internal/approval-line/authorize — X-Internal-Token 일치 시 200")
    void authorize_withInternalToken_returns200() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/internal/approval-line/authorize")
                        .header(INTERNAL_TOKEN_HEADER, "test-internal-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"SLIP_OUTBOUND","actionKey":"OUTBOUND_DISPATCH","userId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString())
                .contains("\"configured\"")
                .contains("\"allowed\"");
    }

    @Test
    @DisplayName("POST /auth/internal/approval-line/authorize — X-Internal-Token 없으면 4xx")
    void authorize_withoutInternalToken_returns4xx() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/internal/approval-line/authorize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"documentType":"SLIP_OUTBOUND","actionKey":"OUTBOUND_DISPATCH","userId":"%s"}
                                """.formatted(UUID.randomUUID())))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
    }
}
