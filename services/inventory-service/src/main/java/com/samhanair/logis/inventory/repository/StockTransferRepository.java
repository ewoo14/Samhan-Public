package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.StockTransfer;
import com.samhanair.logis.inventory.domain.TransferStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** StockTransfer 헤더 조회 + transfer_no 채번 헬퍼. */
public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

    Page<StockTransfer> findAllByStatusAndIsDeletedFalse(TransferStatus status, Pageable pageable);

    Page<StockTransfer> findAllByIsDeletedFalse(Pageable pageable);

    /** {@code YYYY/MM/DD-N} 채번용 — 해당 날짜 prefix 의 마지막 순번을 계산한다. */
    @Query(value = """
            SELECT COALESCE(MAX(CAST(substring(transfer_no from length(:prefix) + 1) AS INTEGER)), 0)
            FROM stock_transfers
            WHERE transfer_no LIKE (:prefix || '%')
              AND is_deleted = false
              AND substring(transfer_no from length(:prefix) + 1) ~ '^[0-9]+$'
            """, nativeQuery = true)
    int findMaxSequenceByTransferNoPrefix(@Param("prefix") String prefix);
}
