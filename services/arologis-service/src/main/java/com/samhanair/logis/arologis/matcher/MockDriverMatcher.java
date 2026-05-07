package com.samhanair.logis.arologis.matcher;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverSource;
import com.samhanair.logis.arologis.domain.MatchSource;
import com.samhanair.logis.arologis.domain.Vehicle;
import com.samhanair.logis.arologis.domain.VehicleStop;
import com.samhanair.logis.arologis.repository.DriverRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mock DriverMatcher — Phase 10 W10-1 default impl.
 *
 * <p>{@code samhan.arologis.matcher.provider=mock} 일 때 활성. 항상 driverCode = "MOCK-001",
 * phoneNumber = "010-0000-0000", source = INTERNAL 의 mock Driver 반환 (DB 에 없으면 자동 생성).
 *
 * <p>W10-2 시점 InsungQuickDriverMatcher 가 prod 활성. 본 Mock 은 dev / test / 초기 단계 유지용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MockDriverMatcher implements DriverMatcher {

    public static final String MOCK_DRIVER_CODE = "MOCK-001";
    public static final String MOCK_DRIVER_PHONE = "010-0000-0000";

    private final DriverRepository driverRepository;

    @Override
    @Transactional
    public DriverMatchResult match(Vehicle vehicle, List<VehicleStop> stops) {
        if (vehicle == null) {
            log.warn("MockDriverMatcher.match — vehicle null");
            return DriverMatchResult.empty(MatchSource.INTERNAL_APP);
        }
        Driver mock = driverRepository.findByDriverCode(MOCK_DRIVER_CODE)
                .orElseGet(() -> driverRepository.save(
                        Driver.of(MOCK_DRIVER_CODE, MOCK_DRIVER_PHONE, "1톤",
                                DriverSource.INTERNAL, Boolean.FALSE, null)));
        String externalRef = "MOCK-" + UUID.randomUUID().toString().substring(0, 8);
        log.debug("MockDriverMatcher 매칭 성공 — vehicleSeq={}, driverCode={}, ref={}",
                vehicle.getSequence(), mock.getDriverCode(), externalRef);
        return new DriverMatchResult(Optional.of(mock), MatchSource.INTERNAL_APP, externalRef);
    }

    @Override
    public MatchSource source() {
        return MatchSource.INTERNAL_APP;
    }
}
