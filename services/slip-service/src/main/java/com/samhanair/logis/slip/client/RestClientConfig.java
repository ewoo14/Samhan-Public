package com.samhanair.logis.slip.client;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Exposes a {@link RestClient.Builder} with Spring Cloud LoadBalancer integration so
 * {@code lb://service-name} URIs are resolved through Eureka.
 * Consumers: {@link ProductClient}, {@link InventoryClient}.
 */
@Configuration
public class RestClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }
}
