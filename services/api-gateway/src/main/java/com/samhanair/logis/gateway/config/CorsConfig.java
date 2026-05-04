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
 * three production sub-domains under samhan-air.com plus local-dev Vite ports
 * (3000 / 3001 / 3002 — web SPA, 5173 — electron-vite default).
 *
 * <h2>Electron 데스크톱 호환</h2>
 * <p>Electron 프로덕션 빌드는 렌더러를 file:// 또는 app:// 프로토콜로
 * 로드하기 때문에 단일 origin 문자열 매칭(allowedOrigins)으로는 잡히지 않는다.
 * 본 설정은 {@code allowedOriginPatterns} 를 함께 사용하여
 * {@code file://}, {@code app://com.samhanair.logis.desktop} 등 패턴 origin
 * 도 허용한다. {@code allowCredentials=true} 와 와일드카드 origin 은
 * 함께 쓸 수 없으므로 명시적 패턴만 등록한다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        CorsConfiguration config = new CorsConfiguration();
        // 프로덕션 웹 + 로컬 dev 웹 origin (정확 매칭)
        config.setAllowedOrigins(List.of(
                "https://app.samhan-air.com",
                "https://order.samhan-air.com",
                "https://sign.samhan-air.com",
                "http://localhost:3000",
                "http://localhost:3001",
                "http://localhost:3002",
                "http://localhost:5173"
        ));
        // Electron / 패턴 origin (file://, app://, dev 동적 포트)
        config.setAllowedOriginPatterns(List.of(
                "http://localhost:*",
                "http://127.0.0.1:*",
                "app://com.samhanair.logis.desktop",
                "app://*.samhanair.logis.desktop",
                "file://*"
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
