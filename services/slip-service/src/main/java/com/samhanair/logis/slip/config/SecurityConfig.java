package com.samhanair.logis.slip.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless servlet security: trusts gateway-injected X-User-* headers.
 *
 * <p>Slice B (notification-slice-B): {@code /public/**} 는 인증 우회 (no auth) — 공개 모바일
 * endpoint 가 토큰만 검증한다. API Gateway 의 {@code /api/public/**} 라우트도 동일하게
 * JwtAuthentication 필터 미적용 (Plan §4.2 + §8).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final InternalAuthProperties internalAuthProperties;

    public SecurityConfig(InternalAuthProperties internalAuthProperties) {
        this.internalAuthProperties = internalAuthProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // Slice B (notification-slice-B): 공개 모바일 endpoint — 토큰만 검증
                        .requestMatchers("/public/**").permitAll()
                        .anyRequest().authenticated())
                // W10-4 (PR #99): InternalTokenFilter 등록 — /internal/** 한정 X-Internal-Token 인증
                .addFilterBefore(new InternalTokenFilter(internalAuthProperties),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new HeaderAuthenticationFilter(), InternalTokenFilter.class);
        return http.build();
    }
}
