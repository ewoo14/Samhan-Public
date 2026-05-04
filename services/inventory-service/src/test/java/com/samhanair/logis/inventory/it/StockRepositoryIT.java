package com.samhanair.logis.inventory.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.samhanair.logis.inventory.InventoryServiceApplication;
import com.samhanair.logis.inventory.domain.StockBalance;
import com.samhanair.logis.inventory.domain.StockLot;
import com.samhanair.logis.inventory.domain.Warehouse;
import com.samhanair.logis.inventory.repository.StockBalanceRepository;
import com.samhanair.logis.inventory.repository.StockLotRepository;
import com.samhanair.logis.inventory.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

/**
 * StockLot FIFO ORDER BY received_at ASC + StockBalance @Version 충돌 검증.
 *
 * <p>BE 도메인 시그니처 (정확):
 * <ul>
 *   <li>{@code StockLot.create(productId, warehouse, lotNo, quantity:int, receivedAt:LocalDateTime, unitCost:BigDecimal)}</li>
 *   <li>{@code StockLotRepository.findAvailableLotsForFifo(productId, warehouseId)} — AVAILABLE + ORDER BY received_at ASC native query</li>
 *   <li>{@code StockBalance.create(productId, warehouse)} — 모든 수량 0, version=0</li>
 *   <li>{@code StockBalance.addInbound(int)} / {@code .adjust(int delta)} — int</li>
 *   <li>{@code @Version Long version} on StockBalance</li>
 * </ul>
 */
@SpringBootTest(classes = InventoryServiceApplication.class)
@Transactional
class StockRepositoryIT extends AbstractPostgresIT {

    @Autowired
    private StockLotRepository stockLotRepository;

    @Autowired
    private StockBalanceRepository stockBalanceRepository;

    @Autowired
    private WarehouseRepository warehouseRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private Warehouse hq;
    private UUID productId;

    @BeforeEach
    void setUp() {
        // V2 시드의 HQ-001 본사창고 사용 (HEADQUARTERS).
        hq = warehouseRepository.findByCode("HQ-001")
                .orElseThrow(() -> new IllegalStateException(
                        "HQ-001 시드 누락 — V2__seed_inventory_warehouses.sql 확인"));
        productId = UUID.randomUUID();
    }

    @Test
    void findAvailableLotsForFifo_returnsLotsOrderedByReceivedAtAsc() {
        // 3개 lot 을 의도적으로 시간 순서를 뒤섞어 저장.
        LocalDateTime t0 = LocalDateTime.parse("2026-04-01T00:00:00");
        LocalDateTime t1 = LocalDateTime.parse("2026-04-15T00:00:00");
        LocalDateTime t2 = LocalDateTime.parse("2026-05-01T00:00:00");

        // 저장 순서 t1 → t0 → t2 (PK 순서 ≠ received_at 순서).
        stockLotRepository.save(StockLot.create(
                productId, hq, "LOT-MID", 10, t1, new BigDecimal("100000")));
        stockLotRepository.save(StockLot.create(
                productId, hq, "LOT-OLD", 5, t0, new BigDecimal("90000")));
        stockLotRepository.save(StockLot.create(
                productId, hq, "LOT-NEW", 8, t2, new BigDecimal("110000")));
        stockLotRepository.flush();
        entityManager.clear();

        List<StockLot> fifo = stockLotRepository.findAvailableLotsForFifo(productId, hq.getId());

        assertThat(fifo).extracting(StockLot::getLotNo)
                .containsExactly("LOT-OLD", "LOT-MID", "LOT-NEW");
    }

    @Test
    void findAvailableLotsForFifo_doesNotIncludeOtherWarehouseOrOtherProduct() {
        // 다른 창고 (VH-001) 의 lot 은 제외돼야 한다.
        Warehouse other = warehouseRepository.findByCode("VH-001")
                .orElseThrow(() -> new IllegalStateException("VH-001 시드 누락"));

        LocalDateTime now = LocalDateTime.parse("2026-05-01T00:00:00");
        stockLotRepository.save(StockLot.create(
                productId, hq, "HQ-LOT", 3, now, new BigDecimal("100000")));
        stockLotRepository.save(StockLot.create(
                productId, other, "OTHER-WH-LOT", 3, now, new BigDecimal("100000")));
        // 다른 productId
        stockLotRepository.save(StockLot.create(
                UUID.randomUUID(), hq, "OTHER-PROD-LOT", 3, now, new BigDecimal("100000")));
        stockLotRepository.flush();
        entityManager.clear();

        List<StockLot> fifo = stockLotRepository.findAvailableLotsForFifo(productId, hq.getId());

        assertThat(fifo).extracting(StockLot::getLotNo).containsExactly("HQ-LOT");
    }

    @Test
    void stockBalance_versionConflict_throwsOptimisticLockingFailure() {
        // 1) 잔액 1건 영속화.
        StockBalance balance = stockBalanceRepository.save(
                StockBalance.create(productId, hq));
        balance.addInbound(100);
        stockBalanceRepository.saveAndFlush(balance);
        UUID balanceId = balance.getId();
        entityManager.clear();

        // 2) 두 사본을 각각 로딩 — 동일 row, 동일 @Version 값.
        StockBalance copyA = stockBalanceRepository.findById(balanceId).orElseThrow();
        entityManager.detach(copyA);
        StockBalance copyB = stockBalanceRepository.findById(balanceId).orElseThrow();
        entityManager.detach(copyB);

        // 3) copyA 가 먼저 저장돼 version 을 bump.
        copyA.adjust(-10);
        stockBalanceRepository.saveAndFlush(copyA);
        entityManager.clear();

        // 4) copyB 가 동일한 (구버전) version 으로 저장 시도 → optimistic lock 충돌.
        copyB.adjust(-5);
        assertThatThrownBy(() -> stockBalanceRepository.saveAndFlush(copyB))
                .isInstanceOfAny(
                        ObjectOptimisticLockingFailureException.class,
                        org.hibernate.StaleObjectStateException.class
                );
    }
}
