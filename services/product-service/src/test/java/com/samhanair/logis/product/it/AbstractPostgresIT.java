package com.samhanair.logis.product.it;

import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * product-service 통합 테스트의 베이스. user-service AbstractPostgresIT 패턴 그대로
 * 모방하여 product_db 컨테이너를 JVM 1회 부팅한다. {@code @DynamicPropertySource} 로
 * datasource URL / Flyway / Eureka off / 내부 토큰을 주입한다.
 *
 * <p>Docker 가 호스트에서 사용 불가하면 {@link DockerAvailableCondition} 이 테스트를
 * fail 이 아닌 skip 으로 처리한다 (Plan §6 정책: "Docker 미가동 시 Testcontainers
 * 가 fail 하는 것은 허용 가능" — 다만 CI 가 아닌 개발자 머신에서는 skip 이 더 친화적).
 */
@Testcontainers
@ExtendWith(AbstractPostgresIT.DockerAvailableCondition.class)
public abstract class AbstractPostgresIT {

    @SuppressWarnings("resource")
    @Container
    protected static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("samhan")
            .withPassword("samhan_dev_pw");

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
                if (DockerClientFactory.instance().isDockerAvailable()) {
                    return org.junit.jupiter.api.extension.ConditionEvaluationResult
                            .enabled("Docker is available");
                }
            } catch (Throwable t) {
                // fall through to disabled
            }
            return org.junit.jupiter.api.extension.ConditionEvaluationResult
                    .disabled("Docker daemon not reachable - skipping Testcontainers IT");
        }
    }
}
