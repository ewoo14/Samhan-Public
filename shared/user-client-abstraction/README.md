# shared:user-client-abstraction (Phase 9 W4)

> UserVerifier 표준 abstraction — `UserVerifier` interface + `DefaultUserVerifier` impl + `UserVerifierProperties`.

## 1. 도입 배경 (W3 BE backlog #1 채택, D-P9-15)

W3 reviewer 토론에서 BE 가 제기한 "notification / groupware UserClient 중복 구현 + groupware Caffeine 누락" 문제를 W4 통합 PR 시점에 abstraction 으로 통합. 동일 책임 (user verify) 의 2 service 중복 코드 + groupware 의 Caffeine 누락은 abstraction 부재의 명백한 비용 — abstraction 으로 통합하면 Phase 10 시점 fail-fast 토글 활성 + 후속 변경의 단일 진입점 확보.

## 2. 산출

| File | 설명 |
|---|---|
| `UserVerifier` | interface — `exists(UUID)` / `verifyBulk(List<UUID>)` / `invalidateCache()` |
| `DefaultUserVerifier` | RestClient + Caffeine cache (TTL 60s, max 10000) 표준 구현 |
| `UserVerifierProperties` | POJO 설정 — baseUrl / internalToken / ttlSeconds / maxSize / failFast / failMode |

## 3. 사용법

각 service 는 자체 `@ConfigurationProperties` 로 binding 후 `DefaultUserVerifier` 생성자에 주입:

```java
@Configuration
public class UserVerifierConfig {
    @Bean
    public UserVerifier userVerifier(RestClient.Builder builder, UserVerifierProperties props) {
        return new DefaultUserVerifier(builder, props);
    }
}
```

## 4. 실패 정책 (post-W5 cleanup, D-P9-11 보강 + D-P9-21)

| Mode | failFast | 동작 |
|---|---|---|
| `OPEN` | false (default) | fail-soft — 네트워크 / discovery / gateway 5xx 실패 시 검증 통과 (true) 반환 |
| `STRICT` | true | fail-fast — 실패 시 false 반환 (Phase 10 cutover 시점 활성) |

`failMode` 와 `failFast` 양방향 alias setter 패턴 — 한 쪽 변경 시 다른 쪽 자동 동기화 (legacy `failFast` 호출자 / 신규 `failMode` 호출자 모두 호환).

```java
UserVerifierProperties p = new UserVerifierProperties();
p.setFailMode(UserVerifierProperties.FailMode.STRICT);  // → failFast=true 자동 동기화
// 또는 동등
p.setFailFast(true);                                     // → failMode=STRICT 자동 동기화
```

### 환경변수 표준

각 service 가 자체 env-template 에 추가:

```
SAMHAN_USER_CLIENT_FAIL_MODE=OPEN  # OPEN(default) | STRICT (Phase 10 cutover)
```

`notification-service.env` + `groupware-service.env` 보유 (post-W5 cleanup, D-P9-21).

## 5. 테스트 (8 case)

| Test | 설명 |
|---|---|
| `exists_with_null_returns_false` | null 입력 → false 반환 |
| `verify_bulk_with_null_returns_empty` | null/empty 입력 → empty Map |
| `exists_network_failure_fail_soft_returns_true` | OPEN 모드 — 네트워크 실패 시 fail-soft true |
| `exists_network_failure_fail_fast_returns_false` | STRICT 모드 — 네트워크 실패 시 fail-fast false |
| `verify_bulk_network_failure_fail_soft_returns_true_for_all` | OPEN bulk — 네트워크 실패 시 모두 true |
| `invalidate_cache_clears_entries` | invalidateCache → cache miss → 재시도 |
| `verify_strictMode_failFast_returnsFalseOnGatewayError` | post-W5 (Q-W3-3) — STRICT setter alias 동작 |
| `verify_openMode_failSoft_returnsTrueOnGatewayError` | post-W5 (Q-W3-3) — OPEN setter alias 동작 |

## 6. 소비자 (현재 + 미래)

| Service | 의존 시작 | 비고 |
|---|---|---|
| `notification-service` | Phase 9 W4 | thin delegate (`UserClient` → `DefaultUserVerifier`) |
| `groupware-service` | Phase 9 W4 | 동일 + Caffeine 결락 통합 (W3 backlog) |
| `dashboard-service` | Phase 9 W4 | 의존성 등록 (실 사용은 Phase 10 시점) |

## 7. Phase 10 cutover 진입 사항

- `SAMHAN_USER_CLIENT_FAIL_MODE=STRICT` 일괄 전환 (P10-1 슬라이스 산출)
- `failMode` setter / `failFast` setter 양방향 alias 정착으로 환경변수 단일 표준 확보
- `STRICT` 모드 활성 후 user-service down / network partition 시 user lookup 실패 → 검증 실패 반환 (시스템 가용성 우선 → 데이터 정합성 우선 cutover)
