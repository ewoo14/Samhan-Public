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
 *   <li>{@code failFast} — legacy 부울 토글 (기본 false = fail-soft) — {@link #failMode} 와 동기화</li>
 *   <li>{@code failMode} — post-W5 backlog cleanup (Q-W3-3, D-P9-21) — 의미 명시 alias.
 *     OPEN = fail-soft (legacy default), STRICT = fail-fast (Phase 10 cutover 활성)</li>
 * </ul>
 *
 * <p>두 토글의 우선순위: {@link #failMode} 가 명시 setter 호출되면 {@link #failFast} 자동 동기화.
 * 둘 다 미설정 시 OPEN / failFast=false (fail-soft) 기본값 — 회귀 안전.
 */
@Data
public class UserVerifierProperties {

    /**
     * fail-mode (post-W5 backlog cleanup, Q-W3-3, D-P9-21).
     *
     * <ul>
     *   <li>{@link #OPEN} — fail-soft. 네트워크 / discovery / gateway 5xx 실패 시 검증 통과 반환
     *     (skeleton 단계 default).</li>
     *   <li>{@link #STRICT} — fail-fast. 실패 시 throw 또는 false 반환. Phase 10 cutover 시점 활성.</li>
     * </ul>
     */
    public enum FailMode {
        OPEN,
        STRICT
    }

    private String baseUrl = "http://localhost:8083";
    private String internalToken = "";
    private long ttlSeconds = 60L;
    private long maxSize = 10000L;
    private boolean failFast = false;

    /**
     * fail-mode (post-W5 backlog cleanup) — 의미 명시 alias. 기본 OPEN (failFast=false 와 일관).
     * setter 호출 시 {@link #failFast} 자동 동기화 (FailMode.STRICT → failFast=true).
     */
    private FailMode failMode = FailMode.OPEN;

    /** failMode setter — failFast 자동 동기화 (post-W5 cleanup, Q-W3-3 일관). */
    public void setFailMode(FailMode failMode) {
        this.failMode = failMode == null ? FailMode.OPEN : failMode;
        this.failFast = (this.failMode == FailMode.STRICT);
    }

    /** failFast setter — failMode 자동 동기화 (legacy 호환). */
    public void setFailFast(boolean failFast) {
        this.failFast = failFast;
        this.failMode = failFast ? FailMode.STRICT : FailMode.OPEN;
    }
}
