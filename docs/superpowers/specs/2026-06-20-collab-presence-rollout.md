# collab presence 롤아웃 — 4문서(회계/주문/견적/그룹웨어 결재)

> §7 전역 협업 presence MVP(슬립 1문서, PR #515) 후속. 스펙 `2026-06-19-collab-presence-mvp.md` §5 "후속: 나머지 5문서 동일 패턴 배선(반복)" 이행.
> **PR1 = 패널 보유 4문서**(회계/주문/견적/그룹웨어). 배차(dispatch)는 FE 패널 미존재(comment-only) → **PR2 별도 슬라이스**.

## 0. 토대 (기구현 — 재사용, 변경 금지)
- `shared/realtime-abstraction`: `PresenceService`(generic, UUID entityId), `PresenceEntry`, `PresenceColor`. `RealtimeAutoConfiguration` 가 `@ConditionalOnMissingBean` 으로 `PresenceService` 빈을 자동 등록 → 4 서비스(accounting/partner-order/slip[estimate]/groupware) 모두 realtime-abstraction 의존 → **빈 가용**. 추가 설정 0.
- 슬립 정본: `SlipCollabController`(presence 3엔드포인트 + `SlipPresenceRequest` DTO + helper) / `createPresenceClient.ts`(`SlipPresenceClient`) / `usePresence.ts` / `PresenceIndicator.tsx` / `SlipCollaborationPanel.tsx`(상단 PresenceIndicator) / `mock.ts`(2721~2790 슬립 presence) / `SlipCollabIT.presence_join_list_validates_...`.

## 1. parity/안전 (필수 준수)
- **순수 additive** — 협업 댓글/수정완료·금액·DB 무관. presence 실패해도 기존 collab 동작 보존(graceful, FE try/catch 기구현).
- **신규 권한 page-code·시드·Flyway 마이그 = 0.** presence 엔드포인트는 각 문서의 **기존 댓글 VIEW page-code 재사용**. (이 PC 시드 부재 무관 — IT 자가검증.)
- **UUID 비공개**: presence wire payload = `{sessionId, displayName, color}` 만. userId/accountId/lastSeenAt 노출 금지(PresenceEntry JSON 직렬화가 이미 보장 — IT 로 박제).
- self-only join/leave: leave 는 session owner(X-User-Id) 일치 시에만 실제 제거(PresenceService 기구현).

## 2. BE — 문서별 presence 엔드포인트 (슬립 SlipCollabController 1:1 복제)

각 `{Doc}CollabController` 에 추가:
- `PresenceService` 생성자 주입.
- `{Doc}PresenceRequest` record DTO: `String sessionId, String displayName` (슬립 `SlipPresenceRequest` 복제).
- 3 엔드포인트(슬립 line 201~238 복제):
  - `POST .../collab/presence/join` → `presenceService.join(entityId, sessionId, userId, displayName)`. **201 아님 200**(ApiResponse.ok).
  - `POST .../collab/presence/leave` → `presenceService.leave(entityId, sessionId, userId)`. 200.
  - `GET .../collab/presence` → `presenceService.list(entityId)`. 200.
- 권한 가드: 각 문서 **댓글 VIEW** 와 동일.
- helper(슬립 295~321 복제): `resolvePresenceUserId`(X-User-Id 필수, 없으면 `UNAUTHORIZED`), `resolvePresenceSessionId`(body.sessionId 필수, 없으면 `INVALID_INPUT`), `resolvePresenceDisplayName`(X-User-Name 우선, UUID-shape/blank→null→PresenceService 가 "사용자" 기본).
- `@ExceptionHandler(MissingRequestHeaderException)`(슬립 323~332 복제): X-User-Id 누락 → 401 UNAUTHORIZED, 그 외 → 400 INVALID_INPUT. **단, 컨트롤러에 이미 @ExceptionHandler 가 있으면 충돌 주의** — 없을 때만 추가, 있으면 X-User-Id 분기만 병합.

### 문서별 차이 (정확히 반영)
| 문서 | 컨트롤러 (절대경로) | base path | entityId | VIEW 가드 | 엔티티 존재검증 |
|---|---|---|---|---|---|
| 회계 | `services/accounting-service/.../web/collab/JournalCollabController.java` | `/accounting/journals/{journalId}/collab` | `journalId`(UUID 직접) | `@RequirePermission(page="accounting.journals", action=VIEW)` | 기존 stream/comments 의 journal 로드 방식 mirror |
| 주문 | `services/partner-order-service/.../web/collab/PartnerOrderCollabController.java` | `/api/v1/partner-orders/{orderId}/collab` | **`resolveOrderId(orderId)` → UUID**(String path) | `@RequirePermission(page=READ_PAGE_CODE, action=VIEW)` | resolveOrderId 가 NOT_FOUND 던짐 |
| 견적 | `services/slip-service/.../estimate/web/collab/EstimateCollabController.java` | `/slips/estimates/{estimateId}/collab` | `estimateId`(UUID) | `@RequirePermission(page=PAGE_CODE, action=VIEW)` **+ `permissionGuard.checkView(parseAccountIdOrNull(callerId), isSystemMaster)`** | `ensureEstimateExists` |
| 그룹웨어 | `services/groupware-service/.../controller/GroupwareApprovalCollabController.java` | `/admin/groupware/approvals/{approvalId}/collab` | `approvalId`(UUID) | `@RequirePermission(page="groupware.approvals", action=VIEW)` | `ApprovalLineRepository.existsById` mirror |

- **주문 특이**: join/leave/list 모두 `@PathVariable String orderId` → `UUID resolvedOrderId = resolveOrderId(orderId)` 후 PresenceService 에 resolvedOrderId 전달(SSE 채널과 동일 UUID 채널 정합). presence userId 는 X-User-Id 그대로(resolveActorId 아님 — presence userId 는 색상 hash 입력).
- **견적 특이**: presence 3엔드포인트에 `X-Is-System-Master` 헤더 + `permissionGuard.checkView(...)` 동반(stream 엔드포인트와 동일 가드 셋). presence userId(X-User-Id 필수)는 `resolvePresenceUserId` 로 별도(parseAccountIdOrNull 은 가드용, null 허용).

## 3. FE — 문서별 PresenceClient + 패널 배선

### 3.1 `createPresenceClient.ts` — 4 client export 추가 (`SlipPresenceClient` 패턴)
**presencePath/streamPath = 각 문서 기존 `{Doc}CollabRealtimeClient` 의 endpointPath(=streamPath) 를 1:1 mirror**, presencePath = streamPath 의 `/stream` → `/presence`(+ action 시 `/${action}`). 게이트웨이 StripPrefix 정합을 위해 **추정 금지, 기존 client 경로 복제**:
- `JournalPresenceClient`: presence `/accounting/journals/${id}/collab/presence`, stream `/accounting/journals/${id}/collab/stream`.
- `PartnerOrderPresenceClient`: presence `/api/v1/partner-orders/${id}/collab/presence`, stream `/api/v1/partner-orders/${id}/collab/stream`.
- `EstimatePresenceClient`: presence `/slips/estimates/${id}/collab/presence`, stream `/slips/estimates/${id}/collab/stream`.
- `GroupwareApprovalPresenceClient`: presence `/admin/groupware/approvals/${id}/collab/presence`, stream `/admin/groupware/approvals/${id}/collab/stream`.
- (모두 `encodeURIComponent(id)`.)

### 3.2 4 패널 배선 (`SlipCollaborationPanel` line 141 + 265~268 복제)
- `const presenceEntries = usePresence({ entityId: <idProp>, client: <DocPresenceClient>, enabled: !!<idProp> })`.
- 패널 협업 헤더(제목 우측)에 `<PresenceIndicator entries={presenceEntries} />`.
- idProp/패널/client:
  - 회계 `JournalCollaborationPanel`(prop `journalId`, `JournalPresenceClient`).
  - 주문 `PartnerOrderCollaborationPanel`(prop `orderId`, `PartnerOrderPresenceClient`).
  - 견적 `EstimateCollaborationPanel`(prop `estimateId`, `EstimatePresenceClient`).
  - 그룹웨어 `GroupwareApprovalCollaborationPanel`(prop `approvalId`, `GroupwareApprovalPresenceClient`).
- 헤더 레이아웃이 슬립과 다르면(기존 패널 구조 유지) PresenceIndicator 를 협업 제목 옆 flex 우측에 자연스럽게 배치. `usePresence` 의 default `client=SlipPresenceClient` 를 **반드시 문서별 client 로 override**(누락 시 전 문서가 슬립 경로로 join → 교차오염).

### 3.3 `mock.ts` — 문서별 presence mock (슬립 2721~2790 복제)
- 각 문서 collab 블록 근처에 presence action(join|leave POST) + list(GET) mock. **per-doc URL regex**(base path 반영) + **per-doc presence store**(`__SAMHAN_MOCK_{DOC}_PRESENCE`).
- 3원칙([[inprocess-mock-principles]]): ①`parseMockBody` ②성공/Void 모두 non-null `envelope`(leave=`envelope(null)`, 미매칭만 fallthrough) ③색상 hash = `readMockHeader('X-User-Id')||sessionId`.
- 공용 helper(`readMockHeader`/`colorForPresence`/`mockPresenceColors`/`MockPresenceEntry` 타입)는 슬립 블록 내 지역 스코프 → **모듈 공용 헬퍼로 hoist**(중복 정의 금지) 또는 각 블록 내 재정의. hoist 우선(DRY) — 단 슬립 기존 동작 불변 보장.

## 4. 테스트
### BE IT (각 서비스 collab IT 에 1 테스트 추가 — `SlipCollabIT.presence_join_list_validates_header_input_payload_and_permission_guard` 복제)
- `JournalCollabIT` / `PartnerOrderCollabIT` / `EstimateCollabIT` / `ApprovalCollabIT`.
- 단언: (a) X-User-Id 누락 join → 401 UNAUTHORIZED, (b) sessionId 빈값 → 400 INVALID_INPUT, (c) 정상 join → 200 + `data` = `{sessionId, displayName, color}` 만(`userId`/`accountId`/`lastSeenAt` 부재), displayName=X-User-Name(body name 무시), (d) GET presence → 1건 + userId 부재, (e) VIEW 권한 deny → 403.
- 주문은 실 주문 seed 후 orderId(또는 orderNo path) 로 호출(resolve 경유). 견적은 permissionGuard deny stub 으로 403.
- **변경 모듈 전체 test 완주 후 push**([[changed-module-full-test-before-push]]) — 신규 IT 타깃만 실행 금지.

### FE
- `npm run typecheck`([[desktop-typecheck-command]]) + design-system 빌드 + vitest(PresenceIndicator.test 기존 + 패널 presence 렌더 단위 추가 가능).
- Playwright mock spec: 각 문서 collab spec 에 presence indicator 등장 단언 추가(가능 시), 또는 슬립 `slip-collab-panel.spec.ts` presence 패턴 복제.

## 5. QA (이 PC 시드 부재 제약 — 정직 보고)
- **2세션 라이브 presence 캡처**는 시드(거래처/전표/주문/견적/결재 문서) 필요 → 이 집 PC 시드 부재로 **불가 가능성**. Docker 스택 가용 + 런타임 엔티티 생성 가능 시 라이브 캡처, 불가 시 **"캡처 불가 + 사유(시드 부재)" 명시 + Linux CI IT(presence join/leave/list/권한) 결과 첨부**([[qa-docker-real-test]] Docker 미가용 시 P2 명시 의무). **가짜 캡처 금지**([[no-fake-data-ever]]).
- BE presence IT(self-seed Testcontainers) = 회귀 1차 게이트. FE mock Playwright = presence indicator UI 박제.

## 6. 리뷰 / 머지
- 조기 PR([[open-pr-early]]) → 듀얼리뷰(Opus 5-agent + Codex 교차; **Codex MCP 다운 시 Agent/codex-exec 대체, 회복 시 알림** [[temp-multimodel-workflow]] 환경예외) → 수렴(마지막 fix 모델 ≠ 마지막 리뷰 모델 금지) → CI green → IT QA → PM 자동 머지([[user-merge-authority]]).
- 문서 동기화([[continuous-docs-sync]]): dev-report `docs/dev-reports/2026-06-20-collab-presence-rollout.md` + README/ROADMAP/overview.html presence 진행 갱신.
- **PR2(배차)**: DispatchCollaborationPanel 신설 + `/admin/dispatch-tasks/{taskId}/collab/presence` 엔드포인트 + 패널을 배차 상세/보드 어디에 노출할지 정찰 후 결정. PR1 머지 후 무중단 진입.
