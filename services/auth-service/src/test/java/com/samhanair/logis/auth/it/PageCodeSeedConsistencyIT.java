package com.samhanair.logis.auth.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.auth.AuthServiceApplication;
import com.samhanair.logis.auth.domain.PageCode;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway 권한 seed page-code 와 PageCode enum 카탈로그 정합성 가드. */
@SpringBootTest(
        classes = AuthServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class PageCodeSeedConsistencyIT extends AbstractPostgresIT {

    private static final Set<String> LEGACY_EXCLUDED = Set.of(
            "ecount.mig14.cash-list",
            "ecount.mig14.aging-snapshot");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("권한 seed 테이블의 활성 page_code는 PageCode enum 카탈로그에 모두 존재한다")
    void seededPageCodesExistInEnumCatalog() {
        List<String> seededPageCodes = jdbcTemplate.queryForList(
                """
                SELECT DISTINCT page_code
                  FROM (
                      SELECT page_code FROM role_page_permissions
                       WHERE is_deleted = FALSE
                      UNION
                      SELECT page_code FROM role_page_permission_templates
                       WHERE is_deleted = FALSE
                      UNION
                      SELECT page_code FROM group_page_permissions
                       WHERE is_deleted = FALSE
                      UNION
                      SELECT page_code FROM account_page_permissions
                       WHERE is_deleted = FALSE
                      UNION
                      SELECT page_code FROM account_permission_overrides
                       WHERE is_deleted = FALSE
                  ) seeded_page_codes
                 ORDER BY page_code
                """,
                String.class);

        assertThat(seededPageCodes)
                .as("권한 seed page_code 조회 결과")
                .isNotEmpty();

        List<String> missing = seededPageCodes.stream()
                .filter(code -> !LEGACY_EXCLUDED.contains(code))
                .filter(code -> !PageCode.isValid(code))
                .sorted()
                .toList();

        assertThat(missing)
                .withFailMessage(
                        "시드됐으나 PageCode enum 누락: %s — enum 추가 또는 LEGACY_EXCLUDED 등재 필요",
                        missing)
                .isEmpty();
    }
}
