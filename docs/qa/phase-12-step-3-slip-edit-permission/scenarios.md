# PR-H3 — Phase 12 Step 3 슬립 수정/삭제 요청 워크플로우 + status 잠금 가드 QA 시나리오

> **branch** — `feature/integrated-phase-12-step-3-slip-edit-permission`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — PR-H3 산출물 (Flyway V19 `slip_edit_requests` + `SlipEditRequestService` (request/approve/reject/listPendingForRole/expirePending) + 신규 4 endpoint + `SlipService` 잠금 가드 + NotificationClient 통합 + SSE `slip:edit-request:created`/`slip:edit-request:decided` + design-system `SlipEditRequestDialog` + desktop `SlipDetailPage`/`SlipEditRequestsPage` + mobile-staff `SlipDetailScreen`/`SlipEditRequestsScreen` + 매뉴얼) 가 사용자 핵심 워크플로우 "잠금 → 요청 → 알림 → 수락 → 해제" 를 만족하는지 측정 가능한 PASS/FAIL 기준으로 명세.
> **연관 산출물** —
> - BE-Schema: `services/slip-service/src/main/resources/db/migration/V19__add_slip_edit_requests.sql` (`slip_edit_requests` 테이블 + 인덱스 3 — `idx_slip_edit_requests_slip_id`, `idx_slip_edit_requests_status_target`, `idx_slip_edit_requests_expires_at`)
> - BE-Domain: `SlipEditRequest` (BaseEntity 7 audit + Soft Delete) + 3 enum (`SlipEditRequestType` EDIT/DELETE, `SlipEditRequestStatus` PENDING/APPROVED/REJECTED/EXPIRED, `SlipEditTargetRole` WAREHOUSE)
> - BE-Service: `SlipEditRequestService` 6 책임 — `request` / `approve` / `reject` / `listPendingForRole` / `listBySlip` / `findActiveApproval` / `consumeApproval` / `expirePending` (`@Scheduled fixedRate=3_600_000L`)
> - BE-Service: `SlipService` 잠금 가드 — `applyOverlayPatch` / `softDelete` 신규 — DRAFT/SAVED/SENT 자유, ACCEPTED/PROCESSING `findActiveApproval` 1건 필요 + `consumeApproval`, INSPECTING/SHIPPING/DELIVERED/CONFIRMED 완전 잠금
> - BE-Web: `SlipEditRequestController` 4 신규 endpoint — `POST /api/v1/slips/{slipId}/edit-request` (작성자) / `POST /api/v1/slips/{slipId}/edit-request/{requestId}/approve` (창고/관리자) / `POST /api/v1/slips/{slipId}/edit-request/{requestId}/reject` (창고/관리자) / `GET /api/v1/slips/edit-requests?status=PENDING` (창고/관리자 대시보드) + `GET /api/v1/slips/{slipId}/edit-requests` (슬립별 이력)
> - BE-Realtime: `SlipRealtimeBroker.publish` payload — `event=slip:edit-request:created` (요청 생성 시 창고 대시보드 + 작성자 화면 동시 broadcast) / `event=slip:edit-request:decided` (수락/거절/만료 시 작성자 화면 broadcast)
> - BE-External: `NotificationClient` (notification-service Internal Feign — SMS/PUSH graceful fallback, 실패 시 slip 비즈니스 로직 진행 + warning log)
> - BE-Test: `SlipEditRequestServiceTest` (8 case — request DRAFT 거부/ACCEPTED 정상/INSPECTING CONFLICT/DELIVERED CONFLICT/approve transition/reject transition/이미 종결된 요청 CONFLICT/expirePending 자동만료) + `SlipServiceLockGuardTest` (6 case — DRAFT 자유/SAVED 자유/ACCEPTED 미승인 CONFLICT/ACCEPTED 승인 후 진행+소진/INSPECTING 완전잠금/DELIVERED softDelete 완전잠금) + `SlipEditRequestControllerIT` (3 case — DRAFT 400 / ACCEPTED 201 + notification 호출 / approve 200 + dashboard empty) — 단위 14 + IT 3
> - FE-Design-System: `clients/web/design-system/src/components/SlipEditRequestDialog/SlipEditRequestDialog.tsx` (사유 textarea ≥ 10자 + 500자 카운터, type=EDIT/DELETE, danger variant 분기) + `.module.css` + `.stories.tsx` (3 story — Edit / Delete / Submitting)
> - FE-Desktop: `clients/desktop/src/renderer/api/slipEditRequest.ts` (`createSlipEditRequest`/`approveSlipEditRequest`/`rejectSlipEditRequest`/`listSlipEditRequests` + `SLIP_EDIT_REQUEST_REVIEWER_ROLES` + `SLIP_EDIT_REQUEST_AUTHOR_ROLES` + 라벨 매핑)
> - FE-Desktop: `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` 보강 — `editRequestDialogType` state + `latestEditRequest` state + SSE `slip:edit-request:decided`/`created` 핸들러 + `slip-detail-edit-request-banner` (CONFIRMED 작성자 노출) + `slip-detail-locked-banner` (INSPECTING/COMPLETED/SHIPPING/DELIVERED) + `decisionToast` (수락/거절 결과)
> - FE-Desktop: `clients/desktop/src/renderer/routes/admin/SlipEditRequestsPage.tsx` 신규 — PENDING list 표 + 수락 confirm + 거절 사유 dialog (≥ 5자) + 30초 polling (SSE 미가용 fallback)
> - FE-Desktop: `clients/desktop/src/renderer/components/AppLayout.tsx` 보강 — `sidebar-warehouse-slip-edit-requests` NavLink (WAREHOUSE/MANAGER/MASTER 가시)
> - FE-Mobile: `clients/mobile-staff/src/api/slipEditRequest.ts` + `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` (작성자 SALES 수정 요청 + 창고 직원 WAREHOUSE PENDING 카드 분기) + `clients/mobile-staff/src/screens/SlipEditRequestsScreen.tsx` (창고 직원 inbox + 수락/거절 + 30초 polling) + `SlipRealtimeClient` SSE foreground Alert
> - 매뉴얼: `docs/manual/02-출고-처리.md` "수정/삭제 요청" section + `docs/manual/03-역할별-권한.md` 잠금 정책 표 + `docs/uiux/phase12/H3-edit-request-workflow.md` (flow chart + 잠금 정책)
> - DevOps (0e6785e): `app.slip.edit-request.expires-hours` 환경변수 (default 24h) + production 가이드
> - 작동 캡처: `working-edit-request-dialog.png` + `working-warehouse-pending-list.png` + `working-edit-request-approved-toast.png` + `working-locked-slip-banner.png` (본 폴더, Playwright 자동)
> - 단위/IT 점검: 본 문서 § 8 (BE 단위 14 case + IT 3 case 정합성 평가)

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — `feedback_role_naming_full` 풀네임 의무)

