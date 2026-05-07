package com.samhanair.logis.dashboard.config;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ShedLock 분산 lock 설정 — Phase 9 W4 후속 fix (DevOps DV-W4-3).
 *
 * <p>multi-instance 환경에서 {@code REFRESH MATERIALIZED VIEW CONCURRENTLY} 가
 * 여러 instance 에서 동시 실행되는 race 를 lock 으로 회피.
 *
 * <ul>
 *   <li>{@code defaultLockAtMostFor} = PT10M — instance crash 시 최대 10분 후 lock 자동 해제</li>
 *   <li>각 {@code @SchedulerLock} 메서드에서 개별 {@code lockAtMostFor} / {@code lockAtLeastFor} override 가능</li>
 *   <li>{@code shedlock} 테이블 (V2 Flyway 신규) 활용 — JdbcTemplateLockProvider 표준</li>
 * </ul>
 *
 * <p>single-instance 환경 영향 0 — 본인 instance 가 lock 즉시 획득 + 정상 해제. 본 단계 functional 회귀 X.
 *
 * <p>H2 / 단위 테스트 환경 — application.yml local profile 의 H2 환경에서도 단순 CREATE TABLE 호환
 * (Flyway V2 가 H2 에서는 비활성). LockProvider Bean 은 어떤 DataSource 든 정상 동작.
 */
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class ShedLockConfig {

    /**
     * JDBC 기반 LockProvider Bean 등록. shedlock 테이블 (V2 Flyway) 사용.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new org.springframework.jdbc.core.JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }
}
