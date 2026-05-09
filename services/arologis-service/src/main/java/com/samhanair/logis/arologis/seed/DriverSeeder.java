package com.samhanair.logis.arologis.seed;

import com.samhanair.logis.arologis.domain.Driver;
import com.samhanair.logis.arologis.domain.DriverSource;
import com.samhanair.logis.arologis.repository.DriverRepository;
import jakarta.annotation.PostConstruct;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DriverSeeder — Phase 10 W10-1 Stage 3 local-test seed.
 *
 * <p>배송기사 10명 (DRV-2026-001 ~ DRV-2026-010) 을 결정적으로 생성한다. INTERNAL 5명 / EXTERNAL_INSUNG_QUICK
 * 3명 / EXTERNAL_KAKAO 2명 분포 (W10-2 인성데이타 vendor 통합 + 카톡 매칭 fallback 시뮬레이션 정합).
 *
 * <p>이중 가드 — {@code @Profile("dev")} + {@code @ConditionalOnProperty
 * (value = "app.arologis.seed-test-data", havingValue = "true")} 양쪽 모두 활성일 때만 동작.
 *
 * <p>idempotent — driverCode (DRV-2026-NNN) 가 이미 존재하면 skip. {@link DispatchSeeder}
 * 보다 먼저 동작 ({@link Order @Order(10)} vs Dispatch {@link Order @Order(20)}) — Vehicle.assignDriver
 * 가 driver UUID 를 참조하므로 의존성 순서 보존.
 *
 * <p>Driver 도메인은 현재 {@code name} / {@code status} 필드를 보유하지 않는다 (PR #66 W10-1 단계 entity
 * skeleton). 이름은 driverCode 와 1:1 매핑 (Javadoc 표 참조), 활성 여부는 BaseEntity.isDeleted 로 표현.
 */
@Component
@Profile("dev")
@ConditionalOnProperty(value = "app.arologis.seed-test-data", havingValue = "true")
@Order(10) // Driver → Dispatch (Vehicle.assignedDriverId 의존성)
public class DriverSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DriverSeeder.class);

    /**
     * 결정적 driver 표본 — 10명 (driverCode 순서 = INTERNAL 5 / INSUNG 3 / KAKAO 2 / 비활성 1).
     *
     * <p>한국식 사업자 분류 — INTERNAL 5명 (본 어플 사용), INSUNG 3명 (인성데이타 5만 프리랜서 풀 매칭),
     * KAKAO 2명 (카톡 단톡방 fallback 매칭). seq 10 (마지막) 은 INACTIVE — soft-delete 시뮬레이션.
     */
    private static final List<DriverSeed> DRIVER_POOL = List.of(
            new DriverSeed("DRV-2026-001", "박배송", "010-2000-0001", "1톤",   DriverSource.INTERNAL,              true,  false),
            new DriverSeed("DRV-2026-002", "최운송", "010-2000-0002", "2.5톤", DriverSource.INTERNAL,              true,  false),
            new DriverSeed("DRV-2026-003", "정물류", "010-2000-0003", "5톤",   DriverSource.INTERNAL,              true,  false),
            new DriverSeed("DRV-2026-004", "강택배", "010-2000-0004", "1톤",   DriverSource.INTERNAL,              true,  false),
            new DriverSeed("DRV-2026-005", "조운반", "010-2000-0005", "2.5톤", DriverSource.INTERNAL,              true,  false),
            new DriverSeed("DRV-2026-006", "윤이동", "010-2000-0006", "5톤",   DriverSource.EXTERNAL_INSUNG_QUICK, false, false),
            new DriverSeed("DRV-2026-007", "임수송", "010-2000-0007", "1톤",   DriverSource.EXTERNAL_INSUNG_QUICK, false, false),
            new DriverSeed("DRV-2026-008", "한보내", "010-2000-0008", "2.5톤", DriverSource.EXTERNAL_INSUNG_QUICK, false, false),
            new DriverSeed("DRV-2026-009", "오가져", "010-2000-0009", "5톤",   DriverSource.EXTERNAL_KAKAO,        false, false),
            new DriverSeed("DRV-2026-010", "권받기", "010-2000-0010", "1톤",   DriverSource.EXTERNAL_KAKAO,        false, true)
    );

    private final DriverRepository driverRepository;

    public DriverSeeder(DriverRepository driverRepository) {
        this.driverRepository = driverRepository;
    }

    @PostConstruct
    void announce() {
        log.warn("[seed] DriverSeeder 활성 — dev profile + app.arologis.seed-test-data=true");
    }

    @Override
    @Transactional
    public void run(String... args) {
        int created = 0;
        int skipped = 0;

        for (DriverSeed seed : DRIVER_POOL) {
            // idempotent — 활성 driverCode 존재 시 skip
            if (driverRepository.findByDriverCode(seed.driverCode()).isPresent()) {
                skipped++;
                continue;
            }
            Driver driver = Driver.of(
                    seed.driverCode(),
                    seed.phoneNumber(),
                    seed.vehicleType(),
                    seed.source(),
                    seed.appInstalled(),
                    null);
            Driver saved = driverRepository.save(driver);
            if (seed.inactive()) {
                // INACTIVE — BaseEntity.markDeleted 로 soft-delete (status 필드 부재 → 활성/비활성은 isDeleted 로 표현)
                saved.markDeleted("seed");
                driverRepository.save(saved);
            }
            created++;
            log.debug("[seed] driver 생성: {} ({}) source={} appInstalled={}",
                    seed.driverCode(), seed.name(), seed.source(), seed.appInstalled());
        }

        log.info("[seed] DriverSeeder 완료 — created={} skipped={} (총 10명)", created, skipped);
    }

    /** 외부 (DispatchSeeder) 가 driver pool 참조 시 사용. */
    static List<DriverSeed> driverPool() {
        return DRIVER_POOL;
    }

    /**
     * Driver seed row.
     *
     * @param driverCode  사용자 노출 식별자 (UUID 비공개 가드)
     * @param name        한국식 사업자 분류용 이름 (entity 미보유 — 시드 메타데이터)
     * @param phoneNumber 전화번호 010-2000-NNNN
     * @param vehicleType 차량 종류 (1톤 / 2.5톤 / 5톤)
     * @param source      INTERNAL / EXTERNAL_INSUNG_QUICK / EXTERNAL_KAKAO
     * @param appInstalled INTERNAL 만 true
     * @param inactive    seq 10 만 true (soft-delete)
     */
    record DriverSeed(
            String driverCode,
            String name,
            String phoneNumber,
            String vehicleType,
            DriverSource source,
            boolean appInstalled,
            boolean inactive) {}
}
