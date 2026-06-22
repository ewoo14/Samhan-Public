package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.auth.AuthServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V61 결재라인 설정 seed 검증.
 *
 * <p>account_page_permissions materialize 는 공유 Testcontainers DB 오염으로 IT 검증 불가하므로
 * fresh Postgres probe(push 전) + ApprovalLineConfigControllerIT 의 MANAGER 200(실 권한 계승)으로 검증한다.
 */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class AuthFlywayV61SeedIT extends AbstractPostgresIT {

    private static final String PAGE_CODE = "admin.approval-line-config";
    private static final String MANAGER_GROUP_ID = "00000000-0000-0000-0000-000000000101";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V61은 출고전표 결재 역할 3개를 seed한다")
    void outboundApprovalLineRolesSeeded() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM approval_line_config
                 WHERE document_type = 'SLIP_OUTBOUND'
                   AND is_deleted = FALSE
                   AND (sequence, label, step_type) IN (
                       (0, '작성자', 'CREATOR'),
                       (1, '출고자', 'GROUP'),
                       (2, '검수자', 'GROUP')
                   )
                """,
                Integer.class);

        assertThat(count).isEqualTo(3);
    }

    @Test
    @DisplayName("V61은 MANAGER 그룹에 admin.approval-line-config view/update를 seed한다")
    void managerGroupPermissionSeeded() {
        PermissionRow row = jdbcTemplate.queryForObject(
                """
                SELECT can_view, can_create, can_update, can_delete, can_restore, can_download, can_print
                  FROM group_page_permissions
                 WHERE group_id = ?::uuid
                   AND page_code = ?
                   AND is_deleted = FALSE
                """,
                (rs, rowNum) -> new PermissionRow(
                        rs.getBoolean("can_view"),
                        rs.getBoolean("can_create"),
                        rs.getBoolean("can_update"),
                        rs.getBoolean("can_delete"),
                        rs.getBoolean("can_restore"),
                        rs.getBoolean("can_download"),
                        rs.getBoolean("can_print")),
                MANAGER_GROUP_ID,
                PAGE_CODE);

        assertThat(row).isNotNull();
        assertThat(row.canView()).isTrue();
        assertThat(row.canCreate()).isFalse();
        assertThat(row.canUpdate()).isTrue();
        assertThat(row.canDelete()).isFalse();
        assertThat(row.canRestore()).isFalse();
        assertThat(row.canDownload()).isFalse();
        assertThat(row.canPrint()).isFalse();
    }

    private record PermissionRow(
            boolean canView,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete,
            boolean canRestore,
            boolean canDownload,
            boolean canPrint) {
    }
}
