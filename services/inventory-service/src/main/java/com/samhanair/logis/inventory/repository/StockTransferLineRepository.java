package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.StockTransferLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** StockTransferLine — 보통 상위 transfer 와 함께 cascade 로 관리, 단독 조회는 거의 없음. */
public interface StockTransferLineRepository extends JpaRepository<StockTransferLine, UUID> {

    List<StockTransferLine> findAllByTransfer_IdAndIsDeletedFalse(UUID transferId);
}
