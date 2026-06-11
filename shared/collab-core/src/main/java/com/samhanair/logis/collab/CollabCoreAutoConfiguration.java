package com.samhanair.logis.collab;

import com.samhanair.logis.shared.realtime.broker.RealtimeBroker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * shared:collab-core 자동 설정 진입점.
 *
 * <p>본 module 은 @MappedSuperclass 와 generic service class 만 제공한다. 구체 서비스 bean 은
 * 소비 service 가 자기 entity/repository/factory 타입으로 등록한다. 따라서 autoconfiguration 은
 * realtime broker bean 이 있는 opt-in 환경에서만 publisher bean 을 등록하고 별도 broker 를 강제하지 않는다.
 */
@AutoConfiguration
@ConditionalOnBean(RealtimeBroker.class)
public class CollabCoreAutoConfiguration {

    /** collab-core generic 서비스가 공유하는 afterCommit SSE publisher. */
    @Bean
    @ConditionalOnMissingBean
    public CollabRealtimePublisher collabRealtimePublisher(RealtimeBroker broker) {
        return new CollabRealtimePublisher(broker);
    }
}
