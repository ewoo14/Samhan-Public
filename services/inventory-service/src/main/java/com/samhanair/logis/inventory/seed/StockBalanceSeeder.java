package com.samhanair.logis.inventory.seed;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * feature/local-test-setup Stage 2 — StockBalance 200건 시드 (100 product × 2 warehouse).
 *
 * <p>활성 조건 (이중 가드):
 * <ul>
 *   <li>{@link Profile @Profile("dev")} — local/dev 프로파일 한정</li>
 *   <li>{@link ConditionalOnProperty}({@code app.inventory.seed-test-data=true}) — toggle 명시적 ON</li>
 * </ul>
 *
 * <p>대상 제품: Stage 1 이 시드한 product 100건 (modelName = "TEST-MODEL-{0001..0100}").
 * UUID = {@code UUID.nameUUIDFromBytes("samhan-seed:product:" + modelName)} — Stage 1 과 동일 namespace.
 *
 * <p>대상 창고: V2 시드 본사창고(HQ-001) + 1호차 차량재고(VH-001) — id 는 V2 SQL 의 명시 UUID 사용.
 *
 * <p>잔량 분포 (결정적): {@code quantity = 30 + (productSeq * 7 + warehouseSeq * 13) % 471}.
 * 결과 범위 30~500. slip-service 의 COMPLETED 슬립 차감 (수량 1~10) 을 충분히 견디는 분포.
 *
 * <p>idempotency: id (deterministic UUID) 의 EXISTS 체크 + 중복 시 skip. 안전 재실행.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(value = "app.inventory.seed-test-data", havingValue = "true")
@Order(10)
public class StockBalanceSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StockBalanceSeeder.class);

    /** Stage 1 product 결정성 UUID namespace prefix. modelName 만 가변. */
    private static final String PRODUCT_UUID_PREFIX = "samhan-seed:product:";
    /** StockBalance 결정성 UUID namespace prefix. {warehouseCode}:{productCode} 가변. */
    private static final String STOCK_BALANCE_UUID_PREFIX = "samhan-seed:stock-balance:";
    /** Stage 1 시드 product 개수. */
    private static final int PRODUCT_COUNT = 100;
    /** product 비공개 식별자 패턴 — Stage 1 시드와 일치. */
    private static final String PRODUCT_MODEL_NAME_PATTERN = "TEST-MODEL-%04d";

    /** V2 시드 본사창고 UUID — V2__seed_inventory_warehouses.sql 의 HQ-001 row. */
    private static final UUID HQ_WAREHOUSE_ID =
            UUID.fromString("11111111-1111-1111-1111-000000000001");
    private static final String HQ_WAREHOUSE_CODE = "HQ-001";
    /** V2 시드 1호차 차량재고 UUID — V2__seed_inventory_warehouses.sql 의 VH-001 row. */
    private static final UUID VH_WAREHOUSE_ID =
            UUID.fromString("11111111-1111-1111-1111-000000000002");
    private static final String VH_WAREHOUSE_CODE = "VH-001";

    private final JdbcTemplate jdbcTemplate;

    public StockBalanceSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional
    public void run(String... args) {
        log.info("[StockBalanceSeeder] Stage 2 시드 시작 — 100 product × 2 warehouse = 200 row");

        int created = 0;
        int skipped = 0;
        for (int productSeq = 1; productSeq <= PRODUCT_COUNT; productSeq++) {
            String modelName = String.format(PRODUCT_MODEL_NAME_PATTERN, productSeq);
            UUID productId = deterministicUuid(PRODUCT_UUID_PREFIX + modelName);
            int qHq = computeQuantity(productSeq, 1);
            int qVh = computeQuantity(productSeq, 2);
            if (insertIfAbsent(productId, HQ_WAREHOUSE_ID, HQ_WAREHOUSE_CODE, modelName, qHq)) {
                created++;
            } else {
                skipped++;
            }
            if (insertIfAbsent(productId, VH_WAREHOUSE_ID, VH_WAREHOUSE_CODE, modelName, qVh)) {
                created++;
            } else {
                skipped++;
            }
        }
        log.info("[StockBalanceSeeder] 완료 — 신규 {}건, skip {}건 (총 {}건)",
                created, skipped, created + skipped);
    }

    private boolean insertIfAbsent(UUID productId, UUID warehouseId, String warehouseCode,
                                   String productCode, int quantity) {
        UUID stockBalanceId = deterministicUuid(
                STOCK_BALANCE_UUID_PREFIX + warehouseCode + ":" + productCode);
        Integer cnt = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM stock_balances WHERE id = ?",
                Integer.class, stockBalanceId);
        if (cnt != null && cnt > 0) {
            return false;
        }
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        jdbcTemplate.update(
                "INSERT INTO stock_balances ("
                        + "  id, product_id, warehouse_id, available_qty, reserved_qty, total_qty,"
                        + "  version, created_at, created_by, is_deleted"
                        + ") VALUES (?, ?, ?, ?, 0, ?, 0, ?, 'system', FALSE)",
                stockBalanceId, productId, warehouseId, quantity, quantity, now);
        return true;
    }

    /**
     * 결정적 잔량 — productSeq + warehouseSeq 기반 (재실행 시 동일).
     * 범위 30~500 → COMPLETED 시 slip 차감 (1~10) 여유.
     *
     * @param productSeq 1~100 product 순번
     * @param warehouseSeq 1=HQ, 2=VH
     * @return 30 이상 500 이하 결정적 정수
     */
    private static int computeQuantity(int productSeq, int warehouseSeq) {
        return 30 + ((productSeq * 7 + warehouseSeq * 13) % 471);
    }

    /**
     * Type-3 (name-based MD5) UUID — Stage 1 / Stage 2 공통 namespace 표준.
     * UTF-8 byte 입력, 같은 문자열은 항상 같은 UUID.
     */
    private static UUID deterministicUuid(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
