package com.samhanair.logis.slip.realtime;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub 인프라 설정 — PR-H2 (Phase 12 Step 2) TM 보완 #3.
 *
 * <p><b>활성 조건</b>: {@code app.realtime.broker=redis} (env: {@code SAMHAN_REALTIME_BROKER=redis})
 * 시만 bean 등록. default 단일 노드 환경에서는 RedisRealtimeBroker 와 함께 미등록.
 *
 * <p><b>*Bean suffix 가드</b>: PR #119 회귀 가드 패턴 — class name 에 {@code Bean} suffix 명시
 * 하여 같은 simple name 의 다른 component 와 BeanDefinitionOverrideException 회피.
 */
@Configuration
@ConditionalOnProperty(name = "app.realtime.broker", havingValue = "redis")
public class RedisRealtimeConfigBean {

    /**
     * Redis pub/sub 메시지 수신 container — RedisRealtimeBroker 가 본 container 에 listener 등록.
     *
     * @param connectionFactory Spring Boot auto-config 가 제공 (Lettuce default)
     * @return container instance (생명주기 = ApplicationContext)
     */
    @Bean
    public RedisMessageListenerContainer slipRealtimeMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
