package com.samhanair.logis.slip.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.slip.client.ExpandedLineDto;
import com.samhanair.logis.slip.SlipServiceApplication;
import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.PartnerInternalClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.client.ProductSummary;
import com.samhanair.logis.slip.client.UserInternalClient;
import com.samhanair.logis.slip.client.WarehouseInternalClient;
import com.samhanair.logis.slip.domain.DeliveryTag;
import com.samhanair.logis.slip.domain.SlipType;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryResponse;
import com.samhanair.logis.slip.price.service.PartnerProductPriceMemoryService;
import com.samhanair.logis.slip.repository.SlipRepository;
import com.samhanair.logis.slip.service.SlipService;
import com.samhanair.logis.slip.web.dto.CreateSlipRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * #809 거래처+품목 최근 수동단가 기억 실 DB 통합 테스트.
 *
 * <p>native upsert 는 컴파일 타임 검증이 없으므로 Testcontainers PostgreSQL 에 Flyway V58 을
 * 실제 적용한 뒤 라운드트립, 충돌 갱신, soft-delete revive, UNIQUE 제약, fail-soft 경계를 검증한다.
 */
@SpringBootTest(classes = SlipServiceApplication.class)
class PartnerProductPriceMemoryIT extends AbstractPostgresIT {

    private static final String FAIL_CONSTRAINT = "chk_pppm_test_fail";

    @Autowired
    private PartnerProductPriceMemoryService priceMemoryService;

    @Autowired
    private SlipService slipService;

    @Autowired
    private SlipRepository slipRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private ProductClient productClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private PartnerInternalClient partnerInternalClient;

    @MockBean
    private UserInternalClient userInternalClient;

    @MockBean
    private WarehouseInternalClient warehouseInternalClient;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("ALTER TABLE partner_product_price_memory DROP CONSTRAINT IF EXISTS " + FAIL_CONSTRAINT);
        jdbcTemplate.update("DELETE FROM partner_product_price_memory");

        lenient().when(productClient.lookup(anyList())).thenAnswer(inv -> {
            List<UUID> productIds = inv.getArgument(0);
            return productIds.stream()
                    .map(this::product)
                    .toList();
        });
        lenient().when(productClient.requireExists(any(UUID.class))).thenAnswer(inv -> product(inv.getArgument(0)));
        lenient().when(partnerInternalClient.resolveBusinessNumber(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(partnerInternalClient.resolvePartnerCode(any(UUID.class))).thenReturn(Optional.empty());
        lenient().when(userInternalClient.resolveFullName(any(UUID.class))).thenReturn(Optional.of("테스트 담당자"));
        lenient().when(warehouseInternalClient.findWarehouseName(any(UUID.class))).thenReturn(Optional.of("테스트 창고"));
    }

    @Test
    void roundTrip_returnsExactVatInclusiveInputPrice() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        BigDecimal inputPrice = new BigDecimal("123456.78");

        priceMemoryService.remember(partnerId, productId, inputPrice, "actor-a");

        PartnerProductPriceMemoryResponse found = priceMemoryService.find(partnerId, productId)
                .orElseThrow();
        assertThat(found.unitPrice()).isEqualByComparingTo(inputPrice);
    }

    @Test
    void upsert_updatesExistingRowAndAuditWithoutDuplicatingPair() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        priceMemoryService.remember(partnerId, productId, new BigDecimal("1000.00"), "actor-1");
        priceMemoryService.remember(partnerId, productId, new BigDecimal("2000.00"), "actor-2");

