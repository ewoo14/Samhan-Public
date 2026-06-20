# collab presence 4문서 롤아웃 (회계/주문/견적/그룹웨어) — dev-report

> PR #545 · 브랜치 `feat/collab-presence-rollout-4docs` · 2026-06-20
> §7 전역 협업 presence MVP(슬립 1문서, PR #515) 후속. 패널 보유 4문서에 동시 접속자 presence 를 순수 additive 배선.

## 1. 범위 / 결정
- **PR1 = 패널 보유 4문서**(회계전표·주문·견적·그룹웨어 결재). 배차(dispatch)는 FE collab 패널 미존재(comment-only) → **PR2 별도 슬라이스**.
- **신규 권한 page-code · 시드 · Flyway 마이그 = 0** (각 문서 기존 댓글 VIEW page-code 재사용). 순수 additive — 기존 collab 댓글/수정완료·금액·DB 무관.

## 2. BE (4 서비스 — 슬립 `SlipCollabController` 1:1 복제)
각 `{Doc}CollabController` 에 추가:
- `PresenceService` 주입(`shared:realtime-abstraction` `RealtimeAutoConfiguration` 자동 빈, 추가 설정 0).
- `POST /presence/join`·`POST /presence/leave`·`GET /presence` (200) + `{Doc}PresenceRequest`(sessionId/displayName) DTO + helper(resolvePresenceUserId/SessionId/DisplayName) + `@ExceptionHandler(MissingRequestHeaderException)`(X-User-Id 누락 401, 그 외 400).
- presence:join/leave 이벤트는 기존 collab SSE 채널(`broker.subscribe(entityId)`)로 발행.

| 문서 | 컨트롤러 | base path | VIEW 가드(재사용) | 특이 |
|---|---|---|---|---|
| 회계 | `JournalCollabController` (accounting-service) | `/accounting/journals/{id}/collab` | `accounting.journals` | — |
| 주문 | `PartnerOrderCollabController` (partner-order-service) | `/api/v1/partner-orders/{id}/collab` | `sales.partner-order.list` | `resolveOrderId(String→UUID)` 후 PresenceService(SSE 채널 정합) |
| 견적 | `EstimateCollabController` (slip-service) | `/slips/estimates/{id}/collab` | `estimates.list` | `EstimatePermissionGuard.checkView` + `X-Is-System-Master` 동반 |
| 그룹웨어 | `GroupwareApprovalCollabController` (groupware-service) | `/admin/groupware/approvals/{id}/collab` | `groupware.approvals` | — |

- IT: 각 서비스 collab IT 에 `presence_join_list_validates_...` — (a)X-User-Id 누락 join→401 (b)sessionId 빈값→400 (c)정상 join→200 + payload 정확히 `{sessionId,displayName,color}`(userId/accountId/lastSeenAt 부재) (d)GET list (e)VIEW deny→403. **실 Postgres Testcontainers 통과**.

## 3. FE (desktop)
- `createPresenceClient.ts`: `Journal/PartnerOrder/Estimate/GroupwareApproval PresenceClient` 4종(각 문서 기존 `{Doc}CollabRealtimeClient` streamPath 1:1 mirror, presencePath=`/stream`→`/presence`).
- 4 패널(`Journal/PartnerOrder/Estimate/GroupwareApproval CollaborationPanel`)에 `usePresence({entityId, client, enabled})` + `<PresenceIndicator/>` 배선(문서별 client override — 슬립 경로 교차오염 방지).
- `mock.ts`: 공용 presence helper hoist + 4문서 presence mock(per-doc URL regex + store, leave=envelope(null) non-null 계약).

## 4. parity / 안전
- UUID 비공개: presence wire payload = `{sessionId, displayName, color}` 만(IT `containsOnlyKeys` 박제). self-only leave(X-User-Id owner 일치). presence 실패해도 기존 collab 동작 보존(graceful).

## 5. QA (Docker 라이브 실 QA — 실 게이트웨이 :8080 + 실 JWT)
- **API-level 4/4**: 4문서 presence join→200 + payload 정확, partner-order 하이픈 path resolve, 멀티유저 LIST count=2(색상 구분).
- **UI 2세션 4/4**(`docs/qa/collab-presence-rollout/`): master + 문서별 2차 사용자 동시 진입 → PresenceIndicator "현재 보는 중: …" 2명 상호 표시 캡처. (회계=개발마스터/개발회계, 견적=개발마스터/개발영업, 주문=개발마스터/개발영업, 그룹웨어=개발마스터/개발매니저). Playwright `4 passed`.

## 6. 🪤 교훈
- **로컬 real-qa 프록시 글롭 함정**: `page.route('**/accounting/**')` 같은 넓은 글롭은 앱 lazy 라우트 청크(`/routes/accounting/*.tsx`)까지 매칭→게이트웨이 404→앱 마운트 실패(백지). **`resourceType`(xhr/fetch) 가드 + `/collab/` 전용 글롭**으로 백엔드 호출만 가로채야 함. 렌더러는 `vite --config vite.renderer.dev.config.ts`(별칭 보유) + `VITE_API_BASE_URL`. networkidle 대기는 SSE 재시도로 영원히 idle 안 됨 → presence-indicator 가시성 대기로 대체.
- **로컬 Docker DB checksum 드리프트**: accounting V39/groupware V7(#482 신규 마이그)이 로컬 DB(과거 피처브랜치 적용)와 checksum 불일치 → 서비스 기동 실패. **내 PR 무관·prod 무해**(파일 status A 신규추가, fresh DB=CI green). flyway repair(checksum 정렬, 마이그 효과 기적용)로 로컬 unblock.
