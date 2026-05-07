package com.samhanair.logis.slip.it;

import com.samhanair.logis.slip.client.InventoryClient;
import com.samhanair.logis.slip.client.PartnerInternalClient;
import com.samhanair.logis.slip.client.ProductClient;
import com.samhanair.logis.slip.delivery.sms.SmsGateway;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * slip-service 통합 테스트의 베이스. **싱글턴 컨테이너 패턴** —
 * static 블록에서 한 번 start, JVM 종료 시 Testcontainers Ryuk 가 자동 stop.
 * 여러 IT 클래스가 같은 컨테이너 인스턴스를 공유한다.
 *
 * <p>{@code @Testcontainers} + {@code @Container} 패턴은 IT 클래스마다 별도 lifecycle 을
 * 관리하려 시도해 race condition / Spring Context 캐시 stale URL 문제를 일으킨다
 * (product-service PR #13 사고 — 메모리 {@code feedback_pm_integration_build_check.md}).
 * 따라서 본 패턴은 {@code @Testcontainers} 사용 안 함.
 *
 * <p>Docker 데몬이 호스트에서 사용 불가하면 {@link DockerAvailableCondition} 이
 * 테스트를 fail 이 아닌 skip 으로 처리한다.
 *
 * <p><b>5차 fix — Spring Context superset {@code @MockBean}</b>:
 * 모든 sub IT 가 동일한 외부 client 4종 ({@link InventoryClient}, {@link ProductClient},
 * {@link SmsGateway}, {@link PartnerInternalClient}) 을 공유하도록 base 에 superset 으로 선언.
 * 직전 PR #99 v3 = 14 IT × 8가지 @MockBean 변형 → ApplicationContext 8회 신축 (~5-10분 손실).
 * 본 fix 로 캐시 키 동일화 → Context 1회만 신축. sub IT 들의 자체 @MockBean 은 모두 제거.
 */
@ExtendWith(AbstractPostgresIT.DockerAvailableCondition.class)
public abstract class AbstractPostgresIT {

    @MockBean protected InventoryClient inventoryClient;
    @MockBean protected ProductClient productClient;
    @MockBean protected SmsGateway smsGateway;
    @MockBean protected PartnerInternalClient partnerInternalClient;

    @SuppressWarnings("resource")
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("slip_db")
                    .withUsername("samhan")
                    .withPassword("samhan_dev_pw");

    static {
        try {
            POSTGRES.start();
        } catch (Throwable ignored) {
            // Docker 미가용 환경. DockerAvailableCondition 이 sub IT 들을 skip 처리.
        }
    }

    @DynamicPropertySource
    static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("eureka.client.enabled", () -> "false");
        registry.add("eureka.client.register-with-eureka", () -> "false");
        registry.add("eureka.client.fetch-registry", () -> "false");
        registry.add("app.security.internal.token", () -> "test-internal-token");
    }

    /** Docker 데몬 미접근 시 테스트를 build fail 대신 skip 처리. */
    static class DockerAvailableCondition implements
            org.junit.jupiter.api.extension.ExecutionCondition {
        @Override
        public org.junit.jupiter.api.extension.ConditionEvaluationResult evaluateExecutionCondition(
                org.junit.jupiter.api.extension.ExtensionContext context) {
            try {
                if (DockerClientFactory.instance().isDockerAvailable() && POSTGRES.isRunning()) {
                    return org.junit.jupiter.api.extension.ConditionEvaluationResult
                            .enabled("Docker is available + container running");
                }
            } catch (Throwable t) {
                // fall through to disabled
            }
            return org.junit.jupiter.api.extension.ConditionEvaluationResult
                    .disabled("Docker daemon not reachable - skipping Testcontainers IT");
        }
    }
}
