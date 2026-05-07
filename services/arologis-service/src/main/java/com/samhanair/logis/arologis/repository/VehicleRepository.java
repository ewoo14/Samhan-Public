package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.Vehicle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Vehicle 저장소 — dispatch 단위 조회 + sequence 단건 lookup.
 */
@Repository
public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    List<Vehicle> findAllByDispatchIdOrderBySequenceAsc(UUID dispatchId);

    Optional<Vehicle> findFirstByDispatchIdAndSequence(UUID dispatchId, Integer sequence);

    List<Vehicle> findAllByAssignedDriverIdOrderByCreatedAtDesc(UUID assignedDriverId);
}
