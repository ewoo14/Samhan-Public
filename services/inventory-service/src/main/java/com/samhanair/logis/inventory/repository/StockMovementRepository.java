package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.StockMovement;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** StockMovement — append-only 감사 로그 조회. soft-delete 사용 안 함 (필터 없음). */
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findAllByLotIdOrderByOccurredAtDesc(UUID lotId, Pageable pageable);

    Page<StockMovement> findAllByProductIdOrderByOccurredAtDesc(UUID productId, Pageable pageable);

    Page<StockMovement> findAllByWarehouseIdOrderByOccurredAtDesc(UUID warehouseId, Pageable pageable);
}
