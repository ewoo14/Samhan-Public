package com.samhanair.logis.arologis.service;

import com.samhanair.logis.arologis.config.ArologisLocationCleanupProperties;
import com.samhanair.logis.arologis.repository.DriverLocationRepository;
import java.time.Clock;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * DriverLocation 30일 cleanup scheduler — Phase 10 W10-1.
 *
 * <p>매일 03:00 KST (cron 표기 = "0 0 3 * * *") 에 retentionDays (default 30) 초과된 GPS 데이터 hard
 * DELETE. ShedLock 으로 multi-instance race 가드.
 *
 * <p>BaseEntity 미상속 entity 이므로 Soft Delete 미적용 — 30일 정책에 따라 hard DELETE 가 의도.
 *
 * <p>{@link Scheduled} cron — `@EnableScheduling` 필요 (본 클래스에 부여).
 */
@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class DriverLocationCleanupScheduler {

    private final DriverLocationRepository repository;
    private final ArologisLocationCleanupProperties properties;
    private final Clock clock;  // QA-4 nit (Fix 10): 자정 race 회피용 — test 시 Clock.fixed 주입

    /**
     * 매일 03:00 cleanup 실행. ShedLock 으로 multi-instance 동시 실행 회피.
     *
     * <p>{@code lockAtMostFor=PT15M} — instance crash 시 최대 15분 후 lock 자동 해제.
     * {@code lockAtLeastFor=PT5M} — 빠른 종료 시점에도 5분간 다른 instance 재진입 차단.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Seoul")
    @SchedulerLock(name = "arologis-location-cleanup", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5M")
    @Transactional
    public void cleanupOldLocations() {
        int retention = Math.max(properties.getRetentionDays(), 1);
        LocalDate threshold = LocalDate.now(clock).minusDays(retention);
        int deleted = repository.deleteOlderThan(threshold);
        log.info("DriverLocation 30일 cleanup 완료 — threshold={} retentionDays={} deleted={}",
                threshold, retention, deleted);
    }

    /**
     * 수동 trigger — 단위 테스트 / admin trigger 용. retention 정책 동일.
     *
     * @return 삭제된 행 수
     */
    @Transactional
    public int cleanupNow() {
        int retention = Math.max(properties.getRetentionDays(), 1);
        LocalDate threshold = LocalDate.now(clock).minusDays(retention);
        return repository.deleteOlderThan(threshold);
    }
}
