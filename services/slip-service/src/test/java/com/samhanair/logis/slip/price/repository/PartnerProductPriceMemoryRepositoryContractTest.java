package com.samhanair.logis.slip.price.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** partner_product_price_memory 저장소/Flyway SQL 계약 테스트. */
class PartnerProductPriceMemoryRepositoryContractTest {

    @Test
    void migrationCreatesUniquePairAndBaseEntityAuditColumns() throws Exception {
        String sql = Files.readString(
                Path.of("src/main/resources/db/migration/V58__create_partner_product_price_memory.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS partner_product_price_memory");
        assertThat(sql).contains("partner_id");
        assertThat(sql).contains("product_id");
        assertThat(sql).contains("unit_price");
        assertThat(sql).contains("UNIQUE (partner_id, product_id)");
        assertThat(sql).contains("created_at");
        assertThat(sql).contains("created_by");
        assertThat(sql).contains("modified_at");
        assertThat(sql).contains("modified_by");
        assertThat(sql).contains("deleted_at");
        assertThat(sql).contains("deleted_by");
        assertThat(sql).contains("is_deleted");
    }

    @Test
    void repositoryUpsertRevivesSoftDeletedRows() throws Exception {
        String repository = Files.readString(
                Path.of("src/main/java/com/samhanair/logis/slip/price/repository/PartnerProductPriceMemoryRepository.java"),
                StandardCharsets.UTF_8);

        assertThat(repository).contains("ON CONFLICT (partner_id, product_id) DO UPDATE");
        assertThat(repository).contains("unit_price = EXCLUDED.unit_price");
        assertThat(repository).contains("source = EXCLUDED.source");
        assertThat(repository).contains("is_deleted = FALSE");
        assertThat(repository).contains("deleted_at = NULL");
        assertThat(repository).contains("deleted_by = NULL");
    }
}
