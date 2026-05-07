package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverSource;
import com.samhanair.logis.arologis.repository.DriverRepository;
import com.samhanair.logis.common.exception.BusinessException;
import com.samhanair.logis.common.exception.ErrorCode;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Driver 조회 / 등록 service — Phase 10 W10-1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DriverService {

    private final DriverRepository driverRepository;

    @Transactional(readOnly = true)
    public List<Driver> findDrivers(DriverSource source, String phoneNumber, Boolean appInstalled) {
        if (phoneNumber != null && !phoneNumber.isBlank()) {
            return driverRepository.findByPhoneNumber(phoneNumber).map(List::of).orElseGet(List::of);
        }
        if (source != null && appInstalled != null) {
            return driverRepository.findAllBySourceAndAppInstalledOrderByCreatedAtDesc(source, appInstalled);
        }
        if (source != null) {
            return driverRepository.findAllBySourceOrderByCreatedAtDesc(source);
        }
        if (appInstalled != null) {
            return driverRepository.findAllByAppInstalledOrderByCreatedAtDesc(appInstalled);
        }
        return driverRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Driver findByCode(String driverCode) {
        return driverRepository.findByDriverCode(driverCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "driver 미존재: " + driverCode));
    }

    @Transactional(readOnly = true)
    public Optional<Driver> findByCodeOptional(String driverCode) {
        if (driverCode == null || driverCode.isBlank()) {
            return Optional.empty();
        }
        return driverRepository.findByDriverCode(driverCode);
    }
}
