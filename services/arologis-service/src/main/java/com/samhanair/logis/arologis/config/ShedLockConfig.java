package com.samhanair.logis.arologis.config;

import java.time.Clock;
import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ShedLock 분산 lock 설정 — Phase 10 W10-1 arologis-service.
 *
 * <p>multi-instance 환경에서 DriverLocation 30일 cleanup scheduler 가 여러 instance 에서 동시 실행되는
 * race 를 lock 으로 회피. dashboard-service W4 ShedLockConfig 패턴 일관.
 *
 * <p>{@code defaultLockAtMostFor} = PT10M (instance crash 시 최대 10분 후 자동 해제).
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }

    /**
     * Clock Bean — QA-4 nit (Fix 10) 채택. DriverLocationCleanupScheduler 에 주입하여 자정 race 회피.
     * 단위 테스트 시 Clock.fixed(...) mock 으로 결정성 확보 가능.
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
