package com.samhanair.logis.shared.realtime.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Realtime broker bean 등록 설정 — PR-H4a (Phase 12 Step 4a).
 *
 * <p>본 configuration 은 {@link com.samhanair.logis.shared.realtime.RealtimeAutoConfiguration}
 * 에 의해 import. consumer service 가 본 module 의존만 추가하면 다음 두 모드 자동 활성화:
 *
 * <ul>
 *   <li><b>default (단일 노드)</b> — {@link InMemoryRealtimeBroker} bean 1건만 등록.
 *       publishHook 는 미설정 (Optional.empty), 자기 노드만 SSE 전송.</li>
 *   <li><b>{@code samhan.realtime.broker=redis}</b> — {@link InMemoryRealtimeBroker} +
 *       {@link RedisRealtimeBroker} ({@link RealtimePublishHook} 구현) +
 *       {@link RedisMessageListenerContainer} 함께 등록. 다중 노드 SSE sync 활성.</li>
 * </ul>
 *
 * <p><b>{@code @ConditionalOnMissingBean}</b> — consumer service 가 자체 broker bean 을 정의했으면
 * 우선 (override 가능).
 *
 * <p><b>*Bean suffix 가드</b> (memory feedback): bean method name 충돌 회피 위해 인프라 bean 은
 * {@code realtime} prefix 로 격리.
 */
@Configuration
public class BrokerConfiguration {

    /** in-memory broker — 모든 환경에서 항상 등록. */
    @Bean
    @ConditionalOnMissingBean(RealtimeBroker.class)
    public RealtimeBroker realtimeBroker() {
        return new InMemoryRealtimeBroker();
    }

    /**
     * Redis pub/sub 메시지 수신 container — RedisRealtimeBroker 가 본 container 에 listener 등록.
     *
     * @param connectionFactory Spring Boot auto-config 가 제공 (Lettuce default)
     * @return container instance (생명주기 = ApplicationContext)
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    @ConditionalOnProperty(name = "samhan.realtime.broker", havingValue = "redis")
    @ConditionalOnMissingBean(name = "realtimeMessageListenerContainerBean")
    public RedisMessageListenerContainer realtimeMessageListenerContainerBean(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    /**
     * Redis cross-node propagate hook — {@code samhan.realtime.broker=redis} 시점만 등록.
     * InMemoryRealtimeBroker 가 setter 주입으로 hook 활용.
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.data.redis.core.StringRedisTemplate")
    @ConditionalOnProperty(name = "samhan.realtime.broker", havingValue = "redis")
    @ConditionalOnMissingBean(RedisRealtimeBroker.class)
    public RedisRealtimeBroker redisRealtimeBroker(StringRedisTemplate redisTemplate,
                                                   RedisMessageListenerContainer listenerContainer,
                                                   @Lazy RealtimeBroker localBroker,
                                                   ObjectMapper objectMapper) {
        return new RedisRealtimeBroker(redisTemplate, listenerContainer, localBroker, objectMapper);
    }
}
