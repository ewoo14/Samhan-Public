package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.StockTransfer;
import com.samhanair.logis.inventory.domain.TransferStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** StockTransfer 헤더 조회 + transfer_no 채번 헬퍼. */
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Page<StockTransfer> findAllByStatusAndIsDeletedFalse(TransferStatus status, Pageable pageable);

    Page<StockTransfer> findAllByIsDeletedFalse(Pageable pageable);

    /** {@code TR-YYYYMMDD-NNN} 채번용 — 그날 prefix 의 발행 건수 계산. */
    long countByTransferNoStartingWith(String prefix);
}
