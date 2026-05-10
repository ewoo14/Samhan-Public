package com.samhanair.logis.shared.realtime.broker;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis pub/sub 기반 cross-node realtime broker — PR-H4a (Phase 12 Step 4a) 통합 구현.
 *
 * <p><b>활성 조건</b>: {@code SAMHAN_REALTIME_BROKER=redis} (또는 {@code samhan.realtime.broker=redis})
 * 환경 변수 설정 시만 bean 등록 ({@link com.samhanair.logis.shared.realtime.RealtimeAutoConfiguration}
 * 의 {@code @ConditionalOnProperty} 가드). default (= "in-memory") 단일 노드 환경에서는 본 broker
 * bean 미등록 → {@link InMemoryRealtimeBroker} 만 작동.
 *
 * <p><b>topic 패턴</b>: {@code samhan:realtime:*} — 모든 entity 의 cross-node SSE event.
 * payload 형식 = JSON {@code {"entityId":"uuid","eventName":"...","data":{...}}}.
 *
 * <p><b>infinite loop 방지</b>: Redis 에서 메시지 수신 시 {@link RealtimeBroker#publishLocal}
 * 만 호출 (publish 가 아닌 — publish 는 다시 Redis 로 전파됨).
 *
 * <p><b>외부 의존</b>: Redis 단독. 미연결 시 startup 정상 (auto-config dependency-only,
 * bean 활성 시에만 connection lazy 초기화 — Spring Data Redis 의 LettuceConnectionFactory 패턴).
 *
 * <p><b>사용 시나리오</b>: 다중 EC2/ECS 노드로 service 수평 확장 시 — 모든 노드가 같은 Redis 에
 * pub/sub 하면 사용자가 어느 노드에 SSE 구독해도 다른 노드의 mutation 신호 수신.
 */
@Slf4j
public class RedisRealtimeBroker implements RealtimePublishHook {

    /** Redis pub/sub topic prefix. {@code samhan:realtime:{entityId}} 패턴. */
    public static final String TOPIC_PREFIX = "samhan:realtime:";

    /** Redis 메시지 수신 시 모든 entity 를 cover 하는 pattern subscribe. */
    public static final String TOPIC_PATTERN = TOPIC_PREFIX + "*";

    private final StringRedisTemplate redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final RealtimeBroker localBroker;
    private final ObjectMapper objectMapper;

    /**
     * 명시 생성자 — {@code @Lazy} 가 InMemoryRealtimeBroker 의 publishHook setter 와 본 broker 의
     * RealtimeBroker 필드 사이의 circular dependency 회피 (broker → hook → broker).
     */
    public RedisRealtimeBroker(StringRedisTemplate redisTemplate,
                               RedisMessageListenerContainer listenerContainer,
                               @Lazy RealtimeBroker localBroker,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.localBroker = localBroker;
        this.objectMapper = objectMapper;
    }

    /** 통계 — 누적 propagate / 수신 / 직렬화 실패 카운터 (운영 모니터링). */
    private final AtomicLong propagateCount = new AtomicLong();
    private final AtomicLong receiveCount = new AtomicLong();
    private final AtomicLong serializationFailureCount = new AtomicLong();

    /**
     * Spring 컨텍스트 부팅 직후 Redis pattern subscribe 시작. 다른 노드의 publish 메시지를 수신하면
     * 자기 노드 RealtimeBroker.publishLocal 호출.
     */
    @PostConstruct
    public void start() {
        listenerContainer.addMessageListener(new RedisListener(), new PatternTopic(TOPIC_PATTERN));
        log.info("[PR-H4a] RedisRealtimeBroker 활성 — topic pattern={}", TOPIC_PATTERN);
    }

    /**
     * cross-node 전파 — JSON 직렬화 후 Redis pub. 직렬화 실패 시 warning + count++ (자기 노드는
     * 이미 publishLocal 처리됨 — fail-open).
     */
    @Override
    public void propagate(UUID entityId, String eventName, Object payload) {
        propagateCount.incrementAndGet();
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("entityId", entityId.toString());
            envelope.put("eventName", eventName);
            envelope.put("data", payload);
            String json = objectMapper.writeValueAsString(envelope);
            redisTemplate.convertAndSend(TOPIC_PREFIX + entityId, json);
        } catch (JsonProcessingException ex) {
            serializationFailureCount.incrementAndGet();
            log.warn("[PR-H4a] Redis propagate JSON 직렬화 실패 — entityId={} event={} cause={}",
                    entityId, eventName, ex.getMessage());
        } catch (RuntimeException ex) {
            log.warn("[PR-H4a] Redis convertAndSend 실패 — entityId={} event={} cause={}",
                    entityId, eventName, ex.getMessage());
        }
    }

    /** 누적 propagate 시도 횟수. */
    public long propagateCount() {
        return propagateCount.get();
    }

    /** 누적 Redis 수신 메시지 횟수. */
    public long receiveCount() {
        return receiveCount.get();
    }

    /** 누적 직렬화 실패 횟수. */
    public long serializationFailureCount() {
        return serializationFailureCount.get();
    }

    /**
     * Redis pattern subscriber — 다른 노드에서 발행된 메시지 수신 시 자기 노드 broker.publishLocal
     * 호출. payload 는 본 broker 가 publish 시 emitter.send 로 그대로 전달되도록 Map 구조 유지
     * (Jackson 자동 재직렬화).
     */
    private final class RedisListener implements MessageListener {
        @Override
        public void onMessage(Message message, byte[] pattern) {
            receiveCount.incrementAndGet();
            String json = new String(message.getBody(), StandardCharsets.UTF_8);
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> envelope = objectMapper.readValue(json, Map.class);
                String entityIdStr = (String) envelope.get("entityId");
                String eventName = (String) envelope.get("eventName");
                Object payload = envelope.get("data");
                if (entityIdStr == null || eventName == null) {
                    log.warn("[PR-H4a] Redis 메시지 schema 위반 (entityId/eventName 누락): {}", json);
                    return;
                }
                // publishLocal 호출 — publish 호출 시 다시 Redis 로 전파되어 infinite loop 발생.
                localBroker.publishLocal(UUID.fromString(entityIdStr), eventName, payload);
            } catch (Exception ex) {
                log.warn("[PR-H4a] Redis 메시지 처리 실패 — body={} cause={}", json, ex.getMessage());
            }
        }
    }
}
