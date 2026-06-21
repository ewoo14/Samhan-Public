package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.auth.AuthServiceApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V62 결재라인 다중 결재자 스키마/action_key 이관 검증. */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class AuthFlywayV62SeedIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V62는 출고 action_key 2개를 seed한다")
    void outboundActionKeysSeeded() {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM approval_line_config
                 WHERE document_type = 'SLIP_OUTBOUND'
                   AND is_deleted = FALSE
                   AND (label, action_key) IN (
                       ('출고인', 'OUTBOUND_DISPATCH'),
                       ('검수인', 'OUTBOUND_INSPECT')
                   )
                """,
                Integer.class);

        assertThat(count).isEqualTo(2);
    }

    @Test
    @DisplayName("V62는 기존 approver_group_id 활성행을 approval_line_approver 로 이관한다")
    void approverGroupMigrationHasNoMissingRows() {
        Integer missing = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                  FROM approval_line_config c
                 WHERE c.approver_group_id IS NOT NULL
                   AND c.is_deleted = FALSE
                   AND NOT EXISTS (
                       SELECT 1
                         FROM approval_line_approver a
                        WHERE a.config_role_id = c.id
                          AND a.approver_type = 'GROUP'
                          AND a.approver_ref_id = c.approver_group_id
                          AND a.is_deleted = FALSE
                   )
                """,
                Integer.class);

        assertThat(missing).isZero();
    }
}
