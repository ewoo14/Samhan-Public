# Phase 9 W3 — notification-service skeleton dev-report

## 1. 슬라이스 개요

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 9 W3 (3차) |
| Service | notification-service |
| Port | 8093 |
| DB | notification_db (service-per-DB) |
| Branch | `feature/integrated-phase-9-step-3-notification-service` |
| Base | `main` (HEAD `003800f` Merge PR #92) |
| 외부 의존 | user-service (UserClient, ServiceDiscoveryClient 세 번째 소비자) |

## 2. 도메인 모델 (2 entity + 3 enum)

### 2-1. NotificationRequest

발송 요청 1건. 채널 / 수신자 / 본문 / 상태 / payload (JSONB) / attempt_count.

라이프사이클:
- `open()` — status=PENDING, attempt=0
- `markSent()` — status=SENT, attempt+=1
- `markFailed(retryable)` — status=RETRYING(true) / FAILED(false), attempt+=1
- `requeueForRetry()` — FAILED/RETRYING → RETRYING 명시 전이 (admin retry)

### 2-2. NotificationLog

발송 이력 (1 request : N attempt). attempt_no / gateway_status / gateway_message_id / gateway_response / sent_at.

### 2-3. Enum

- `NotificationChannel`: PUSH / EMAIL / SMS
- `NotificationStatus`: PENDING / SENT / FAILED / RETRYING
- `RecipientType`: USER / PARTNER / EXTERNAL_PHONE

## 3. REST API

### Internal (X-Internal-Token + ROLE_MASTER)

```
POST /internal/notifications/send         → 201 NotificationAdminResponse
GET  /internal/notifications/{id}/status  → 200 NotificationStatusResponse
```

### Admin (X-User-Role + ROLE_MANAGER 이상)

```
POST /admin/notifications/send                 → 201
GET  /admin/notifications?channel&status       → 200 (페이지)
GET  /admin/notifications/{id}                 → 200 / 404
POST /admin/notifications/{id}/retry           → 200 / 404 / 409
```

## 4. Adapter (3 channel + strategy)

| Channel | 운영 어댑터 | test 어댑터 |
|---|---|---|
| PUSH | `FcmPushAdapter` (placeholder, credentials missing → stub-success) | `MockPushAdapter` |
| EMAIL | `SesEmailAdapter` (Phase 10 cutover 시 활성) | `MockEmailAdapter` |
| SMS | `AligoSmsAdapter` (apis.aligo.in/send/ form-urlencoded) | `MockSmsAdapter` |

`NotificationGateway` 공통 인터페이스 + `NotificationGatewayConfig` 가 Spring 발견 bean 을 EnumMap 으로 라우팅. service 레이어 1회 lookup → 호출 → result 적재.

Aligo 흡수 — `slip-service.delivery.sms.AligoSmsGateway` 의 form-urlencoded 호출 모델 (key/user_id/sender/receiver/msg/testmode_yn) 동일 적용. 응답 `result_code == 1` 만 success.

## 5. UserClient bulk verify (BE backlog #4 채택)

PR #92 BE Reviewer 가 식별한 후속 backlog #4 (ApprovalLine N 결재자 fan-out 직렬 RPC) 를 W3 시점에 처리:

- `UserClient.verifyBulk(List<UUID>)` — Caffeine cache hit + 1회 bulk RPC
- user-service 신규 `POST /internal/users/verify-bulk` (Repository.findAllByIdIn 활용)
- groupware-service `ApprovalLineService.create` 도 bulk 1회 호출로 전환 — 본 PR 통합 적용
- Caffeine TTL 60초 / max 10000 entries

영향 file 5 (notification-service / groupware-service / user-service / user-service web/dto / config). 본 PR 통합 일관 적용으로 향후 W4/W5 시점 부하 누적 방지.

## 6. 테스트 (12 단위 PASS + 9 IT skip / Linux CI PASS 예정)

| Test | 케이스 |
|---|---|
| `NotificationGatewayTest` | 3 |
| `NotificationServiceTest` | 6 |
| `UserClientBulkVerifyTest` | 3 |
| `NotificationInternalControllerIT` | 4 |
| `NotificationAdminControllerIT` | 5 |

본 worktree 에서 Docker 미가용 — IT 9 case skip 정책으로 gradle test PASS. CI Linux runner 에서 IT 21 case 모두 실행 + PASS.

## 7. 환경변수 / 인프라 영향

- `infrastructure/env-templates/notification-service.env` 신규
- `infrastructure/postgres/init/01-create-databases.sql` `notification_db` 추가
- `infrastructure/prometheus/prometheus.yml` `notification-service:8093` + `groupware-service:8092` (DevOps Follow-up #11/#12 채택)

chained-default 표준: `SAMHAN_NOTIFICATION_*` 우선 / `LEGACY_*` legacy fallback / `CHANGE_ME_LOCAL_ONLY` placeholder (GitGuardian 통과).

## 8. 가드 일관 적용

- BaseEntity 7 audit + Soft Delete (`@SQLRestriction`)
- VARCHAR(N) only (CHAR 금지) + JSONB (Postgres standard, RDS 호환)
- UUID 비공개 — 응답 DTO 는 admin / 형제 service 한정
- 한국어 Javadoc + dev-report (본 문서) + springdoc-openapi
- IT 외부 client `@MockBean` 격리 + lenient setup
- AbstractPostgresIT + Docker skip
- 한글 path 트랩 회피 (assemble + 단위 테스트 PASS)
- gradlew exec bit 보존
- InternalTokenFilter `/internal/**` prefix 한정 (PR #91 fix 일관)
- prod + dev 기본 토큰 부팅 거부 가드

## § 후속 backlog

- **Designer #10 권고 채택** — `3-api-endpoints-summary.html` 의 슬레이트 팔레트 + method 컬러 (GET=#0f9d58 / POST=#1a73e8 / PUT=#f9ab00 / DELETE=#d93025) + badge 토큰 (b-ok/b-warn/b-info) + .qa-grid 2-column 모두 PR #92 (W2) 와 1:1 복제 적용.
- **DevOps Follow-up #11/#12 통합 흡수** — postgres init `notification_db` + prometheus `notification-service:8093` / `groupware-service:8092` 본 PR 동시 반영.
- **Phase 10 cutover 진입 사항**: FCM SDK 통합 / SES SDK 통합 / Aligo 운영 secrets / aws-cloud-map provider toggle / UserClient fail-fast 정책.
- **W4 dashboard-service 진입 시점 회고**: bulk verify 패턴이 partner / inventory / accounting 다중 client 통합 패턴으로 일반화 가능 — TM 검토.

### § 후속 backlog 종합 매트릭스 (5 reviewer 토론 — PR #93)

본 PR 머지 영향 0. 위임 시점에 각 항목 처리. 5 reviewer (BE / FE / Designer / QA / DevOps) 모두 머지 동의 + 채택 fix 0건 (minimal-change 원칙, PR #92 패턴 학습).

| # | 카테고리 | 항목 | 위임 시점 | 출처 |
|---|---|---|---|---|
| 1 | shared module | UserClient cache 일관성 — groupware 측 Caffeine 미적용 → `shared:user-client-abstraction` 모듈 통합 | W4 | BE |
| 2 | 정책 | UserClient.verifyBulk fail-fast 토글 (`samhan.user-client.fail-fast=true`) + DECISIONS D-P9-11 보강 | Phase 10 cutover | BE |
| 3 | 자동화 | NotificationGatewayResult.failure → 자동 재시도 큐 (현재 admin 수동 retry 의존, 5xx/timeout 도 retryable=false) | Phase 10 cutover | BE |
| 4 | 디자인 토큰 | 3 channel adapter 시각 일관성 — `.b-channel-push / .b-channel-email / .b-channel-sms` 3종 토큰 신설 + RGB 값 정의 | W4 dashboard 진입 | Designer (D-W3-1) |
| 5 | 디자인 baseline | W4+ API matrix HTML method 컬러 baseline = W3 (Google Material `#0f9d58/#1a73e8/#f9ab00/#d93025`) — PR template 색상 reference 갱신 | W4 진입 시점 | Designer |
| 6 | 보안/회로 | AligoSmsAdapter / FcmPushAdapter Resilience4j (timeout / circuit breaker / retry) | Phase 10 cutover | DevOps |
| 7 | 시크릿 | FCM credentials path → secrets manager mount 모델 표준화 | Phase 10 cutover | DevOps |
| 8 | 캐시 | Caffeine in-process cache vs Redis 공유 캐시 트레이드오프 검토 | W4 시점 | DevOps |
| 9 | 정책 | UserClient.verifyBulk fail-soft → fail-fast 전환 약속 추적 (D-P9-11 보강) | Phase 10 cutover | DevOps |
| 10 | 모니터링 | NotificationGateway adapter 별 Micrometer counter/timer 노출 | Phase 10 cutover | DevOps |
| 11 | IT 가드 | 재시도 한도 미설정 — `samhan.notification.retry.max-attempts` property + IT 1건 (`markFailed(true) → requeueForRetry() → invokeGateway` 무한 chain 가드) | Phase 10 cutover 전 | QA |
| 12 | IT 가드 | JSONB payload size 무제한 — DTO `payload` `@Size(4000)` 추가 + 4001byte 차단 IT (Postgres TOAST 임계 우회 가능) | Phase 10 cutover 전 | QA |
| 13 | 정책 IT | UserClient `fail-mode=OPEN/STRICT` property + STRICT 모드 IT 2건 (Phase 10 fail-fast 전환 시 회귀 가드) | Phase 10 cutover 전 | QA |
| 14 | 모바일 | 모바일 push 화면 — RN FCM SDK 통합 + Detox e2e 2종 (`mobile-v4/notification-push.test.ts` Android + `mobile-staff/notification-push.test.ts` iOS+APNs+permission) | W5 또는 Phase 10 client 통합 PR | FE |
| 15 | 명칭 정정 | client 영역 `notification-slice-B` ↔ 신규 `notification-service` (8093) 충돌 — slice 코드명 rename (예: `link-dispatch-slice`) + `clients/desktop/src/renderer/api/delivery.ts:4` 주석 정정 | W4 또는 다음 client 통합 PR | FE |

위임 시점 분포:
- **W4 진입**: 5건 (BE-1 / Designer-1 / Designer-2 / DevOps-3 / FE-3)
- **Phase 10 cutover 전**: 9건 (BE-2/3 / DevOps-1/2/4/5 / QA-1/2/3)
- **W5 또는 Phase 10 client 통합 PR**: 1건 (FE-2)

## 산출물 요약

- 신규 file 28 (`services/notification-service/**`)
- 갱신 file 8 (groupware-service UserClient + ApprovalLineService + IT 2 / user-service SecurityConfig + InternalTokenFilter (신규) + InternalUserController (신규) + 2 dto / settings.gradle / build.gradle / postgres init / prometheus / env-templates)
- 도메인 entity 2 / enum 3 / adapter 인터페이스 3 + impl 6 / config 9 / controller 2 / service 1 / repository 2 / dto 3 / exception 1 / Application 1
- Flyway V1 (2 테이블 + JSONB + partial unique index)
- 테스트 5 class / 21 case (12 단위 PASS + 9 IT skip → CI PASS)
