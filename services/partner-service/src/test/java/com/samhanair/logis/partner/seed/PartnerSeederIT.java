package com.samhanair.logis.partner.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.samhanair.logis.partner.PartnerServiceApplication;
import com.samhanair.logis.partner.it.AbstractPostgresIT;
import com.samhanair.logis.partner.repository.PartnerRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PartnerSeeder} native INSERT 를 실제 PostgreSQL schema 로 검증하는 회귀 가드.
 *
 * <p>mock 기반 {@link PartnerSeederTest} 가 잡지 못하는 컬럼명 / NOT NULL / 타입 정합 문제를
 * Testcontainers + Flyway 적용 DB 에서 확인한다.
 */
@SpringBootTest(classes = PartnerServiceApplication.class)
@Transactional
class PartnerSeederIT extends AbstractPostgresIT {

    private static final String FIRST_PARTNER_CODE = "P-2026-0001";
    private static final String FIRST_PARTNER_ID = "8e809b05-1426-387c-a13e-14e53ffdb3ea";

    @Autowired
    private PartnerRepository partnerRepository;

    @Autowired
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void run_insertsDeterministicPartnersAndIsIdempotentAgainstRealPostgresSchema() {
        PartnerSeeder seeder = new PartnerSeeder(partnerRepository, jdbcTemplate);

        cleanSeedPartners();
        seeder.run();

        assertThat(findId(FIRST_PARTNER_CODE)).isEqualTo(FIRST_PARTNER_ID);
        assertThat(countSeedPartners()).isEqualTo(50);
        assertRequiredColumnsArePresent(FIRST_PARTNER_CODE);

        seeder.run();

        assertThat(countSeedPartners()).isEqualTo(50);
    }

    private void cleanSeedPartners() {
        jdbcTemplate.update("""
                DELETE FROM partner_credit_history
                 WHERE partner_id IN (
                       SELECT id FROM partners WHERE partner_code LIKE 'P-2026-%'
                 )
                """, new MapSqlParameterSource());
        jdbcTemplate.update("""
                DELETE FROM partner_attachments
                 WHERE partner_id IN (
                       SELECT id FROM partners WHERE partner_code LIKE 'P-2026-%'
                 )
                """, new MapSqlParameterSource());
        jdbcTemplate.update(
                "DELETE FROM partners WHERE partner_code LIKE 'P-2026-%'",
                new MapSqlParameterSource());
    }

    private String findId(String partnerCode) {
        return jdbcTemplate.queryForObject(
                "SELECT id::text FROM partners WHERE partner_code = :partnerCode",
                new MapSqlParameterSource("partnerCode", partnerCode),
                String.class);
    }

    private int countSeedPartners() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM partners WHERE partner_code LIKE 'P-2026-%'",
                new MapSqlParameterSource(),
                Integer.class);
        return count == null ? 0 : count;
    }

    private void assertRequiredColumnsArePresent(String partnerCode) {
        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT biz_no, name, status, credit_limit, outstanding_balance,
                       created_at, created_by, is_deleted
                  FROM partners
                 WHERE partner_code = :partnerCode
                """, new MapSqlParameterSource("partnerCode", partnerCode));

        assertThat(row.get("biz_no")).isInstanceOf(String.class).asString().isNotBlank();
        assertThat(row.get("name")).isInstanceOf(String.class).asString().isNotBlank();
        assertThat(row.get("status")).isInstanceOf(String.class).asString().isNotBlank();
        assertThat(row.get("credit_limit")).isInstanceOf(BigDecimal.class);
        assertThat(row.get("outstanding_balance")).isInstanceOf(BigDecimal.class);
        assertThat(row.get("created_at")).isInstanceOf(Timestamp.class);
        assertThat(row.get("created_by")).isInstanceOf(String.class).asString().isNotBlank();
        assertThat(row.get("is_deleted")).isEqualTo(Boolean.FALSE);
    }
}
