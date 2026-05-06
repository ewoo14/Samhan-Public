package com.samhanair.logis.notification.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * UserClient 와 AligoSmsAdapter 가 사용하는 {@link RestClient.Builder} bean — 외부 service /
 * 외부 API 호출용. baseUrl 은 호출 측에서 직접 부여 (multi-target 재사용).
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
