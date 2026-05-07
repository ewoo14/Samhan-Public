package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.VehicleStop;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * VehicleStop 저장소 — 차량 단위 정차 목록 + sequence 단건 lookup.
 */
@Repository
public interface VehicleStopRepository extends JpaRepository<VehicleStop, UUID> {

    List<VehicleStop> findAllByVehicleIdOrderBySequenceAsc(UUID vehicleId);

    Optional<VehicleStop> findFirstByVehicleIdAndSequence(UUID vehicleId, Integer sequence);
}