| 페르소나 | ROLE | 도메인 지식 | 컴퓨터 숙련도 | 본 PR 검증 관점 |
|---|---|---|---|---|
| **신입 영업** | SALES | 신규 거래처 입력 | 일반 office | 자기 작성 슬립이 ACCEPTED 단계 진입 후 (창고 인계 후) 직접 수정 불가 → "수정/삭제 요청" 버튼만 노출. 사유 ≥ 10자 입력 후 dialog 전송 → toast 알림 수신 (수락/거절). UUID 비공개 — slipNo / requesterName 만 노출 |
| **창고원** | WAREHOUSE | 픽업/검수 | 보통 | sidebar "전표 수정 요청" 메뉴 노출 → PENDING list 표 진입. 수락 confirm 후 작성자 측 자동 잠금 해제 (1회 한정). 거절 시 사유 ≥ 5자 입력 dialog. 30초 polling 으로 멀티 워크스테이션 자동 동기화 |
| **배송 기사** | DRIVER | 배차/도착 시각 | 모바일 위주 | mobile-staff 화면에서 "수정/삭제 요청" 버튼 노출 차단 (`SLIP_EDIT_REQUEST_AUTHOR_ROLES` 에 DRIVER 미포함). PENDING list 권한 부재 → BE `@PreAuthorize` 가 403 응답 |
| **관리자** | MANAGER | 전 도메인 | 보통 | 작성자 권한 (요청 생성) + reviewer 권한 (수락/거절) 모두 보유. 창고 부재 시 운영상 처리 백업 가능. INSPECTING 등 완전 잠금 단계도 본인이 force 우회 차단 (BE 가드 동일) |
| **개발책임자 / IT 관리자** | MASTER | 전 도메인 + infra | high | broker 통계 + scheduler `expirePending` 동작 확인 + notification-service 연동 graceful fallback 검증 + 만료 24h policy 환경변수 ovrride 검증 |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소를 모두 명시:

