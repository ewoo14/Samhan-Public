# Phase 9 2차 — W2 groupware-service skeleton

본 보고서는 Phase 9 (잔여 도메인) 2차 작업 결과를 정리한다.

- W1 = 완료 — partner-service skeleton + M5 partnerCode lookup endpoint (PR #91)
- W2 = **본 PR — groupware-service skeleton (port 8092, 결재선 + 메신저 + 일정 + UserClient)**
- W3 예정 = notification-service (push/email/sms 통합)
- W4 예정 = dashboard-service (KPI + 실시간 재고 + 매출)
- W5 예정 = Phase 9 회고 + Phase 10 진입 plan

---

## 1. 배경

`docs/migration/phase9/M-PHASE-9-readiness.md` §3-2 일관 — 결재선 (전자결재 chain) + 메신저 (1:1) + 일정 (참여자 포함) 의 3 도메인을 단일 service 로 묶는다. 그룹웨어 영역은 사용 흐름이 인접 (사내 알림 / 캘린더 / 결재 trigger 가 상호 참조) 하므로 도메인을 함께 보유.

본 PR 은 W1 partner-service 의 패턴 (BaseEntity 7 audit + Soft Delete + Internal/Admin 2단 controller + ServiceDiscoveryClient + chained-default 환경변수) 을 1:1 복제 + UserClient 외부 client 를 추가한 형태.

ServiceDiscoveryClient 두 번째 소비자 = groupware-service. W1 partner-service 가 첫 소비자였고, 본 슬라이스부터 모든 신규 service 가 동일 의존성 도입.

---

## 2. 산출 요약

### 2-1. 신규 service `services/groupware-service/` (port 8092)

```text
services/groupware-service/
├── build.gradle                                       (Spring Boot + Eureka client + Flyway + shared:common + shared:discovery-abstraction)
├── README.md                                          (도입 배경 + Domain + REST + 환경변수 + 테스트 + Phase 10 영향)
├── src/main/java/com/samhanair/logis/groupware/
│   ├── GroupwareServiceApplication.java               (@SpringBootApplication + @EnableDiscoveryClient + JPA auditing)
│   ├── client/
│   │   └── UserClient.java                            (user-service Internal API lookup, ServiceDiscoveryClient 두 번째 소비자)
│   ├── config/
│   │   ├── SecurityConfig.java                        (InternalToken + HeaderAuth filter chain)
│   │   ├── InternalAuthProperties.java                (@ConfigurationProperties("app.security.internal"))
│   │   ├── InternalTokenFilter.java                   (X-Internal-Token → ROLE_MASTER, /internal/** prefix 한정)
│   │   ├── HeaderAuthenticationFilter.java            (X-User-Id/Role → ROLE_<ROLE>)
│   │   ├── InternalTokenGuard.java                    (prod 프로파일 + dev 기본 토큰 부팅 거부)
│   │   └── WebClientConfig.java                       (RestClient.Builder bean for UserClient)
│   ├── controller/
│   │   ├── GroupwareInternalController.java           (GET /internal/groupware/approvals/{id} + /messages/unread-count)
│   │   └── GroupwareAdminController.java              (결재 3 + 메신저 2 + 일정 4 endpoint)
│   ├── service/
│   │   ├── ApprovalLineService.java                   (create / findById / approve / reject / withdraw)
│   │   ├── MessageService.java                        (send / inbox / unreadCount / markRead)
│   │   └── ScheduleService.java                       (create / findById / findInRange / update / addParticipant / delete)
│   ├── domain/
│   │   ├── ApprovalLine.java                          (BaseEntity + chain @OneToMany + status 5상태)
│   │   ├── ApprovalStep.java                          (chain 단일 단계, sequence ASC)
│   │   ├── ApprovalStatus.java                        (5상태 enum)
│   │   ├── ApprovalStepStatus.java                    (3상태 enum)
│   │   ├── Message.java                               (sender / recipient / body / status / sentAt / readAt)
│   │   ├── MessageStatus.java                         (UNREAD / READ)
│   │   ├── Schedule.java                              (owner / 시작-종료 / status + 참여자 @OneToMany)
│   │   ├── ScheduleParticipant.java                   (1:N)
│   │   └── ScheduleStatus.java                        (DRAFT / CONFIRMED / CANCELLED)
│   ├── repository/
│   │   ├── ApprovalLineRepository.java                (findAllByRequesterId / findAllByStatus / findAllByRequesterIdAndStatus)
│   │   ├── MessageRepository.java                     (findAllByRecipientIdOrderBySentAtDesc + countByRecipientIdAndStatus)
│   │   └── ScheduleRepository.java                    (findOwnedInRange — JPQL 기간 겹침)
│   ├── dto/
│   │   ├── ApprovalLineCreateRequest.java             (record + Bean Validation)
│   │   ├── ApprovalDecisionRequest.java               (approverId + 반려 사유)
│   │   ├── ApprovalLineAdminResponse.java             (chain 전체 노출 — admin 화면용)
│   │   ├── ApprovalLineInternalResponse.java          (UUID 포함, internal caller 전용)
│   │   ├── MessageSendRequest.java
│   │   ├── MessageResponse.java
│   │   ├── UnreadCountResponse.java
│   │   ├── ScheduleRequest.java                       (등록/수정 공용)
│   │   └── ScheduleResponse.java
│   └── exception/GroupwareExceptionHandler.java       (BusinessException / Validation / IllegalState → ApiResponse 봉투)
├── src/main/resources/
│   ├── application.yml                                (port 8092 + chained-default DB + samhan.discovery.provider + samhan.user-service.url + local 프로파일)
│   └── db/migration/V1__init_groupware.sql            (5 테이블 + BaseEntity 7 audit + Soft Delete + partial unique index 2종)
└── src/test/java/com/samhanair/logis/groupware/
    ├── service/
    │   ├── ApprovalLineServiceTest.java               (단위, 8 case)
    │   ├── MessageServiceTest.java                    (단위, 4 case)
    │   └── ScheduleServiceTest.java                   (단위, 4 case)
    └── it/
        ├── AbstractPostgresIT.java                    (Testcontainers PostgreSQL + Docker 미가용 skip)
        ├── GroupwareInternalControllerIT.java         (4 case + UserClient @MockBean)
        └── GroupwareAdminControllerIT.java            (6 case + UserClient @MockBean)
```

### 2-2. 기존 파일 갱신

| 파일 | 변경 |
|---|---|
| `settings.gradle` | `include 'services:groupware-service'` + `projectDir` 매핑 + Phase 9 services 주석 갱신 |
| `build.gradle` (root) | `leafProjects` 에 `:services:groupware-service` 추가 |
| `README.md` (root) | service 인벤토리 8092 groupware 행 갱신 (예정 → 본 PR) + 디렉토리 트리 + bootRun + 진척률 |
| `ROADMAP.md` | Phase 9 = "2차 진행 (W2 groupware-service skeleton)" + 산출물 섹션 + 머지 PR 매트릭스 + 디렉토리 매트릭스 |
| `migration/decisions/DECISIONS.md` | D-P9-06 / D-P9-07 / D-P9-08 추가 |
| `infrastructure/env-templates/groupware-service.env` | 신규 (chained-default + CHANGE_ME_LOCAL_ONLY placeholder) |
| `docs/migration/phase9/M-PHASE-9-readiness.md` | W2 = "완료 (본 PR — 5 entity + 2 controller + 3 service + 9 dto + 5 config + 1 client + 1 exception handler + IT 2 + 단위 테스트 3)" |

---

## 3. 도메인 모델

### 3-1. 결재선 chain (ApprovalLine + ApprovalStep)

```text
ApprovalLine (status: ApprovalStatus 5상태)
  └─ steps: List<ApprovalStep>  @OneToMany + @OrderBy("sequence ASC")
       ├─ ApprovalStep #0 (approver_a, status: ApprovalStepStatus, decidedAt, reason)
       ├─ ApprovalStep #1 (approver_b, ...)
       └─ ApprovalStep #2 (approver_c, ...)
```

- chain 흐름: PENDING → IN_PROGRESS (1번째 승인) → APPROVED (모든 step 승인) 또는 REJECTED (1명 반려) 또는 WITHDRAWN (요청자 회수)
- 본인 결재자 차단 (요청자 ≠ approver) — `appendStep` 가드
- chain 순서 강제 — `currentStep()` PENDING 중 sequence 최소 step 만 처리, 다른 결재자 호출 거부
- 종료 상태 재호출 거부 — `ensureMutable()` 가드

### 3-2. 메신저 (Message)

- 1:1 row 단위 — 그룹/단체는 row 다중 발행으로 표현
- self-send 거부 (sender ≠ recipient) — 도메인 가드
- markRead = idempotent (READ 재호출 no-op)
- 수신자 본인만 markRead 가능

### 3-3. 일정 (Schedule + ScheduleParticipant)

- starts_at < ends_at 강제
- 참여자 추가 idempotent (중복 시 no-op) + partial unique index 보강
- 조회 = JPQL 기간 겹침 (`endsAt >= from AND startsAt <= to`)
- soft-delete = BaseEntity `markDeleted(actor)` 사용

---

## 4. REST API (Internal 2 + Admin 9)

### 4-1. Internal — X-Internal-Token + ROLE_MASTER (`/internal/**` prefix 한정)

| Method | Path | 응답 |
|---|---|---|
| GET | `/internal/groupware/approvals/{approvalId}` | ApprovalLineInternalResponse (approvalId / requesterId / title / status) |
| GET | `/internal/groupware/messages/unread-count?userId={UUID}` | UnreadCountResponse (userId / unreadCount) |

### 4-2. Admin — JWT Bearer + @PreAuthorize

| Method | Path | 권한 |
|---|---|---|
| POST | `/admin/groupware/approvals` | MASTER / MANAGER |
| PUT | `/admin/groupware/approvals/{id}/approve` | MASTER / MANAGER |
| PUT | `/admin/groupware/approvals/{id}/reject` | MASTER / MANAGER |
| POST | `/admin/groupware/messages` | 전체 ROLE |
| GET | `/admin/groupware/messages/inbox?userId={UUID}` | 전체 ROLE |
| POST | `/admin/groupware/schedules` | 전체 ROLE |
| GET | `/admin/groupware/schedules?ownerId&from&to` | 전체 ROLE |
| PUT | `/admin/groupware/schedules/{id}` | 전체 ROLE |
| DELETE | `/admin/groupware/schedules/{id}` | MASTER / MANAGER |

응답 = `ApiResponse<T>` 봉투. UUID 비공개 가드 — Internal endpoint 만 UUID 노출 (caller = 내부 형제 service).

---

## 5. 테스트 결과

### 5-1. 단위 테스트 (3 class, 16 case 모두 PASS)

```text
> ./gradlew :services:groupware-service:test --tests com.samhanair.logis.groupware.service.*

ApprovalLineServiceTest (8 case)
  ✓ open_then_appendStep_creates_chain_in_sequence_order
  ✓ approve_first_step_transitions_to_in_progress
  ✓ approve_all_steps_transitions_to_approved
  ✓ reject_first_step_terminates_to_rejected
  ✓ appendStep_blocks_requester_self_as_approver
  ✓ withdraw_by_requester_terminates_to_withdrawn
  ✓ approve_after_terminal_state_is_rejected
  ✓ approve_out_of_order_blocks_second_step_when_first_pending

MessageServiceTest (4 case)
  ✓ send_initialises_unread_with_sentAt_now
  ✓ send_blocks_self_send
  ✓ markRead_by_recipient_transitions_unread_to_read
  ✓ markRead_by_non_recipient_is_rejected

ScheduleServiceTest (4 case)
  ✓ create_defaults_to_draft_status_when_status_missing
  ✓ update_rejects_invalid_time_range
  ✓ addParticipant_is_idempotent_for_same_id
  ✓ cancel_transitions_status_to_cancelled

BUILD SUCCESSFUL — 16 tests, 0 failures
```

### 5-2. 컴파일 검증

```text
> ./gradlew :services:groupware-service:compileJava :services:groupware-service:compileTestJava

BUILD SUCCESSFUL in 7s
5 actionable tasks: 3 executed, 2 from cache
```

### 5-3. IT (Linux CI runner 위임)

`GroupwareInternalControllerIT` (4 case) + `GroupwareAdminControllerIT` (6 case) 는 Testcontainers PostgreSQL 16 + Spring Boot context 부팅이 필요. Windows 한글 path 로컬 환경 대신 GitHub Actions Ubuntu runner 에서 실행. `DockerAvailableCondition` 이 Docker 미가용 환경 자동 skip.

UserClient = `@MockBean` 격리 (memory feedback_it_mockbean_external_clients) — `lenient().when(userClient.exists(any())).thenReturn(true)` 로 모든 사용자 존재 검증을 통과시켜 결재선 / 메신저 / 일정 fixture seed 가능.

---

## 6. 환경변수 (chained-default 표준)

| 변수 | default | 비고 |
|---|---|---|
| `SAMHAN_GROUPWARE_PORT` | 8092 | server.port |
| `SAMHAN_GROUPWARE_DB_HOST` / `PORT` / `NAME` / `USER` / `PASSWORD` | localhost / 5432 / groupware_db / CHANGE_ME_LOCAL_ONLY / CHANGE_ME_LOCAL_ONLY | LEGACY_DB_* fallback |
| `SAMHAN_INTERNAL_TOKEN` | dev-internal-token-change-me | INTERNAL_AUTH_TOKEN fallback (prod 부팅 거부 가드) |
| `SAMHAN_USER_SERVICE_URL` | http://localhost:8083 | UserClient base URL |
| `SAMHAN_GROUPWARE_SERVICE_URL` | http://groupware-service:8092 | 형제 service 호출용 (env-template only) |
| `SAMHAN_DISCOVERY_PROVIDER` | eureka | aws-cloud-map 토글 시 Phase 10 vendor 전환 |
| `EUREKA_URL` | http://localhost:8761/eureka/ | service discovery |

GitGuardian 회피 — 모든 시크릿 `CHANGE_ME_LOCAL_ONLY` placeholder + `dev-internal-token-change-me` (yml dev default) + `test-internal-token` (IT 전용). `samhan_dev_pw` literal 0건.

---

## 7. AWS 호환 (Phase 10 cutover 영향)

| 영역 | 호환성 |
|---|---|
| 12-factor (config 외부화) | chained-default 환경변수 + `CHANGE_ME_LOCAL_ONLY` placeholder → AWS Secrets Manager 마이그레이션 시 `spring.config.import: aws-secretsmanager:samhan/<env>/...` 한 줄 추가 |
| RDS PostgreSQL | Flyway V1 = standard SQL (extension 0건) → RDS 즉시 사용 가능 |
| Service Discovery | `SAMHAN_DISCOVERY_PROVIDER=aws-cloud-map` 토글 시 `AwsCloudMapServiceDiscoveryClient` 활성 (Phase 10 cutover 시점). 본 PR 시점 = eureka default |
| UserClient 회로차단 | 본 PR = fail-open (network 실패 시 통과). cutover 시점 = Resilience4j `@CircuitBreaker` + fail-fast 강화 (별도 PR) |

---

## 8. 가드 일관 적용

| 가드 | 적용 |
|---|---|
| BaseEntity 7 audit | 5 entity 모두 `extends BaseEntity` |
| Soft Delete only | `@SQLRestriction("is_deleted = false")` 5 entity 적용 |
| partial unique index | `schedule_participants` (schedule_id+participant_id) / `approval_steps` (line+sequence) 활성 행 한정 |
| DB 컬럼 타입 | `VARCHAR(N)` / `TIMESTAMP` / `INT` / `UUID` (CHAR 0건) |
| UUID 비공개 | Internal/Admin 응답 분리 — Internal 만 UUID 노출 (내부 caller 전용) |
| 함수 단위 한국어 Javadoc | 3 service / 2 controller / 5 entity / 3 enum / 9 dto / 5 config / 1 client 모두 적용 |
| springdoc-openapi | `@Operation` + `@ApiResponses` 두 controller 적용 |
| 환경변수 chained-default | `${SAMHAN_GROUPWARE_DB_HOST:${LEGACY_DB_HOST:localhost}}` 패턴 |
| GitGuardian 회피 | placeholder = `CHANGE_ME_LOCAL_ONLY` (env-template) + `dev-internal-token-change-me` (yml dev default) + `test-internal-token` (IT 전용) |
| InternalTokenGuard | `@PostConstruct` 검증 — prod 프로파일 + dev 기본값 시 `IllegalStateException` 부팅 거부 |
| InternalTokenFilter `/internal/**` prefix 한정 | PR #91 fix 패턴 1:1 복제 |
| IT mockbean 외부 client | `UserClient = @MockBean` + lenient setup (memory feedback_it_mockbean_external_clients) |
| 한글 path JDK 트랩 | `AbstractPostgresIT` 의 `DockerAvailableCondition` 가 Docker 미가용 시 IT skip |

---

## 9. 후속 단계

본 PR 머지 후:
- W3 = `services/notification-service` (8093) — 푸시 / 이메일 / SMS adapter + UserClient (수신자 정보)
- W4 = `services/dashboard-service` (8094) — KPI 집계 + materialized view + 다중 service client (Inventory / Accounting / PartnerOrder / Partner)
- W5 = Phase 9 회고 + Phase 10 진입 plan
- M5 slip-service + 본 W2 결재선 통합 시점 = W5 또는 Phase 10 cutover (별도 PR)

---

## 10. 참조

- `services/groupware-service/README.md`
- `docs/migration/phase9/M-PHASE-9-readiness.md` (4 service skeleton + 5주 roadmap)
- `migration/decisions/DECISIONS.md` D-P9-06 / D-P9-07 / D-P9-08
- `shared/discovery-abstraction/` (Phase 8 2차 도입, 본 PR 두 번째 소비자)
- `infrastructure/env-templates/groupware-service.env`
- `docs/dev-reports/phase9-step-1-partner-service.md` (W1 1:1 패턴 참고)

---

## 11. 후속 단계 backlog (5 reviewer 토론 종합)

본 PR 머지 영향 0. 후속 PR 또는 W3/W4/W5 통합 PR 시점에 처리.

| # | 카테고리 | 항목 | 위임 시점 | 출처 reviewer |
|---|---|---|---|---|
| 1 | IT 시나리오 | 메신저 self-send 400 controller 망 가드 | W3 또는 후속 PR | QA |
| 2 | IT 시나리오 | 결재 본인 결재자 IT 가드 | W3 또는 후속 PR | QA |
| 3 | IT 시나리오 | 일정 기간 겹침 boundary case (>= / <= 경계) | W3 또는 후속 PR | QA |
| 4 | 성능 | UserClient bulk verify endpoint or 짧은 TTL 캐시 (결재자 fan-out 직렬 RPC) | W3 (notification-service 합류 시점 user-service 부하 누적) | BE |
| 5 | client 노출 | groupware admin 화면 — clients/desktop 단일 흡수 (admin-portal 분리 비권장) | W5 또는 Phase 10 | FE |
| 6 | edge case | Message body XSS 가드 (`@Size(2000)` 만 적용 중) | 후속 PR | QA |
| 7 | edge case | Schedule timezone (`LocalDateTime.now()` server TZ 의존) | 후속 PR | QA |
| 8 | edge case | 결재선 동시성 race lock (현재 sequence ASC 강제로 위험 낮음) | 후속 PR | QA |
| 9 | edge case | Playwright e2e 매핑 — `qa/playwright/README.md` 15 spec 에 groupware 도메인 신규 추가 | W5 | QA |
| 10 | 디자인 토큰 일관 | W3/W4 통합 PR dev-report 에 "API matrix HTML slate / method / badge 토큰 1:1 복제" 1줄 가이드 | W3 통합 PR (별도 PR X) | Designer |
| 11 | DevOps 운영 | `infrastructure/postgres/init/01-create-databases.sql` 에 `groupware_db` 추가 | W3+W4 와 묶어 운영 PR | DevOps |
| 12 | DevOps 운영 | `infrastructure/prometheus/prometheus.yml` scrape target — `groupware-service:8092/actuator/prometheus` job 추가 | W3+W4 일괄 | DevOps |
| 13 | DevOps 운영 | `UserClient.exists()` fail-soft → fail-fast 전환 시점을 D-P9-NN 으로 등록 (Phase 10 cutover plan 추적용) | Phase 10 진입 시점 | DevOps |

본 PR 채택 fix (W2 종합 TM 적용):
1. ApprovalLineRepository 모든 find* 에 `@EntityGraph(attributePaths = "steps")` 적용 — BE+QA 일치 우려 (LazyInit + N+1 회귀 방지)
2. ScheduleRepository.findById 에 `@EntityGraph(attributePaths = "participants")` 적용 — QA 식별 (update path lucky pass 명시적 fix)
3. 본 backlog 섹션 명시 (별도 docs PR X, `feedback_continuous_docs_sync.md` 일관)
