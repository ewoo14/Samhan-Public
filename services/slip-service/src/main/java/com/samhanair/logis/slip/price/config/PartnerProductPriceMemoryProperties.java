package com.samhanair.logis.slip.price.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 가격기억 fail-soft 저장의 시간/동시성 한계 설정. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.slip.price-memory")
public class PartnerProductPriceMemoryProperties {

    /** 인기 pair row lock 은 원 저장 응답을 지연시키지 않도록 1초 안에 포기한다. */
    private int lockTimeoutMs = 1_000;

    /** 최대 100행 단일 upsert 정상 실행 여유를 포함하되 장애는 3초 안에 격리한다. */
    private int statementTimeoutMs = 3_000;

    /** statement timeout 정리 여유 1초를 포함한 REQUIRES_NEW 전체 상한. */
    private int transactionTimeoutSeconds = 4;

    /** outer connection 반환을 막지 않는 전용 worker 기본 개수. */
    private int asyncCorePoolSize = 2;

    /** Hikari 20개 중 최대 4개만 가격기억이 동시에 사용하도록 제한한다. */
    private int asyncMaxPoolSize = 4;

    /** 순간 저장 burst 를 흡수하되 무제한 메모리 증가를 막는 대기열 상한. */
    private int asyncQueueCapacity = 100;

    /** 종료 시 이미 수락한 짧은 가격기억 작업을 기다리는 최대 시간. */
    private int asyncShutdownAwaitSeconds = 5;
}
