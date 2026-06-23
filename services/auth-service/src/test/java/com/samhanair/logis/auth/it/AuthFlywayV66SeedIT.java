package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.auth.AuthServiceApplication;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V66 accounting.receivables page-code 기본 grant 시드 검증. */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class AuthFlywayV66SeedIT extends AbstractPostgresIT {

    private static final String PAGE_CODE = "accounting.receivables";
    private static final List<String> WRITE_ROLES = List.of("MASTER", "MANAGER", "ACCOUNTANT");
    private static final UUID MANAGER_GROUP = UUID.fromString("00000000-0000-0000-0000-000000000101");
    private static final UUID ACCOUNTANT_GROUP = UUID.fromString("00000000-0000-0000-0000-000000000104");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V66은 MASTER/MANAGER/ACCOUNTANT에 accounting.receivables VIEW/CREATE/UPDATE 기본값을 seed한다")
    void v66SeedsReceivablesWritePermissions() {
        for (String role : WRITE_ROLES) {
            assertThat(countRolePageRows(role)).isEqualTo(1);
            assertThat(countTemplateRows(role)).isEqualTo(1);
        }
        assertThat(countGroupRows(MANAGER_GROUP)).isEqualTo(1);
        assertThat(countGroupRows(ACCOUNTANT_GROUP)).isEqualTo(1);
        assertThat(countAccountPermissionRows(MANAGER_GROUP)).isGreaterThanOrEqualTo(1);
        assertThat(countAccountPermissionRows(ACCOUNTANT_GROUP)).isGreaterThanOrEqualTo(1);
    }

    private Integer countRolePageRows(String roleCode) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM role_page_permissions
                 WHERE role_code = ?
                   AND page_code = ?
                   AND can_view = TRUE
                   AND can_edit = TRUE
                   AND is_deleted = FALSE
                """, Integer.class, roleCode, PAGE_CODE);
    }

    private Integer countTemplateRows(String roleCode) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM role_page_permission_templates
                 WHERE role_code = ?
                   AND page_code = ?
                   AND can_view = TRUE
                   AND can_create = TRUE
                   AND can_update = TRUE
                   AND can_delete = FALSE
                   AND is_deleted = FALSE
                """, Integer.class, roleCode, PAGE_CODE);
    }

    private Integer countGroupRows(UUID groupId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM group_page_permissions
                 WHERE group_id = ?
                   AND page_code = ?
                   AND can_view = TRUE
                   AND can_create = TRUE
                   AND can_update = TRUE
                   AND can_delete = FALSE
                   AND is_deleted = FALSE
                """, Integer.class, groupId, PAGE_CODE);
    }

    private Integer countAccountPermissionRows(UUID groupId) {
        return jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM account_page_permissions app
                  JOIN account_groups ag
                    ON ag.account_id = app.account_id
                   AND ag.group_id = ?
                   AND ag.is_deleted = FALSE
                 WHERE app.page_code = ?
                   AND app.can_view = TRUE
                   AND app.can_create = TRUE
                   AND app.can_update = TRUE
                   AND app.can_delete = FALSE
                   AND app.is_deleted = FALSE
                """, Integer.class, groupId, PAGE_CODE);
    }
}
