package com.samhanair.logis.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 4 외부 client (InventoryClient / AccountingClient / PartnerOrderClient / PartnerClient) 가
 * 사용하는 {@link RestClient.Builder} bean — multi-target 재사용 (각 client 가 baseUrl 직접 부여).
 *
 * <p>Phase 9 W4 — ServiceDiscoveryClient 네 번째 소비자 (W1 partner / W2 groupware / W3 notification → W4 dashboard).
 * Phase 10 cutover 시점에 service-name 기반 lookup 으로 전환.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
