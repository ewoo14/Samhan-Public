package com.samhanair.logis.accounting.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Exposes a {@link RestClient.Builder} with Spring Cloud LoadBalancer integration so
 * {@code lb://service-name} URIs (또는 {@code http://service-name}) 가 Eureka 를 통해 해석된다.
 * Consumers: {@link SlipServiceClient}.
 *
 * <p>slip-service 답습 패턴 (services/slip-service/src/main/java/.../client/RestClientConfig.java).
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
