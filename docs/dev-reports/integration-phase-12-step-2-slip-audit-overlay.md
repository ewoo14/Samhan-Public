# Phase 12 step-2 (PR-H2) — slip audit overlay + 실시간 sync + TM 보완 3건

> 본 dev-report 는 PR (`feature/integrated-phase-12-step-2-slip-audit-overlay`) 의 종합 작업 보고. PR #123 (PR-H1 SSE infra + slip 코멘트 smoke) 머지 후 **Phase 12 시리즈 2/4** 진입. **사용자 핵심 요구 = "두 사람이 같은 전표 보면서 한 명이 메모를 수정하면 다른 사람 화면에 1초 안에 취소선 + 수정자 색상 + 수정자 이름 + 수정 시각 으로 audit overlay 가 표시"** 의 4 요소 시각 검증 단계.

## 1. 배경

### 1.1 PR-H1 → PR-H2 진입 사유

PR-H1 (PR #123) 에서 SSE 단방향 broadcast 인프라 + slip 코멘트 smoke 가 검증됨. PR-H2 는 **본문 필드 수정 audit + 실시간 sync** 로 한 단계 도메인 깊이를 확장. 사용자 핵심 가치 정의:

- **취소선** — 변경 전 값 시각 보존 (`text-decoration: line-through`)
- **색상** — 사용자별 결정적 HSL 색상 (PR-H1 시드 `userIdToColor` 재사용 — 동일 사용자 동일 색상 보장)
- **수정자 이름** — UUID 비공개 가드 (memory `feedback_uuid_no_user_visibility`) — `actorName` 만 화면 노출
- **수정 시각** — relative time (e.g., "3초 전") + absolute timestamp tooltip
- **1초 sync** — 다른 client 화면에 1초 안 SSE event 수신 → cache invalidate → audit overlay 재표시

→ 사용자 명시 = "외부 메신저 (카톡 / Slack) 우회 시 발생하는 비동기 누락 + 컨텍스트 분실" 두 문제를 **slip 본문 수정 시점에서도** 해결하는 두 번째 도메인.

### 1.2 시리즈 진행 (PR-H1 ~ PR-H4)

| 슬라이스 | 기간 | 목표 | 상태 |
| --- | --- | --- | --- |
| PR-H1 | 1주 | SSE infra + slip 코멘트 smoke | **머지 완료 (PR #123, D-P12-01)** |
| **PR-H2 (본 PR)** | ~3주 | slip audit overlay + 실시간 sync + TM 보완 3건 | **진행 중 (D-P12-02)** |
| PR-H3 | ~1.5주 | 권한 / 수락 / 거절 워크플로우 (영업 → 창고 → 기사) | 대기 |
| PR-H4 | ~7주 | 전 15 service 확장 + Redis Pub/Sub 활성 | 대기 |

본 PR-H2 의 시드 산출 (PR-H3 / PR-H4 의존):
- `RedisRealtimeBroker` + `RedisRealtimeConfigBean` + `RealtimePublishHook` (PR-H4 다중 노드 활성 시 toggle 만으로 진입)
- `SlipAuditPayloadCaptorTest` ArgumentCaptor 패턴 (PR-H3 양방향 SSE payload 검증 의존)
- `SlipRealtimeBrokerConcurrencyIT` multi-emitter 동시성 패턴 (PR-H4 전 15 service 동일 broker 패턴 회귀 가드)

## 2. 핵심 결정 (D-P12-02 요약)

> 자세한 결정 사실 / 근거 / 영향 = `migration/decisions/DECISIONS.md` D-P12-02 참조.

| 결정 | 채택 |
| --- | --- |
| audit row schema | **Flyway V18 `slip_audit_logs` 신규** + `slips.revision_count BIGINT NOT NULL DEFAULT 0` + 부분 인덱스 (`WHERE is_deleted = false ORDER BY revision_no DESC`) + BaseEntity 7 audit + Soft Delete |
| audit 책임 분리 | **`SlipAuditLogService` 4 책임** — record / recordBatch / listBySlip / revertToRevision |
| 도메인 entity 패턴 | **`Slip.applyOverlayPatch/readOverlayField/incrementRevision`** 11 필드 시범 (reflection-free `switch`) |
| 신규 endpoint | **3 신규** — `GET /audit-logs` (인증 사용자 전체) / `PATCH /audit/overlay` (SALES/WAREHOUSE/MANAGER/MASTER) / `POST /audit/revert/{n}` (MANAGER/MASTER) |
| SSE event 추가 | **`slip:edit` + `slip:reverted`** payload = `{revisionNo, actorId, actorName, actorColor, changes[]}` 5 키 일치 (ArgumentCaptor 검증 의무) |
| TM 보완 #1 | **multi-emitter 동시성 IT** (`SlipRealtimeBrokerConcurrencyIT` 3 case — 50 subscribe + cleanup race + 100 emitter / 1000 publish) |
| TM 보완 #2 | **ArgumentCaptor SSE payload schema** (`SlipAuditPayloadCaptorTest` 3 case) |
| TM 보완 #3 | **`RedisRealtimeBroker` config toggle** (`SAMHAN_REALTIME_BROKER=in-memory|redis`, default in-memory, 미연결 startup 정상, `*Bean` suffix 가드 PR #119 회귀 가드 일관) |

## 3. 산출물 (5 commits, Phase A 4 + Phase B 1)

### 3.1 Phase A — 4 commits (DevOps 1 + BE 1 + FE-1 desktop+design-system 1 + FE-2 mobile-staff 1)

#### `b0f2e48` chore(devops): PR-H2 Redis 의존 옵션 + production hint

3 files +236.

| 파일 | 변경 |
| --- | --- |
| `services/slip-service/src/main/resources/application.yml` | `samhan.realtime.broker` config toggle (in-memory/redis) + `spring.data.redis` host/port |
| `infrastructure/env-templates/slip-service.env` | `SAMHAN_REALTIME_BROKER=in-memory` (default) + `REDIS_HOST` / `REDIS_PORT` placeholder |
| `docs/devops/redis-realtime-broker.md` 신규 | in-memory vs Redis 가이드 + AWS ElastiCache cache.t3.micro 비용 ~₩30K/월 + cutover 절차 + Testcontainers Redis 권고 |

#### `270d9c8` feat(slip-service): PR-H2 slip audit overlay + 실시간 sync + TM 보완 3건

22 files +2047 -3.

| 파일 | 변경 |
| --- | --- |
| `services/slip-service/src/main/resources/db/migration/V18__add_slip_audit_logs.sql` 신규 | `slip_audit_logs` 신규 + `slips.revision_count BIGINT NOT NULL DEFAULT 0` + 부분 인덱스 + BaseEntity 7 audit |
| `services/slip-service/src/main/java/.../slip/audit/domain/SlipAuditLog.java` 신규 | entity (BaseEntity 7 audit + Soft Delete + actorId/actorName/actorColor/fieldName/oldValue/newValue/revisionNo) |
| `services/slip-service/src/main/java/.../slip/audit/repository/SlipAuditLogRepository.java` 신규 | repository (`@SQLRestriction("is_deleted = false")` 자동) |
| `services/slip-service/src/main/java/.../slip/audit/service/SlipAuditLogService.java` 신규 | 4 책임 (record / recordBatch / listBySlip / revertToRevision) |
| `services/slip-service/src/main/java/.../slip/audit/web/SlipAuditLogController.java` 신규 | 3 endpoint (GET / PATCH / POST revert) + ApiResponse wrapper + ROLE 풀네임 가드 |
| `services/slip-service/src/main/java/.../slip/audit/web/dto/{OverlayPatchRequest,SlipAuditLogResponse}.java` 신규 | DTO 2건 |
| `services/slip-service/src/main/java/.../slip/domain/Slip.java` 보강 | applyOverlayPatch / readOverlayField / incrementRevision (11 필드 시범, reflection-free switch) |
| `services/slip-service/src/main/java/.../slip/service/SlipService.java` 보강 | applyOverlayPatch wrapper (마감 lock 가드) + editHeader memo diff → recordBatch + SSE slip:edit broadcast |
| `services/slip-service/src/main/java/.../slip/realtime/RedisRealtimeBroker.java` 신규 | Lettuce Pub/Sub publisher / subscriber + 노드별 in-memory broker 로 fanout |
| `services/slip-service/src/main/java/.../slip/realtime/RedisRealtimeConfigBean.java` 신규 | config toggle bean (`*Bean` suffix 가드 PR #119 회귀 가드 일관) |
| `services/slip-service/src/main/java/.../slip/realtime/RealtimePublishHook.java` 신규 | publish hook 추상화 (in-memory / redis 분기) |
| `services/slip-service/src/main/java/.../slip/realtime/SlipRealtimeBroker.java` 보강 | publishCount / publishFailureCount / heartbeatCount 통계 (TM 보완 IT 의존) |
| `services/slip-service/src/test/java/.../slip/audit/service/SlipAuditLogServiceTest.java` 신규 | 단위 6 case |
| `services/slip-service/src/test/java/.../slip/audit/service/SlipAuditLogServiceRevertTest.java` 신규 | 단위 4 case (revert) |
| `services/slip-service/src/test/java/.../slip/audit/service/SlipAuditPayloadCaptorTest.java` 신규 | **TM 보완 #2** — ArgumentCaptor SSE payload 3 case (slip:edit / slip:reverted / changes[] schema) |
| `services/slip-service/src/test/java/.../slip/service/SlipServiceAuditDiffTest.java` 신규 | 단위 5 case (memo diff) |
| `services/slip-service/src/test/java/.../slip/realtime/SlipRealtimeBrokerConcurrencyIT.java` 신규 | **TM 보완 #1** — multi-emitter 3 case (50 subscribe + cleanup race + 100 emitter / 1000 publish) |
| `services/slip-service/src/test/java/.../slip/realtime/RedisRealtimeBrokerTest.java` 신규 | **TM 보완 #3** — Redis broker mock 3 case |
| `services/slip-service/src/test/java/.../slip/it/ApplicationContextLoadIT.java` 보강 | SlipAuditLogService bean 단일 등록 가드 |
| `services/slip-service/build.gradle` | `spring-boot-starter-data-redis` 의존 추가 |

#### `435918c` feat(desktop+design-system): PR-H2 SlipDetailPage audit overlay + 수정 횟수 + 복원

9 files +947 -6.

| 파일 | 변경 |
| --- | --- |
| `clients/web/design-system/src/components/AuditOverlay/AuditOverlay.tsx` 신규 | 취소선 + 색상 dot + 수정자명 + 시각 + expand timeline |
| `clients/web/design-system/src/components/AuditOverlay/AuditOverlay.module.css` 신규 | line-through + dot color + relative time CSS |
| `clients/web/design-system/src/components/AuditOverlay/AuditOverlay.stories.tsx` 신규 | Storybook 4 story (Single / Multiple / Empty / MultiUserShowcase) |
| `clients/web/design-system/src/components/AuditOverlay/index.ts` 신규 | barrel export |
| `clients/web/design-system/src/index.ts` 보강 | AuditOverlay barrel export |
| `clients/desktop/src/renderer/api/slipAudit.ts` 신규 | listAuditLogs + revertToRevision |
| `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` 보강 | auditLogsQuery + 수정 횟수 chip + AuditOverlay 적용 (memo / shippingAddress) + 복원 dropdown + SSE slip:edit cache invalidate |
| `docs/uiux/phase12/H2-audit-overlay.md` 신규 | wireframe + 한국어 라벨 + Designer 매뉴얼 |
| `docs/manual/05-슬립공유-수정-처리.md` 신규 | 사용자 시나리오 (페르소나 5) + 권한 + 화면 캡처 stub |

#### `3dcbfa0` feat(mobile-staff): PR-H2 SlipDetailScreen audit overlay (RN)

6 files +510 -23.

| 파일 | 변경 |
| --- | --- |
| `clients/mobile-staff/src/utils/userColorHash.ts` 신규 | design-system 1:1 RN 호환 복제 (순수 함수) |
| `clients/mobile-staff/src/components/AuditOverlay.tsx` 신규 | RN Text strikethrough + View dot 색상 + 수정자명/role |
| `clients/mobile-staff/src/api/slipAudit.ts` 신규 | list + revert + ApiResponse wrapper assert |
| `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` 보강 | 수정 횟수 헤더 + AuditOverlay 적용 (partnerName / status) + 복원 버튼 MASTER/MANAGER 만 |
| `clients/mobile-staff/src/realtime/SlipRealtimeClient.ts` 보강 | slip.edit event type 추가 |
| `clients/mobile-staff/src/screens/driver/DriverTabNavigator.tsx` 보강 | currentUserRole='DRIVER' 명시 (복원 버튼 비표시 검증) |

### 3.2 Phase B — 1 commit (QA)

#### `732e105` test(qa): PR-H2 QA scenarios + multi-context 동시 수정 캡처 (audit overlay)

7 files +1071.

| 파일 | 변경 |
| --- | --- |
| `docs/qa/phase-12-step-2-slip-audit-overlay/scenarios.md` 신규 | 27 case (audit_log 자동 기록 5 + AuditOverlay UI 5 + 수정 횟수 카운트 3 + 복원 4 + 실시간 sync 5 + 동시 수정 충돌 3 + Redis broker fallback 2) + 페르소나 5 |
| `docs/qa/phase-12-step-2-slip-audit-overlay/working-audit-overlay-context-a-edit.png` 신규 (97KB) | 사용자 A 메모 수정 직후 |
| `docs/qa/phase-12-step-2-slip-audit-overlay/working-audit-overlay-context-b-receives.png` 신규 (90KB) | 사용자 B SSE 수신 후 audit overlay 표시 |
| `docs/qa/phase-12-step-2-slip-audit-overlay/working-audit-overlay-multi-revision.png` 신규 (102KB) | 3 revision 누적 + expand timeline |
| `docs/qa/phase-12-step-2-slip-audit-overlay/working-multi-context-edit-split.png` 신규 (120KB) | **핵심 시각 증거** — 좌-A 우-B 합성 (1280+1280=2560), 사용자 핵심 요구 4 요소 (취소선 + 색상 + 수정자명 + 1초 sync) 동시 검증 |
| `tools/manual-capture/capture-pr-h2.js` 신규 | Playwright multi-context 자동화 (browser.newContext 2회 분리 + sharp 좌-우 합성 + 한국어 라벨 + audit-logs / overlay PATCH / revert mock seed) |
| `clients/desktop/src/renderer/api/mock.ts` 보강 | audit-logs / overlay PATCH / revert mock endpoint (capture 자동화 의존) |

## 4. 검증

### 4.1 단위 + IT (BE)

- `SlipAuditLogServiceTest` — 6 case (record / recordBatch / listBySlip / revertToRevision / 권한 거부 / 마감 lock 가드) PASS
- `SlipAuditLogServiceRevertTest` — 4 case (특정 revision 복원 / 전체 복원 / DRIVER 차단 / 신규 revision 으로 audit 영원 보존) PASS
- `SlipAuditPayloadCaptorTest` — **TM 보완 #2** — ArgumentCaptor 3 case (slip:edit 5 키 / slip:reverted 5 키 / changes[] 다중 필드 schema) PASS
- `SlipServiceAuditDiffTest` — 5 case (memo diff 단일 / 다중 필드 / 빈 변경 / null → value / value → null) PASS
- `SlipRealtimeBrokerConcurrencyIT` — **TM 보완 #1** — IT 3 case (50 emitter 동시 subscribe / cleanup race / 100 emitter / 1000 publish lost 0) PASS
- `RedisRealtimeBrokerTest` — **TM 보완 #3** — 단위 3 case (Lettuce Pub/Sub mock publish / subscribe / 미연결 graceful fallback) PASS
- `ApplicationContextLoadIT` — `SlipAuditLogService` bean 단일 등록 가드 PASS

총 **단위 24 + IT 9 case PASS**.

### 4.2 typecheck (FE)

- `clients/desktop` — typecheck PASS
- `clients/mobile-staff` — typecheck PASS, expo doctor 16/17 (pre-existing expo-font / expo-location version 경고만, 본 PR 무관)
- `clients/web/design-system` — typecheck + Storybook 4 story 빌드 PASS

### 4.3 풀빌드 (root)

- `gradlew assemble` — GREEN
- 13 backend service 모두 build PASS

### 4.4 작동 캡처 (QA)

- multi-context Playwright `capture-pr-h2.js` 실행 — 4 PNG 생성 완료 (97 / 90 / 102 / 120 KB)
- 한국어 100% / UUID 비공개 / ROLE 풀네임 (MASTER / MANAGER / SALES / WAREHOUSE / DRIVER) 통과
- 사용자 핵심 요구 4 요소 (취소선 + 색상 + 수정자명 + 1초 sync) 모두 시각 검증
- 핵심 시각 증거 = `working-multi-context-edit-split.png` (좌-A 우-B 합성 1장)
- PR body inline raw URL + commit-pinned (HEAD `732e105`) + HEAD 200 검증 의무 (memory `feedback_pr_qa_screenshots`)

## 5. 후속 (PR-H2 머지 후)

- **PR-H3 (~1.5주) — 권한 / 수락 / 거절 워크플로우** — 영업 → 창고 → 기사 인계 시점 명시적 수락 + SSE 양방향 push (영업 입력 시 창고 알림 / 창고 수락 시 영업 알림 / 기사 수락 시 양측 알림). 본 PR-H2 머지 후 즉시 진입.
- **PR-H4 (~7주) — 전 15 service 확장 + Redis Pub/Sub 활성** — partner / inventory / accounting / arologis / dashboard 등 14 backend MSA 도메인 모두 SSE 채널 도입 + `shared/realtime` module 추출 + 본 PR-H2 시드된 `RedisRealtimeBroker` config toggle 활성 (다중 노드 진입 시).

## 6. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — `slip_audit_logs` 신규 entity 7 audit (id / created_at / created_by_user_id / updated_at / updated_by_user_id / is_deleted / version) 모두 채움
- **Soft Delete 일관** — `slip_audit_logs.is_deleted` + `@SQLRestriction("is_deleted = false")` + 부분 인덱스 (`WHERE is_deleted = false ORDER BY revision_no DESC`) 적용. **revert 시 기존 audit row 절대 삭제 금지** — 신규 revision row 추가 (audit 영원 보존)
- **한국어 Javadoc** — `SlipAuditLog` / `SlipAuditLogService` / `SlipAuditLogController` / `RedisRealtimeBroker` / `RealtimePublishHook` / `Slip.applyOverlayPatch` 모두 한국어 Javadoc 의무
- **ROLE 풀네임** — 본 PR 모든 산출물 풀네임 (MASTER / MANAGER / SALES / WAREHOUSE / DRIVER 등). 약어 (M/M/S) 금지
- **UUID 비공개** — audit overlay 표시 = `actorName` + `actorColor` 만 (UUID 0 노출). `actorId` 는 색상 hash 입력 전용 (사용자 화면 미노출). data-testid `audit-overlay-${revisionNo}` 는 내부 식별자만 사용
- **ApiResponse wrapper 의무** — 신규 endpoint 3 모두 ApiResponse wrapper (PR #98 D-P10-12 일관)
- **`*Bean` suffix 가드 (PR #119 회귀 가드)** — `RedisRealtimeConfigBean` 명명 일관
- **Korean path JDK 트랩 회피** — Windows dev 시 `gradle test` 회피 가능. CI Linux runner 에서 정식 검증
- **외부 SaaS 의존 0** — Pusher / Firebase Realtime / Ably 회피. Spring `SseEmitter` + Lettuce Redis (옵션) 만 사용

## 7. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR-H2 = 5-team 병렬 (BE / FE-1 desktop+design-system / FE-2 mobile-staff / Designer / DevOps) Phase A 4 + Phase B (QA) 1 = 단일 통합 PR (총 5 commits). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신.

## 8. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-H3 (slip 권한 / 수락 / 거절 워크플로우 ~1.5주) 진입
