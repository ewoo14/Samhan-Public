package com.samhanair.logis.inventory.repository;

import com.samhanair.logis.inventory.domain.InventoryAuditLine;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** InventoryAuditLine 단건 조회. */
public interface InventoryAuditLineRepository extends JpaRepository<InventoryAuditLine, UUID> {

    Optional<InventoryAuditLine> findByIdAndAudit_Id(UUID lineId, UUID auditId);
}
