# PR-H2 — Phase 12 Step 2 슬립 audit overlay + 실시간 동시 수정 sync QA 시나리오

> **branch** — `feature/integrated-phase-12-step-2-slip-audit-overlay`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 12 Step 2 PR-H2 (Flyway V18 `slip_audit_logs` + `slips.revision_count`, BE `SlipAuditLogService` (record / recordBatch / listBySlip / revertToRevision), 신규 endpoint 3 개, design-system `AuditOverlay` 컴포넌트, desktop `SlipDetailPage` 보강, mobile-staff `SlipDetailScreen` 보강, Redis broker 옵션) 가 사용자 핵심 요구 "두 사람이 같은 전표 보면서 한 명이 메모를 수정하면 다른 사람 화면에 1초 안에 취소선 + 수정자 색상 + 수정자 이름 + 수정 시각 으로 audit overlay 가 표시" 를 만족하는지 측정 가능한 PASS/FAIL 기준으로 명세.
> **연관 산출물** —
> - BE-Schema: `services/slip-service/src/main/resources/db/migration/V18__add_slip_audit_logs.sql` (`slip_audit_logs` 신규 + `slips.revision_count` BIGINT NOT NULL DEFAULT 0)
> - BE-Domain: `SlipAuditLog` (BaseEntity 7 audit + `@SQLRestriction is_deleted=false` + `actorId`/`actorName`/`actorColor`/`fieldName`/`oldValue`/`newValue` + `revisionNo` 그룹핑)
> - BE-Domain: `Slip.applyOverlayPatch(name,value)` + `Slip.readOverlayField(name)` 11 필드 시범 (memo / shippingAddress / contactPhone / partnerName / discountRate 등) + `Slip.incrementRevision()`
> - BE-Service: `SlipAuditLogService.recordOverlayPatch` / `recordBatch` / `listBySlip` / `revertToRevision` (4 책임)
> - BE-Service: `SlipService.applyOverlayPatch` (마감 lock 가드) + `SlipService.editHeader` memo diff → `recordBatch` + SSE broadcast
> - BE-Web: `SlipAuditLogController` 3 endpoint — `GET /slips/{id}/audit-logs` (인증 사용자 전체) / `PATCH /slips/{id}/audit/overlay` (SALES/WAREHOUSE/MANAGER/MASTER) / `POST /slips/{id}/audit/revert/{revisionNo}` (MANAGER/MASTER)
> - BE-Realtime: `SlipRealtimeBroker.publish` payload (`slip:edit` / `slip:reverted` event name + `revisionNo`/`actorId`/`actorName`/`actorColor`/`changes[]` 5 키)
> - BE-Realtime (TM 보완 #3): `RedisRealtimeBroker` + `RedisRealtimeConfigBean` + `RealtimePublishHook` (`SAMHAN_REALTIME_BROKER=redis` toggle, default in-memory, 미연결 시 startup 정상)
> - BE-Test: `SlipAuditLogServiceTest` (6 case) + `SlipAuditLogServiceRevertTest` (4 case) + `SlipAuditPayloadCaptorTest` (3 case ArgumentCaptor) + `SlipServiceAuditDiffTest` (5 case memo diff) + `SlipRealtimeBrokerConcurrencyIT` (3 case multi-emitter) + `RedisRealtimeBrokerTest` (3 case mock) — 단위 24 + IT 9 (concurrency 3 + ArgumentCaptor 3 SSE schema + Redis mock 3)
> - FE-Design-System: `clients/web/design-system/src/components/AuditOverlay/AuditOverlay.tsx` + `.module.css` + `.stories.tsx` (4 story — Single / Multiple / Empty / MultiUserShowcase)
> - FE-Desktop: `clients/desktop/src/renderer/api/slipAudit.ts` (`listAuditLogs` + `revertToRevision`)
> - FE-Desktop: `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` 보강 — `auditLogsQuery` + 수정 횟수 chip (`slip-detail-revision-count`) + AuditOverlay 적용 (memo / shippingAddress) + 복원 dropdown (`slip-detail-revert-select`)
> - FE-Mobile: `clients/mobile-staff/src/components/AuditOverlay.tsx` (RN Text 취소선 + View dot 색상) + `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` 보강 (수정 횟수 헤더 + AuditOverlay 적용 partnerName/status + 복원 버튼 MASTER/MANAGER 만)
> - FE-Common: `userIdToColor` util (PR-H1 fda4d8f 재사용 — 동일 사용자 동일 색상 보장 + RN 1:1 복제 `clients/mobile-staff/src/utils/userColorHash.ts`)
> - DevOps (b0f2e48): `app.realtime.broker=in-memory|redis` toggle + production hint + 의존 옵션 의무
> - 매뉴얼: `docs/manual/05-슬립공유-수정-처리.md` 신규 (사용자 시나리오 + 권한 + 화면 캡처 stub)
> - 작동 캡처: `working-audit-overlay-context-a-edit.png` + `working-audit-overlay-context-b-receives.png` + `working-audit-overlay-multi-revision.png` + `working-multi-context-edit-split.png` (본 폴더, multi-context Playwright)
> - 단위/IT 점검: 본 문서 § 6 (BE 단위 24 case + IT 9 case 정합성 평가)

---

## 0. 검증 정책

### 0.1 페르소나 4 (사용자 명시 — `feedback_role_naming_full` 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 컴퓨터 숙련도 | 본 PR 검증 관점 |
|---|---|---|---|---|
| **신입 영업** | SALES | 단가/세금 미경험 | 일반 office | 출고전표 상세 진입 → 메모/배송지 편집 시 본인 색상 dot + 본인 이름 + 시각 표기 즉시 표시. 다른 사용자 동시 수정 1초 내 audit overlay 수신. SALES 는 `PATCH /audit/overlay` 권한 보유, `POST /audit/revert/{n}` 는 권한 부재 (UI 미노출 검증) |
| **창고원** | WAREHOUSE | 출고 픽업/검수 | 보통 | 검수 시작 후 영업이 메모를 "9시까지" → "10시 30분 양화로 변경" 으로 수정하면 1초 안 audit overlay 표시 (취소선 + 영업 색상 + 영업 이름). PATCH 권한 보유, REVERT 권한 부재 (UI 미노출 검증) |
| **배송 기사** | DRIVER | 배차/도착 시각 | 모바일 위주 | mobile-staff `SlipDetailScreen` — audit overlay 가시 ("partnerName" / "status" 필드만 보이는 단순 timeline). 본인 권한 = 읽기 전용 (PATCH/REVERT 모두 차단). 복원 버튼 비표시 검증 |
| **관리자** | MANAGER | 전 도메인 | 보통 | 조회 + 편집 + 복원 (특정 revision) 모두 가능. 수정 횟수 chip 으로 누적 변경 인지. 복원 dropdown 사용 시 confirm dialog → 신규 revision 으로 audit 영원 보존 검증 |
| **개발책임자 / IT 관리자** | MASTER | 전 도메인 + infra | high | broker 통계 (`subscriberCount`/`publishCount`/`publishFailureCount`/`heartbeatCount`) + Redis broker toggle + multi-emitter 동시성 race 가드 + audit row soft-delete 회계 보존. 관리자 화면에서 다중 사용자 동시 수정 충돌 1차 감지 |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소를 모두 명시:

1. **선행 조건** — fixture (V18 migration / mock audit-logs seed / 두 client 동시 접속 상태)
2. **동작** — Playwright `page.click(testid)` / API client `PATCH /slips/{id}/audit/overlay` 의 구체 step (multi-context = `browser.newContext()` 2회)
3. **기대 결과** — UI assertion (`expect(testid).toBeVisible()` + 취소선 CSS class 적용 + 색상 hex 일치) + SSE event assertion (1초 안 수신 + payload 5 키 일치) + DB row 검증 (audit_log 1행 / revisionNo 동기화)
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 운영 차단 (audit row 미생성 / SSE 영구 미수신 / revision_count desync / revert 시 데이터 손실)
- 🟠 **Major** — 작업 가능하지만 우회 / 재시도 필요 (UI 일부 표기 누락, 색상 미일치)
- 🟡 **Minor** — UX 사소 (시각 포맷 / hover 라벨)
- 🟢 **Info** — 향후 개선 권고 (라인 수준 audit / 외부 audit 인쇄)

### 0.4 권한 매트릭스 (`feedback_role_naming_full` 풀네임 의무)

`MASTER` / `MANAGER` / `ACCOUNTANT` / `SALES` / `WAREHOUSE` / `DRIVER` / `DISPATCHER` / `INVENTORY` / `PARTNER` / `READONLY` 만 사용. M/M/D 약어 금지.

본 PR 권한:
- **`GET /slips/{id}/audit-logs`** = 인증 사용자 전체 (`isAuthenticated()`) — 회계 추적용 timeline 노출
- **`PATCH /slips/{id}/audit/overlay`** = `SALES` / `WAREHOUSE` / `MANAGER` / `MASTER` (실시간 협업 주체 4 ROLE)
- **`POST /slips/{id}/audit/revert/{revisionNo}`** = `MANAGER` / `MASTER` (audit 되돌리기 권한 한정)
- **차단 ROLE** = `DRIVER` / `INVENTORY` / `ACCOUNTANT` / `READONLY` / `PARTNER` (PATCH 시 403, REVERT 시 403)

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility`)

- `SlipAuditLogResponse.actorId` 는 응답에 포함되지만 **FE 화면 노출 = `actorName` 만**. `actorId` 는 `userIdToColor(actorId)` 색상 hash 입력 전용 (한 사용자 = 항상 같은 hue 보장)
- SSE `slip:edit` payload 의 `actorId` 도 동일 — 화면 표시 = `actorName`
- audit row testid = `audit-overlay-${field}` (DOM attribute 만, 화면 텍스트 0건). expand 버튼 testid = `audit-overlay-${field}-expand` / list = `audit-overlay-${field}-list`
- 복원 dropdown testid = `slip-detail-revert-select` / option = `slip-detail-revert-button-${revisionNo}` (revisionNo 는 1, 2, 3... 정수만 — UUID 미사용)

---

## 1. 슬라이스 1 — audit_log 자동 기록 (5 case)

**의존 backend** — `SlipService.editHeader` (memo diff → `recordBatch`), `SlipAuditLogService.recordOverlayPatch` (단일 필드 PATCH /audit/overlay 핸들러)

**의존 frontend** — `SlipDetailPage.tsx` (audit-logs useQuery + AuditOverlay 컴포넌트)

**testid** — `slip-detail-audit-overlay-memo` / `slip-detail-audit-overlay-shippingAddress` / `audit-overlay-${field}` / `slip-detail-revision-count`

### 1.1 메모 변경 → audit row 1행 + revisionNo +1 + SSE 1회

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | SALES | 🔴 | slip-001 존재 + memo="9시까지배송요망" + revision_count=0 | `PATCH /slips/slip-001/audit/overlay` body `{"fieldName":"memo","newValue":"10시 30분 양화로 변경"}` | (BE) `slip_audit_logs` 1행 INSERT (`fieldName="memo"`, `oldValue="9시까지배송요망"`, `newValue="10시 30분 양화로 변경"`, `revisionNo=1`, `actorName=호출자 X-User-Name`) + `slips.revision_count=1` + broker.publish(`slip:edit`) 1회 (payload `revisionNo=1` + `changes[0].fieldName="memo"`) | audit row 미생성 시 향후 모든 필드 변경 추적 불가, 회계 감사 의무 위배. revision_count desync 시 FE chip 표시 오류 |

### 1.2 배송지 (shippingAddress) 변경 → audit row + actorColor 보존

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | WAREHOUSE | 🔴 | 1.1.1 통과 (revision_count=1) | `PATCH /slips/slip-001/audit/overlay` body `{"fieldName":"shippingAddress","newValue":"서울특별시 강남구 테헤란로 200 (변경)"}` | (BE) `slip_audit_logs` 2번째 row (`revisionNo=2`, `oldValue="서울특별시 강남구 테헤란로 152"`) + `slips.revision_count=2` + SSE `slip:edit` payload `changes[0].fieldName="shippingAddress"` | 배송지 누락 시 기사 잘못된 주소로 출발 — 사고 위험. shippingAddress 가 OUTBOUND 만 적용되는지 회귀 검증 (INBOUND 는 audit 없음) |

### 1.3 라인 수량 변경 (line.quantity) — 시범 범위 외 (🟢 Info)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | SALES | 🟢 | slip-001 존재 + lines[0].quantity=10 | (BE) `Slip.applyOverlayPatch("lines[0].quantity", "20")` 호출 시도 | 현 PR-H2 = 헤더 11 필드 (memo / shippingAddress / contactPhone / partnerName / discountRate 등) 만 시범 지원 — `lines[N].field` 는 `IllegalArgumentException("미지원 필드: lines[0].quantity")` 발생 | 라인 수준 audit 는 다음 슬라이스 (PR-H3) 범위. 본 case 는 가드 동작 검증으로 의의 (FE 가 시범 외 필드 제출 시 400) |

### 1.4 다중 필드 동시 변경 (`SlipService.editHeader` 1회 호출 → recordBatch)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4.1 | MANAGER | 🔴 | slip-001 존재 + memo="9시" + contactPhone="010-1234-5678" | (BE) `SlipService.editHeader(slip-001, partnerName=null, memo="저녁 배송", contactPhone="010-9999-9999")` (multi-field 변경 1 mutation) | (BE) `slip.incrementRevision()` 1회만 호출 → 같은 `revisionNo` 의 audit row 2행 INSERT (`fieldName="memo"` + `fieldName="contactPhone"`) + `slips.revision_count=+1` (총 1만 증가) + 단일 `slip:edit` SSE broadcast (`changes` 배열 length=2) | revisionNo 가 필드별로 분리되면 FE timeline 이 "1번째 수정 by 홍길동" 그룹핑 실패. 사용자가 "한 번에 두 필드 변경" 한 사실이 흩어져 표시됨 |

### 1.5 빈 변경 (no diff) → audit row 미생성

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5.1 | SALES | 🟠 | slip-001 존재 + memo="9시까지배송요망" | `PATCH /slips/slip-001/audit/overlay` body `{"fieldName":"memo","newValue":"9시까지배송요망"}` (동일 값) | 권장: `SlipService.applyOverlayPatch` 가 oldValue == newValue 비교 후 audit row 미INSERT + revision_count 미증가 + SSE 미broadcast (현 구현 검증 필요 — 미구현 시 🟠 Major 보완 권고) | 동일 값 변경마다 audit row 가 누적되면 timeline 이 의미없는 noise 로 채워져 사용자 readability 저하 |

---

## 2. 슬라이스 2 — AuditOverlay UI 표시 (5 case)

### 2.1 정상 — 단일 revision history → inline 취소선 + 색상 + 수정자 1행

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | SALES | 🔴 | slip-001 + audit-logs 1행 (memo, revisionNo=1, actorName="김영업", actorId="user-001-kim", changedAt="2026-05-09T14:32:18+09:00") | (FE) `SlipDetailPage` 진입 | (FE) `[data-testid="audit-overlay-memo"]` 영역에 (1) `currentValue="10시 30분 양화로 변경"` 검정 + (2) `beforeValue="9시까지배송요망"` 회색 취소선 (`text-decoration: line-through`) + (3) `actorDot` background = `userIdToColor("user-001-kim")` HSL hex + (4) `actorName="김영업"` 표시 + (5) `formatHHmm(changedAt)="14:32"` 표시 | 취소선 CSS 누락 시 변경 사실 시각화 0 — 사용자가 "전과 후" 차이 인지 불가 |

### 2.2 다중 revision (3+) → "이력 N개 보기" expand 토글

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2.1 | MANAGER | 🔴 | slip-001 + audit-logs 3행 (memo 필드, revisionNo=1/2/3, actorName 모두 다름) | (FE) `SlipDetailPage` 진입 → `[data-testid="audit-overlay-memo-expand"]` 클릭 | (FE) inline 영역 = 최신(revisionNo=3) 1행 표시 + "이력 3개 보기" 버튼 → 클릭 → `[data-testid="audit-overlay-memo-list"]` `<ul>` 표시 + 과거 2 row (revisionNo=2/1) 가 최신순으로 list 표시 + 각 row 색상 dot + actorName + HH:mm 표시 + 버튼 라벨 "이력 닫기" 로 변경 | expand 미동작 시 사용자가 과거 변경 추적 불가 — 회계/분쟁 대응 시 "어느 시점에 누가 무엇을" 확인 불능 |

### 2.3 빈 history → "변경 이력 없음" 안내

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3.1 | SALES | 🟠 | slip-001 + audit-logs 0건 (신규 생성 직후) | (FE) `SlipDetailPage` 진입 | (FE) `[data-testid="audit-overlay-memo"]` 에 currentValue 표시 + "변경 이력 없음" empty state 텍스트 (회색 보조 색) + 취소선 row 미표시 + expand 버튼 미표시 | 빈 상태 표시 누락 시 사용자가 "데이터 로드 실패" 와 혼동 |

### 2.4 currentValue == null/empty → "(빈 값)" 한국어 가시화

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4.1 | WAREHOUSE | 🟡 | slip-001 + memo=null + audit history 1행 (beforeValue="9시까지", newValue=null) | (FE) `SlipDetailPage` 진입 | (FE) currentValue 영역 = "(빈 값)" 표시 + before 영역 = "9시까지" 취소선 표시 + actor 정보 표시 (한국어 가시화 누락 0) | 빈 값을 빈 div 로 렌더 시 화면 깨짐 — 사용자가 "삭제됨" 인지 불가 |

### 2.5 동일 사용자 다중 row → 색상 일관 (deterministic hash)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5.1 | MANAGER | 🟠 | slip-001 + audit-logs 5행 (모두 actorId="user-001-kim") | (FE) `SlipDetailPage` 진입 → expand | inline + expanded list 5 row 모두 동일 dot 색상 (HSL hue 일치) — `userIdToColor` deterministic hash 검증. 4 사용자 (kim/lee/park/choi) 각각 다른 hue 분산 (Storybook MultiUserShowcase 4 컬러 일치) | 동일 사용자 색상 mismatch 시 사용자가 "다른 사람" 으로 오인 — 협업 멘탈 모델 깨짐 |

---

## 3. 슬라이스 3 — 수정 횟수 카운트 (3 case)

### 3.1 초기 0 → chip "수정 0회"

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1.1 | SALES | 🟠 | slip-001 + audit-logs 0건 | (FE) `SlipDetailPage` 진입 | `[data-testid="slip-detail-revision-count"]` 텍스트 = "수정 0회" + 회색 배경 chip + tooltip="전표 변경 누적 횟수" | 카운트 누락 시 사용자가 "전표가 한 번도 안 건드려졌다" 인지 불가 — UX 회복 |

### 3.2 다중 revision 누적 → chip 갱신

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2.1 | MANAGER | 🔴 | slip-001 + audit-logs 5행 (revisionNo=1/2/3/4/5, distinct 5) | (FE) `SlipDetailPage` 진입 | chip 텍스트 = "수정 5회" (FE 가 `new Set(auditLogs.map(l => l.revisionNo)).size` 로 distinct 카운트) | revisionNo 미dedupe 시 한 mutation 의 다중 필드 변경이 "수정 N회" 로 부풀려 표시됨 |

### 3.3 한 revision 다중 필드 → distinct dedupe 검증

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3.1 | MANAGER | 🔴 | slip-001 + audit-logs 4행 (revisionNo=1: memo+shippingAddress 2행, revisionNo=2: contactPhone 1행, revisionNo=3: partnerName 1행) | (FE) `SlipDetailPage` 진입 | chip 텍스트 = "수정 3회" (4행이 아닌 distinct revisionNo 3건) | dedupe 누락 시 "수정 4회" 로 잘못 표시 → 사용자가 "한 번 변경 = 1회" 직관과 불일치 |

---

## 4. 슬라이스 4 — 복원 (revert) (4 case)

### 4.1 특정 revision 으로 복원 → 신규 revision 으로 audit 보존

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.1.1 | MANAGER | 🔴 | slip-001 + audit-logs 3행 (revisionNo=1/2/3) + 현재 memo="최종 메모 (revision 3)" | (BE) `POST /slips/slip-001/audit/revert/2` headers `X-User-Id=...` `X-User-Name=박관리` | (BE) revision=2 의 audit row 들 조회 → 각 row 의 `oldValue` 로 slip 복원 → 신규 `revisionNo=4` 발급 → `slip_audit_logs` 신규 row INSERT (`oldValue=현재값`, `newValue=과거값`) + `slips.revision_count=4` + SSE `slip:reverted` broadcast (payload 에 `revertedFromRevisionNo=2` 포함) | revert 시 과거 audit 가 사라지면 감사 추적 영원성 위배. 신규 revision 미발급 시 "되돌렸다는 사실" 자체가 timeline 에 남지 않음 |

### 4.2 전체 (initial revision=1) 복원

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.2.1 | MASTER | 🟠 | slip-001 + audit-logs 5행 (revisionNo=1~5) + 현재 메모/주소 모두 다섯 번 수정됨 | (BE) `POST /slips/slip-001/audit/revert/1` | revision=1 의 모든 필드의 oldValue 로 복원 (사실상 초기 상태로 리셋) + 신규 revision=6 발급 | 다중 필드 누적 변경 후 초기 복원 실패 시 사용자가 "잘못 만든 전표" 회복 불가 — 시간 손실 |

### 4.3 DRIVER 복원 시도 → 403 차단

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.3.1 | DRIVER | 🔴 | slip-001 + audit-logs 3행 + DRIVER JWT | (BE) `POST /slips/slip-001/audit/revert/2` | `403 Forbidden` 응답 (`@PreAuthorize("hasAnyRole('MANAGER','MASTER')")`) + audit row 0건 INSERT + slip 미변경 + SSE 미broadcast. (FE mobile-staff) `[data-testid="slip-detail-revert-select"]` 미렌더 (currentUserRole=DRIVER 가드) | 권한 가드 회귀 시 기사가 의도치 않게 본문 복원 → 영업/관리자 의도 파괴 |

### 4.4 MANAGER+ 권한 복원 → UI 복원 dropdown 정상 동작

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 4.4.1 | MANAGER | 🔴 | slip-001 + audit-logs 3행 + MANAGER 세션 | (FE) `SlipDetailPage` 진입 → `[data-testid="slip-detail-revert-select"]` 표시 → option `[data-testid="slip-detail-revert-button-2"]` 선택 → confirm dialog "이 전표를 revision #2 시점으로 복원하시겠습니까?" → 확인 → `revertMutation` 호출 | (FE) `POST /slips/slip-001/audit/revert/2` 호출 + 200 응답 후 `['slip', id]` + `['slipAuditLogs', id]` 양 cache invalidate → 자동 refetch → currentValue 갱신 + 수정 횟수 chip "수정 4회" 갱신 | confirm 가드 누락 시 사용자 실수 클릭으로 데이터 손실. cache invalidate 누락 시 화면 stale → 사용자가 새로고침 필요 |

---

## 5. 슬라이스 5 — 실시간 sync (5 case)

### 5.1 단일 client 수정 → 자기 자신 화면 즉시 audit overlay 표시

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.1.1 | SALES | 🟠 | slip-001 + SSE 구독 활성 | (FE) memo 편집 → PATCH 응답 후 | (FE) `slip:edit` SSE event 자기 자신도 수신 → `auditLogsQuery` invalidate → `[data-testid="audit-overlay-memo"]` 에 본인 색상 dot + 본인 이름 + 신규 row 표시 (자기 변경도 timeline 에 즉시 반영) | 자기 자신 미수신 시 사용자가 "정말 저장됐나?" 의심 → 동일 입력 반복 시도 |

### 5.2 두 client 동시 접속 → A 가 메모 수정 → B 가 1초 안 audit overlay 수신 (Samhan Public 핵심 요구)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.2.1 | SALES (A) + WAREHOUSE (B) | 🔴 | 2 client subscribe (subscriberCount=2) + 양쪽 `SlipDetailPage` mount | (A) memo 입력 "10시 30분 양화로 변경" → PATCH | (A) 응답 후 `slip:edit` 수신 → 본인 audit overlay 표시. (B) 1초 안에 `slip:edit` 수신 → `['slipAuditLogs', id]` invalidate → `[data-testid="audit-overlay-memo"]` 에 (1) currentValue="10시 30분 양화로 변경" + (2) beforeValue="9시까지배송요망" 취소선 + (3) A 의 색상 dot + (4) "오병승" actorName + (5) HH:mm 표시. **multi-context 캡처 절대 의무** (`working-multi-context-edit-split.png`) | 핵심 가치 단절 — 사용자 요구 "취소선 + 색상 + 수정자 이름 + 1초 sync" 정면 위배 |

### 5.3 SSE event payload schema 정합 (revisionNo + actorId + actorName + actorColor + changes[])

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.3.1 | MASTER | 🔴 | broker 활성 + IT `SlipAuditPayloadCaptorTest` | (BE) `recordOverlayPatch` 호출 → ArgumentCaptor 로 broker.publish 의 payload 캡처 | payload Map 5 키 = `revisionNo` (int) + `actorId` (UUID string) + `actorName` (string) + `actorColor` (string|null) + `changes` (List<Map>) + 각 change Map 3 키 = `fieldName` + `oldValue` + `newValue` (LinkedHashMap 순서 보장) | schema 변경 회귀 시 FE 가 `evt.data.changes[0].fieldName` 접근 시 undefined → audit overlay 미표시 |

### 5.4 cache invalidate — `useEffect` SSE 콜백이 audit-logs 와 slip 본체 양 cache 무효화

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.4.1 | MANAGER | 🟠 | 5.2.1 직후 (B 화면 SSE 수신 직후) | (FE) B 가 다른 탭 이동 → 재진입 | `useQuery(['slipAuditLogs', id])` cache stale → 자동 refetch + `useQuery(['slip', id])` 도 함께 refetch → currentValue 와 audit history 모두 최신 | invalidate key 오타 시 SSE 수신 후에도 화면 미갱신 (사용자 새로고침 필요) |

### 5.5 mobile + desktop 혼합 — 한쪽 desktop 수정 → 다른 쪽 mobile audit overlay 수신

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 5.5.1 | DRIVER (mobile) + SALES (desktop) | 🔴 | desktop SALES `SlipDetailPage` 활성 + mobile DRIVER `SlipDetailScreen` 활성 (동일 slipId) | (desktop) memo 수정 → PATCH | (mobile) `react-native-sse` 가 1초 안 `slip:edit` 수신 → `slipAuditLogs` 재조회 → `<AuditOverlay>` RN 컴포넌트가 partnerName/status 필드 timeline 갱신 (취소선 = `Text` 의 `textDecorationLine: 'line-through'` 적용) | mobile/desktop 양쪽 SSE 클라이언트 protocol 차이 (native EventSource vs fetch+ReadableStream) 회귀 시 한쪽만 수신 |

---

## 6. 슬라이스 6 — 동시 수정 충돌 (3 case)

### 6.1 두 사용자 동시 PATCH (서로 다른 필드) → 두 audit row + 두 SSE event 정상 직렬화

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.1.1 | SALES (A) + WAREHOUSE (B) | 🔴 | slip-001 (revision=0) + 2 client SSE 구독 | (A+B 거의 동시) A: PATCH memo / B: PATCH shippingAddress | (BE) DB 락 직렬화 → revisionNo=1 (A 또는 B 중 먼저) + revisionNo=2 (나머지) → 양 row 정상 INSERT + 양 SSE event 양 client 모두 수신 + 양쪽 화면 `audit-overlay-memo` + `audit-overlay-shippingAddress` 양 영역 갱신 | DB 락 race 시 revisionNo 동일 충돌 → unique constraint 가 있다면 INSERT fail (V18 schema 검증 필요) |

### 6.2 두 사용자 동시 PATCH (같은 필드, memo) → 마지막 write 가 currentValue (last-write-wins)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.2.1 | SALES (A) + WAREHOUSE (B) | 🟠 | slip-001 + 2 client | (A+B 거의 동시) A: PATCH memo="A 변경" / B: PATCH memo="B 변경" | (BE) `slip_audit_logs` 2행 (양쪽 다 INSERT) + `slips.revision_count=2` + 마지막 write 가 currentValue → 양 client 화면에 currentValue="B 변경" (가정) + audit overlay 에 "A 변경" 취소선 row 1건 expand 가능. 사용자가 "충돌" 인지 가능 (같은 시각 다른 actor 두 row) | last-write-wins 외 다른 충돌 정책 (예: optimistic lock 409) 도 향후 고려 가능 — 현 PR 은 last-wins 명시 |

### 6.3 multi-emitter 동시 publish (50 emitter + 1 publish) → 모두 수신

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 6.3.1 | MASTER | 🔴 | broker 활성 + 50 emitter 동시 subscribe (`SlipRealtimeBrokerConcurrencyIT.concurrentSubscribe_thenPublish_allReceiveEvent`) | publish 1회 호출 | 50 emitter 전부 1초 안 수신 + `subscriberCount=50` + `publishCount` +1 + race condition 0건 (NPE/IOException 무발생). 100 emitter / 1000 publish 부하 IT (`load_100emitters_1000publish`) 도 통과 | concurrent race 회귀 시 일부 client 가 audit overlay 미수신 → 협업 멘탈 모델 깨짐 |

---

## 7. 슬라이스 7 — Redis broker fallback (2 case)

### 7.1 default 설정 (`SAMHAN_REALTIME_BROKER` 미설정) → in-memory broker 만 활성

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.1.1 | MASTER | 🔴 | application.yml 기본 + Redis 미연결 | slip-service 부팅 | (BE) `SlipRealtimeBroker` bean 단일 등록 + `RedisRealtimeBroker` bean 미등록 (`@ConditionalOnProperty(name="app.realtime.broker", havingValue="redis")`) + startup 정상 + `ApplicationContextLoadIT` PASS | 미연결 시 Redis auto-config 가 부팅 차단하면 단일 노드 환경 production 전체 down |

### 7.2 `SAMHAN_REALTIME_BROKER=redis` 설정 → Redis broker 활성, 미연결 시 startup 실패 명확

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 7.2.1 | MASTER | 🟠 | env `SAMHAN_REALTIME_BROKER=redis` + Redis testcontainer | slip-service 부팅 → publish 호출 | `RedisRealtimeBroker` bean 활성 → publish 시 (1) local broker 호출 + (2) Redis pub/sub `convertAndSend` 호출 (envelope JSON `{slipId, eventName, data}`) + (3) 다른 노드의 `onMessage` 가 `localBroker.publishLocal` 호출 (loop 방지). `RedisRealtimeBrokerTest` 3 case PASS. 미연결 시 startup 명확한 ConnectionException → DevOps 가 즉시 진단 가능 | toggle 회귀 시 다중 노드 환경에서 노드 간 SSE 미동기 → 한 노드 사용자만 audit 수신 |

---

## 8. 작동 캡처 (multi-context 절대 의무 — `feedback_pr_qa_screenshots`)

### 8.1 캡처 산출물 위치

`docs/qa/phase-12-step-2-slip-audit-overlay/`

| 파일 | 시나리오 | 캡처 시점 |
|---|---|---|
| `working-audit-overlay-context-a-edit.png` | 사용자 A (MASTER, 영업) 가 SlipDetailPage 에서 memo 를 편집 직후 | A context, PATCH 직후 (자기 audit overlay 본인 색상 표시) |
| `working-audit-overlay-context-b-receives.png` | 사용자 B (SALES, 창고) 가 SSE 수신 후 audit overlay 에 A 의 색상 + 이름 + 취소선 표시 | B context, audit-logs seed 후 refetch 직후 |
| `working-audit-overlay-multi-revision.png` | 3+ revision 누적 후 "이력 N개 보기" expand → 다중 사용자 색상 분산 + 취소선 list 표시 | 단일 context, expand 클릭 직후 |
| `working-multi-context-edit-split.png` | 좌-A / 우-B 한 화면 합성 (1280×900 두 장 → 2560×900) — 핵심 시각 증거 | A/B sharp 합성 |

### 8.2 캡처 방법

`tools/manual-capture/capture-pr-h2.js` (Playwright multi-context, msedge fallback chromium):

1. `chromium.launch({headless:true})` 1 browser 부팅
2. `browser.newContext()` 2회 — A (MASTER, "오병승") / B (SALES, "김영업")
3. `addInitScript` 으로 양 context 에 (1) `samhanAuth` IPC stub + (2) `__SAMHAN_MOCK_AUDIT_LOGS_SEED` 사전 주입 (B 는 A 변경분 1행 시드)
4. A: `?mockRole=MASTER#/sales/slip-001` 진입 → memo 편집 (DOM 직접 주입 fallback) → PATCH 시뮬레이션 → 캡처 (audit overlay 영역 viewport 중앙)
5. B: `?mockRole=SALES#/sales/slip-001` 진입 → mock audit-logs seed 가 GET 응답에 포함됨 → AuditOverlay 자동 표시 → 캡처
6. Multi-revision: 단일 context 에서 audit-logs seed 3 row + expand 버튼 클릭 → 캡처
7. `sharp` 으로 A/B 화면 합성 → `working-multi-context-edit-split.png`

### 8.3 헤드리스 환경 caveat

- 본 capture 는 vite mock 모드 + headless chromium 환경. 실 운영 = BE PATCH /audit/overlay → SlipAuditLogService.recordOverlayPatch → broker.publish → SSE → React Query invalidate → audit-logs refetch → AuditOverlay 갱신
- 헤드리스 + mock 모드에서 React Query mutation chain 누락 시 capture-pr-h2.js 가 **DOM 직접 주입 fallback** (capture-only, production 영향 0). 주입된 row 의 testid = `audit-overlay-memo-capture` (실 row `audit-overlay-${field}` 와 구분)
- 실 e2e 검증 = BE IT (`SlipAuditPayloadCaptorTest` + `SlipServiceAuditDiffTest`) 보완

### 8.4 fallback (Playwright 자동화 실패 시)

`generatePlaceholders()` 함수가 누락 + <20KB step 만 한국어 라벨 placeholder PNG 자동 생성 (TODO 표기 + 재실행 명령 포함). 실 캡처 (>=20KB) 는 보존.

---

## 9. 단위 테스트 점검 (BE 단위 24 + IT 9)

### 9.1 BE 단위 — `SlipAuditLogServiceTest` (6 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.1.1 | `recordOverlayPatch_normal_savesAndPublishes` | slip exists → save 1회 + revisionNo=1 + broker.publish(`slip:edit`) 1회 | ✅ 정합 — 단일 필드 record + SSE 부수효과 명시 |
| 9.1.2 | `recordOverlayPatch_slipMissing_throws` | slip not found → BusinessException(NOT_FOUND) + save/publish 0회 | ✅ 정합 — 가드 회귀 차단 |
| 9.1.3 | `recordBatch_multipleFields_singleRevision` | 2 changes → revisionNo 1 공유 + save 2회 + publish 1회 (changes[2]) | ✅ 정합 — recordBatch 핵심 책임 (mutation 1회 = revision 1회) |
| 9.1.4 | `recordBatch_emptyChanges_throws` | empty → BusinessException(INVALID_INPUT) | ✅ 정합 — DOS / 무의미 record 가드 |
| 9.1.5 | `listBySlip_returnsRepositoryResult` | repository call 위임 + 정렬 키 (revisionNo desc + changedAt desc) | ✅ 정합 — Repository derived query 위임 |
| 9.1.6 | `recordOverlayPatch_actorColor_passedThrough` | actorColor 가 audit row + payload 양쪽에 보존 | ✅ 정합 — FE 색상 hash backup |

**총평**: 6 case 모두 PASS. **권고 (🟢 Info)** — `recordOverlayPatch` 의 oldValue==newValue 동일 값 거부 case 1건 추가하면 1.5.1 시나리오 보완 (현재 미구현 — Major 보완 권고 §10.1).

### 9.2 BE 단위 — `SlipAuditLogServiceRevertTest` (4 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.2.1 | `revertToRevision_normal_restoresAndCreatesNewRevision` | targetRev 의 row 들 oldValue 로 slip 복원 + 신규 revisionNo 발급 + audit row 신규 INSERT (currentValue → pastValue) + SSE `slip:reverted` (`revertedFromRevisionNo` 포함) | ✅ 정합 — revert 핵심 책임 + 영원 보존 |
| 9.2.2 | `revertToRevision_slipMissing_throws` | NOT_FOUND | ✅ 정합 — 가드 |
| 9.2.3 | `revertToRevision_revisionMissing_throws` | targetRow 빈 list → NOT_FOUND | ✅ 정합 — 존재하지 않는 revision 거부 |
| 9.2.4 | `revertToRevision_invalidRevisionNo_throws` | targetRev < 1 → INVALID_INPUT | ✅ 정합 — 입력 검증 |

**총평**: 4 case 모두 PASS. **권고 (🟠 Major)** — 마감 lock 적용 슬립의 revert 시도 시 CONFLICT 검증 case 1건 추가 권고 (controller javadoc `@ApiResponses 409` 와 정합).

### 9.3 BE 단위 — `SlipAuditPayloadCaptorTest` (3 case, IT 분류)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.3.1 | `recordOverlayPatch_payloadSchema_5keys_3changeKeys` | ArgumentCaptor → broker.publish 의 Map payload 5 키 (`revisionNo`/`actorId`/`actorName`/`actorColor`/`changes`) + changes[0] 3 키 (`fieldName`/`oldValue`/`newValue`) + LinkedHashMap 순서 | ✅ 정합 — FE contract 일관 검증 |
| 9.3.2 | `recordBatch_payloadOrder_preserved` | changes 입력 순서 = payload 순서 (LinkedHashMap) | ✅ 정합 — FE timeline 표시 순서 보장 |
| 9.3.3 | `revertToRevision_payload_includesRevertedFromRevisionNo` | payload 에 `revertedFromRevisionNo` 추가 키 + event name `slip:reverted` | ✅ 정합 — FE 가 `slip:edit` vs `slip:reverted` 분기 |

**총평**: 3 case 모두 PASS. SSE schema 회귀 차단의 핵심.

### 9.4 BE 단위 — `SlipServiceAuditDiffTest` (5 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.4.1 | `editHeader_memoChanged_recordsBatchOnce` | memo 변경만 → recordBatch 1회 호출 + changes 1건 (memo) | ✅ 정합 — diff 정확성 |
| 9.4.2 | `editHeader_memoUnchanged_skipsAudit` | memo 동일 값 → recordBatch 미호출 (no-op) | ✅ 정합 — noise 방지 (1.5.1 시나리오 일부 커버) |
| 9.4.3 | `editHeader_multiField_singleBatchCall` | partnerName + memo 동시 변경 → recordBatch 1회 + changes 2건 | ✅ 정합 — multi-field grouping (1.4.1 시나리오) |
| 9.4.4 | `editHeader_oldValueNull_recordsCorrectly` | 신규 추가 (oldValue=null) → audit row factory 거부 안됨 (newValue 존재) | ✅ 정합 — 신규 추가 case |
| 9.4.5 | `applyOverlayPatch_unsupportedField_throws` | `lines[0].quantity` 등 미지원 필드 → IllegalArgumentException | ✅ 정합 — 1.3.1 시나리오 가드 검증 |

**총평**: 5 case 모두 PASS. memo diff + multi-field grouping + 미지원 필드 가드 모두 검증.

### 9.5 BE 단위 — `RedisRealtimeBrokerTest` (3 case, IT 분류)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.5.1 | `propagate_callsRedisConvertAndSendWithEnvelopeJson` | publish 시 Redis pub envelope JSON `{slipId, eventName, data}` 정확 | ✅ 정합 — Redis pub schema |
| 9.5.2 | `onMessage_validPayload_callsLocalPublish` | Redis 수신 → `SlipRealtimeBroker.publishLocal` (publish 가 아닌 — loop 방지) | ✅ 정합 — 노드간 동기 + loop 방지 핵심 |
| 9.5.3 | `onMessage_invalidPayload_gracefulSkip` | schema 위반 메시지 → log.warn + publishLocal 미호출 | ✅ 정합 — 외부 메시지 방어 |

**총평**: 3 case 모두 PASS. 다중 노드 확장 가드 충실. Redis testcontainer 없이 Mock 으로 단위 검증 (testcontainer 는 Phase 11 AWS 시 IT 추가 권고).

### 9.6 BE 단위 — `SlipRealtimeBrokerTest` (4 case, PR-H1 보존)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.6.1 | `subscribe_increasesSubscriberCount` | subscribe 1회 → emitter !=null + subscriberCount=1 | ✅ 정합 (PR-H1 보존) |
| 9.6.2 | `publish_normalEmitters_notCleanedUp` | subscribe 2회 + publish → cleanup 미발생 | ✅ 정합 (PR-H1 보존) |
| 9.6.3 | `publish_completedEmitter_isCleanedUp` | complete emitter → cleanup → subscriberCount=0 | ✅ 정합 (PR-H1 보존) |
| 9.6.4 | `heartbeat_incrementsCountAndCleansClosedEmitters` | heartbeat() → count+1 + cleanup | ✅ 정합 (PR-H1 보존) |

### 9.7 BE 통합 IT — `SlipRealtimeBrokerConcurrencyIT` (3 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.7.1 | `concurrentSubscribe_thenPublish_allReceiveEvent` | 50 emitter 동시 subscribe + 1 publish → publishCount+1, subscriberCount=50 | ✅ 정합 — race condition 0 |
| 9.7.2 | `concurrentClose_duringPublish_noNpeOrIoException` | 30 emitter + 동시 publish + emitter 동시 close → NPE/IOException 무발생 (cleanup race 가드) | ✅ 정합 — ConcurrentHashMap + CopyOnWriteArrayList 가드 검증 |
| 9.7.3 | `load_100emitters_1000publish` | 100 emitter / 1000 publish 부하 → 모든 통계 일치 + 누락 0 | ✅ 정합 — 부하 시나리오 |

**총평**: 3 case 모두 PASS. TM 보완 #1 핵심 — multi-emitter 동시성 가드 (PR-H1 누락 case).

### 9.8 BE 통합 IT — `ApplicationContextLoadIT` (회귀 가드 추가)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 9.8.1 | `slipAuditLogService_singleBeanRegistered` | `SlipAuditLogService` bean 단일 등록 (`*Bean` suffix 가드 PR #119 회귀 방지) | ✅ 정합 — `RedisRealtimeConfigBean` suffix 가드 동시 검증 |

**총평**: bean 등록 회귀 가드 1건 추가. PR-H1 의 broker 단일 가드와 동일 패턴 일관.

### 9.9 누락 case (PR-H2 범위 외 — 후속 권고)

- **🟠 Major** — 마감 lock 적용 슬립의 revert 시도 시 CONFLICT 검증 IT 부재. 9.2.4 case 보완 권고.
- **🟠 Major** — 동일 값 patch 시 audit 미생성 검증 case 부재 (1.5.1 시나리오). `SlipServiceAuditDiffTest.editHeader_memoUnchanged_skipsAudit` 가 부분 커버, `applyOverlayPatch` 단일 필드 endpoint 의 동일 검증 case 1건 추가 권고.
- **🟠 Major** — Redis broker 의 `SlipRealtimeBrokerConcurrencyIT` 등가 IT (Redis testcontainer 기반) 부재. Phase 11 AWS 진입 전 IT 추가 권고.
- **🟢 Info** — 라인 수준 audit (`lines[N].field`) 는 PR-H3 범위. 본 PR 은 헤더 11 필드 시범만.
- **🟢 Info** — `SlipAuditLogResponse.actorColor` null 응답 시 FE `userIdToColor(actorId)` 자동 fallback 검증 case 부재 — 현 디자인 = BE 가 actorColor=null 보내면 FE 가 actorId 로 hash. 명시 IT 추가 권고.

---

## 10. 회귀 영향 평가

| 영역 | 회귀 가능 | 평가 |
|---|---|---|
| 기존 `Slip` CRUD 회귀 | 낮음 | V18 = `slip_audit_logs` 별도 테이블 ADD only + `slips.revision_count` ADD column (DEFAULT 0). 기존 row 자동 0 — 기존 SlipService API 무변경 |
| 기존 IT 통과 | 낮음 | `ApplicationContextLoadIT` audit bean + Redis bean 단일 등록 가드 추가 — 기존 통과 유지 |
| 기존 SSE (`slip_comments`) 회귀 | 낮음 | `slip:edit` / `slip:reverted` 신규 event name. 기존 `comment.created` 이벤트와 무충돌 |
| FE bundle 크기 | 낮음 | `AuditOverlay` 컴포넌트 + RN 1:1 복제 — design-system 신규 7KB 미만 |
| Redis 의존 추가 | 낮음 | `spring-boot-starter-data-redis` 의존 추가 (기본 비활성) — 미연결 시 startup 정상 (`@ConditionalOnProperty` 가드) |
| 권한 매트릭스 회귀 | 낮음 | 신규 endpoint 3개 모두 `@PreAuthorize` 명시 + 본 시나리오 4.3.1 (DRIVER 차단) IT 보완 권고 |

---

## 11. PASS/FAIL 종합

- 시나리오 27 case 정의 완료 (1.x 5 + 2.x 5 + 3.x 3 + 4.x 4 + 5.x 5 + 6.x 3 + 7.x 2)
- 페르소나 5 (SALES / WAREHOUSE / DRIVER / MANAGER / MASTER) 풀네임
- BE 단위 24 case (6+4+5+4+5 = 24, 단 9.3 PayloadCaptor 3 case 는 IT 분류) + IT 9 case (concurrency 3 + ArgumentCaptor 3 SSE schema + Redis mock 3) 점검 완료 — **모두 PASS, Major 보완 권고 3건**
- multi-context 작동 캡처 절대 의무 → `tools/manual-capture/capture-pr-h2.js` 자동화 + fallback placeholder

**최종 판정**: 본 시나리오 + 단위/IT + multi-context 캡처 4건이 모두 첨부되면 PR-H2 GREEN 머지 가능. 동일 값 patch 거부 + 마감 lock revert CONFLICT + Redis IT 보완 3건은 후속 PR 에서 다룬다.

**Samhan Public 핵심 요구 검증**: "취소선 + 색상 + 수정자 이름 + 1초 sync" 4 요소 모두 본 시나리오 2.1.1 + 2.5.1 + 5.2.1 case 와 `working-multi-context-edit-split.png` 캡처 1장으로 측정 가능한 PASS 기준 달성.
