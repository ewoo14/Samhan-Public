package com.samhanair.logis.arologis.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 4 외부 client (PartnerClient / UserClient / SlipClient / NotificationClient) 가
 * 사용하는 {@link RestClient.Builder} bean — multi-target 재사용.
 *
 * <p>Phase 10 W10-1 — ServiceDiscoveryClient 5번째 소비자 (partner / groupware / notification /
 * dashboard → arologis).
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