1. **선행 조건** — fixture (V19 migration / mock slip seed / 두 client 동시 접속 상태 / `?mockRole=SALES` query string)
2. **동작** — Playwright `page.click(testid)` / API client `POST /api/v1/slips/{id}/edit-request` 의 구체 step
3. **기대 결과** — UI assertion (`expect(testid).toBeVisible()` + Badge variant 일치) + SSE event assertion (1초 안 수신 + payload 키 일치) + DB row 검증 (`slip_edit_requests` 1행 + `status=PENDING`/`APPROVED`/`REJECTED`/`EXPIRED`)
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 운영 차단 (잠금 우회 / 알림 미발생 / SSE 영구 미수신 / scheduler 미동작 / 1회 소진 누락)
- 🟠 **Major** — 작업 가능하지만 우회 / 재시도 필요 (UI 일부 표기 누락, 30초 polling fallback 미동작)
- 🟡 **Minor** — UX 사소 (사유 textarea placeholder / 카운터 색상)
- 🟢 **Info** — 향후 개선 권고 (요청 이력 timeline / 다중 PENDING 정책)

### 0.4 권한 매트릭스 (`feedback_role_naming_full` 풀네임 의무)

`MASTER` / `MANAGER` / `ACCOUNTANT` / `SALES` / `WAREHOUSE` / `DRIVER` / `DISPATCHER` / `INVENTORY` / `PARTNER` / `READONLY` 만 사용. M/M/D 약어 금지.

