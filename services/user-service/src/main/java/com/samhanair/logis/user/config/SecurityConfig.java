package com.samhanair.logis.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless servlet security: trusts gateway-injected X-User-* headers + Phase 9 W3 신규 InternalTokenFilter
 * ({@code /internal/**} prefix 한정, X-Internal-Token 인증).
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
                        .anyRequest().authenticated())
                .addFilterBefore(new InternalTokenFilter(internalAuthProperties),
                        UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new HeaderAuthenticationFilter(), InternalTokenFilter.class);
        return http.build();
    }
}
