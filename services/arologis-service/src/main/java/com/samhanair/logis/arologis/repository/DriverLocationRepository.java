package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.DriverLocation;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * DriverLocation 저장소 — GPS 적재 + 30일 cleanup.
 */
@Repository
public interface DriverLocationRepository extends JpaRepository<DriverLocation, UUID> {

    /**
     * 30일 cleanup (Hard DELETE — Soft Delete 미적용 entity).
     *
     * @param threshold 이 일자 이전 (포함) GPS 데이터 삭제
     * @return 삭제된 행 수
     */
    @Modifying
    @Query("delete from DriverLocation d where d.capturedDate < :threshold")
    int deleteOlderThan(@Param("threshold") LocalDate threshold);

    long countByDriverId(UUID driverId);
}
