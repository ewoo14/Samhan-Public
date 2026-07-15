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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** 가격기억 afterCommit 작업을 outer 트랜잭션 커넥션 정리 이후 실행하는 bounded executor 설정. */
@Configuration
@EnableConfigurationProperties(PartnerProductPriceMemoryProperties.class)
public class PartnerProductPriceMemoryAsyncConfig {

    /** 가격기억 전용 bounded executor. 포화 시 caller thread 에서 실행하지 않고 fail-soft 로 거부한다. */
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
     * <p>platform thread 에서는 Boot 와 동일한 {@link ThreadPoolTaskExecutor} 분기를 사용한다.
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
     * Boot 자동구성과 동일하게 {@link SimpleAsyncTaskExecutor} 를 제공한다. 가격기억 전용 bounded
     * pool 은 이 분기와 무관하게 platform thread 로 격리한다.
     */
    @Lazy
    @ConditionalOnThreading(Threading.VIRTUAL)
    @Bean(name = {"applicationTaskExecutor", "taskExecutor"})
    public SimpleAsyncTaskExecutor applicationTaskExecutorVirtualThreads(
            SimpleAsyncTaskExecutorBuilder builder) {
        return builder.build();
    }
}
