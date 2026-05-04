package com.samhanair.logis.gateway.config;

import java.time.Duration;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * Global reactive CORS filter mounted on the gateway.
 *
 * <p>Exposes {@code Authorization}, {@code X-User-Id}, and
 * {@code X-User-Role} so the SPA can read identity headers that downstream
 * services attach. Allowed origins follow the project_plan §4 domain matrix:
 * three production sub-domains under samhan-air.com plus three local-dev
 * Vite ports (3000 / 3001 / 3002).
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "https://app.samhan-air.com",
                "https://order.samhan-air.com",
                "https://sign.samhan-air.com",
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:3002"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type", "X-User-Id", "X-User-Role"));
        config.setAllowCredentials(true);
        config.setMaxAge(Duration.ofSeconds(3600));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsWebFilter(source);
    }
}
