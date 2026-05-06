package com.samhanair.logis.userclient;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * user-service 호출 표준 abstraction — Phase 9 W4 신규.
 *
 * <p>Phase 9 W3 BE backlog #1 채택. notification-service / groupware-service / dashboard-service
 * 의 중복 {@code UserClient} 구현 (Caffeine cache + bulk verify) 을 단일 추상화로 통합.
 *
 * <p>2 operation:
 * <ol>
 *   <li>{@link #exists(UUID)} — 단건 검증 (cache hit 시 RPC skip)</li>
 *   <li>{@link #verifyBulk(List)} — 다건 검증 (cache hit 후 미스 ID 만 1회 bulk RPC)</li>
 * </ol>
 *
 * <p>실패 정책 (skeleton 단계 fail-soft) — 네트워크 / discovery 실패 시 검증 통과 (true) 반환.
 * Phase 10 cutover 시점에 {@code samhan.user-client.fail-fast=true} 토글로 strict 모드 활성
 * (W3 backlog #2 추적).
 *
 * @see DefaultUserVerifier 운영 impl (RestClient + Caffeine)
 */
public interface UserVerifier {

    /**
     * 사용자 단건 존재 검증.
     *
     * @param userId user UUID (null → false)
     * @return 존재 시 {@code true}, 404 시 {@code false}, 네트워크 실패 시 fail-soft true
     */
    boolean exists(UUID userId);

    /**
     * 사용자 다건 존재 검증 — fan-out 직렬 RPC 회피.
     *
     * @param userIds user UUID 목록 (null / empty → 빈 결과)
     * @return userId → exists 매핑 (모든 입력 포함, 누락은 false 또는 fail-soft true)
     */
    Map<UUID, Boolean> verifyBulk(List<UUID> userIds);

    /** 단위 테스트용 — cache 명시 invalidate. */
    void invalidateCache();
}
