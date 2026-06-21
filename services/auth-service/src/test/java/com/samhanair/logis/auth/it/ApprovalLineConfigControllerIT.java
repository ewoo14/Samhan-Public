package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.samhanair.logis.auth.AuthServiceApplication;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

/** 결재라인 설정 admin API 실 HTTP + 실 권한 enforcement IT. */
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
class ApprovalLineConfigControllerIT extends AbstractPostgresIT {

    private static final UUID MANAGER_ACCOUNT_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000003");
    private static final UUID SALES_ACCOUNT_ID =
            UUID.fromString("a0000000-0000-0000-0000-000000000004");
    private static final UUID WAREHOUSE_GROUP_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000103");
    private static final String PAGE = "admin.approval-line-config";
    private static final String DOCUMENT_TYPE = "SLIP_OUTBOUND";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        cleanPermissionRowsWithoutTouchingManagerSeed();
        resetOutboundDispatcherRole();
    }

    @AfterEach
    void tearDown() {
        resetOutboundDispatcherRole();
        cleanPermissionRowsWithoutTouchingManagerSeed();
    }

    @Test
    @DisplayName("GET 역할목록 — V61 seed MANAGER 그룹 권한으로 200 + 출고 3역할")
    void listRoles_managerWithSeedGrant_returns200AndThreeRoles() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/admin/approval-line-configs")
                        .param("documentType", DOCUMENT_TYPE)
                        .header("X-User-Id", MANAGER_ACCOUNT_ID.toString())
                        .header("X-User-Role", "MANAGER")
                        .header("X-Is-System-Master", "false"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("작성자")
                .contains("출고인")
                .contains("검수인");
    }

    @Test
    @DisplayName("PUT 출고인 역할 — V61 seed MANAGER UPDATE 권한으로 권한그룹 지정 200")
    void updateDispatcherRole_managerWithSeedGrant_returns200() throws Exception {
        UUID roleId = outboundRoleId("출고인");

        MvcResult result = mockMvc.perform(put("/auth/admin/approval-line-configs/{id}", roleId)
                        .header("X-User-Id", MANAGER_ACCOUNT_ID.toString())
                        .header("X-User-Role", "MANAGER")
                        .header("X-Is-System-Master", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approverGroupId":"%s","required":true}
                                """.formatted(WAREHOUSE_GROUP_ID)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains(WAREHOUSE_GROUP_ID.toString());
        assertThat(jdbcTemplate.queryForObject("""
                SELECT approver_group_id
                FROM approval_line_config
                WHERE id = ?
                  AND is_deleted = FALSE
                """, UUID.class, roleId)).isEqualTo(WAREHOUSE_GROUP_ID);
    }

    @Test
    @DisplayName("PUT 출고인 역할 — 미존재 권한그룹 지정은 4xx")
    void updateDispatcherRole_unknownGroup_returns4xx() throws Exception {
        UUID roleId = outboundRoleId("출고인");
        UUID unknownGroupId = UUID.randomUUID();

        MvcResult result = mockMvc.perform(put("/auth/admin/approval-line-configs/{id}", roleId)
                        .header("X-User-Id", MANAGER_ACCOUNT_ID.toString())
                        .header("X-User-Role", "MANAGER")
                        .header("X-Is-System-Master", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"approverGroupId":"%s","required":true}
                                """.formatted(unknownGroupId)))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isBetween(400, 499);
        assertThat(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .contains("존재하지 않는 권한 그룹");
    }

    @Test
    @DisplayName("GET 역할목록 — admin.approval-line-config 미보유 계정은 403")
    void listRoles_salesWithoutGrant_returns403() throws Exception {
        MvcResult result = mockMvc.perform(get("/auth/admin/approval-line-configs")
                        .param("documentType", DOCUMENT_TYPE)
                        .header("X-User-Id", SALES_ACCOUNT_ID.toString())
                        .header("X-User-Role", "SALES")
                        .header("X-Is-System-Master", "false"))
                .andReturn();

        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    private UUID outboundRoleId(String label) {
        return jdbcTemplate.queryForObject("""
                SELECT id
                FROM approval_line_config
                WHERE document_type = ?
                  AND label = ?
                  AND is_deleted = FALSE
                ORDER BY sequence
                LIMIT 1
                """, UUID.class, DOCUMENT_TYPE, label);
    }

    private void resetOutboundDispatcherRole() {
        jdbcTemplate.update("""
                UPDATE approval_line_config
                SET approver_group_id = NULL,
                    required = TRUE,
                    modified_at = NOW(),
                    modified_by = 'approval-line-config-it'
                WHERE document_type = ?
                  AND label = '출고인'
                  AND is_deleted = FALSE
                """, DOCUMENT_TYPE);
    }

    private void cleanPermissionRowsWithoutTouchingManagerSeed() {
        jdbcTemplate.update("""
                DELETE FROM account_permission_overrides
                WHERE account_id IN (?, ?)
                  AND page_code = ?
                """, MANAGER_ACCOUNT_ID, SALES_ACCOUNT_ID, PAGE);
        jdbcTemplate.update("""
                DELETE FROM account_page_permissions
                WHERE account_id = ?
                  AND page_code = ?
                """, SALES_ACCOUNT_ID, PAGE);
    }
}
