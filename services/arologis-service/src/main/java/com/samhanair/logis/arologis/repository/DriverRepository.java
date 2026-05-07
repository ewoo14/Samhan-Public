package com.samhanair.logis.arologis.repository;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverSource;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Driver 저장소 — driverCode / phoneNumber unique lookup + source 필터.
 */
@Repository
public interface DriverRepository extends JpaRepository<Driver, UUID> {

    Optional<Driver> findByDriverCode(String driverCode);

    Optional<Driver> findByPhoneNumber(String phoneNumber);

    List<Driver> findAllBySourceOrderByCreatedAtDesc(DriverSource source);

    List<Driver> findAllBySourceAndAppInstalledOrderByCreatedAtDesc(DriverSource source, Boolean appInstalled);

    List<Driver> findAllByAppInstalledOrderByCreatedAtDesc(Boolean appInstalled);
}
