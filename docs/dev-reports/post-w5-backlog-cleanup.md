# post-W5 backlog cleanup — Phase 10 위임 backlog 중 즉시 처리 가능 7건 본 PR 채택

> 본 dev-report 는 Phase 9 W5 (PR #95) 머지 직후 (`2d07c12`) 시점의 backlog 정리 결과를 종합한다. 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지") 일관 적용으로 Phase 10 위임 backlog 중 환경 의존이 없는 7건을 본 PR 에 채택했다.

---

## 1. 배경

Phase 9 5/5 슬라이스 완료 (PR #91 ~ #95) 후 W5 회고 문서 (`docs/dev-reports/phase9-retrospective.md`) 의 § 8 잔존 backlog 매트릭스를 재검토한 결과, 다음 분류가 도출되었다:

| 분류 | 항목 | 본 PR 처리 |
|---|---|---|
| 환경 의존 (AWS / Redis / Aurora) | Phase 10 P10-1 ~ P10-3 슬라이스 본격 작업 | Phase 10 위임 (변경 없음) |
| 환경 의존 X, 즉시 처리 가능 | retry / payload size / fail-mode alias / Micrometer counter / Employee 주석 / design-system tokens / PR template wrapper | **본 PR 7건 일괄 채택 (D-P9-21)** |
| Phase 10 W2 Resilience4j 통합 시점 | `partner_client_fail_total` 같은 추가 Micrometer counter | Phase 10 W2 위임 |

사용자 가드 (`feedback_integrated_pr_pattern.md`) 가 명시적으로 단편 fix 후속 PR / Phase 위임 시 backlog 누적을 금지하므로, 즉시 처리 가능 7건을 본 PR 에 채택. Phase 10 진입 시점 backlog 누적 0 보장.

---

## 2. 채택 7건 매트릭스

| # | 영역 | 출처 | 산출 | IT 추가 |
|---|---|---|---|---|
| 1 | design-system PR template | Designer D-W4-3 보강 | QA HTML mobile responsive table wrapper (`.qa-table-wrapper` + `@media max-width 768px`) | 0 (template) |
| 2 | design-system tokens | Designer D-W5-2 채택 | slice accent 3색 토큰 (`--color-slice-{success,pending,deferred}` Google Material Green/Yellow/Gray) + utility class | 0 (CSS only) |
| 3 | notification-service | QA Q-W3-1 채택 | retry max-attempts property + DEAD_LETTER 영구 FAILED 처리 | 1 — `requeueForRetry_exceedsMaxAttempts_marksFailedPermanent` |
| 4 | notification-service | QA Q-W3-2 채택 | `NotificationSendRequest.payload` `@Size(max=4000)` (Postgres TOAST 임계 회피) | 1 — `send_payloadOver4000Bytes_returns400` |
| 5 | shared:user-client-abstraction | QA Q-W3-3 채택 | `UserVerifierProperties.FailMode` enum (OPEN/STRICT) alias + 양방향 자동 동기화 | 2 — `verify_strictMode_failFast_returnsFalseOnGatewayError` / `verify_openMode_failSoft_returnsTrueOnGatewayError` |
| 6 | notification-service | DevOps backlog 채택 | `NotificationGatewayMetrics` 신규 (3 channel × 2 result = 6 Micrometer counter) — `notification_gateway_send_total{channel,result}` actuator/prometheus 노출 | 2 (단위) — `send_recordsSuccessCounter` / `send_recordsFailureCounter` |
| 7 | user-service | DevOps backlog 채택 | `Employee.DEFAULT_HIRE_DATE = 2026-01-01` 의도 주석 + 한국어 Javadoc — W4 slip-service 시간 의존 회귀 학습 적용 | 0 (주석만, 코드 동작 변경 0) |

---

## 3. IT 추가 5건 (운영 진입 직전 보강)

### 3-1. `requeueForRetry_exceedsMaxAttempts_marksFailedPermanent` (Q-W3-1)

```
maxRetryAttempts=5 + attemptCount=6 fixture
→ retry() 시점에 게이트웨이 호출 skip
→ markFailed(false) 영구 FAILED
→ log FAILURE_MAX_ATTEMPTS_EXCEEDED 기록 (deadLetter:true)
```

### 3-2. `send_payloadOver4000Bytes_returns400` (Q-W3-2)

```
4001 byte 이상 JSON payload 입력
→ @Size(max=4000) bean validation 위반
→ MethodArgumentNotValidException → ExceptionHandler
→ 400 INVALID_INPUT 반환
```

### 3-3. `verify_strictMode_failFast_returnsFalseOnGatewayError` + `verify_openMode_failSoft_returnsTrueOnGatewayError` (Q-W3-3)

```
setFailMode(STRICT) → setFailFast(true) 자동 동기화 검증
setFailMode(OPEN) → setFailFast(false) 자동 동기화 검증
gateway 실패 시 STRICT=false / OPEN=true (기존 동작 1:1 보존)
```

### 3-4. `NotificationGatewayMetricsTest` 2 case (DevOps)

```
SimpleMeterRegistry in-memory 검증
recordSuccess(PUSH) → counter PUSH/success = 1.0
recordFailure(SMS) × 3 → counter SMS/failure = 3.0
EMAIL/failure = 0.0 (다른 채널 격리 검증)
```

---

## 4. 회귀 검증 (4 service + 1 shared module)

| 영역 | 결과 |
|---|---|
| `:shared:user-client-abstraction:test` | PASS (8 case, fail-mode IT 2건 추가) |
| `:services:notification-service:test` | PASS (NotificationServiceTest 7 case + NotificationGatewayMetricsTest 2 case + 기존 IT) |
| `:services:user-service:test` | PASS (영향 0 — 주석 + 상수 추가만) |
| `:services:groupware-service:test` | PASS (UserClient fail-mode 회귀 0) |
| `:services:dashboard-service:test` | PASS (UserClient fail-mode 회귀 0) |

회귀 위험 = 0. 모든 변경은 옵션 토글 (default 보존) + 신규 IT 추가 + 신규 컴포넌트 (Micrometer counter — nullable 주입) 패턴.

---

## 5. Phase 10 위임 잔존 backlog (환경 의존 항목만)

| 항목 | 시점 | 비고 |
|---|---|---|
| ChannelBadge 일관성 | Phase 10 W1 | Designer #1 (W6 client 통합 슬라이스 시점) |
| skeleton-mode IT sweep | Phase 10 P10-1 | QA Q-P10-1 (Caffeine→Redis testcontainer / aws-cloud-map mock) |
| `partner_client_fail_total` Micrometer counter | Phase 10 W2 | DevOps — Resilience4j `@CircuitBreaker` 통합 시점 |
| Secrets Manager + Cache 전환 | Phase 10 P10-1 | AWS account 발급 후 |
| Discovery + Resilience | Phase 10 P10-2 | AWS Cloud Map namespace 사전 등록 |
| RDS migration + Cutover | Phase 10 P10-3 | Aurora cluster 사전 생성 |
| 정식 React `<SliceAccent>` 컴포넌트 | W6 client 통합 | utility class 는 본 PR 채택, 정식 컴포넌트 후속 |

---

## 6. 가드 일관 적용

- **사용자 가드** (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지") — 7건 본 PR 일괄 채택
- **회귀 안전성** — 모든 변경은 옵션 토글 default 보존 + 신규 IT 추가 패턴 (기존 IT 영향 0)
- **slip-service 시간 의존 회피 일관 검토** — Employee.DEFAULT_HIRE_DATE 는 **fixture 회귀 패턴 0건 검증** (W4 학습 trigger 패턴). `LocalDateTime.now().isAfter(tokenExpiresAt)` 도메인 의도 비교는 `services/slip-service/.../domain/Slip.java:713` + `DeliveryBatch.java:195` 2건 정상 (production 만료 검증 로직, 테스트는 동적 fixture `LocalDateTime.now().minusHours(1)` + `ReflectionTestUtils.setField` 적용). Employee 는 단순 fixture default `LocalDate.of(2026,1,1)` 만 — slip-service 회고 패턴 (시간 흐름에 따른 회귀) 영향 없음 검증
- **한국어 commit + Javadoc + dev-report** — 일관 적용
- **InternalTokenFilter `/internal/**` prefix 한정** — 변경 없음 (기존 가드 보존)
- **GitGuardian** — 모든 시크릿 `CHANGE_ME_LOCAL_ONLY` 보존 (env-template 신규 entry 도 동일)
- **PR 본문 가드** — "개발책임자" 단어 + 변형 (결정/명시/요청/승인) 0회

---

## 7. 후속 단계 (Phase 10 진입 P10-1)

본 PR 머지 후:

1. AWS account + IAM baseline 정의 (사용자 결정 시점)
2. Secrets Manager 인스턴스 + ElastiCache (Redis) cluster 사전 준비
3. Phase 10 P10-1 슬라이스 진입 — `samhan.user-client.fail-mode=STRICT` 전환 + `samhan.cache.provider=redis` 전환 + Secrets Manager `spring.config.import` 도입
4. 14 service `application.yml` 일괄 갱신 + env-template 14건 갱신
5. dev-report + DECISIONS D-P10-01 ~ D-P10-05

---

## 8. 연관

- **출처 PR**: PR #95 (Phase 9 5차 W5, `2d07c12`) — 회고 + Phase 10 plan + 잔존 backlog 1건 흡수
- **DECISIONS**: D-P9-11 보강 (fail-mode 토글) + D-P9-21 신규 (post-W5 backlog cleanup, 7건 채택)
- **Phase 10 readiness**: `docs/migration/phase10/M-PHASE-10-readiness.md` § 5-1-1 흡수 backlog 갱신 (본 PR 채택 7건 제외 후 잔존 = 환경 의존 항목만)
- **사용자 가드**: `feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지"
- **W4 slip-service 시간 의존 학습**: `cde6db9` (PR #94 후속 fix) — DEFAULT_HIRE_DATE 의도 주석에 학습 적용

---

## 9. 종합 TM fix 8건 (5 reviewer 토론 종합, 사용자 가드 일관 적용)

PR #96 발행 후 5 reviewer (BE / FE / Designer / QA / DevOps) 토론을 종합하여 8건을 본 PR 에 추가 채택. 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지", 2026-05-07) 일관 적용 — 8 fix 즉시 본 PR commit, W6 / Phase 10 위임 거의 0.

| # | reviewer | 분류 | 산출 |
|---|---|---|---|
| FE-1 | FE | slice-accent CSS variable 일관 | `--badge-radius` (4px) + `--badge-channel-font-size` (12px) 변수 인용. `b-channel-*` 와 `slice-accent-*` 양쪽 동일 token (`var(--badge-radius, 4px)` / `var(--badge-channel-font-size, 12px)`). `.slice-accent-sm` modifier 추가 (11px). |
| FE-2 | FE | qa-table-wrapper 3단계 변수 | `--qa-table-min-width-{sm,md,lg}` 600/800/1000px. PR-template-color-reference.md § 5.2 컬럼 수별 가이드 1줄 추가 (4 이하 / 5~6 / 7 이상). |
| BE-1 | BE | payload byte 검증 | `NotificationSendRequest.isPayloadByteSizeValid()` `@AssertTrue` — UTF-8 byte length ≤ 4000 검증. multi-byte 문자 (한국어 / 이모지) 정합 보장. 기존 `@Size(max=4000)` (char length) 보존 + 추가. |
| BE-2 | BE | DEAD_LETTER metrics | `NotificationService.retry()` DEAD_LETTER 분기 `gatewayMetrics.recordFailure(channel)` 호출. Grafana `notification_gateway_send_total{result="failure"}` 가시화. |
| BE-3 | BE | OrgChartSeeder DRY | `LocalDate.of(2026,1,1)` 중복 상수 제거 + `Employee.DEFAULT_HIRE_DATE` 인용 (단일 출처). |
| QA-1 | QA | IT fixture 압축 | `send_payloadOver4000Bytes_returns400` 의 4001 byte fixture 빌드 로직 → `"a".repeat(4001)` 1줄 (dead code 제거, ASCII 1 byte/char 정합). |
| QA-2 | QA | timeout 명시 (WireMock 대안) | `UserVerifierProperties.connectTimeoutMs` (1000ms) + `readTimeoutMs` (5000ms) 추가. `DefaultUserVerifier.buildClient()` 에 `SimpleClientHttpRequestFactory` timeout 적용. 테스트는 100ms/200ms 명시 — 가용 X 포트 호출 시 OS 기본 timeout (Linux ~ 75s, Windows ~ 21s) 회귀 회피. WireMock 의존성 추가 대안 보다 가벼움 + production fail-fast 효과. |
| QA-3 | QA | 문서 정합 | dev-report § 6 + DECISIONS D-P9-21 + Employee Javadoc 모두 "만료 비교 패턴 부재" → "fixture 회귀 패턴 0 + 도메인 의도 비교 (`Slip.java:713` + `DeliveryBatch.java:195`) 2건 정상" 정정. |

### 9-1. 종합 fix 회귀 검증

```
:shared:user-client-abstraction:test  PASS (DefaultUserVerifierTest 8 case + connectTimeout 명시)
:services:notification-service:test   PASS (NotificationServiceTest + NotificationGatewayMetricsTest + IT fixture 압축)
:services:user-service:test           PASS (OrgChartSeeder DRY 정합 후 회귀 0)
```

### 9-2. 종합 fix 잔존 위임 (W6 / Phase 10)

본 PR 채택 후 W6 / Phase 10 잔존 위임 항목은 환경 의존 / 본격 컴포넌트화 / 운영 첫 주차 학습 의존 항목만:

- W6 client 통합 슬라이스: 정식 React `<SliceAccent>` 컴포넌트 + Storybook
- Phase 10 W2 Resilience4j 통합 시점: `partner_client_fail_total` Micrometer counter
- Phase 10 P10-1 운영 첫 주차 후: `recipient_type` tag 추가
- Phase 10 user-service 화면 슬라이스: Employee.DEFAULT_HIRE_DATE 호출자 입력 화면

---

## 마무리

Phase 9 = 완료 + post-W5 cleanup 완료 상태로 종료. Phase 10 진입 시점 backlog 누적 0. 사용자 가드 정착 + 통합 PR 패턴 일관 적용 + 회귀 0 — Phase 10 P10-1 슬라이스 진입 직전 baseline 안정화. PR #96 = 1차 채택 7건 + 종합 TM 채택 8건 = **합계 15건 본 PR 일괄 처리**.