본 PR 권한:
- **`POST /slips/{id}/edit-request`** = `SALES` / `MANAGER` / `MASTER` (작성자 그룹)
- **`POST /slips/{id}/edit-request/{requestId}/approve`** = `WAREHOUSE` / `MANAGER` / `MASTER` (reviewer 그룹)
- **`POST /slips/{id}/edit-request/{requestId}/reject`** = `WAREHOUSE` / `MANAGER` / `MASTER` (reviewer 그룹, 사유 필수)
- **`GET /slips/edit-requests?status=PENDING`** = `WAREHOUSE` / `MANAGER` / `MASTER` (대시보드 진입 가드)
- **차단 ROLE** = `DRIVER` / `INVENTORY` / `ACCOUNTANT` / `READONLY` / `PARTNER` (POST 시 403, GET 시 403)

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility`)

- `SlipEditRequestResponse.id` (요청 UUID), `slipId` (전표 UUID), `requesterId` (요청자 UUID), `decidedBy` (결정자 UUID) 는 응답에 포함되지만 **FE 화면 노출 금지**. mutation key + path variable + 색상 hash 입력 전용
- 화면 표시 = `slipNo` ("2026/05/04-2") / `requesterName` ("오병승") / `decidedByName` ("김창고") / `type` ("수정"/"삭제") / `reason` (사유 그대로) / `requestedAt`/`decidedAt` ("2026-05-10 14:32")
- data-testid = `admin-slip-edit-requests-row-${slipNo}` (UUID 비공개), `slip-detail-edit-request-banner`, `slip-detail-locked-banner`, `slip-detail-edit-request-decision-toast`, `slip-edit-request-dialog-reason` 등
- mobile-staff 도 동일 정책 — `SLIP_EDIT_REQUEST_TYPE_LABEL` 한국어 라벨로 화면 표시

---

## 1. 슬라이스 1 — status 별 잠금 가드 6 case (사용자 핵심 정책)

**의존 backend** — `SlipEditRequestService.guardRequestableStatus` (status set 분기) + `SlipService.applyOverlayPatch` mutation 가드 (`findActiveApproval` 호출) + `SlipService.softDelete` 가드

**의존 frontend** — `SlipDetailPage.tsx` `isConfirmed`/`isLocked` 계산 + 배너 분기

### 1.1 DRAFT 단계 — 작성자 직접 수정/삭제 자유

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | SALES | 🔴 | slip-099 = `status=DRAFT` 작성자 본인 | (FE) `/sales/slip-099` 진입. `slip-detail-edit-request-banner` 미표시 검증. (BE) `POST /api/v1/slips/slip-099/edit-request {type:"EDIT", reason:"..."}` 직접 호출 | (UI) banner / 수정 요청 버튼 미노출. (BE) `BusinessException(INVALID_INPUT, "현 단계 (DRAFT) 는 작성자가 직접 수정/삭제 가능합니다 — 별도 요청 불필요")` 400 응답 | DRAFT 에서 요청 채널이 동작하면 사용자 혼란 + 작성자 직접 mutation 우회 가능 (이중 트랙). banner 미노출 정책 일관 |

### 1.2 SAVED/SENT 단계 — 작성자 직접 수정 자유

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | SALES | 🔴 | slip-098 = `status=SAVED` | (BE) `POST /edit-request` 호출 | `INVALID_INPUT` 400 응답 (`SlipEditRequestService.guardRequestableStatus` SAVED 분기) | SAVED → 직접 mutation 가능, 본 endpoint 호출은 invalid (FE 가 banner 노출하지 않으므로 BE 만 호출되는 경우는 외부 API client) |
| 1.2.2 | SALES | 🔴 | slip-097 = `status=SENT` | (BE) `POST /edit-request` 호출 | `INVALID_INPUT` 400 응답 (SENT 분기) | 전송 후/창고 인계 전 단계 — 작성자가 회수/재전송 가능, 별도 요청 불필요 |

### 1.3 ACCEPTED/PROCESSING 단계 — 잠금 + 요청 채널 활성

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | SALES | 🔴 | slip-096 = `status=ACCEPTED` 작성자 본인 | (BE) `POST /edit-request {type:"EDIT", reason:"수량 5 → 7 변경 필요"}` | (DB) `slip_edit_requests` 1행 INSERT — `status=PENDING`, `target_role=WAREHOUSE`, `expires_at=now+24h`. (SSE) `slip:edit-request:created` broadcast 1회. (notification) `notificationClient.notifyTargetRole` 호출 (warehouse 그룹). 200 응답 | ACCEPTED = 창고 인계 직후 — 작성자 직접 mutation 차단 + 본 endpoint 만 채널. row 미INSERT 시 협력 워크플로우 단절 |
| 1.3.2 | SALES | 🔴 | slip-096 진행 중 (PENDING 1건 존재) | (BE) `SlipService.applyOverlayPatch(slip-096, "memo", "...")` 직접 호출 (mock controller 우회) | `BusinessException(CONFLICT, "잠금 단계 (ACCEPTED) 는 APPROVED 요청 1건 필요")` — `findActiveApproval` 0건 반환 → 차단 | 잠금 우회 시 협력사 검수 직전 데이터 변동 → 분쟁 위험. `findActiveApproval` 호출 의무 |
| 1.3.3 | WAREHOUSE | 🔴 | 1.3.1 통과 (PENDING 1건) | (BE) `POST /edit-request/{requestId}/approve` → 작성자 측 `applyOverlayPatch` 호출 | (DB) `slip_edit_requests.status=APPROVED`. `findActiveApproval` 1건 반환 → mutation 진행. mutation 직후 `consumeApproval` 호출 → row soft-delete (`is_deleted=true`). (SSE) `slip:edit-request:decided` broadcast (작성자 화면 toast) | 1회 한정 소진 누락 시 작성자가 동일 승인 무한 재사용 → audit 무력화 |

---

## 2. 슬라이스 2 — INSPECTING/SHIPPING/DELIVERED/CONFIRMED 완전 잠금 4 case

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | SALES | 🔴 | slip-095 = `status=INSPECTING` | (BE) `POST /edit-request` | `BusinessException(CONFLICT, "현 단계 (INSPECTING) 는 완전 잠금")` 409 응답. row 미INSERT | 검수 중 데이터 변동 → 결과 신뢰성 손상. 사용자 명시 정책 "INSPECTING + OUT_FOR_DELIVERY: 창고도 수락 불가" |
| 2.1.2 | SALES | 🔴 | slip-094 = `status=SHIPPING` | (BE) `POST /edit-request` | `CONFLICT` 409 (FULLY_LOCKED set 의 SHIPPING 분기) | 배송 중 거래처 정보 변경 → 잘못된 주소 출고 사고 위험 |
| 2.1.3 | MANAGER | 🔴 | slip-093 = `status=DELIVERED` | (BE) `POST /edit-request` (관리자 force 시도) | `CONFLICT` 409 — 관리자도 우회 불가 (BE 가드 일관) | 배송 완료 후 거래내역 변경 → 회계 불일치. MANAGER 도 별도 채널 (직접 SQL audit) 필요 |
| 2.1.4 | SALES | 🔴 | slip-002 = `status=CONFIRMED` | (BE) `POST /edit-request` | `CONFLICT` 409 (사용자 명시 "DELIVERED/CONFIRMED 영구 잠금" 정책) | 회계 마감 후 변동 차단 — 한국 일반기업회계기준 보존 의무 |

> **FE 정책 정정 필요 (🟠 Major)** — 현재 `SlipDetailPage.tsx` `isConfirmed = slip.status === 'CONFIRMED'` 일 때만 banner + 요청 버튼 노출. 그러나 BE `SlipEditRequestService.LOCKED_REQUIRES_APPROVAL = {ACCEPTED, PROCESSING}` 이므로 **FE 의 CONFIRMED 분기는 BE 가드와 불일치**. CONFIRMED 는 FULLY_LOCKED 라 요청 자체가 409 거부됨. 후속 PR 에서 FE `isConfirmed` 명칭/분기를 `isApprovalRequired (status in {ACCEPTED, PROCESSING})` 로 정정 필요. **본 캡처는 화면 시연용으로 CONFIRMED 분기 그대로 사용** (mock 환경에서는 BE 가드 미적용, 실 운영에서는 ACCEPTED/PROCESSING 진입 후 노출).

---

## 3. 슬라이스 3 — 수정/삭제 요청 → 알림 → 수락/거절 5 case

### 3.1 작성자 dialog 입력 → 요청 생성 + 알림

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1.1 | SALES | 🔴 | ACCEPTED 슬립 + dialog open | (FE) `slip-edit-request-dialog-reason` textarea 에 "거래처 요청으로 수량 5 → 7 변경 필요" (≥ 10자) 입력 → `slip-edit-request-dialog-submit` 클릭 | (BE) `POST /edit-request {type:"EDIT", reason:"..."}` 호출. (UI) dialog 닫힘 + `slip-detail-edit-request-status-badge` "요청 처리 대기" warning 표시 | 작성자 입력 → BE 전송 → UI 상태 동기화 1 cycle 무결성. 사유 ≥ 10자 가드 + 카운터 (250/500) UX |
| 3.1.2 | SALES | 🟠 | dialog open + reason="짧다" (< 10자) | (FE) submit 버튼 클릭 시도 | submit 버튼 `disabled=true` (`canSubmit=false`). `slip-edit-request-dialog-error` "사유는 최소 10자 이상 입력해주세요" 표시 | < 10자 사유 통과 시 BE 가 spam 요청 받음 — UX 가드 의무 |

### 3.2 알림 (notification-service Feign) — graceful fallback

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2.1 | SALES | 🔴 | 3.1.1 통과 + notification-service UP | (BE) `NotificationClient.notifyTargetRole(WAREHOUSE)` 호출 | notification-service 가 SMS / FCM push 발송 (warehouse 그룹 전원). slip-service log "[PR-H3] slip ... 수정 요청 생성 — type=EDIT" + notification log "발송 성공" | 알림 미발생 시 창고 직원이 PENDING list 진입 시점까지 30초+ 지연 → 처리 시간 손실 |
| 3.2.2 | SALES | 🔴 | 3.1.1 통과 + notification-service DOWN | (BE) `NotificationClient.notifyTargetRole` 호출 → `FeignException` 발생 | slip-service 가 graceful fallback — `try/catch` 후 warning log 만 출력, slip 비즈니스 로직 진행 (요청 row 정상 INSERT). 200 응답 | notification 실패가 slip mutation 차단하면 협력 워크플로우 마비. graceful fallback 의무 |

### 3.3 창고 직원 수락 → 작성자 toast

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3.1 | WAREHOUSE | 🔴 | 3.1.1 통과 (PENDING 1건) | (FE) `/admin/slip-edit-requests` 진입 → `admin-slip-edit-requests-row-{slipNo}` 행 표시. `admin-slip-edit-requests-approve-{slipNo}` 클릭 → confirm dialog → 확인 | (BE) `POST /edit-request/{requestId}/approve` 호출. (DB) `status=APPROVED` + `decided_by`/`decided_at` 기록. (SSE) `slip:edit-request:decided` broadcast → 작성자 SlipDetailPage 가 `decisionToast` "수정 요청이 수락되었습니다." (success variant) 표시 | 수락 → 작성자 통보 1 cycle. SSE 미수신 시 작성자가 "처리 됐는지" 30초+ polling 의존 |

---

## 4. 슬라이스 4 — 수락 후 잠금 해제 + 수정 가능 4 case

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1.1 | SALES | 🔴 | 3.3.1 통과 (APPROVED 1건) | (BE) `SlipService.applyOverlayPatch(slip-096, "memo", "신규 메모")` | (BE) `findActiveApproval` 1건 반환 → mutation 진행 (slip 헤더 갱신 + audit row INSERT). mutation 직후 `consumeApproval` 호출 → request row soft-delete | 1회 한정 소진 — `consumeApproval` 후 `findActiveApproval` 0건 반환 보장 |
| 4.1.2 | SALES | 🔴 | 4.1.1 통과 직후 | (BE) 같은 slip 에 `applyOverlayPatch` 재시도 | `findActiveApproval` 0건 → `BusinessException(CONFLICT)` 차단 | 1회 소진 후 재사용 차단 — audit 무력화 방지 |
| 4.1.3 | SALES | 🔴 | 4.1.1 통과 (request soft-delete 됨) | (FE) `SlipDetailPage` 재진입 | `slip-detail-edit-request-banner` "확정 전표" 다시 표시 + `latestEditRequest` 의 `status=APPROVED` 표시 (success badge). 다시 PENDING 요청 가능 | 이력 잔존 + 재요청 자유 — UX 일관 |
| 4.1.4 | SALES (DELETE 분기) | 🔴 | DELETE type 요청 + WAREHOUSE 수락 | (BE) `SlipService.softDelete(slip)` 호출 | (DB) `slips.is_deleted=true` + `status=CANCELED`. SSE 발행 후 작성자 toast "삭제 요청이 수락되었습니다." | DELETE 수락이 hard-delete 면 audit 손실. soft-delete 의무 |

---

## 5. 슬라이스 5 — 거절 후 status 유지 3 case

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.1.1 | WAREHOUSE | 🔴 | PENDING 1건 + WAREHOUSE 진입 | (FE) `admin-slip-edit-requests-reject-{slipNo}` 클릭 → Modal "이미 출고가 완료된 전표라 수정 불가" (≥ 5자) 입력 → `admin-slip-edit-requests-reject-submit` 클릭 | (BE) `POST /edit-request/{requestId}/reject {reason:"..."}` 호출. (DB) `status=REJECTED` + `decision_reason` 저장. (SSE) `slip:edit-request:decided` broadcast → 작성자 toast "거절: 이미 출고가..." (danger variant) | 거절 → 작성자가 사유 즉시 인지 → 다시 정정 후 재요청 cycle |
| 5.1.2 | SALES | 🔴 | 5.1.1 통과 | (BE) slip status 확인 | slip status = `ACCEPTED` 그대로 (변동 없음). `findActiveApproval` 0건 → mutation 차단 유지 | 거절이 slip status 영향 시 잠금 우회 발생 |
| 5.1.3 | WAREHOUSE | 🟠 | reject dialog open + reason="짧" (< 5자) | submit 클릭 시도 | submit 버튼 `disabled=true` (`rejectReason.trim().length >= 5` 미충족) | < 5자 사유 통과 시 작성자에게 "(이유)" 표시 무의미 |

---

## 6. 슬라이스 6 — 만료 (24h) 자동 EXPIRED 2 case

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.1.1 | MASTER | 🔴 | PENDING 1건 + `requested_at = now-25h` (테스트 fixture) | (BE) `SlipEditRequestService.expirePending` (Scheduler tick or 직접 호출) | (DB) `findExpired(now)` 1건 반환 → `req.expire()` → `status=EXPIRED`. (SSE) `slip:edit-request:decided` broadcast (payload `status=EXPIRED`) | 24h 만료 누락 시 PENDING 무한 누적 → 창고 대시보드 noise. scheduler `fixedRate=3_600_000L` 정상 동작 의무 |
| 6.1.2 | MASTER | 🟠 | `app.slip.edit-request.expires-hours=1` env override | (BE) 1h 후 `expirePending` 호출 | (DB) `expires_at = requested_at + 1h` 기준으로 만료 처리. 환경변수 적용 정상 | DevOps PR (0e6785e) 환경변수 동작 검증 — production tuning 가능 |

---

## 7. 슬라이스 7 — DRIVER 권한 차단 + WAREHOUSE/MANAGER 수락 권한 4 case

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.1.1 | DRIVER | 🔴 | DRIVER 로그인 + ACCEPTED 슬립 | (BE) `POST /edit-request` 호출 | 403 Forbidden (`@PreAuthorize("hasAnyRole('SALES','MANAGER','MASTER')")` 차단) | DRIVER 가 작성자 권한 우회 시 협력 워크플로우 혼선 |
| 7.1.2 | DRIVER | 🔴 | DRIVER 로그인 + sidebar | "전표 수정 요청" 메뉴 미노출 (`SLIP_EDIT_REQUEST_REVIEWER_ROLES` 에 DRIVER 미포함) | NavLink 미렌더 + 직접 URL 진입 시 RoleGuard 가드 작동 | DRIVER 가 reviewer dashboard 접근 시 PENDING list 노출 (privacy 위반) |
| 7.1.3 | WAREHOUSE | 🔴 | WAREHOUSE 로그인 + PENDING 1건 | (BE) `POST /edit-request/{requestId}/approve` 호출 | 200 OK + status=APPROVED. `@PreAuthorize` 통과 | reviewer 정상 권한 보유 검증 |
| 7.1.4 | MANAGER | 🔴 | MANAGER 로그인 (창고 부재 backup) + PENDING 1건 | (BE) `POST /edit-request/{requestId}/approve` 호출 | 200 OK — MANAGER 도 reviewer 권한 보유 (운영 backup) | 창고 부재 시 MANAGER 가 처리 불가하면 워크플로우 정체 |

---

## 8. 슬라이스 8 — 단위 14 + IT 3 점검 (BE 자동화 정합성)

### 8.1 단위 — `SlipEditRequestServiceTest` (8 case)

| # | 메서드 | 검증 | 정합성 평가 |
|---|---|---|---|
| 8.1.1 | `request_draftStage_throwsInvalidInput` | DRAFT 단계 요청 시 400 | ✅ 시나리오 1.1.1 와 일치 |
| 8.1.2 | `request_acceptedStage_createsPendingAndPublishesAndNotifies` | ACCEPTED 정상 요청 + broker.publish + notificationClient 호출 | ✅ 시나리오 1.3.1 + 3.1.1 + 3.2.1 와 일치 |
| 8.1.3 | `request_inspectingStage_throwsConflictFullyLocked` | INSPECTING 완전 잠금 | ✅ 시나리오 2.1.1 와 일치 |
| 8.1.4 | `request_deliveredStage_throwsConflictFullyLocked` | DELIVERED 완전 잠금 | ✅ 시나리오 2.1.3 와 일치 |
| 8.1.5 | `approve_pendingRequest_transitionsAndPublishesAndNotifiesRequester` | approve transition + SSE + notification | ✅ 시나리오 3.3.1 와 일치 |
| 8.1.6 | `reject_pendingRequest_transitionsAndNotifiesWithReason` | reject transition + 사유 보존 | ✅ 시나리오 5.1.1 와 일치 |
| 8.1.7 | `approve_alreadyApproved_throwsConflict` | 종결된 요청 재approve 차단 | ✅ status terminal 가드 검증 |
| 8.1.8 | `expirePending_pastExpiresAt_transitionsToExpiredAndPublishes` | scheduler 자동 만료 + SSE | ✅ 시나리오 6.1.1 와 일치 |

### 8.2 단위 — `SlipServiceLockGuardTest` (6 case)

| # | 메서드 | 검증 | 정합성 평가 |
|---|---|---|---|
| 8.2.1 | `applyOverlayPatch_draftStage_proceedsWithoutApproval` | DRAFT 직접 mutation 가능 | ✅ 시나리오 1.1.1 와 일치 |
| 8.2.2 | `applyOverlayPatch_savedStage_proceedsWithoutApproval` | SAVED 직접 mutation 가능 | ✅ 시나리오 1.2.1 와 일치 |
| 8.2.3 | `applyOverlayPatch_acceptedStage_withoutApproval_throwsConflict` | ACCEPTED 미승인 시 차단 | ✅ 시나리오 1.3.2 와 일치 |
| 8.2.4 | `applyOverlayPatch_acceptedStage_withApproval_proceedsAndConsumes` | ACCEPTED 승인 후 진행 + consumeApproval | ✅ 시나리오 1.3.3 + 4.1.1 와 일치 |
| 8.2.5 | `applyOverlayPatch_inspectingStage_alwaysFullyLocked_throwsConflict` | INSPECTING 완전 잠금 | ✅ 시나리오 2.1.1 와 일치 |
| 8.2.6 | `softDelete_deliveredStage_alwaysFullyLocked_throwsConflict` | DELIVERED softDelete 완전 잠금 | ✅ 시나리오 2.1.3 + 4.1.4 와 일치 |

### 8.3 IT — `SlipEditRequestControllerIT` (3 case)

| # | 메서드 | 검증 | 정합성 평가 |
|---|---|---|---|
| 8.3.1 | `createRequest_draftStage_returns400` | controller layer DRAFT 거부 | ✅ HTTP 400 응답 검증 (단위 8.1.1 의 service-layer 검증과 별개로 controller 통과 확인) |
| 8.3.2 | `createRequest_acceptedStage_returns201_andCallsNotification` | ACCEPTED 정상 + @MockBean NotificationClient 호출 검증 | ✅ feedback_it_mockbean_external_clients.md 패턴 준수 (Feign client mock) |
| 8.3.3 | `approveRequest_pending_returns200_andDashboardEmpty` | approve 200 + dashboard PENDING 0건 | ✅ end-to-end mutation 흐름 검증 |

### 8.4 누락 case 평가 (🟢 Info — 후속 PR 권고)

- **slip-별 이력 조회 (`GET /slips/{slipId}/edit-requests`) IT 부재** — controller endpoint 존재하나 IT 미작성. 단위 `listBySlip` 만으로 일부 검증.
- **DELETE type 수락 시 softDelete 발동 IT 부재** — service-layer 단위만 (8.2.6). controller 통합은 후속.
- **scheduler `expirePending` IT 부재** — 단위 (8.1.8) 만. `@Scheduled` 의 trigger 자체는 Spring 위임이라 단위 검증으로 충분.

---

## 9. 작동 캡처 (사용자 절대 의무)

### 9.1 캡처 산출물 4 PNG

| 파일 | 시각 검증 항목 | mockRole |
|---|---|---|
| `working-edit-request-dialog.png` | SALES 작성자가 SlipEditRequestDialog (modal) 에 사유 입력 진행 중. type="EDIT" + slipNo 안내 + textarea + counter 표시 | `SALES` |
| `working-warehouse-pending-list.png` | WAREHOUSE 가 `/admin/slip-edit-requests` PENDING list 표 진입. 행 1+ 표시 (전표번호/요청자/Badge type/사유/시각/수락-거절 버튼) | `WAREHOUSE` |
| `working-edit-request-approved-toast.png` | SALES 작성자 화면에 SSE `slip:edit-request:decided` 수신 → `slip-detail-edit-request-decision-toast` (success variant) "수정 요청이 수락되었습니다." 표시 | `SALES` |
| `working-locked-slip-banner.png` | CONFIRMED 슬립 진입 시 `slip-detail-edit-request-banner` (확정 전표 안내) + 수정/삭제 요청 버튼 표시 | `SALES` |

### 9.2 캡처 자동화

- 도구: `tools/manual-capture/capture-pr-h3.js` (Playwright + sharp, PR-H1/H2 패턴 활용)
- 전제: `clients/desktop` 에서 vite mock dev server (`VITE_MOCK_MODE=1 vite --port 5176`) 사전 부팅
- fallback: 부팅 실패 시 한국어 placeholder PNG 자동 생성 (≥ 20KB)

---

## 10. 회귀 차단 종합

본 PR-H3 실패 시 production 재현 가능 시나리오:

1. **잠금 가드 우회** — ACCEPTED 단계 슬립을 작성자가 직접 mutation 가능 → 협력사 검수 직전 데이터 변동 → 분쟁
2. **1회 소진 누락** — APPROVED 요청 무한 재사용 → audit 의도 무력화
3. **알림 미발생** — notification-service down 시 slip mutation 차단 → 전체 워크플로우 마비
4. **SSE 미수신** — 작성자가 수락/거절 결과를 30초+ 폴링으로만 인지 → UX 저하
5. **scheduler 미동작** — PENDING 무한 누적 → 창고 대시보드 noise
6. **DRIVER 권한 우회** — 작성자/reviewer 권한 침해 → privacy 위반
7. **CONFIRMED 영구 잠금 우회** — 회계 마감 후 변동 → 한국 일반기업회계기준 보존 의무 위배

---

## 11. 참조

- `feedback_uuid_no_user_visibility.md` — UUID 비공개 정책
- `feedback_role_naming_full.md` — ROLE 풀네임 의무
- `feedback_it_mockbean_external_clients.md` — Feign client @MockBean 패턴
- `feedback_pr_qa_screenshots.md` — 작동 캡처 절대 의무
- `docs/uiux/phase12/H3-edit-request-workflow.md` — 디자이너 flow chart + 잠금 정책
- `docs/manual/02-출고-처리.md` § "수정/삭제 요청"
- `docs/manual/03-역할별-권한.md` § 잠금 정책 표
