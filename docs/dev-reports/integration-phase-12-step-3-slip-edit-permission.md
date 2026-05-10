# Phase 12 step-3 (PR-H3) — slip 수정/삭제 요청 워크플로우 + status 잠금 가드

> 본 dev-report 는 PR (`feature/integrated-phase-12-step-3-slip-edit-permission`) 의 종합 작업 보고. PR #124 (PR-H2 audit overlay + 실시간 sync) 머지 후 **Phase 12 시리즈 3/4** 진입. **사용자 핵심 워크플로우 = "잠금 → 요청 → 알림 → 수락 → 해제"** 5 단계 검증 단계.

## 1. 배경

### 1.1 PR-H2 → PR-H3 진입 사유

PR-H2 (PR #124) 에서 audit overlay (취소선 + 색상 + 수정자명 + 1초 sync) + Flyway V18 + 실시간 sync 가 검증됨. PR-H3 는 **"누가 언제 변경했는지" 의 audit 영구 기록 위에 한 단계 더 = "변경할 권리를 어떻게 통제할지" 의 명시적 권한 워크플로우** 도입. 사용자 명시 잠금 정책:

- **DRAFT / SAVED / SENT** — 작성자 직접 수정 자유 (별도 요청 채널 차단, `INVALID_INPUT` 400)
- **CONFIRMED / ACCEPTED / PROCESSING** (`LOCKED_REQUIRES_APPROVAL`) — 작성자 직접 수정 차단 + 별도 요청 채널 + 창고 (혹은 관리자) 수락 → APPROVED 1건 한정 mutation 진행 + mutation 직후 즉시 소진 (audit 무력화 차단)
- **INSPECTING / SHIPPING / DELIVERED** (`FULLY_LOCKED`) — 검수 무결성 + 배송 진행 중 데이터 변동 차단 + 한국 일반기업회계기준 보존 의무 (관리자도 force 우회 차단, 별도 SQL audit 채널만 허용)

→ 사용자 명시 = "외부 메신저 (카톡 / Slack) 우회 시 발생하는 비동기 누락 + 컨텍스트 분실" 두 문제를 **권한 단계에서도** 해결하는 세 번째 도메인.

### 1.2 시리즈 진행 (PR-H1 ~ PR-H4)

| 슬라이스 | 기간 | 목표 | 상태 |
| --- | --- | --- | --- |
| PR-H1 | 1주 | SSE infra + slip 코멘트 smoke | **머지 완료 (PR #123, D-P12-01)** |
| PR-H2 | ~3주 | slip audit overlay + 실시간 sync + TM 보완 3건 | **머지 완료 (PR #124, D-P12-02)** |
| **PR-H3 (본 PR)** | ~1.5주 | slip 수정/삭제 요청 워크플로우 + 잠금 가드 | **진행 중 (D-P12-03)** |
| PR-H4 | ~7주 | 전 15 service + 50+ page audit+sync+권한 일괄 확장 | 대기 |

본 PR-H3 의 시드 산출 (PR-H4 의존):
- `SlipEditRequestService` 6 책임 패턴 (request / approve / reject / listPendingForRole / findActiveApproval+consumeApproval / `@Scheduled` expirePending) — PR-H4 시점 14 backend MSA 도메인에 동일 패턴 일괄 확장 (partner / inventory / accounting / arologis / dashboard 모두 `EditRequestService`)
- `LOCKED_REQUIRES_APPROVAL` / `FULLY_LOCKED` 분류 — PR-H4 도메인별 status enum 에 동일 분류 적용 (도메인 라이프사이클 단계의 권한 매트릭스 일관)
- `NotificationClient` graceful fallback 패턴 — 14 도메인 모두 notification-service Internal Feign 재사용 (실패가 비즈니스 로직 차단하면 협력 워크플로우 마비 → graceful fallback 의무)

## 2. 핵심 결정 (D-P12-03 요약)

> 자세한 결정 사실 / 근거 / 영향 = `migration/decisions/DECISIONS.md` D-P12-03 참조.

| 결정 | 채택 |
| --- | --- |
| 요청 row schema | **Flyway V19 `slip_edit_requests` 신규** + 인덱스 3 (`slip_id` / `status_target` / `expires_at`) + BaseEntity 7 audit + Soft Delete |
| 도메인 책임 분리 | **`SlipEditRequestService` 6 책임** — request / approve / reject / listPendingForRole / findActiveApproval+consumeApproval / `@Scheduled` expirePending |
| 잠금 정책 분류 | **3 카테고리** — `FREE_DIRECT_EDIT={DRAFT,SAVED,SENT}` / `LOCKED_REQUIRES_APPROVAL={CONFIRMED,ACCEPTED,PROCESSING}` / `FULLY_LOCKED={INSPECTING,SHIPPING,DELIVERED}` (사용자 명시) |
| 1회 한정 소진 | **`findActiveApproval` + `consumeApproval` 패턴** — mutation 직후 row soft-delete 로 audit 무력화 차단 |
| 신규 endpoint | **4 신규** — POST /edit-request (작성자 그룹) / POST .../approve / POST .../reject / GET /edit-requests?status=PENDING (창고 그룹) |
| SSE event | **`slip:edit-request:created`** (요청 생성 → 창고 + 작성자) + **`slip:edit-request:decided`** (수락/거절/만료 → 작성자 toast) |
| 알림 통합 | **`NotificationClient` notification-service Internal Feign + graceful fallback** (try/catch FeignException, slip 비즈니스 진행) |
| 만료 정책 | **24h default + `samhan.slip.edit-request.expires-hours` 환경변수** + `@Scheduled fixedRate=1h` |
| TM 후속 fix | **`69779b8` BE/FE 정책 정합** — CONFIRMED LOCKED_REQUIRES_APPROVAL 이동 + FE `isConfirmed` → `isApprovalRequired` 정정 + LockGuard 7 case + ServiceTest 9 case 회귀 가드 보강 (본 PR 안에서 fix 완료) |

## 3. 산출물 (7 commits, Phase A 4 + Phase B 1 + TM fix 1 + docs 1)

### 3.1 Phase A — 4 commits (DevOps 1 + FE-2 mobile-staff 1 + FE-1 desktop+design-system+uiux+manual 1 + BE 1)

#### `0e6785e` chore(devops): PR-H3 slip 수정 요청 알림 production 가이드 + 만료 시간 환경변수

2 files +185.

| 파일 | 변경 |
| --- | --- |
| `docs/devops/slip-edit-request-notification.md` 신규 | Aligo SMS + Expo push production 가이드 (환경변수 / Secret Manager / 멱등 키 / 재시도 정책) |
| `services/slip-service/src/main/resources/application.yml` | `samhan.slip.edit-request.expires-hours=24` (default) |

#### `83cdf67` feat(mobile-staff): PR-H3 slip 수정 요청 + 창고 직원 수락 모바일 UI

4 files +1541 -12.

| 파일 | 변경 |
| --- | --- |
| `clients/mobile-staff/src/api/slipEditRequest.ts` 신규 | request / approve / reject / list / listPending |
| `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` 보강 | 작성자 SALES 수정 요청 + 창고 직원 WAREHOUSE PENDING 카드 분기 + DRIVER 차단 |
| `clients/mobile-staff/src/screens/SlipEditRequestsScreen.tsx` 신규 | 창고 직원 inbox + 수락/거절 + 30s polling |
| `clients/mobile-staff/src/realtime/SlipRealtimeClient.ts` 보강 | `slip.edit-request.{created,approved,rejected}` event + foreground `Alert.alert` |

#### `f5ada0c` feat(uiux+manual): PR-H3 수정 요청 UX + 잠금 정책 매뉴얼

13 files +2001 -3.

| 파일 | 변경 |
| --- | --- |
| `clients/web/design-system/src/components/SlipEditRequestDialog/{SlipEditRequestDialog.tsx,.module.css,.stories.tsx,index.ts}` 4 신규 | 사유 textarea ≥ 10자 + 500자 카운터 + EDIT/DELETE danger variant + Storybook 3 story (Edit / Delete / Submitting) |
| `clients/web/design-system/src/index.ts` | barrel export 보강 |
| `clients/desktop/src/renderer/api/slipEditRequest.ts` 신규 | create / approve / reject / list + `SLIP_EDIT_REQUEST_REVIEWER_ROLES` + `SLIP_EDIT_REQUEST_AUTHOR_ROLES` + 라벨 매핑 |
| `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` 보강 | `editRequestDialogType` state + `latestEditRequest` state + SSE `slip:edit-request:decided`/`created` 핸들러 + `slip-detail-edit-request-banner` (LOCKED_REQUIRES_APPROVAL 작성자 노출) + `slip-detail-locked-banner` (FULLY_LOCKED) + `decisionToast` |
| `clients/desktop/src/renderer/routes/admin/SlipEditRequestsPage.tsx` 신규 | PENDING list 표 + 수락 confirm + 거절 사유 dialog (≥ 5자) + 30s polling fallback |
| `clients/desktop/src/renderer/components/AppLayout.tsx` 보강 | `sidebar-warehouse-slip-edit-requests` NavLink (WAREHOUSE/MANAGER/MASTER 가시) |
| `clients/desktop/src/renderer/routes/index.tsx` 보강 | admin/slip-edit-requests 라우트 등록 |
| `docs/uiux/phase12/H3-edit-request-workflow.md` 신규 | flow chart + 잠금 정책 + 한국어 라벨 + Designer 매뉴얼 |
| `docs/manual/02-출고-처리.md` 보강 | "수정/삭제 요청" section |
| `docs/manual/03-역할별-권한.md` 보강 | 잠금 정책 표 (status × ROLE 매트릭스) |

#### `1ebc00a` feat(slip-service): PR-H3 slip 수정/삭제 요청 워크플로우 + status 잠금 가드

18 files +2094.

| 파일 | 변경 |
| --- | --- |
| `services/slip-service/src/main/java/.../slip/editrequest/domain/{SlipEditRequest,SlipEditRequestType,SlipEditRequestStatus,SlipEditTargetRole}.java` 4 신규 | entity (BaseEntity 7 audit + Soft Delete) + 3 enum (Type EDIT/DELETE / Status PENDING/APPROVED/REJECTED/EXPIRED / TargetRole WAREHOUSE) |
| `services/slip-service/src/main/java/.../slip/editrequest/repository/SlipEditRequestRepository.java` 신규 | `@SQLRestriction("is_deleted = false")` 자동 + slip-id / status-target-role / expires-at 쿼리 |
| `services/slip-service/src/main/java/.../slip/editrequest/service/SlipEditRequestService.java` 신규 | 6 책임 (request / approve / reject / listPendingForRole / findActiveApproval+consumeApproval / `@Scheduled` expirePending fixedRate=1h) |
| `services/slip-service/src/main/java/.../slip/editrequest/web/SlipEditRequestController.java` 신규 | 4 endpoint + ApiResponse wrapper + ROLE 풀네임 가드 |
| `services/slip-service/src/main/java/.../slip/editrequest/web/dto/{ApproveRequest,CreateEditRequestRequest,RejectRequest,SlipEditRequestResponse}.java` 4 신규 | DTO 4건 (UUID 비공개 응답 — slipNo / requesterName / decidedByName 만 화면 노출 의도) |
| `services/slip-service/src/main/java/.../slip/client/NotificationClient.java` 신규 | notification-service Internal Feign (`@FeignClient` + `try/catch FeignException` graceful fallback + warning log) |
| `services/slip-service/src/main/java/.../slip/config/SlipEditRequestProperties.java` 신규 | `samhan.slip.edit-request.expires-hours` `@ConfigurationProperties` binding |
| `services/slip-service/src/main/java/.../slip/service/SlipService.java` 보강 | `applyOverlayPatch` 잠금 가드 (`findActiveApproval` + `consumeApproval`) + `softDelete` 신규 (DELETE 요청 수락 후 1회 한정 소진) |
| `services/slip-service/src/main/resources/db/migration/V19__add_slip_edit_requests.sql` 신규 | `slip_edit_requests` + 인덱스 3 (`slip_id` / `status_target` / `expires_at`) + BaseEntity 7 audit + Soft Delete |
| `services/slip-service/src/test/java/.../slip/editrequest/service/SlipEditRequestServiceTest.java` 신규 | 단위 8 case (DRAFT 거부 / ACCEPTED 정상 / INSPECTING CONFLICT / DELIVERED CONFLICT / approve transition / reject transition / 이미 종결 CONFLICT / expirePending) |
| `services/slip-service/src/test/java/.../slip/service/SlipServiceLockGuardTest.java` 신규 | 단위 6 case (DRAFT 자유 / SAVED 자유 / ACCEPTED 미승인 CONFLICT / ACCEPTED 승인 후 진행+소진 / INSPECTING 완전잠금 / DELIVERED softDelete 완전잠금) |
| `services/slip-service/src/test/java/.../slip/it/SlipEditRequestControllerIT.java` 신규 | IT 3 case (DRAFT 400 / ACCEPTED 201 + notification 호출 / approve 200 + dashboard empty) |

### 3.2 Phase B — 1 commit (QA)

#### `24b22f9` test(qa): PR-H3 QA scenarios + 작동 캡처 (수정 요청 워크플로우)

6 files +904.

| 파일 | 변경 |
| --- | --- |
| `docs/qa/phase-12-step-3-slip-edit-permission/scenarios.md` 신규 | 24 case (status 잠금 6 + FULLY_LOCKED 4 + 요청→알림→수락/거절 5 + 수락 후 잠금 해제 + 1회 소진 4 + 만료 scheduler + UX 5) + 페르소나 5 + § 8 단위/IT 정합성 |
| `docs/qa/phase-12-step-3-slip-edit-permission/working-edit-request-dialog.png` 신규 (113KB) | SALES dialog 사유 입력 |
| `docs/qa/phase-12-step-3-slip-edit-permission/working-warehouse-pending-list.png` 신규 (22KB) | WAREHOUSE PENDING list 표 |
| `docs/qa/phase-12-step-3-slip-edit-permission/working-edit-request-approved-toast.png` 신규 (95KB) | 작성자 SSE 수락 toast (`slip:edit-request:decided`) |
| `docs/qa/phase-12-step-3-slip-edit-permission/working-locked-slip-banner.png` 신규 (81KB) | LOCKED_REQUIRES_APPROVAL banner + 요청 버튼 |
| `tools/manual-capture/capture-pr-h3.js` 신규 | Playwright 자동화 (PR-H1/H2 패턴 일관) |

QA 발견 Major (FE CONFIRMED 분기 vs BE LOCKED_REQUIRES_APPROVAL 정책 불일치) `scenarios.md` § 2 🟠 Major 표기 — TM 후속 fix `69779b8` 본 PR 안에서 fix 완료.

### 3.3 TM 후속 fix — 1 commit (BE/FE 정책 정합)

#### `69779b8` fix(slip-service+desktop): PR-H3 BE/FE 잠금 정책 정합 — CONFIRMED LOCKED_REQUIRES_APPROVAL 이동

5 files +95 -29.

| 파일 | 변경 |
| --- | --- |
| `services/slip-service/src/main/java/.../slip/editrequest/service/SlipEditRequestService.java` | `LOCKED_REQUIRES_APPROVAL = {CONFIRMED, ACCEPTED, PROCESSING}` (CONFIRMED 추가) + `FULLY_LOCKED = {INSPECTING, SHIPPING, DELIVERED}` (CONFIRMED 제거) |
| `services/slip-service/src/main/java/.../slip/service/SlipService.java` | LockGuard 분류 정합 |
| `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` | `isConfirmed` → `isApprovalRequired` 명명 정정 (가독성 + status set 정확) |
| `services/slip-service/src/test/java/.../slip/editrequest/service/SlipEditRequestServiceTest.java` | 8 → 9 case (CONFIRMED 정상 PENDING 생성 회귀 가드) |
| `services/slip-service/src/test/java/.../slip/service/SlipServiceLockGuardTest.java` | 6 → 7 case (CONFIRMED + APPROVED 부재 → CONFLICT 회귀 가드) |

QA Major 본 PR 머지 전 fix → 별도 후속 PR 회피 + 통합 PR 패턴 (memory `feedback_integrated_pr_pattern`) 일관.

### 3.4 docs — 1 commit (TM 본 PR 안)

ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신 (memory `feedback_continuous_docs_sync` 일관). 별도 docs PR 폐기 패턴 일관.

## 4. 검증

### 4.1 단위 + IT (BE)

- `SlipEditRequestServiceTest` — 9 case (DRAFT 거부 / ACCEPTED 정상 / **CONFIRMED 정상 (TM fix)** / INSPECTING CONFLICT / DELIVERED CONFLICT / approve transition / reject transition / 이미 종결 CONFLICT / expirePending) PASS
- `SlipServiceLockGuardTest` — 7 case (DRAFT 자유 / SAVED 자유 / ACCEPTED 미승인 CONFLICT / ACCEPTED 승인 후 진행+소진 / **CONFIRMED 미승인 CONFLICT (TM fix)** / INSPECTING 완전잠금 / DELIVERED softDelete 완전잠금) PASS
- `SlipEditRequestControllerIT` — 3 case (DRAFT 400 / ACCEPTED 201 + notification 호출 / approve 200 + dashboard empty) PASS
- `ApplicationContextLoadIT` — `SlipEditRequestService` + `NotificationClient` bean 단일 등록 가드 PASS
- 회귀 — PR-H1 SSE infra IT 5 + PR-H2 audit overlay IT 9 모두 PASS

총 **단위 30+ + IT 3 case PASS** + 회귀 IT 14 PASS.

### 4.2 typecheck (FE)

- `clients/desktop` — typecheck PASS
- `clients/mobile-staff` — typecheck PASS
- `clients/web/design-system` — typecheck + Storybook 3 story (`SlipEditRequestDialog`) 빌드 PASS

### 4.3 풀빌드 (root)

- `gradlew assemble` — GREEN
- 14 backend service 모두 build PASS

### 4.4 작동 캡처 (QA)

- Playwright `capture-pr-h3.js` 실행 — 4 PNG 생성 완료 (113 / 22 / 95 / 81 KB)
- 한국어 100% / UUID 비공개 / ROLE 풀네임 (MASTER / MANAGER / SALES / WAREHOUSE / DRIVER) 통과
- 사용자 핵심 워크플로우 5 단계 (잠금 → 요청 → 알림 → 수락 → 해제) 모두 시각 검증
- PR body inline raw URL + commit-pinned (HEAD `69779b8`) + HEAD 200 검증 의무 (memory `feedback_pr_qa_screenshots`)

## 5. 후속 (PR-H3 머지 후)

- **PR-H4 (~7주) — 전 15 service + 50+ page audit+sync+권한 일괄 확장** — partner / inventory / accounting / arologis / dashboard 등 14 backend MSA 도메인 모두 SSE 채널 도입 + `shared/realtime` module 추출 + 본 PR-H2 시드 `RedisRealtimeBroker` config toggle 활성 (다중 노드 진입 시) + 본 PR-H3 시드 잠금 정책 (`LOCKED_REQUIRES_APPROVAL` / `FULLY_LOCKED`) + `EditRequestService` 6 책임 패턴 14 도메인 적용 (요청 → 수락 → 1회 한정 소진 + audit 무력화 차단 일관). 본 PR-H3 머지 후 즉시 진입.

## 6. 제약 / 가드 일관

- **BaseEntity 7 audit fields 의무** — `slip_edit_requests` 신규 entity 7 audit (id / created_at / created_by_user_id / updated_at / updated_by_user_id / is_deleted / version) 모두 채움
- **Soft Delete 일관** — `slip_edit_requests.is_deleted` + `@SQLRestriction("is_deleted = false")` + 부분 인덱스 (status_target). **consumeApproval = soft-delete (1회 한정 소진)** — 물리 삭제 금지, 이력 영구 보존
- **한국어 Javadoc** — `SlipEditRequest` / `SlipEditRequestService` / `SlipEditRequestController` / `NotificationClient` / `SlipService.applyOverlayPatch+softDelete` 모두 한국어 Javadoc 의무
- **ROLE 풀네임** — 본 PR 모든 산출물 풀네임 (MASTER / MANAGER / SALES / WAREHOUSE / DRIVER 등). 약어 (M/M/S) 금지
- **UUID 비공개** — 응답 DTO 는 UUID 포함 (mutation key + path variable + 색상 hash 입력 전용) 이지만 **FE 화면 노출 0** — `slipNo` / `requesterName` / `decidedByName` / `type` (한국어 "수정"/"삭제") / `reason` / `requestedAt`/`decidedAt` 만 표시. data-testid `admin-slip-edit-requests-row-{slipNo}` (UUID 비공개)
- **ApiResponse wrapper 의무** — 신규 endpoint 4 모두 ApiResponse wrapper (PR #98 D-P10-12 일관)
- **graceful fallback 의무** — `NotificationClient` 실패 시 slip 비즈니스 로직 진행 (요청 row 정상 INSERT + warning log) — 알림 실패가 협력 워크플로우 차단 금지
- **Korean path JDK 트랩 회피** — Windows dev 시 `gradle test` 회피 가능. CI Linux runner 에서 정식 검증
- **외부 SaaS 의존 0** — notification-service 의 SMS/PUSH 채널만 사용 (Aligo + Expo 자체 도메인). Pusher / Firebase / Ably 회피

## 7. 통합 PR 패턴 일관 (memory `feedback_integrated_pr_pattern`)

본 PR-H3 = 5-team 병렬 (BE / FE-1 desktop+design-system+uiux+manual / FE-2 mobile-staff / Designer / DevOps) Phase A 4 + Phase B (QA) 1 + TM 후속 fix 1 + docs 1 = 단일 통합 PR (총 7 commits). 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` 일관) — ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신. **QA Major (BE/FE 잠금 정책 불일치) 본 PR 안에서 fix 완료** — 별도 후속 PR 회피 (사용자 명시 가드 일관).

## 8. 5-team 리뷰 + CI + PM + 사용자 머지 워크플로우 (memory `feedback_pr_review_workflow`)

본 PR 머지 절차:
1. PR 발행 즉시 `gh pr checks --watch` 자동 시작 (memory `feedback_pr_ci_monitoring`)
2. 5-team 리뷰 (BE / FE / Designer / QA / DevOps) PR comment 토론 (memory `feedback_tm_led_agent_discussion`)
3. CI green + reviewer agent 토론 종료 후 TM 종합 추가 commit (필요 시)
4. PM 최종 승인 댓글 + 머지 요청 (memory `feedback_user_merge_authority`)
5. 사용자 머지
6. 머지 후 PR-H4 (전 15 service + 50+ page audit+sync+권한 일괄 확장 ~7주) 진입
