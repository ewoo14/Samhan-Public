package com.samhanair.logis.userclient;

import lombok.Data;

/**
 * UserVerifier 공통 설정 — Phase 9 W4 (W3 backlog #1 채택).
 *
 * <p>각 service 가 자체 {@code @ConfigurationProperties} 로 binding 후 본 객체를
 * {@link DefaultUserVerifier} 생성자에 주입. 본 모듈 자체는 Spring config 미보유
 * (소비자 책임 — 단순 POJO).
 *
 * <ul>
 *   <li>{@code baseUrl} — user-service base (예: {@code http://localhost:8083})</li>
 *   <li>{@code internalToken} — user-service Internal API X-Internal-Token</li>
 *   <li>{@code ttlSeconds} — Caffeine TTL (기본 60)</li>
 *   <li>{@code maxSize} — Caffeine maximumSize (기본 10000)</li>
 *   <li>{@code failFast} — Phase 10 cutover 시점 strict 모드 토글 (기본 false = fail-soft)</li>
 * </ul>
 */
@Data
public class UserVerifierProperties {

    private String baseUrl = "http://localhost:8083";
    private String internalToken = "";
    private long ttlSeconds = 60L;
    private long maxSize = 10000L;
    private boolean failFast = false;
}
