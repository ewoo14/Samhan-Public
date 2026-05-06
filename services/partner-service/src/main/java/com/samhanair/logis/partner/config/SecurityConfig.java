package com.samhanair.logis.partner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless servlet security. 두 종류의 인증 진입점:
 * <ul>
 *   <li>{@link InternalTokenFilter} — X-Internal-Token 헤더 (slip-service / 운영 admin)</li>
 *   <li>{@link HeaderAuthenticationFilter} — X-User-Id / X-User-Role (gateway 경유 일반 사용자)</li>
 * </ul>
 *
 * <p>모든 endpoint 는 인증 필수 (actuator + swagger 제외). {@code /internal/**} 는
 * X-Internal-Token 으로, {@code /admin/**} 는 X-User-* + {@code @PreAuthorize} 로 통과.
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
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalTokenFilter(internalAuthProperties),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new HeaderAuthenticationFilter(), InternalTokenFilter.class);
        return http.build();
    }
}
