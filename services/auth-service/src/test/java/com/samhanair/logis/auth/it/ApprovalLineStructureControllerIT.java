package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.samhanair.logis.auth.AuthServiceApplication;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** 결재라인 구조 read API — 인증 사용자용, approver 신원 제외 계약 IT. */
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
class ApprovalLineStructureControllerIT extends AbstractPostgresIT {

    private static final UUID SALES_ACCOUNT_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000004");
    private static final UUID WAREHOUSE_GROUP_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final String DOCUMENT_TYPE = "SLIP_OUTBOUND";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("GET structure — 인증 사용자는 admin 권한 없이 판매전표 구조만 sequence 순 조회")
    void getStructure_authenticatedUser_returnsStructureOnly() throws Exception {
        UUID dispatcherRoleId = jdbcTemplate.queryForObject("""
                SELECT id
                  FROM approval_line_config
                 WHERE document_type = ?
                   AND action_key = 'OUTBOUND_DISPATCH'
                   AND is_deleted = FALSE
                 ORDER BY sequence
                 LIMIT 1
                """, UUID.class, DOCUMENT_TYPE);
        jdbcTemplate.update("""
                INSERT INTO approval_line_approver
                    (id, config_role_id, approver_type, approver_ref_id, created_at, created_by, is_deleted)
                VALUES (?, ?, 'GROUP', ?, NOW(), 'structure-it', FALSE)
                """, UUID.randomUUID(), dispatcherRoleId, WAREHOUSE_GROUP_ID);

        MvcResult result = mockMvc.perform(get("/auth/approval-line-configs/{documentType}/structure", DOCUMENT_TYPE)
                        .header("X-User-Id", SALES_ACCOUNT_ID.toString())
                        .header("X-User-Role", "SALES")
                        .header("X-Is-System-Master", "false"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(body)
                .contains("\"sequence\":0")
                .contains("\"label\":\"작성자\"")
                .contains("\"stepType\":\"CREATOR\"")
                .contains("\"sequence\":1")
                .contains("\"label\":\"출고자\"")
                .contains("\"actionKey\":\"OUTBOUND_DISPATCH\"")
                .contains("\"sequence\":2")
                .contains("\"label\":\"검수자\"")
                .contains("\"actionKey\":\"OUTBOUND_INSPECT\"")
                .doesNotContain("approver")
                .doesNotContain("approverRefId")
                .doesNotContain(WAREHOUSE_GROUP_ID.toString());
        assertThat(body.indexOf("\"sequence\":0")).isLessThan(body.indexOf("\"sequence\":1"));
        assertThat(body.indexOf("\"sequence\":1")).isLessThan(body.indexOf("\"sequence\":2"));
    }

    @Test
    @DisplayName("GET structure — 비인증 요청은 401")
    void getStructure_anonymous_returns401() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/approval-line-configs/{documentType}/structure", DOCUMENT_TYPE))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(401);
    }
}
