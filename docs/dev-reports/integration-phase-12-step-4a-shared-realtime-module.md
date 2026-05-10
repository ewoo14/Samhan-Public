# Phase 12 step-4a (PR-H4a) — `shared/realtime-abstraction` module 추출 + slip-service 시범 마이그

> 본 dev-report 는 PR (`feature/integrated-phase-12-step-4a-shared-realtime-module`) 의 종합 작업 보고. PR #125 (PR-H3 slip 수정/삭제 요청 워크플로우 + 잠금 가드) 머지 후 **Phase 12 시리즈 4 (전 15 service + 50+ page 일괄 확장, ~7주) 분할 1/3** 진입. 본 PR = BE 인프라 시드 단계 — `shared/realtime-abstraction` 모듈 추출 + slip-service 시범 마이그 (호출자 0 변경 + 회귀 0).

## 1. 배경

### 1.1 PR-H3 → PR-H4a 진입 사유

PR-H1/H2/H3 (PR #123/#124/#125) 머지 완료로 slip-service 한 도메인에 한정해 SSE infra + audit overlay + 수정/삭제 요청 워크플로우 + 잠금 가드 4 책임이 모두 구현됨. PR-H4 = 본 시드 패턴을 **나머지 13 backend MSA + 50+ page 에 일괄 확장** 단계. 단일 PR 7주는 diff 가독성 / 단계별 검증 게이트 부재 / 회귀 폭주 위험 → **3분할 채택 (사용자 결정 옵션)**:

| 분할 | 기간 | 책임 | 산출 |
| --- | --- | --- | --- |
| **PR-H4a (본 PR)** | ~1주 | BE 인프라 시드 | `shared/realtime-abstraction` 모듈 + slip-service 시범 마이그 (호출자 0 변경) + Designer/DevOps/QA 가이드 |
| PR-H4b | ~3주 | BE 13 service 일괄 | `shared/realtime-abstraction` 의존만 추가 + 도메인별 Flyway template 활용 |
| PR-H4c | ~3주 | FE 50+ page 통합 | desktop `<Domain>DetailPage` 일괄 audit overlay + mobile-staff RN screen |

### 1.2 시리즈 진행 (PR-H1 ~ PR-H4c)

| 슬라이스 | 기간 | 목표 | 상태 |
| --- | --- | --- | --- |
| PR-H1 | 1주 | SSE infra + slip 코멘트 smoke | **머지 완료 (PR #123, D-P12-01)** |
| PR-H2 | ~3주 | slip audit overlay + 실시간 sync + TM 보완 3건 | **머지 완료 (PR #124, D-P12-02)** |
| PR-H3 | ~1.5주 | slip 수정/삭제 요청 워크플로우 + 잠금 가드 | **머지 완료 (PR #125, D-P12-03)** |
| **PR-H4a (본 PR)** | ~1주 | `shared/realtime-abstraction` module 추출 + slip-service 시범 마이그 | **진행 중 (D-P12-04a)** |
| PR-H4b | ~3주 | BE 13 service 일괄 적용 | 대기 |
| PR-H4c | ~3주 | FE 50+ page UI 통합 | 대기 |

## 2. 핵심 결정 (D-P12-04a 요약)

> 자세한 결정 사실 / 근거 / 영향 = `migration/decisions/DECISIONS.md` D-P12-04a 참조.

| 결정 | 채택 |
| --- | --- |
| 모듈 위치 / 패턴 | **`shared/realtime-abstraction` (java-library + Spring Boot autoconfigure)** — `shared:common` / `shared:security` 패턴 일관 (Spring Boot plugin 미적용) |
| 4 책임 분리 | **broker (5 file) + audit (4 file) + lock (4 file) + editrequest (5 file) + autoconfig (1 file) = 19 신규 java file** |
| Broker 기본 / 옵션 | **`InMemoryRealtimeBroker` default + `RedisRealtimeBroker` 옵션 (Conditional `app.realtime.broker=redis`)** — Redis 의존 = `compileOnly` (consumer 가 starter-data-redis 의존 시만 활성) |
| AutoConfiguration 표준 | **Spring Boot 3 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 1 entry (`RealtimeAutoConfiguration`)** — legacy `spring.factories` 폐기 |
| Audit / Editrequest base | **`AuditLogEntry` / `EditRequestRecord` `@MappedSuperclass`** — `slip_audit_logs` (PR-H2) / `slip_edit_requests` (PR-H3) schema 1:1 일반화 + BaseEntity 7 audit 상속 |
| Lock policy | **`EditLockPolicy` enum (FREE_DIRECT_EDIT / LOCKED_REQUIRES_APPROVAL / FULLY_LOCKED)** — PR-H3 시드 3 카테고리 정책 일반화 |
| slip-service 마이그 | **호출자 0 변경 — thin facade `SlipRealtimeBroker extends InMemoryRealtimeBroker`** (259 → 109 line) + 4 file 삭제 (shared 으로 이전) |
| db template | **`db/template/{audit_log_template,edit_request_template}.sql` 2 신규 — PR-H4b 13 service 가 1:1 복제 + 도메인 prefix 교체** |
| 회귀 가드 | **shared 단위 29 PASS + slip-service 336 tests / 0 fail** + 풀빌드 GREEN |
| FE 변경 | **0 — 작동 캡처 면제** (QA 5.5.2 multi-context 회귀 게이트만 수행) |

## 3. 산출물 (3 commits = Phase A docs 1 + Phase A BE 1 + TM docs 1)

### 3.1 `d18e80e` docs(phase-12-h4a): Designer audit overlay 패턴 가이드 + DevOps Redis production + QA shared module 시나리오

3 files +784.

| 파일 | 변경 |
| --- | --- |
| `docs/uiux/phase12/H4a-shared-realtime-pattern.md` 신규 (277 line) | 14 service × audit overlay 적용 매트릭스 (9 service / 약 30~40 page 1차 대상) + SlipDetailPage 시드 패턴 PR-H2 commit `435918c` 1:1 복제 6 단계 가이드 + 한국어 라벨 매핑 표 (도메인 5 시범) + UUID 비공개 가드 + PR-H4c 50+ page 적용 체크리스트 + mobile-staff RN 확장 가이드 |
| `docs/devops/redis-realtime-broker.md` 보강 (143 line 추가) | shared module + AWS ElastiCache cache.t3.micro ~₩30K/월 + cutover 절차 무중단 + Testcontainers Redis 권고 + 환경변수 (`SAMHAN_REALTIME_BROKER`) + production 운영 hint |
| `docs/qa/phase-12-step-4a-shared-realtime-module/scenarios.md` 신규 (364 line) | 61 case (shared module 회귀 게이트 12 + slip-service 회귀 무손실 8 + cross-domain 색상 일관 5 + Redis fallback 4 + AutoConfig classpath 4 + multi-context SSE 회귀 5 + 시각/한국어/UUID 회귀 15 + PR-H4b/H4c 진입 게이트 8) + 페르소나 5 |

### 3.2 `3b36e2d` feat(shared+slip-service): PR-H4a shared-realtime module 추출 + slip-service 마이그

35 files +2030 -335.

#### 신규 module — `shared/realtime-abstraction/` (24 신규 file)

| 책임 | 파일 (java) | 비고 |
| --- | --- | --- |
| broker (5) | `RealtimeBroker` interface / `InMemoryRealtimeBroker` (PR-H2 `SlipRealtimeBroker` 1:1 일반화) / `RedisRealtimeBroker` (Conditional) / `BrokerConfiguration` (Bean factory + `*Bean` suffix 가드) / `RealtimePublishHook` (옵저버 hook) | `Map<String, CopyOnWriteArrayList<SseEmitter>>` + 30s heartbeat + cleanup race 방어 |
| audit (4) | `AuditLogRecorder` interface / `AuditLogEntry` `@MappedSuperclass` (BaseEntity 7 audit 상속) / `AuditEventPayloadBuilder` (5 키 SSE payload) / `ChangeEntry` record | `slip_audit_logs` PR-H2 시드 schema 일반화 |
| lock (4) | `EditLockGuard` interface / `DefaultEditLockGuard` default impl (3 카테고리 분기) / `EditLockPolicy` enum (FREE/LOCKED/FULLY 3) / `LockedException` (BusinessException 상속) | PR-H3 `SlipServiceLockGuardTest` 패턴 일반화 |
| editrequest (5) | `EditRequestService` interface / `EditRequestRecord` `@MappedSuperclass` (status transition + `consumeApproval` 1회 한정) / `EditRequestStatus` enum (PENDING/APPROVED/REJECTED/EXPIRED) / `EditRequestType` enum (EDIT/DELETE) / `EditTargetRole` enum (WAREHOUSE 등) | `slip_edit_requests` PR-H3 시드 schema 일반화 |
| autoconfig (1) | `RealtimeAutoConfiguration` (`@AutoConfiguration` + `@EnableConfigurationProperties` + `@ConditionalOnClass(SseEmitter)`) | Spring Boot 3 표준 (`spring.factories` 폐기) |

추가 자산:
- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 신규 (1 entry)
- `db/template/audit_log_template.sql` 신규 (50 line) — `<domain>_audit_logs` 패턴 (BaseEntity 7 audit + Soft Delete + 부분 인덱스)
- `db/template/edit_request_template.sql` 신규 (58 line) — `<domain>_edit_requests` 패턴 (인덱스 3 + status enum + expires_at)

#### 단위 — 7 testfile / 29 case

| 테스트 | case | 비고 |
| --- | --- | --- |
| `InMemoryRealtimeBrokerTest` | subscribe / broadcast / cleanup race / 100 emitter / 1000 publish | PR-H2 `SlipRealtimeBrokerConcurrencyIT` 패턴 일반화 |
| `RedisRealtimeBrokerTest` | subscribe / publish / connection fallback | PR-H2 `RedisRealtimeBrokerTest` 1:1 이전 |
| `AuditEventPayloadBuilderTest` | 5 키 schema (actorId/actorName/actorColor/changes[]/revisionNo) | PR-H2 `SlipAuditPayloadCaptorTest` ArgumentCaptor 패턴 일반화 |
| `EditRequestRecordTest` | status transition guard + consumeApproval 1회 한정 + expirePending | PR-H3 `SlipEditRequestServiceTest` 패턴 일반화 |
| `DefaultEditLockGuardTest` | 3 카테고리 분기 (FREE/LOCKED/FULLY) | PR-H3 `SlipServiceLockGuardTest` 패턴 일반화 |
| `EditLockPolicyTest` | enum 일관성 (FREE/LOCKED/FULLY 3) + status set 검증 | |
| `RealtimeAutoConfigurationTest` | bean 단일 등록 + Redis disabled default + classpath 분기 | shared:security AutoConfig 패턴 일관 |

#### slip-service 시범 마이그

| 파일 | 변경 |
| --- | --- |
| `services/slip-service/build.gradle` | `implementation project(':shared:realtime-abstraction')` 의존 추가 (3 line) |
| `services/slip-service/.../slip/realtime/SlipRealtimeBroker.java` | 259 → 109 line **thin facade `extends InMemoryRealtimeBroker`** (도메인 메서드 `broadcastEdit` / `broadcastEditRequestCreated` / `broadcastEditRequestDecided` 만 보존) |
| `services/slip-service/.../slip/realtime/RealtimePublishHook.java` 삭제 (25 line) | shared module 으로 이전 |
| `services/slip-service/.../slip/realtime/RedisRealtimeConfigBean.java` 삭제 (35 line) | shared module 으로 이전 |
| `services/slip-service/.../slip/realtime/RedisRealtimeBroker.java` 삭제 (153 line) | shared module 으로 이전 |
| `services/slip-service/.../slip/realtime/RedisRealtimeBrokerTest.java` 삭제 (111 line) | shared module 으로 이전 |
| `settings.gradle` | `shared:realtime-abstraction` include 보강 |

**호출자 변경 0** — `SlipService` / `SlipController` / `SlipEditRequestService` / IT 모두 변경 0 — thin facade 가 기존 도메인 메서드 signature 그대로 유지 + base subscribe/cleanup/heartbeat 만 shared 위임.

### 3.3 TM docs (본 commit) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신

| 파일 | 변경 |
| --- | --- |
| `ROADMAP.md` | Phase 12 row 갱신 (PR #123/#124/#125 머지 + 본 PR-H4a 진행) + Phase 12 section 산출물 (본 PR-H4a) 추가 + PR-H4 3분할 (H4a/H4b/H4c) 명시 + PR 매트릭스 #125 확정 + 본 PR row 추가 |
| `migration/decisions/DECISIONS.md` | D-P12-04a 신규 항목 추가 (시리즈 4 분할 사유 + 4 책임 분리 + slip 시범 마이그 호출자 0 변경 + AutoConfiguration 표준 + db template + 후속 PR-H4b/H4c plan) |
| `docs/dev-reports/integration-phase-12-step-4a-shared-realtime-module.md` 신규 | 본 dev-report |

memory `feedback_continuous_docs_sync` 일관 — 별도 docs PR 폐기 패턴 일관.

## 4. 검증

### 4.1 단위 — shared module (29 case)

- `InMemoryRealtimeBrokerTest` — 5 case PASS
- `RedisRealtimeBrokerTest` — 3 case PASS (Redis mock + connection fallback)
- `AuditEventPayloadBuilderTest` — 5 case PASS (5 키 schema)
- `EditRequestRecordTest` — 6 case PASS (status transition + consume + expire)
- `DefaultEditLockGuardTest` — 5 case PASS (3 카테고리 + 경계)
- `EditLockPolicyTest` — 3 case PASS
- `RealtimeAutoConfigurationTest` — 2 case PASS (bean + classpath 분기)

### 4.2 회귀 — slip-service (336 tests / 0 fail)

- PR-H1 SSE infra IT 5 case PASS (회귀 무손실)
- PR-H2 audit overlay IT 9 case PASS (회귀 무손실)
- PR-H3 edit-request IT 3 case PASS (회귀 무손실)
- 단위 30+ 회귀 0
- 호출자 변경 0 → thin facade 신뢰성 검증

### 4.3 풀빌드 (root)

- `gradlew assemble` — GREEN (14 backend service + shared:realtime-abstraction 모두 build PASS)

### 4.4 FE 변경 0 → 작동 캡처 면제

- 본 PR-H4a 는 BE shared module 추출 + slip 마이그 (호출자 0 변경) — FE 산출물 0 → Playwright multi-context 작동 캡처 면제
- QA 5.5.2 multi-context 회귀 게이트만 수행 — PR-H1/H2 작동 캡처 그대로 회귀 0 (스크립트 재실행 시 동일 PNG 생성 검증)
- PR body QA 섹션 = 회귀 게이트 통과 사실 + scenarios.md 링크만 (memory `feedback_pr_qa_screenshots` 면제 조건 = "BE 인프라 분리 PR 이며 FE 산출물 0")

## 5. 후속 (PR-H4a 머지 후)

- **PR-H4b (~3주) — BE 13 service 일괄 의존 추가** — partner / inventory / accounting / arologis / product / dc-config / partner-order / partner-auth / user / notification / groupware / dashboard / logging 13 backend MSA. 본 PR-H4a `shared/realtime-abstraction` 의존만 추가 + 도메인별 Flyway 신규 V N migration (`db/template/audit_log_template.sql` + `edit_request_template.sql` 1:1 복제 + 도메인 prefix 교체) + 도메인 메서드 broker thin facade. 본 PR-H4a 머지 후 즉시 진입.
- **PR-H4c (~3주) — FE 50+ page UI 통합** — desktop `<Domain>DetailPage` 일괄 audit overlay + edit-request banner + mobile-staff 적용 (DispatchScreen / StockAdjustScreen 등) + Designer wireframe 도메인별 1건씩. PR-H4b 머지 후 진입.

## 6. 제약 / 가드 일관

- **`shared:common` / `shared:security` 패턴 일관** — java-library + Spring Boot dependency-management bom + Spring Boot plugin 미적용 (consumer service 만 plugin 적용)
- **AutoConfiguration 표준** — Spring Boot 3 `AutoConfiguration.imports` (legacy `spring.factories` 폐기)
- **`*Bean` suffix 가드 (PR #119 회귀 가드 일관)** — `BrokerConfiguration` (정확히 Configuration class) + `RealtimeAutoConfiguration` (정확히 AutoConfiguration class) 명명 가드
- **BaseEntity 7 audit + Soft Delete 의무** — `AuditLogEntry` / `EditRequestRecord` `@MappedSuperclass` 모두 BaseEntity 상속 + db template 7 audit field 채움
- **호출자 변경 0 의무 (slip-service 시범 마이그)** — thin facade 패턴 + 도메인 메서드 signature 그대로 유지 + 기존 IT/단위 회귀 0 검증
- **Redis 의존 `compileOnly` 의무** — consumer service 가 `starter-data-redis` 의존 시만 활성 (default 환경 영향 0)
- **한국어 Javadoc 의무** — shared module 19 신규 file 모두 한국어 Javadoc (PR-H4b 13 service 의존 — 의도 명확화)
- **`@MappedSuperclass` 패턴** — entity 본체는 consumer service 가 자체 정의 (도메인 prefix 교체 + 추가 컬럼 자유) — shared 는 base 만
- **외부 SaaS 의존 0** — Redis 도 사내 (AWS ElastiCache 또는 self-host) — Pusher / Firebase / Ably 회피

## 7. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR-H4a = 5-team 병렬 (BE shared + Designer + DevOps + QA + TM) Phase A 2 + TM docs 1 = 단일 통합 PR (총 3 commits). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신. **slip-service 마이그 회귀 0 + 풀빌드 GREEN** — 별도 후속 fix PR 회피.

## 8. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-H4b (BE 13 service 일괄 ~3주) 즉시 진입
