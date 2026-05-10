package com.samhanair.logis.arologis.realtime.repository;

import com.samhanair.logis.arologis.realtime.domain.ArologisAuditLog;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * arologis audit overlay repository — PR-H4b (Phase 12 Step 4b).
 */
public interface ArologisAuditLogRepository extends JpaRepository<ArologisAuditLog, UUID> {

    List<ArologisAuditLog> findByEntityIdOrderByRevisionNoDescChangedAtDesc(UUID entityId);

    List<ArologisAuditLog> findByEntityIdAndRevisionNo(UUID entityId, int revisionNo);

    long countByEntityId(UUID entityId);
}
