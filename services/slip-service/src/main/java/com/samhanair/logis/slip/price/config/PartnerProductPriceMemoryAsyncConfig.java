package com.samhanair.logis.slip.price.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.task.SimpleAsyncTaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 가격기억 afterCommit 작업을 outer 트랜잭션 커넥션 정리 이후 실행하는 bounded executor 설정. */
@Configuration
@EnableConfigurationProperties(PartnerProductPriceMemoryProperties.class)
public class PartnerProductPriceMemoryAsyncConfig {

    /**
     * 가격기억 전용 bounded executor. 포화 시 caller thread 에서 실행하지 않고 fail-soft 로 거부한다.
     *
     * <p><b>{@code @DependsOn} 이 파괴 순서를 고정한다 (R8-BE-7)</b>: Spring 은 의존 빈을 <b>먼저</b>
     * 파괴하므로, 이 선언이 executor 의 {@code waitForTasksToCompleteOnShutdown} drain(최대 5초)을
     * {@code priceMemoryDataSource} 의 Hikari {@code close()} <b>이전</b>에 끝나도록 보장한다.
     * 의존 선언이 없으면 파괴 순서가 무제약이라 Hikari 가 먼저 닫힐 수 있고, 그러면 drain 중인
     * 가격기억 작업이 커넥션 획득에 전량 실패한다 — fail-soft 가 그 예외를 삼키므로
     * <b>배포마다 큐에 남은 기억이 조용히 유실</b>된다.
     */
    @DependsOn("priceMemoryDataSource")
    @Bean(name = "priceMemoryExecutor")
    public Executor priceMemoryExecutor(PartnerProductPriceMemoryProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("price-memory-");
        executor.setCorePoolSize(properties.getAsyncCorePoolSize());
        executor.setMaxPoolSize(properties.getAsyncMaxPoolSize());
        executor.setQueueCapacity(properties.getAsyncQueueCapacity());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAsyncShutdownAwaitSeconds());
        return executor;
    }

    /**
     * Boot 기본 {@code applicationTaskExecutor}({@code taskExecutor} alias 포함) 명시 복원.
     *
     * <p>Boot 3.3 의 {@code TaskExecutionAutoConfiguration} 은 {@code @ConditionalOnMissingBean(Executor.class)}
     * 조건이라 {@link #priceMemoryExecutor} 가 slip-service 최초의 {@link Executor} 빈이 되는 순간
     * {@code applicationTaskExecutor} 자동구성이 back-off 된다. 그대로 두면 향후 {@code @Async} 도입 시
     * 유일 executor 인 가격기억 전용 4스레드 AbortPolicy 풀을 조용히 잡아, 무관한 비동기 작업이
     * 가격기억 풀에서 거부/경합하는 잠재 트랩이 생긴다.
     *
     * <p>platform thread 에서는 Boot 와 <b>동등</b>한 {@link ThreadPoolTaskExecutor} 분기를
     * 제공한다 — 단 완전 동일은 아니다 (R6-L3 바이트코드 실측): Boot 3.3.5 PLATFORM 분기는
     * deprecated {@code TaskExecutorBuilder} 를 {@code ObjectProvider.getIfUnique()} 로 우선
     * 조회한 뒤 {@code ThreadPoolTaskExecutorBuilder} 로 fallback 하지만, 본 빈은 신형 builder 만
     * 직주입한다 (deprecated builder 커스터마이즈 미지원 — 현재 코드베이스에 해당 커스터마이즈 0건).
     */
    @Lazy
    @ConditionalOnThreading(Threading.PLATFORM)
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public ThreadPoolTaskExecutor applicationTaskExecutor(ThreadPoolTaskExecutorBuilder builder) {
        return builder.build();
    }

    /**
     * Boot virtual-thread 의미를 보존하는 기본 executor 분기.
     *
     * <p>{@code spring.threads.virtual.enabled=true} 이고 런타임이 virtual thread 를 지원하면
     * Boot 자동구성과 <b>동등</b>하게 {@link SimpleAsyncTaskExecutor} 를 제공한다 — 단 완전
     * 동일은 아니다 (R6-L3 바이트코드 실측): Boot 3.3.5 VIRTUAL 분기는 {@code @Lazy} 없이
     * eager 인 반면 본 빈은 {@code @Lazy} 를 부여한다 (현재 Java 17 + VIRTUAL 비활성이라 inert).
     * 가격기억 전용 bounded pool 은 이 분기와 무관하게 platform thread 로 격리한다.
     */
    @Lazy
    @ConditionalOnThreading(Threading.VIRTUAL)
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public SimpleAsyncTaskExecutor applicationTaskExecutorVirtualThreads(
            SimpleAsyncTaskExecutorBuilder builder) {
        return builder.build();
    }
}
