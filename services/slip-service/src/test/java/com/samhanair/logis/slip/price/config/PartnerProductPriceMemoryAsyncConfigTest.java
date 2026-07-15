package com.samhanair.logis.slip.price.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * PartnerProductPriceMemoryAsyncConfig — executor 빈 공존 계약 테스트.
 */
class PartnerProductPriceMemoryAsyncConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withConfiguration(AutoConfigurations.of(TaskExecutionAutoConfiguration.class))
            .withUserConfiguration(PartnerProductPriceMemoryAsyncConfig.class);

    @Test
    void priceMemoryExecutor_doesNotBackOffBootApplicationTaskExecutor() {
        // R4-B1 — priceMemoryExecutor 가 slip-service 유일 Executor 빈이 되면 Boot 3.3
        // TaskExecutionAutoConfiguration(@ConditionalOnMissingBean(Executor.class))의
        // applicationTaskExecutor 자동구성이 back-off 되어, 향후 @Async 도입 시 무관한 비동기
        // 작업이 가격기억 4스레드 AbortPolicy 풀을 조용히 잡는 트랩이 생긴다.
        // 명시 복원 빈이 두 풀의 공존을 보장하는지 검증한다.
        contextRunner.run(context -> {
            assertThat(context).hasBean("priceMemoryExecutor");
            assertThat(context).hasBean("applicationTaskExecutor");

            Executor applicationTaskExecutor = context.getBean("applicationTaskExecutor", Executor.class);
            Executor aliasedTaskExecutor = context.getBean("taskExecutor", Executor.class);
            Executor priceMemoryExecutor = context.getBean("priceMemoryExecutor", Executor.class);

            // @Async 의 기본 탐색 이름(taskExecutor)은 Boot 기본 풀을 가리키고,
            // 가격기억 전용 풀과는 서로 다른 인스턴스로 격리되어야 한다.
            assertThat(aliasedTaskExecutor).isSameAs(applicationTaskExecutor);
            assertThat(applicationTaskExecutor).isNotSameAs(priceMemoryExecutor);
            assertThat(applicationTaskExecutor).isInstanceOf(ThreadPoolTaskExecutor.class);
        });
    }

    @Test
    void priceMemoryExecutor_keepsDedicatedBoundedPoolSettings() {
        // applicationTaskExecutor 복원이 가격기억 전용 bounded 풀 설정을 오염시키지 않는지 고정한다.
        contextRunner.run(context -> {
            ThreadPoolTaskExecutor executor =
                    context.getBean("priceMemoryExecutor", ThreadPoolTaskExecutor.class);

            assertThat(executor.getThreadNamePrefix()).isEqualTo("price-memory-");
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(4);
        });
    }

    @Test
    void applicationTaskExecutor_declaresBootEquivalentPlatformAndVirtualThreadBranches()
            throws NoSuchMethodException {
        Method platform = PartnerProductPriceMemoryAsyncConfig.class.getDeclaredMethod(
                "applicationTaskExecutor", ThreadPoolTaskExecutorBuilder.class);
        Method virtual = PartnerProductPriceMemoryAsyncConfig.class.getDeclaredMethod(
                "applicationTaskExecutorVirtualThreads", SimpleAsyncTaskExecutorBuilder.class);

        assertThat(platform.getAnnotation(ConditionalOnThreading.class).value())
                .isEqualTo(Threading.PLATFORM);
        assertThat(platform.getReturnType()).isEqualTo(ThreadPoolTaskExecutor.class);
        assertThat(virtual.getAnnotation(ConditionalOnThreading.class).value())
                .isEqualTo(Threading.VIRTUAL);
        assertThat(virtual.getReturnType()).isEqualTo(SimpleAsyncTaskExecutor.class);
    }

    @Test
    void hikariConnectionAcquisitionWait_isBoundedToFourSeconds() {
        contextRunner.run(context -> assertThat(context.getEnvironment()
                .getProperty("spring.datasource.hikari.connection-timeout"))
                .isEqualTo("4000"));
    }
}
