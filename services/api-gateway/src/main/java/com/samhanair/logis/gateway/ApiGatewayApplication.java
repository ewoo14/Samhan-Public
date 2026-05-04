package com.samhanair.logis.gateway;

import com.samhanair.logis.gateway.config.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Edge router for the SamhanLogis MSA.
 *
 * <p>Registers with Eureka, routes traffic to downstream services, and
 * enforces JWT authentication via {@code JwtAuthenticationGatewayFilterFactory}.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