        Integer rowCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                  FROM partner_product_price_memory
                 WHERE partner_id = ? AND product_id = ?
                """, Integer.class, partnerId, productId);
        assertThat(rowCount).isEqualTo(1);

        PriceMemoryRow row = jdbcTemplate.queryForObject("""
                SELECT unit_price, created_by, modified_by, modified_at IS NOT NULL AS has_modified_at
                  FROM partner_product_price_memory
                 WHERE partner_id = ? AND product_id = ?
                """, (rs, rowNum) -> new PriceMemoryRow(
                rs.getBigDecimal("unit_price"),
                rs.getString("created_by"),
                rs.getString("modified_by"),
                rs.getBoolean("has_modified_at")), partnerId, productId);
        assertThat(row).isNotNull();
        assertThat(row.unitPrice()).isEqualByComparingTo("2000.00");
        assertThat(row.createdBy()).isEqualTo("actor-1");
        assertThat(row.modifiedBy()).isEqualTo("actor-2");
        assertThat(row.hasModifiedAt()).isTrue();
    }

    @Test
    void upsert_revivesSoftDeletedRow() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        priceMemoryService.remember(partnerId, productId, new BigDecimal("1000.00"), "actor-1");
        jdbcTemplate.update("""
                UPDATE partner_product_price_memory
                   SET is_deleted = TRUE,
                       deleted_at = CURRENT_TIMESTAMP,
                       deleted_by = 'deleter'
                 WHERE partner_id = ? AND product_id = ?
                """, partnerId, productId);

        assertThat(priceMemoryService.find(partnerId, productId)).isEmpty();

        priceMemoryService.remember(partnerId, productId, new BigDecimal("3000.00"), "actor-2");

        RevivedRow row = jdbcTemplate.queryForObject("""
                SELECT unit_price,
                       is_deleted,
                       deleted_at IS NULL AS deleted_at_null,
                       deleted_by IS NULL AS deleted_by_null
                  FROM partner_product_price_memory
                 WHERE partner_id = ? AND product_id = ?
                """, (rs, rowNum) -> new RevivedRow(
                rs.getBigDecimal("unit_price"),
                rs.getBoolean("is_deleted"),
                rs.getBoolean("deleted_at_null"),
                rs.getBoolean("deleted_by_null")), partnerId, productId);
        assertThat(row).isNotNull();
        assertThat(row.unitPrice()).isEqualByComparingTo("3000.00");
        assertThat(row.deleted()).isFalse();
        assertThat(row.deletedAtNull()).isTrue();
        assertThat(row.deletedByNull()).isTrue();
    }

    @Test
    void v58Migration_createsRealUniqueConstraintForPartnerProductPair() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        rawInsert(partnerId, productId, new BigDecimal("1000.00"), "actor-1");

        assertThatThrownBy(() -> rawInsert(partnerId, productId, new BigDecimal("2000.00"), "actor-2"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void callerFailSoft_keepsSlipCommittedWhenPriceMemoryRequiresNewFailsOnRealDatabaseConstraint() {
        UUID partnerId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        CreateSlipRequest request = inboundSlipRequest(partnerId, productId, new BigDecimal("99000.00"));

        jdbcTemplate.update("DELETE FROM partner_product_price_memory");
        jdbcTemplate.execute("ALTER TABLE partner_product_price_memory ADD CONSTRAINT "
                + FAIL_CONSTRAINT + " CHECK (unit_price < 0)");
        UUID createdSlipId = null;
        try {
            var response = slipService.create(request, "actor-fail-soft", "가격기억 실패 테스트");
            createdSlipId = response.id();

            assertThat(slipRepository.findById(createdSlipId)).isPresent();
            Integer memoryRows = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                      FROM partner_product_price_memory
                     WHERE partner_id = ? AND product_id = ?
                    """, Integer.class, partnerId, productId);
            assertThat(memoryRows).isZero();
        } finally {
            try {
                jdbcTemplate.execute("ALTER TABLE partner_product_price_memory DROP CONSTRAINT IF EXISTS " + FAIL_CONSTRAINT);
            } finally {
                if (createdSlipId != null) {
                    cleanupSlip(createdSlipId);
                }
            }
        }
    }

    @Test
    void bundleCreate_remembersParentSetPriceOnlyAndSkipsComponents() {
        UUID partnerId = UUID.randomUUID();
        UUID bundleProductId = UUID.randomUUID();
        UUID componentProductId = UUID.randomUUID();
        ProductSummary bundle = bundleProduct(bundleProductId);
        when(productClient.lookup(anyList())).thenReturn(List.of(bundle));
        when(productClient.expand(any(), any(), any(), any())).thenReturn(List.of(
                new ExpandedLineDto(componentProductId, "COMP-1", "COMP-1", "구성품",
                        new BigDecimal("1"), new BigDecimal("1000.00"), "COMPONENT", true)));

        var response = slipService.create(
                inboundSlipRequest(partnerId, bundleProductId, new BigDecimal("550000.00")),
                "actor-bundle", "세트 기억 테스트");
        try {
            PartnerProductPriceMemoryResponse memory = priceMemoryService.find(partnerId, bundleProductId)
                    .orElseThrow();
            assertThat(memory.unitPrice()).isEqualByComparingTo("550000.00");
            assertThat(memory.source()).isEqualTo("BUNDLE_SET");
            assertThat(priceMemoryService.find(partnerId, componentProductId)).isEmpty();
        } finally {
            cleanupSlip(response.id());
        }
    }

    @Test
    void bundleCreateRollback_doesNotLeaveGhostParentPriceMemory() {
        UUID partnerId = UUID.randomUUID();
        UUID bundleProductId = UUID.randomUUID();
        ProductSummary bundle = bundleProduct(bundleProductId);
        when(productClient.lookup(anyList())).thenReturn(List.of(bundle));
        when(productClient.expand(any(), any(), any(), any())).thenReturn(List.of(
                new ExpandedLineDto(null, "MISSING", "MISSING", "미등록",
                        new BigDecimal("1"), new BigDecimal("1000.00"), "COMPONENT", true)));

        assertThatThrownBy(() -> slipService.create(
                inboundSlipRequest(partnerId, bundleProductId, new BigDecimal("660000.00")),
                "actor-rollback", "세트 롤백 테스트"))
                .isInstanceOf(BusinessException.class);

        assertThat(priceMemoryService.find(partnerId, bundleProductId)).isEmpty();
    }

    private ProductSummary product(UUID productId) {
        return new ProductSummary(productId, "테스트 품목", "MODEL-809", "P-809",
                UUID.randomUUID(), new BigDecimal("110000.00"), "ACTIVE", false);
    }

    private ProductSummary bundleProduct(UUID productId) {
        return new ProductSummary(productId, "테스트 세트", "SET-809", "SET-809",
                UUID.randomUUID(), new BigDecimal("550000.00"), "ACTIVE", false,
                "SET-809", "BUNDLE");
    }

    private CreateSlipRequest inboundSlipRequest(UUID partnerId, UUID productId, BigDecimal unitPrice) {
        return new CreateSlipRequest(
                SlipType.INBOUND,
                LocalDate.of(2026, 7, 15),
                null,
                UUID.randomUUID(),
                partnerId,
                "테스트 거래처",
                DeliveryTag.RETURN_TRIP,
                "가격기억 fail-soft 검증",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(new CreateSlipRequest.SlipLineRequest(
                        productId,
                        "테스트 품목",
                        "MODEL-809",
                        null,
                        1,
                        unitPrice,
                        "라인",
                        null,
                        true)));
    }

    private void rawInsert(UUID partnerId, UUID productId, BigDecimal unitPrice, String actor) {
        jdbcTemplate.update("""
                INSERT INTO partner_product_price_memory
                    (id, partner_id, product_id, unit_price, source,
                     created_at, created_by, is_deleted)
                VALUES
                    (?, ?, ?, ?, 'LINE_SAVE',
                     CURRENT_TIMESTAMP, ?, FALSE)
                """, UUID.randomUUID(), partnerId, productId, unitPrice, actor);
    }

    private void cleanupSlip(UUID slipId) {
        jdbcTemplate.update("DELETE FROM slip_revisions WHERE slip_id = ?", slipId);
        jdbcTemplate.update("DELETE FROM slip_lines WHERE slip_id = ?", slipId);
        jdbcTemplate.update("DELETE FROM slips WHERE id = ?", slipId);
    }

    private record PriceMemoryRow(BigDecimal unitPrice, String createdBy, String modifiedBy,
                                  boolean hasModifiedAt) {
    }

    private record RevivedRow(BigDecimal unitPrice, boolean deleted, boolean deletedAtNull,
                              boolean deletedByNull) {
    }
}
