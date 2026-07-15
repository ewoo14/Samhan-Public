package com.samhanair.logis.slip.price.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
}
