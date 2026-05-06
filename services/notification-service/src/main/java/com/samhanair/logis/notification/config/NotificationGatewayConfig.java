package com.samhanair.logis.notification.config;

import com.samhanair.logis.notification.adapter.NotificationGateway;
import com.samhanair.logis.notification.domain.NotificationChannel;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 채널 → 어댑터 strategy 매핑 — Spring 이 발견한 모든 {@link NotificationGateway} bean 을
 * channel() 키로 묶어 EnumMap 으로 노출.
 *
 * <p>service 레이어가 {@code GatewayRouter#route(channel)} 로 lookup → 1회 호출.
 * 같은 채널의 어댑터 다수 등록 시 마지막 발견 어댑터 우선 (정상 환경에서는 채널당 1개 운영 어댑터만 등록).
 */
@Configuration
public class NotificationGatewayConfig {

    @Bean
    public Map<NotificationChannel, NotificationGateway> notificationGatewayMap(List<NotificationGateway> gateways) {
        Map<NotificationChannel, NotificationGateway> map = new EnumMap<>(NotificationChannel.class);
        for (NotificationGateway g : gateways) {
            map.put(g.channel(), g);
        }
        return map;
    }
}
