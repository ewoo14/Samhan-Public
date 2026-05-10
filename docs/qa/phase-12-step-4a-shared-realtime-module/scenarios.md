# PR-H4a — Phase 12 Step 4a `shared-realtime` 공통 모듈 + slip-service 회귀 QA 시나리오

> **branch** — `feature/integrated-phase-12-step-4a-shared-realtime-module`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 12 Step 4a PR-H4a (`services/shared-realtime` 공통 Gradle 모듈 신설 + slip-service `realtime` 패키지 추출 + slip-service / 14 service `EditRequest` 패턴 추출 위임 + Redis broker config toggle 일반화) 가 **(1) shared 모듈 단위 책임을 정확히 분리**하고 **(2) 기존 slip-service PR-H1/H2/H3 동작 100% 회귀 보존** 을 측정 가능한 PASS/FAIL 로 명세.
> **연관 산출물** —
> - BE-Module: `services/shared-realtime/build.gradle` 신규 (lombok / slf4j / spring-data-redis optional)
> - BE-Module: `services/shared-realtime/src/main/java/com/samhanair/logis/shared/realtime/SamhanRealtimeBroker.java` (interface — publish / subscribe / publishLocal / heartbeat / subscriberCount / publishCount)
> - BE-Module: `InMemorySamhanRealtimeBroker.java` (slip-service `SlipRealtimeBroker` 추출 — ConcurrentHashMap + CopyOnWriteArrayList + AtomicLong 통계)
> - BE-Module: `RedisSamhanRealtimeBroker.java` (slip-service `RedisRealtimeBroker` 추출 — channel prefix `samhan:<service>:` 자동 prepend)
> - BE-Module: `RealtimePublishHook.java` + `RealtimeBrokerAutoConfig.java` (`@ConditionalOnProperty(samhan.realtime.broker)` 자동 등록)
> - BE-Module: `services/shared-edit-request/` (PR-H3 `SlipEditRequestService` 6 책임 패턴 추출 — `EditRequestService<T>` generic + `LockGuard` policy interface — 14 도메인 일괄 적용)
> - BE-Slip: `slip-service` 의 `realtime` 패키지 → shared 모듈 의존으로 교체 (`SlipRealtimeBroker` deprecated → `SamhanRealtimeBroker` 위임 wrapper 유지 — FE/test 회귀 차단)
> - BE-Slip: `slip-service` 의 `editrequest` 패키지 → shared 모듈 generic 합류 (slip 도메인 specialization 만 잔존)
> - DevOps: `docs/devops/redis-realtime-broker.md` § 9 보강 (shared module + 14 service 단일 ElastiCache 공유 + cutover 단계적 절차)
> - Designer: `docs/uiux/phase12/H4a-shared-realtime-pattern.md` 신규 (14 service / 50+ page audit overlay 적용 가이드 + 한국어 라벨 매핑 표)
> - 본 PR 범위 = **BE-1 agent 단독 (코드)** + **본 agent (Designer + DevOps + QA docs only)** — FE 변경 0, 14 service 도입은 PR-H4b/H4c 위임

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — `feedback_role_naming_full` 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 본 PR 검증 관점 |
| --- | --- | --- | --- |
| **신입 영업** | SALES | 단가/세금 미경험 | shared 모듈 추출이 기존 SlipDetailPage UX 에 회귀 0건. memo 편집 → 1초 sync → audit overlay 표시 동일 동작 |
| **창고원** | WAREHOUSE | 출고 픽업/검수 | SlipEditRequestsScreen / SlipEditRequestsPage 의 PENDING list / 수락 / 거절 모두 동일 동작. shared 모듈 generic 으로 추출되어도 mobile-staff 30s polling + Alert.alert 변동 0건 |
| **관리자** | MANAGER | 전 도메인 | 복원 dropdown / 수정 횟수 chip / SSE 양방향 push 모두 회귀 0건. shared 모듈 audit-logs 응답 schema 1:1 동일 |
| **개발책임자 / IT 관리자** | MASTER | 전 도메인 + infra | broker 통계 (`subscriberCount`/`publishCount`/`publishFailureCount`/`heartbeatCount`) 의 actuator 노출 1:1 동일. Redis broker toggle 동일 환경변수 사용. `RedisRealtimeBrokerConcurrencyIT` 등가 IT 가 shared 모듈 측에서 PASS |
| **DevOps 엔지니어** | DEVOPS | infra 운영 | shared 모듈 의존 추가만으로 14 service broker bean 자동 등록 — service 별 코드 0 변경. cutover 단계적 절차 안전 |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소 모두 명시:

