package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.StockBalance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** StockBalance — (product, warehouse) 단위 집계 조회. */
public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    Optional<StockBalance> findByProductIdAndWarehouse_IdAndIsDeletedFalse(
            UUID productId, UUID warehouseId);

    Page<StockBalance> findAllByProductIdAndIsDeletedFalse(UUID productId, Pageable pageable);

    Page<StockBalance> findAllByWarehouse_IdAndIsDeletedFalse(UUID warehouseId, Pageable pageable);
}
