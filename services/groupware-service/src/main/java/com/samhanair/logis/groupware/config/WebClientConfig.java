package com.samhanair.logis.groupware.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * UserClient 가 사용하는 {@link RestClient.Builder} bean — 외부 service 호출용.
 *
 * <p>baseUrl / 헤더 전략은 호출 측 ({@code UserClient}) 에서 직접 부여한다 (multi-service
 * 호출 시 builder 재사용 가능).
 */
@Configuration
public class WebClientConfig {

    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
