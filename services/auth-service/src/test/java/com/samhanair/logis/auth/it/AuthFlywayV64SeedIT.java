package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.auth.AuthServiceApplication;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** V64 거래처 주문 결재라인 seed/action_key 검증. */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class AuthFlywayV64SeedIT extends AbstractPostgresIT {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("V64는 주문 결재 역할 2개와 PARTNER_ORDER_CONVERT action_key를 seed한다")
    void partnerOrderApprovalLineRolesSeeded() {
        List<RoleRow> rows = jdbcTemplate.query(
                """
                SELECT sequence, label, step_type, action_key
                  FROM approval_line_config
                 WHERE document_type = 'PARTNER_ORDER'
                   AND is_deleted = FALSE
                 ORDER BY sequence
                """,
                (rs, rowNum) -> new RoleRow(
                        rs.getInt("sequence"),
                        rs.getString("label"),
                        rs.getString("step_type"),
                        rs.getString("action_key")));

        assertThat(rows).containsExactly(
                new RoleRow(0, "작성자", "CREATOR", null),
                new RoleRow(1, "승인자", "GROUP", "PARTNER_ORDER_CONVERT"));
    }

    private record RoleRow(int sequence, String label, String stepType, String actionKey) {
    }
}
