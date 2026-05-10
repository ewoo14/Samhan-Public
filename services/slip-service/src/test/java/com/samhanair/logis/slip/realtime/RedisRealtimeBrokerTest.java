package com.samhanair.logis.slip.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * PR-H2 BE — TM 보완 #3: RedisRealtimeBroker 단위 (3 case, Redis testcontainer 없이 mock).
 *
 * <ol>
 *   <li>propagate — Redis pub 호출 + JSON envelope schema (slipId/eventName/data)</li>
 *   <li>onMessage — Redis 수신 시 SlipRealtimeBroker.publishLocal 호출 (publish 가 아닌 — loop 방지)</li>
 *   <li>onMessage — schema 위반 메시지 graceful skip (publishLocal 미호출)</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class RedisRealtimeBrokerTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private RedisMessageListenerContainer listenerContainer;
    @Mock private SlipRealtimeBroker localBroker;

    private RedisRealtimeBroker broker;

    @BeforeEach
    void setUp() {
        broker = new RedisRealtimeBroker(redisTemplate, listenerContainer, localBroker,
                new ObjectMapper());
    }

    @Test
    void propagate_callsRedisConvertAndSendWithEnvelopeJson() {
        UUID slipId = UUID.randomUUID();
        broker.propagate(slipId, "slip:edit", Map.of("revisionNo", 5, "actorName", "홍길동"));

        // topic = slip:realtime:{slipId}
        verify(redisTemplate, times(1)).convertAndSend(
                eq(RedisRealtimeBroker.TOPIC_PREFIX + slipId),
                contains("\"slipId\":\"" + slipId + "\""));
        verify(redisTemplate, times(1)).convertAndSend(
                eq(RedisRealtimeBroker.TOPIC_PREFIX + slipId),
                contains("\"eventName\":\"slip:edit\""));
        assertThat(broker.propagateCount()).isEqualTo(1);
    }

    @Test
    void onMessage_validEnvelope_callsPublishLocalNotPublish() throws Exception {
        // listener 직접 호출 — listenerContainer 의 onMessage 시뮬레이션
        UUID slipId = UUID.randomUUID();
        String json = new ObjectMapper().writeValueAsString(Map.of(
                "slipId", slipId.toString(),
                "eventName", "slip:edit",
                "data", Map.of("revisionNo", 7, "actorName", "관리자")));
        DefaultMessage message = new DefaultMessage(
                ("slip:realtime:" + slipId).getBytes(StandardCharsets.UTF_8),
                json.getBytes(StandardCharsets.UTF_8));

        invokeListener(broker, message);

        verify(localBroker, times(1))
                .publishLocal(eq(slipId), eq("slip:edit"), any());
        // publish (cross-node 재전파) 미호출 확인 — loop 방지 가드
        verify(localBroker, times(0)).publish(any(), any(), any());
        assertThat(broker.receiveCount()).isEqualTo(1);
    }

    @Test
    void onMessage_schemaViolation_gracefulSkipNoPublishLocal() throws Exception {
        // slipId/eventName 누락
        String malformed = "{\"data\":{\"foo\":\"bar\"}}";
        DefaultMessage message = new DefaultMessage(
                "slip:realtime:any".getBytes(StandardCharsets.UTF_8),
                malformed.getBytes(StandardCharsets.UTF_8));

        invokeListener(broker, message);

        verify(localBroker, times(0)).publishLocal(any(), any(), any());
        assertThat(broker.receiveCount()).isEqualTo(1);
    }

    /** RedisListener 는 inner class — 본 helper 가 reflection 으로 직접 onMessage 호출. */
    private static void invokeListener(RedisRealtimeBroker broker, DefaultMessage msg)
            throws Exception {
        Class<?> listenerClazz = Class.forName(
                RedisRealtimeBroker.class.getName() + "$RedisListener");
        java.lang.reflect.Constructor<?> ctor = listenerClazz.getDeclaredConstructors()[0];
        ctor.setAccessible(true);
        Object listener = ctor.newInstance(broker);
        java.lang.reflect.Method onMessage = listenerClazz.getDeclaredMethod(
                "onMessage", org.springframework.data.redis.connection.Message.class, byte[].class);
        onMessage.setAccessible(true);
        onMessage.invoke(listener, msg, null);
    }
}
