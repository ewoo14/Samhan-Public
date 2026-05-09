package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.VehicleStop;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * VehicleStop 저장소 — 차량 단위 정차 목록 + sequence 단건 lookup.
 *
 * <p>PR-E1 BE-3 신규 — parsed_partner_code IN 조회 (PreClassify / Unassigned 서비스의 left join
 * 시뮬레이션 source). 인덱스 ix_vehicle_stops_partner_code_active (V4 migration) 활용.
 */
@Repository
public interface VehicleStopRepository extends JpaRepository<VehicleStop, UUID> {

    List<VehicleStop> findAllByVehicleIdOrderBySequenceAsc(UUID vehicleId);

    Optional<VehicleStop> findFirstByVehicleIdAndSequence(UUID vehicleId, Integer sequence);

    /**
     * partnerCode (PR-E1 lookup 결과 채워진 컬럼) IN 조회 — 가배차/미배차 분류 서비스의 매칭 source.
     *
     * <p>service-per-DB 패턴 — slip-service slips 와 SQL JOIN 불가, 따라서 application-level 매칭으로
     * left join 시뮬레이션. 본 메서드가 batch IN 조회로 N+1 회피.
     *
     * @param parsedPartnerCodes partner-service partner_code 리스트 (예: "P-2026-0001")
     * @return 매칭된 활성 vehicle_stops (soft-delete 제외, classifyRegionGroup / dispatch 매핑 보유)
     */
    List<VehicleStop> findAllByParsedPartnerCodeIn(Collection<String> parsedPartnerCodes);
}