1. **선행 조건** — fixture (기존 V17/V18/V19 migration 적용 + slip-service 부팅 + shared 모듈 의존 적용)
2. **동작** — Gradle test / curl / Playwright step
3. **기대 결과** — 단위 assertion (`SamhanRealtimeBroker` interface 동작) + IT assertion (Redis testcontainer round-trip) + FE 회귀 assertion (PR-H1/H2/H3 시나리오 100% 통과)
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 PR-H4b/H4c 진입 차단 (shared module 의존 추가만으로 service 부팅 실패 / slip-service 회귀 / Redis cutover 위험)
- 🟠 **Major** — shared module 단위 책임 누락 (interface 표면 부족 / channel prefix 누락 / 자동 등록 회귀)
- 🟡 **Minor** — 문서 링크 / Javadoc 한국어 누락
- 🟢 **Info** — 향후 14 service 도입 시 권고 (PR-H4b/H4c 의존)

### 0.4 권한 매트릭스 (PR-H4a 본 PR 은 권한 변경 0)

본 PR-H4a 는 **모듈 추출만** — 권한 / endpoint / DB schema 변경 0건. 기존 PR-H1/H2/H3 권한 매트릭스 그대로:

- **`GET /slips/{id}/audit-logs`** = 인증 사용자 전체
- **`PATCH /slips/{id}/audit/overlay`** = `SALES` / `WAREHOUSE` / `MANAGER` / `MASTER`
- **`POST /slips/{id}/audit/revert/{revisionNo}`** = `MANAGER` / `MASTER`
- **`POST /slips/{id}/edit-request`** = `SALES` (작성자) / `MANAGER` / `MASTER`
- **`POST /slips/{id}/edit-request/{rid}/approve|reject`** = `WAREHOUSE` / `MANAGER` / `MASTER`

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility`)

본 PR-H4a 는 FE 변경 0 — UUID 노출 회귀 위험 없음. shared 모듈 응답 schema (`AuditLogResponse` 등) 도 PR-H2 SlipAuditLogResponse 1:1 동일 (`actorId` 는 색상 hash 입력 전용 / 화면 표시 = `actorName` 만).

---

## 1. shared 모듈 단위 시나리오 — audit (5 case)

> **모듈 위치** — `services/shared-realtime/src/main/java/com/samhanair/logis/shared/realtime/audit/` (BE-1 agent 추출 예정).
> 본 절은 PR-H2 `SlipAuditLogService` 6 case 를 generic `EditAuditService<T extends BaseEntity>` 로 추출했을 때의 단위 회귀.

### 1.1 generic record — slip 도메인 호출 회귀

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | MASTER | 🔴 | shared 모듈 의존 적용 + V18 migration 적용 + slip-001 (revision_count=0, memo="9시까지") | `SlipAuditLogService.recordOverlayPatch(slip-001, "memo", "10시 30분")` (slip 도메인 specialization 호출) | shared 모듈 generic `EditAuditService.record` 가 위임 호출 → `slip_audit_logs` 1행 INSERT (`revisionNo=1`) + `slips.revision_count=1` + `SamhanRealtimeBroker.publish(slip-001, "slip:edit", payload)` 1회 | 추출 회귀 시 PR-H2 audit 동작 전체 단절 — 운영 차단 |

### 1.2 generic recordBatch — multi-field 1 mutation

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | MANAGER | 🔴 | 1.1.1 통과 (revision_count=1) | `SlipService.editHeader(slip-001, partnerName=null, memo="X", contactPhone="010-9999-9999")` | shared 모듈 `EditAuditService.recordBatch` 가 1회 호출 → 같은 `revisionNo=2` 의 audit row 2행 INSERT + 단일 SSE broadcast (`changes` length=2) | 추출 시 grouping 회귀하면 사용자 "한 번 변경 = 1회" 직관 위배 |

### 1.3 generic listBy — repository 위임 호환성

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | MANAGER | 🟠 | 1.1.1 + 1.2.1 통과 | `GET /slips/slip-001/audit-logs` | shared 모듈 generic listBy 가 도메인 repository 의 `findBySlipIdOrderByRevisionNoDescChangedAtDesc` 를 호출 → 응답 3건 (revisionNo=2 의 2건 + revisionNo=1 의 1건) + 정렬 (DESC, DESC) | 추출 시 정렬 키 누락하면 timeline 역순 표시 |

### 1.4 generic revertToRevision — 신규 revision 발급

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4.1 | MANAGER | 🔴 | 1.3.1 통과 (revision_count=2) | `POST /slips/slip-001/audit/revert/1` | shared 모듈 generic revert → revision=1 의 oldValue 로 slip 복원 + 신규 `revisionNo=3` 발급 + 신규 audit row INSERT + SSE `slip:reverted` broadcast (`revertedFromRevisionNo=1`) | 추출 시 revert 회귀하면 PR-H2 핵심 가치 단절 |

### 1.5 generic empty changes 가드

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5.1 | MASTER | 🟠 | 적용 | `EditAuditService.recordBatch(slip-001, [])` | `BusinessException(INVALID_INPUT)` | 추출 시 가드 누락하면 빈 audit row 다수 발생 |

---

## 2. shared 모듈 단위 시나리오 — broker (6 case)

> **모듈 위치** — `services/shared-realtime/src/main/java/com/samhanair/logis/shared/realtime/broker/`
> PR-H2 `SlipRealtimeBroker` (4 case) + `SlipRealtimeBrokerConcurrencyIT` (3 case) 를 `SamhanRealtimeBroker` interface 로 추출 후 등가 시나리오.

### 2.1 interface 표면 — subscribe + publish + heartbeat 3 메서드 의무

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | MASTER | 🔴 | shared 모듈 build | `javac` compile + `interface SamhanRealtimeBroker` 표면 검사 | 5 메서드 표면 = `subscribe(UUID)` + `publish(UUID, String, Object)` + `publishLocal(UUID, String, Object)` + `heartbeat()` + `subscriberCount(UUID)` 통계 3 (`publishCount` / `publishFailureCount` / `heartbeatCount`) | interface 표면 부족 시 14 service 일괄 도입 불가 |

### 2.2 InMemory 구현 — slip-service `SlipRealtimeBroker` 추출 1:1 동작

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2.1 | MASTER | 🔴 | InMemory 구현 + `samhan.realtime.broker=in-memory` | `subscribe(slipId) → publish(slipId, "slip:edit", payload)` | emitter 1건 발급 + send 1회 + `subscriberCount=1` + `publishCount=1` + `publishFailureCount=0` (PR-H2 `SlipRealtimeBrokerTest.publish_normalEmitters_notCleanedUp` 등가) | 추출 회귀 시 slip-service 단방향 push 단절 |

### 2.3 InMemory 구현 — emitter cleanup race (concurrency)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3.1 | MASTER | 🔴 | InMemory 구현 + 50 emitter 동시 subscribe | publish 1회 (PR-H2 `SlipRealtimeBrokerConcurrencyIT.concurrentSubscribe_thenPublish_allReceiveEvent` 등가) | 50 emitter 전부 1초 안 수신 + `subscriberCount=50` + race condition 0 | concurrent race 회귀 시 일부 client audit overlay 미수신 |

### 2.4 InMemory 구현 — 100 emitter / 1000 publish 부하

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4.1 | MASTER | 🟠 | InMemory + 100 emitter | publish 1000회 (`load_100emitters_1000publish` 등가) | 모든 통계 일치 + 누락 0 | 부하 회귀 시 production 다중 사용자 시나리오 위험 |

### 2.5 Redis 구현 — channel prefix 자동 prepend

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5.1 | MASTER | 🔴 | Redis 구현 + `samhan.realtime.service-name=slip` 환경변수 | `publish(slipId, "slip:edit", payload)` | Redis `convertAndSend` 의 channel = `samhan:slip:slip:edit:{slipId}` (service-name prefix 자동 prepend) + envelope JSON `{slipId, eventName, data}` | prefix 누락 시 14 service 공유 환경에서 채널 충돌 |

### 2.6 Redis 구현 — onMessage loop 방지

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.6.1 | MASTER | 🔴 | Redis 구현 + 다른 노드 publish | onMessage 수신 (`RedisRealtimeBrokerTest.onMessage_validPayload_callsLocalPublish` 등가) | `localBroker.publishLocal` 호출 (publish 가 아님 — loop 방지) + `publishCount` +1 (단, hook 재호출 0) | loop 방지 누락 시 메시지 폭주 + 다중 노드 환경 publish 무한 증식 |

---

## 3. shared 모듈 단위 시나리오 — lock + edit-request (7 case)

> **모듈 위치** — `services/shared-edit-request/src/main/java/com/samhanair/logis/shared/editrequest/`
> PR-H3 `SlipEditRequestService` 6 책임 + `LockGuard` 7 case 를 generic 으로 추출.

### 3.1 generic LockGuard — `FREE_DIRECT_EDIT` 분류

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1.1 | SALES | 🔴 | shared 모듈 + slip 도메인 specialization (`SlipLockPolicy implements LockPolicy<SlipStatus>`) | DRAFT 상태 slip 직접 수정 시도 | shared `LockGuard.assertEditable` 통과 → mutation 진행 (PR-H3 `SlipServiceLockGuardTest.draft_freeEdit` 등가) | 추출 시 정책 회귀하면 SAVED 영업 작성 슬립도 잠금 → 사용자 작업 차단 |

### 3.2 generic LockGuard — `LOCKED_REQUIRES_APPROVAL` 분류

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2.1 | SALES | 🔴 | ACCEPTED 상태 slip + 활성 승인 0건 | direct 수정 시도 | shared `LockGuard.assertEditable` → `BusinessException(CONFLICT, "수정 요청 후 권한자 수락 필요")` (PR-H3 `accepted_unapproved_conflict` 등가) | 추출 회귀 시 무단 수정 가능 — audit 무력화 |

### 3.3 generic LockGuard — `FULLY_LOCKED` 분류

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3.1 | MASTER | 🔴 | INSPECTING 상태 slip | direct 수정 시도 | shared `LockGuard.assertEditable` → `BusinessException(CONFLICT, "검수 중 본문 수정 불가 — 별도 SQL audit 채널 사용")` (PR-H3 `inspecting_fully_locked` 등가) | FULLY_LOCKED 회귀 시 한국 일반기업회계기준 보존 의무 위배 |

### 3.4 generic EditRequestService — request 책임

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.4.1 | SALES | 🔴 | ACCEPTED slip + WAREHOUSE 활성 사용자 | `EditRequestService.request(slip-001, EDIT, "거래처 요청 변경")` | shared generic 으로 row 1건 INSERT (PENDING) + SSE broadcast (`slip:edit-request:created`) + `NotificationClient.notify` 호출 (graceful fallback 의무 — Feign 실패도 transaction commit) | request 회귀 시 작성자가 권한자에게 알릴 수단 단절 |

### 3.5 generic EditRequestService — approve + 1회 한정 소진

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.5.1 | WAREHOUSE | 🔴 | 3.4.1 통과 (PENDING row 1건) | `EditRequestService.approve(rid)` → 작성자가 mutation 1회 → 두 번째 mutation 시도 | (1) approve 시 row APPROVED + SSE broadcast (`slip:edit-request:decided`) + 작성자 toast. (2) 첫 mutation = `findActiveApproval` 통과 + `consumeApproval` (row soft-delete). (3) 두 번째 mutation = `findActiveApproval` 비어 → `LOCKED_REQUIRES_APPROVAL` CONFLICT (PR-H3 `consume_after_first_mutation` 등가) | 1회 한정 소진 회귀 시 한 번 승인으로 무한 수정 가능 — audit 무력화 |

### 3.6 generic EditRequestService — `@Scheduled` expirePending

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.6.1 | MASTER | 🟠 | PENDING row 3건 + 만료 시각 (created_at + 24h) 경과 | `@Scheduled fixedRate=1h` 트리거 | 3 row 모두 EXPIRED status 전환 + SSE broadcast (작성자 toast "수정 요청이 만료되었습니다") | scheduler 회귀 시 stale PENDING 누적 → 권한자 inbox 오염 |

### 3.7 generic EditRequestService — graceful Feign fallback

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.7.1 | SALES | 🔴 | notification-service 부팅 X | `EditRequestService.request(slip-001, EDIT, "사유")` | (1) row 1건 INSERT 정상 + SSE broadcast 정상 + (2) `NotificationClient.notify` 의 FeignException 을 try/catch + warning log + (3) request transaction commit 성공 (FeignException rethrow 0) | fallback 누락 시 외부 장애가 비즈니스 로직 마비로 전파 |

---

## 4. shared 모듈 단위 시나리오 — auto-config + bean (5 case)

### 4.1 `RealtimeBrokerAutoConfig` — default in-memory

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1.1 | DEVOPS | 🔴 | service 가 shared 의존만 추가 + 환경변수 무설정 | `SpringBootTest` startup | `InMemorySamhanRealtimeBroker` bean 단일 등록 + `RedisSamhanRealtimeBroker` bean 미등록 (`@ConditionalOnProperty` 가드) + startup 정상 | default 회귀 시 14 service 의존 추가만으로 부팅 실패 → roll-out 불가 |

### 4.2 `RealtimeBrokerAutoConfig` — `SAMHAN_REALTIME_BROKER=redis`

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.2.1 | DEVOPS | 🟠 | shared 의존 + `SAMHAN_REALTIME_BROKER=redis` + Redis testcontainer | `SpringBootTest` startup | `RedisSamhanRealtimeBroker` bean 활성 + Redis 연결 정상 + publish round-trip OK + `RealtimePublishHook` 등록 | toggle 회귀 시 다중 노드 환경 fan-out 단절 |

### 4.3 `RealtimeBrokerAutoConfig` — Redis 미연결 시 startup 명확 실패

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.3.1 | DEVOPS | 🟠 | `SAMHAN_REALTIME_BROKER=redis` + Redis 미가용 | `SpringBootTest` startup | `RedisConnectionFailureException` + ApplicationContext load 실패 + 명확한 한국어 로그 ("Redis 연결 실패 — SAMHAN_REALTIME_BROKER=redis 인데 REDIS_HOST 가 미가용") | 명확한 실패 메시지 누락 시 DevOps 진단 시간 폭증 |

### 4.4 `RealtimeBrokerAutoConfig` — `*Bean` suffix 가드

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.4.1 | MASTER | 🟠 | shared 의존 + `ApplicationContextLoadIT` | bean 단일성 검증 | `SamhanRealtimeBroker` bean = 1건 (`*Bean` suffix 가드 PR #119 회귀 방지) + `RealtimeBrokerAutoConfig` bean 단일 + `RealtimePublishHook` Optional 주입 정상 | suffix 회귀 시 PR #119 동일 ApplicationContext 충돌 재발 |

### 4.5 service-name prefix 환경변수

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.5.1 | DEVOPS | 🟠 | `samhan.realtime.service-name=slip` 환경변수 | publish 호출 | Redis channel = `samhan:slip:<eventName>:{entityId}` (prefix 자동 prepend). 환경변수 미설정 시 startup 명확 실패 (`@Value` 의무 + `IllegalStateException`) | prefix 누락 시 14 service 공유 환경에서 채널 충돌 |

---

## 5. slip-service 회귀 시나리오 — audit 보존 (24 case)

> **목적**: shared 모듈 추출 후 PR-H1/H2 의 audit overlay 동작 100% 회귀 보존 검증.
> **base** = PR-H2 시나리오 (`docs/qa/phase-12-step-2-slip-audit-overlay/scenarios.md`) 27 case 중 audit overlay 본체 24 case.

### 5.1 audit_log 자동 기록 회귀 (5 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 5.1.1 | PR-H2 1.1.1 | SALES | 🔴 | PATCH /audit/overlay memo 변경 | shared 모듈 위임 후에도 1.1.1 결과 1:1 동일 (audit row + revision_count + SSE) |
| 5.1.2 | PR-H2 1.2.1 | WAREHOUSE | 🔴 | PATCH /audit/overlay shippingAddress | 1.2.1 결과 1:1 동일 |
| 5.1.3 | PR-H2 1.3.1 | SALES | 🟢 | unsupported field | 1.3.1 결과 1:1 동일 (`IllegalArgumentException`) |
| 5.1.4 | PR-H2 1.4.1 | MANAGER | 🔴 | editHeader multi-field | 1.4.1 결과 1:1 동일 (single revisionNo + 2 audit row) |
| 5.1.5 | PR-H2 1.5.1 | SALES | 🟠 | empty diff | 1.5.1 결과 1:1 동일 (audit 미생성 — Major 권고 보존) |

### 5.2 AuditOverlay UI 표시 회귀 (5 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 5.2.1 | PR-H2 2.1.1 | SALES | 🔴 | SlipDetailPage 진입 (audit-logs 1행) | shared 모듈 응답 schema 1:1 동일 → AuditOverlay 표시 1:1 동일 (취소선 + 색상 dot + actorName) |
| 5.2.2 | PR-H2 2.2.1 | MANAGER | 🔴 | expand 토글 (3+ revision) | 2.2.1 결과 1:1 동일 |
| 5.2.3 | PR-H2 2.3.1 | SALES | 🟠 | empty history | 2.3.1 결과 1:1 동일 ("변경 이력 없음") |
| 5.2.4 | PR-H2 2.4.1 | WAREHOUSE | 🟡 | currentValue null | 2.4.1 결과 1:1 동일 ("(빈 값)") |
| 5.2.5 | PR-H2 2.5.1 | MANAGER | 🟠 | 동일 사용자 다중 row | 2.5.1 결과 1:1 동일 (deterministic hash 색상 일관) |

### 5.3 수정 횟수 카운트 회귀 (3 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 5.3.1 | PR-H2 3.1.1 | SALES | 🟠 | audit-logs 0건 | 3.1.1 결과 1:1 동일 ("수정 0회" — 또는 hide) |
| 5.3.2 | PR-H2 3.2.1 | MANAGER | 🔴 | distinct 5 revision | 3.2.1 결과 1:1 동일 ("수정 5회") |
| 5.3.3 | PR-H2 3.3.1 | MANAGER | 🔴 | 다중 필드 dedupe | 3.3.1 결과 1:1 동일 ("수정 3회") |

### 5.4 복원 (revert) 회귀 (4 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 5.4.1 | PR-H2 4.1.1 | MANAGER | 🔴 | revert/{n} 정상 | shared 모듈 위임 후에도 4.1.1 결과 1:1 동일 (신규 revision + audit row) |
| 5.4.2 | PR-H2 4.2.1 | MASTER | 🟠 | revert/1 (전체) | 4.2.1 결과 1:1 동일 |
| 5.4.3 | PR-H2 4.3.1 | DRIVER | 🔴 | revert 권한 차단 | 4.3.1 결과 1:1 동일 (403) |
| 5.4.4 | PR-H2 4.4.1 | MANAGER | 🔴 | UI dropdown | 4.4.1 결과 1:1 동일 (cache invalidate + 자동 refetch) |

### 5.5 실시간 sync 회귀 (5 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 5.5.1 | PR-H2 5.1.1 | SALES | 🟠 | self-receive | 5.1.1 결과 1:1 동일 |
| 5.5.2 | PR-H2 5.2.1 | SALES+WAREHOUSE | 🔴 | multi-context 1초 sync (Samhan Public 핵심 요구) | 5.2.1 결과 1:1 동일 (취소선 + 색상 + actorName + 1초 sync) |
| 5.5.3 | PR-H2 5.3.1 | MASTER | 🔴 | SSE payload schema | 5.3.1 결과 1:1 동일 (5 키 + LinkedHashMap 순서) |
| 5.5.4 | PR-H2 5.4.1 | MANAGER | 🟠 | cache invalidate | 5.4.1 결과 1:1 동일 |
| 5.5.5 | PR-H2 5.5.1 | DRIVER+SALES | 🔴 | mobile + desktop 혼합 | 5.5.1 결과 1:1 동일 |

### 5.6 동시 수정 충돌 + Redis fallback 회귀 (2 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 5.6.1 | PR-H2 6.3.1 | MASTER | 🔴 | 50 emitter + 1 publish | 6.3.1 결과 1:1 동일 (race condition 0) |
| 5.6.2 | PR-H2 7.1.1 | MASTER | 🔴 | default in-memory | 7.1.1 결과 1:1 동일 (Redis 미연결 startup 정상) |

---

## 6. slip-service 회귀 시나리오 — edit-request 보존 (14 case)

> **목적**: shared 모듈 추출 후 PR-H3 의 잠금/요청/수락 워크플로우 100% 회귀 보존 검증.
> **base** = PR-H3 시나리오 (`docs/qa/phase-12-step-3-slip-edit-permission/scenarios.md`) 24 case 중 잠금 + 워크플로우 핵심 14 case.

### 6.1 status 잠금 회귀 (6 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 6.1.1 | PR-H3 LockGuard 1 | SALES | 🔴 | DRAFT 자유 수정 | shared 모듈 LockGuard 위임 후에도 1:1 동일 |
| 6.1.2 | PR-H3 LockGuard 2 | SALES | 🔴 | SAVED 자유 수정 | 1:1 동일 |
| 6.1.3 | PR-H3 LockGuard 3 | SALES | 🔴 | ACCEPTED 미승인 CONFLICT | 1:1 동일 |
| 6.1.4 | PR-H3 LockGuard 4 | SALES | 🔴 | ACCEPTED 승인 후 진행+소진 | 1:1 동일 (consumeApproval 정상) |
| 6.1.5 | PR-H3 LockGuard 5 | MASTER | 🔴 | INSPECTING FULLY_LOCKED | 1:1 동일 (CONFLICT) |
| 6.1.6 | PR-H3 LockGuard 6 | MASTER | 🔴 | DELIVERED softDelete FULLY_LOCKED | 1:1 동일 |

### 6.2 요청→알림→수락/거절 회귀 (5 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 6.2.1 | PR-H3 ServiceTest 1 | SALES | 🔴 | DRAFT request 거부 | shared 모듈 위임 후에도 INVALID_INPUT 1:1 동일 |
| 6.2.2 | PR-H3 ServiceTest 2 | SALES | 🔴 | ACCEPTED request 정상 | 1:1 동일 (PENDING row + SSE + NotificationClient) |
| 6.2.3 | PR-H3 ServiceTest 3 | SALES | 🔴 | INSPECTING request CONFLICT | 1:1 동일 |
| 6.2.4 | PR-H3 ServiceTest 4 | WAREHOUSE | 🔴 | approve transition | 1:1 동일 (APPROVED + SSE 작성자 toast) |
| 6.2.5 | PR-H3 ServiceTest 5 | WAREHOUSE | 🔴 | reject transition | 1:1 동일 (REJECTED + SSE 작성자 toast) |

### 6.3 만료 + 1회 소진 + UX 회귀 (3 case)

| # | 회귀 base | 페르소나 | 우선순위 | 동작 | 기대 |
|---|---|---|---|---|---|
| 6.3.1 | PR-H3 expirePending | MASTER | 🟠 | scheduler 24h 경과 | shared 모듈 generic `@Scheduled` 위임 후에도 EXPIRED 전환 1:1 동일 |
| 6.3.2 | PR-H3 consumeApproval | SALES | 🔴 | 첫 mutation 후 두 번째 mutation 시도 | 1:1 동일 (LOCKED_REQUIRES_APPROVAL CONFLICT) |
| 6.3.3 | PR-H3 IT controller | SALES | 🟠 | E2E (request → approve → mutation → consume) | 1:1 동일 (PR-H3 `SlipEditRequestControllerIT` 등가) |

---

## 7. PASS/FAIL 종합

- **shared 모듈 단위 시나리오** = 23 case (audit 5 + broker 6 + lock+edit-request 7 + auto-config 5)
- **slip-service 회귀 시나리오** = 38 case (audit 24 + edit-request 14)
- **총 61 case** (사용자 명세 일치)
- **페르소나 5** (SALES / WAREHOUSE / MANAGER / MASTER / DEVOPS) — `feedback_role_naming_full` 풀네임 의무
- **본 PR-H4a 작동 캡처** = **불필요** (FE 변경 0 — 기존 PR-H1/H2/H3 캡처 보존). 필요 시 PR-H4b/H4c 단계에서 도메인별 multi-context 캡처

### 7.1 회귀 우선순위 매트릭스

| 우선순위 | shared 단위 | slip 회귀 | 합계 |
| --- | --- | --- | --- |
| 🔴 Critical | 14 | 30 | **44** |
| 🟠 Major | 8 | 7 | **15** |
| 🟡 Minor | 0 | 1 | **1** |
| 🟢 Info | 1 | 0 | **1** |
| **합계** | **23** | **38** | **61** |

### 7.2 최종 판정

본 시나리오 61 case + BE-1 agent 단위/IT 보강 (shared 모듈 BE 단위 + slip-service 회귀 IT) + 본 agent docs 3 건 (Designer + DevOps + QA) 모두 첨부 시 PR-H4a GREEN 머지 가능.

**Samhan Public 핵심 요구 검증**: 본 PR-H4a 는 코드 변경이 모듈 추출만이므로 사용자 화면 변동 0건. PR-H2 시드 요구 ("취소선 + 색상 + 수정자 이름 + 1초 sync") 4 요소가 추출 후에도 5.5.2 multi-context 회귀 case 에서 1:1 보존되는지가 핵심 GREEN 게이트.

**PR-H4b/H4c 진입 조건**: 본 PR-H4a 머지 + 5.x / 6.x 회귀 case 38건 모두 PASS 후 → PR-H4b (5 backend 도입) → PR-H4c (50+ page UI 통합) 순차 진입.

---

## 8. 참고

- shared-realtime BE 모듈 (PR-H4a BE-1 산출): `services/shared-realtime/`
- shared-edit-request BE 모듈 (PR-H4a BE-1 산출): `services/shared-edit-request/`
- Designer 패턴 가이드: `docs/uiux/phase12/H4a-shared-realtime-pattern.md`
- DevOps 14 service 공유 가이드: `docs/devops/redis-realtime-broker.md` § 9 (PR-H4a 보강)
- PR-H2 시드 시나리오 (audit overlay base): `docs/qa/phase-12-step-2-slip-audit-overlay/scenarios.md`
- PR-H3 시드 시나리오 (edit-request base): `docs/qa/phase-12-step-3-slip-edit-permission/scenarios.md`
- userColorHash util (deterministic): `clients/web/design-system/src/utils/userColorHash.ts`
- AuditOverlay 컴포넌트: `clients/web/design-system/src/components/AuditOverlay/`
- SlipDetailPage 시드 (1:1 복제 base): `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` (commit `435918c`)
