package com.samhanair.logis.shared.realtime.broker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

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
 *       Redis listener container 함께 등록. 다중 노드 SSE sync 활성.</li>
 * </ul>
 *
 * <p><b>{@code @ConditionalOnMissingBean}</b> — consumer service 가 자체 broker bean 을 정의했으면
 * 우선 (override 가능).
 *
 * <p><b>NoClassDefFoundError 회귀 가드 (PR #127)</b>: redis bean 들의 method signature 가
 * {@code RedisConnectionFactory} / {@code StringRedisTemplate} / {@code RedisMessageListenerContainer}
 * 를 직접 참조하면, 13 service 중 redis 의존이 없는 service 가 본 클래스를 introspect 할 때
 * {@code Class.getDeclaredMethods0()} 가 NoClassDefFoundError 를 던짐 ({@code @ConditionalOnClass}
 * 만으로는 method signature 보호 불가). 따라서 redis-dependent bean 은 별도 nested
 * {@link RedisBrokerConfig} 로 격리하고 nested class 자체에 {@code @ConditionalOnClass} 적용 →
 * redis 미의존 service 는 nested class 자체가 skip 되므로 method signature 도 introspect 안 됨.
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
     * Redis broker bean 들을 격리하는 nested configuration.
     *
     * <p>nested class 자체에 {@code @ConditionalOnClass(RedisConnectionFactory)} 적용 →
     * redis 미의존 consumer service 는 본 클래스 자체가 skip 되어 method signature 의
     * {@code RedisConnectionFactory} 등 type 도 introspect 안 됨 → NoClassDefFoundError 회귀 가드.
     */
    @Configuration
    @ConditionalOnClass(name = {
            "org.springframework.data.redis.connection.RedisConnectionFactory",
            "org.springframework.data.redis.core.StringRedisTemplate",
            "org.springframework.data.redis.listener.RedisMessageListenerContainer"
    })
    @ConditionalOnProperty(name = "samhan.realtime.broker", havingValue = "redis")
    public static class RedisBrokerConfig {

        /**
         * Redis pub/sub 메시지 수신 container — RedisRealtimeBroker 가 본 container 에 listener 등록.
         *
         * @param connectionFactory Spring Boot auto-config 가 제공 (Lettuce default)
         * @return container instance (생명주기 = ApplicationContext)
         */
        @Bean
        @ConditionalOnMissingBean(name = "realtimeMessageListenerContainerBean")
        public org.springframework.data.redis.listener.RedisMessageListenerContainer realtimeMessageListenerContainerBean(
                org.springframework.data.redis.connection.RedisConnectionFactory connectionFactory) {
            org.springframework.data.redis.listener.RedisMessageListenerContainer container =
                    new org.springframework.data.redis.listener.RedisMessageListenerContainer();
            container.setConnectionFactory(connectionFactory);
            return container;
        }

        /**
         * Redis cross-node propagate hook — {@code samhan.realtime.broker=redis} 시점만 등록.
         * InMemoryRealtimeBroker 가 setter 주입으로 hook 활용.
         */
        @Bean
        @ConditionalOnMissingBean(RedisRealtimeBroker.class)
        public RedisRealtimeBroker redisRealtimeBroker(
                org.springframework.data.redis.core.StringRedisTemplate redisTemplate,
                org.springframework.data.redis.listener.RedisMessageListenerContainer listenerContainer,
                @Lazy RealtimeBroker localBroker,
                ObjectMapper objectMapper) {
            return new RedisRealtimeBroker(redisTemplate, listenerContainer, localBroker, objectMapper);
        }
    }
}
