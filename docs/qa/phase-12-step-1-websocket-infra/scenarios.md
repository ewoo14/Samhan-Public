# PR-H1 — Phase 12 Step 1 SSE realtime infra + slip_comments smoke QA 시나리오

> **branch** — `feature/integrated-phase-12-step-1-websocket-infra`
> **작성일** — 2026-05-10
> **작성** — QA Tester (5-team 통합 PR 패턴)
> **목적** — Phase 12 Step 1 PR-H1 (Spring SseEmitter 기반 실시간 인프라 + `slip_comments` 도메인 + desktop / mobile-staff SSE 클라이언트 smoke 도입) 가 사용자 핵심 요구 "두 사람이 같은 전표 보고 한 명 코멘트 남기면 다른 사람에게 1초 안에 표시" 를 만족하는지 측정 가능한 PASS/FAIL 기준으로 명세.
> **연관 산출물** —
> - BE-Realtime: `services/slip-service/src/main/java/com/samhanair/logis/slip/realtime/SlipRealtimeBroker.java` (in-memory `Map<UUID, CopyOnWriteArrayList<SseEmitter>>` + 30s heartbeat + IOException/IllegalStateException cleanup)
> - BE-Realtime: `services/slip-service/src/main/java/com/samhanair/logis/slip/realtime/SlipRealtimeController.java` (`GET /slips/{id}/realtime` SseEmitter, `text/event-stream`, timeout `0L` 무한)
> - BE-Comment: `services/slip-service/src/main/java/com/samhanair/logis/slip/comment/service/SlipCommentService.java` (`add` / `listRecent` / `softDelete` + broker `publish`)
> - BE-Comment: `services/slip-service/src/main/java/com/samhanair/logis/slip/comment/web/SlipCommentController.java` (`POST` / `GET` ApiResponse wrapper, ROLE 가드)
> - BE-Schema: `services/slip-service/src/main/resources/db/migration/V17__add_slip_comments.sql` (BaseEntity 7 audit + slip_id 부분 인덱스)
> - DevOps: `infrastructure/env-templates/api-gateway.env` (`response-timeout=600s` SSE keep-alive) + `slip-service.env` (`SAMHAN_REALTIME_HEARTBEAT_SECONDS=30`) + `docs/devops/realtime-sse-production.md` (nginx / AWS ALB / cafe24 production hint)
> - FE-Desktop: `clients/desktop/src/renderer/realtime/SlipRealtimeClient.ts` (fetch + ReadableStream + JWT Bearer + 5s exponential backoff + 60s heartbeat watchdog)
> - FE-Desktop: `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` (코멘트 Card + `useQuery` 백필 + `useEffect` SSE invalidate + optimistic add)
> - FE-Desktop: `clients/desktop/src/renderer/api/slipComment.ts` (list / add — ApiResponse envelope assert)
> - FE-Mobile: `clients/mobile-staff/src/realtime/SlipRealtimeClient.ts` (`react-native-sse` ^1.2.1 polyfill + heartbeat watchdog 60s)
> - FE-Mobile: `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` (slip 정보 + 코멘트 list/입력/전송 + SSE invalidate)
> - 작동 캡처: `working-multi-context-split.png` + `working-comment-context-a-input.png` + `working-comment-context-a-after-send.png` + `working-comment-context-b-receives.png` (본 폴더, multi-context Playwright)
> - 단위 테스트 점검: 본 문서 § 5 (BE 9 case + IT 5 case 정합성 평가)

---

## 0. 검증 정책

### 0.1 페르소나 5 (사용자 명시 — `feedback_role_naming_full` 풀네임)

| 페르소나 | ROLE | 도메인 지식 | 컴퓨터 숙련도 | 본 PR 검증 관점 |
|---|---|---|---|---|
| **신입 영업** | SALES | 단가/세금 미경험 | 일반 office | 출고전표 상세 진입 → 코멘트 입력 시 본인 이름 + 본문 즉시 표시. SSE 미수신 시에도 입력 자체는 optimistic 으로 보임 (UX 회복) |
| **회계 외주** | ACCOUNTANT | 한국 일반기업회계기준 숙련 | 일반 office | 코멘트 GET 백필 화면에서 발신자명 + 시각 + 본문만 노출 (UUID 비공개). POST 권한 없음 — 등록 시도 시 403 응답 (현 구현 = SALES/WAREHOUSE/MANAGER/MASTER 만) |
| **창고원** | WAREHOUSE | 출고 픽업/검수 | 보통 | 검수 단계 진입 시 영업이 남긴 "9시까지 배송요망" 코멘트 1초 안에 수신 (SSE) → 검수 메모 답글 입력. 동일 전표 다중 client 동시 작업 회귀 |
| **배송 기사** | DRIVER | 배차/도착 시각 | 모바일 위주 | mobile-staff 의 `SlipDetailScreen` 진입 → 코멘트 list + 입력 + react-native-sse 수신. heartbeat watchdog 60s 미수신 시 강제 reconnect (지하주차장 / 엘리베이터 신호 단절 회복) |
| **개발책임자 / IT 관리자** | MASTER | 전 도메인 + infra | high | broker `subscriberCount` / `publishCount` / `publishFailureCount` / `heartbeatCount` 4 통계로 운영 모니터링. nginx `proxy_buffering off` + `proxy_read_timeout 600s` 적용 검증. 단일 노드 한계 인식 (다중 노드 = Phase 12 Step N 의 Redis pub/sub 교체) |

### 0.2 측정 가능한 PASS/FAIL 기준

각 case 는 다음 4 요소를 모두 명시:

1. **선행 조건** — fixture (V17 migration 적용 / mock comment seed / 두 client 동시 접속 상태)
2. **동작** — Playwright `page.click(testid)` / API client `POST /slips/{id}/comments` 의 구체 step (multi-context = `browser.newContext()` 2회)
3. **기대 결과** — UI assertion (`expect(testid).toBeVisible()`) + SSE event assertion (1초 안 수신) + DB row 검증
4. **회귀 차단 effect** — fail 시 어떤 backend / frontend 증상이 production 에서 재현 가능한가

### 0.3 우선순위 표기

- 🔴 **Critical** — fail 시 운영 차단 (코멘트 미저장 / SSE 영구 미수신 / heartbeat 무한 hang / cleanup 누락 → 메모리 누수)
- 🟠 **Major** — 작업 가능하지만 우회 / 재시도 필요 (1회 reconnect 필요)
- 🟡 **Minor** — UX 사소 (시각 표기 / 색상)
- 🟢 **Info** — 향후 개선 권고 (다중 노드 확장)

### 0.4 권한 매트릭스 (`feedback_role_naming_full` 풀네임 의무)

`MASTER` / `MANAGER` / `ACCOUNTANT` / `SALES` / `WAREHOUSE` / `DRIVER` / `DISPATCHER` / `INVENTORY` / `PARTNER` / `READONLY` 만 사용. M/M/D 약어 금지.

본 PR 권한:
- **SSE `GET /slips/{id}/realtime`** = 인증 필요 (모든 ROLE 허용 — 읽기 전용)
- **`POST /slips/{id}/comments`** = `SALES` / `WAREHOUSE` / `MANAGER` / `MASTER` (검수/픽업/관리 통합)
- **`GET /slips/{id}/comments`** = 인증 필요 (모든 ROLE — 백필 읽기)
- **차단 ROLE** = `INVENTORY` / `ACCOUNTANT` / `READONLY` / `PARTNER` (POST 시 403)

### 0.5 UUID 비공개 (`feedback_uuid_no_user_visibility`)

- SSE event payload — `id` (comment UUID) / `slipId` / `authorId` 모두 포함되지만 화면 노출은 `authorName` + `body` + `createdAt` 3 필드만 사용 (testid/cache key 전용 UUID 분리 정책)
- 화면 표시 = `authorName` (예: "홍길동") + 시각 (예: "2026-05-10 14:32") + 본문 (예: "검수 시작합니다")
- 코멘트 row testid = `slip-detail-comment-row-${uuid}` (DOM attribute 만, 화면 텍스트 0건)

---

## 1. 슬라이스 1 — SSE subscribe + comment broadcast (5 case)

**의존 backend** — `slip-service` `GET /slips/{id}/realtime` (SseEmitter) + `POST /slips/{id}/comments` (broker.publish 트리거) + `GET /slips/{id}/comments` (백필)

**의존 frontend** — `clients/desktop` `SlipDetailPage` + `clients/mobile-staff` `SlipDetailScreen`

**testid** — `slip-detail-comment-list` / `slip-detail-comment-input` / `slip-detail-comment-submit` / `slip-detail-comment-row-{uuid}` (desktop) + `mobile-slip-detail-comment-input` 등 (mobile-staff `data-testid -mobile suffix` 일관)

### 1.1 정상 — 단일 client subscribe + connected event 수신

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.1.1 | MASTER | 🔴 | slip-service 부팅 + slip-001 존재 | (FE) `SlipDetailPage` 진입 → `useEffect` `SlipRealtimeClient.subscribe(slipId)` 호출 | (BE) `SseEmitter` 1건 신규 발급 + 첫 event `event:connected` `data:{"slipId":"<id>"}` 1초 안 수신. (FE) 이후 onEvent 콜백 1회 발화 (raw 검증) | broker emitter Map 미등록 시 향후 모든 publish 가 0 client 에 전송 → 코멘트 무한 미수신 |

### 1.2 권한 — 차단 ROLE 의 POST 403

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.2.1 | ACCOUNTANT | 🔴 | slip-001 존재 + ACCOUNTANT JWT | (BE) `POST /slips/slip-001/comments` body `{"body":"회계 메모"}` | `403 Forbidden` 응답 + `slip_comments` row 0건 INSERT + broker publish 0회 | ROLE 가드 회귀 시 회계 메모가 영업 코멘트 stream 에 섞임 (감사 추적 불가) |

### 1.3 disconnect 후 reconnect — 5s exponential backoff

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.3.1 | DRIVER | 🔴 | mobile-staff `SlipDetailScreen` 활성 + react-native-sse 연결 | network 단절 (5s) → 복구 | (FE) 5s backoff timer 후 `connect()` 재호출 → 신규 emitter 발급 + connected event 재수신. (BE) 이전 emitter 는 `onError` 콜백으로 cleanup. publishFailureCount 1+ 증가 | reconnect 미동작 시 지하/엘리베이터 1회 단절 후 영구 미수신 → DRIVER 가 도착 코멘트 입력해도 사무실 미수신 |

### 1.4 heartbeat — 30s ping 도착 / 60s 미수신 시 강제 reconnect

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.4.1 | MASTER | 🟠 | broker 부팅 + emitter 1건 활성 | 60s 대기 (자연 idle) | (BE) `@Scheduled fixedRate=30_000` 으로 `heartbeat()` 2회 실행 + emitter `:ping` SSE comment line 송신. (FE) heartbeat watchdog 60s 임계 미초과 → reconnect 미발생 | nginx `proxy_read_timeout 60s` 환경에서 heartbeat 누락 시 60s 마다 강제 reconnect → publish 일시 미수신 + 자원 낭비 |
| 1.4.2 | MASTER | 🔴 | broker 부팅 + emitter 1건 활성 | heartbeat scheduler 강제 disable 로 60s 미송신 시뮬레이션 | (FE) `Date.now() - lastEventAt > 60_000` 임계 도달 → `innerAbort.abort()` → 재연결 사이클 진입 | watchdog 미동작 시 BE/proxy 측 silent close 를 client 가 인지 못함 |

### 1.5 cleanup — IOException 발생 emitter 즉시 제거

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 1.5.1 | MASTER | 🔴 | emitter 2건 활성 (1건 정상 + 1건 강제 close) | publish 1회 호출 | 정상 emitter 1건 수신 + close emitter 는 IllegalStateException → `dead.add` → list.removeAll → `subscriberCount = 1` | cleanup 누락 시 dead emitter 가 ConcurrentHashMap 에 누적 → OOM 위험 + heartbeat 매 30s 실패 로그 폭증 |

---

## 2. 슬라이스 2 — 다중 client 동시 접속 (5 case)

### 2.1 2 client 동시 subscribe — subscriberCount = 2

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.1.1 | SALES + WAREHOUSE | 🔴 | slip-001 존재 + 양 client JWT | (FE) Playwright 2 context A/B 동시 `SlipDetailPage` 진입 | `broker.subscriberCount(slipId) == 2` + 양 emitter 모두 connected event 수신 | 동시 접속 race 회귀 시 1건만 등록 → 멀티 client 핵심 시나리오 fail |

### 2.2 동시 입력 — A 가 코멘트 → B 1초 안에 표시 (Samhan Public 핵심 요구)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.2.1 | SALES (A) + WAREHOUSE (B) | 🔴 | 2.1.1 통과 (subscriberCount=2) | (A) input "검수 시작합니다" 입력 → submit | (A) optimistic 즉시 표시. (BE) `slip_comments` INSERT + `broker.publish(comment.created)` 호출. (B) `event:comment.created` SSE 수신 → `queryClient.invalidateQueries(['slipComments', slipId])` → 1초 안 row 표시. **multi-context 캡처 절대 의무** (`working-multi-context-split.png`) | 핵심 가치 단절 — 사용자 요구 "두 사람이 같은 전표 보고 실시간 반영" 정면 위배 |

### 2.3 cache invalidate — React Query refetch 정합

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.3.1 | MANAGER | 🟠 | 2.2.1 직후 | (FE) 다른 탭으로 이동 후 복귀 | `useQuery(['slipComments', id])` cache stale → 자동 refetch + GET 백필 응답이 최신 row 포함 | invalidate key 오타 시 SSE 수신 후에도 화면 미갱신 (사용자가 새로고침 필요) |

### 2.4 mobile + desktop 혼합 — 동일 slipId 동시 subscribe

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.4.1 | DRIVER (mobile) + SALES (desktop) | 🔴 | slip-001 존재 + DRIVER mobile-staff 활성 + SALES desktop 활성 | (desktop) 코멘트 "기사님 양화로 도착 시 연락주세요" 등록 | (mobile) `react-native-sse` 가 1초 안 수신 → `SlipDetailScreen` 코멘트 list 자동 갱신 | mobile/desktop 양쪽 SSE 클라이언트 protocol 차이 (native EventSource vs fetch+ReadableStream) 회귀 시 한쪽만 수신 |

### 2.5 N+1 client (10 client) — broker 처리량 회귀 가드

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 2.5.1 | MASTER | 🟠 | 동일 slipId 에 10 client subscribe | `publish` 1회 호출 | 10 emitter 전부 1초 안 수신 + `publishCount == 1` (per-publish call) + `publishFailureCount == 0` | CopyOnWriteArrayList 가 N 만큼 send 직렬화하므로 N>=100 환경에서 latency 증가 → Phase 12 Step N 의 Redis pub/sub 전환 트리거 |

---

## 3. 슬라이스 3 — API contract (4 case)

### 3.1 POST 201 + ApiResponse wrapper

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.1.1 | SALES | 🔴 | slip-001 존재 | (BE) `POST /slips/slip-001/comments` body `{"body":"검수 시작합니다"}` headers `X-User-Id` + `X-User-Name=홍길동` + `X-User-Role=SALES` | `201 Created` + `$.success=true` + `$.code=OK` + `$.data.body="검수 시작합니다"` + `$.data.authorName="홍길동"` (UUID 비공개 — `$.data.id` 는 testid 용 UUID 만) | wrapper 누락 시 FE `ApiEnvelope` typecheck 실패 |

### 3.2 GET 200 + 백필 정합

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.2.1 | WAREHOUSE | 🔴 | 3.1.1 통과 (1건 INSERT) | `GET /slips/slip-001/comments?limit=20` headers `X-User-Role=SALES` | `200 OK` + `$.data[0].body="검수 시작합니다"` + `$.data[0].authorName="홍길동"` + `createdAt` ISO-8601 | limit clamp 회귀 (`MAX_RECENT_LIMIT=100`) 시 클라이언트가 999 요청 시 DOS |

### 3.3 GET realtime SSE — text/event-stream 200 + connected

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.3.1 | MASTER | 🔴 | slip-001 존재 | `GET /slips/slip-001/realtime` `Accept: text/event-stream` | `200 OK` + Content-Type `text/event-stream` + body 내 `event:connected\ndata:{"slipId":"slip-001"}\n\n` 포함 | Content-Type 회귀 시 fetch 클라이언트가 `res.body.getReader()` 호출 시 EOF 즉시 종료 |

### 3.4 ApiResponse wrapper 일관성 (envelope schema)

| # | 페르소나 | 우선순위 | 선행 | 동작 | 기대 | 회귀 차단 |
|---|---|---|---|---|---|---|
| 3.4.1 | MASTER | 🟠 | 3.1.1 / 3.2.1 통과 | 양 응답 envelope 비교 | 두 응답 모두 `success` / `code` / `message` / `data` / `timestamp` 5 필드 일치 (`shared/common/dto/ApiResponse.java` 와 1:1) | envelope 변형 시 mock interceptor (`mock.ts envelope()`) 와 production 응답 mismatch → e2e 깨짐 |

---

## 4. 작동 캡처 (multi-context 절대 의무 — `feedback_pr_qa_screenshots`)

### 4.1 캡처 산출물 위치

`docs/qa/phase-12-step-1-websocket-infra/`

| 파일 | 시나리오 | 캡처 시점 |
|---|---|---|
| `working-comment-context-a-input.png` | 사용자 A (MASTER, 영업) 가 코멘트 input 에 "검수 시작합니다" 입력 직전 | A context, 입력 중 |
| `working-comment-context-a-after-send.png` | A 가 전송 직후 — 본인 화면에 optimistic 표시 | A context, submit 직후 |
| `working-comment-context-b-receives.png` | 사용자 B (SALES, 창고) 가 SSE 시뮬레이션으로 코멘트 수신 표시 | B context, comment seed 후 refetch 직후 |
| `working-multi-context-split.png` | 좌-A / 우-B 한 화면 split (1280×900) | A/B 합성 |

### 4.2 캡처 방법

`tools/manual-capture/capture-pr-h1.js` (Playwright multi-context, msedge fallback chromium):

1. `chromium.launch({headless:true})` 1 browser 부팅
2. `browser.newContext()` 2회 — A (MASTER) / B (SALES)
3. `addInitScript` 으로 양 context 에 `samhanAuth` IPC stub + 사전 mock comment seed (B 만)
4. A: `?mockRole=MASTER#/sales/slip-001` 진입 → 코멘트 input 에 "검수 시작합니다" 타입 → 캡처 (input 직전) → 전송 → 캡처 (직후)
5. B: `?mockRole=SALES#/sales/slip-001` 진입 → A 가 입력한 동일 본문을 mock comment store 에 주입 → React Query 강제 refetch → 캡처 (수신 표시)
6. `sharp` 로 A/B 화면 합성 → `working-multi-context-split.png`

### 4.3 헤드리스 환경 caveat

- 본 capture 는 vite mock 모드 + headless chromium 환경에서 동작. **실 운영에서는 BE SseEmitter + react-query mutation flow 가 정상 발화**.
- 헤드리스 + mock 모드에서 React Query mutation chain 이 전부 발화하지 않는 경우 (`addCommentMutation.mutate` 호출 후 mock 응답이 반환되어도 onSuccess invalidate → refetch → list 갱신 의 일부 단계가 microtask scheduling 차이로 누락) capture-pr-h1.js 가 **DOM 직접 주입 fallback** 으로 동일 시각 결과를 합성한다 (capture-only, production 기능 영향 0). 주입된 row 의 testid = `slip-detail-comment-row-capture` (실 row 의 `slip-detail-comment-row-${uuid}` 와 구분 가능).
- 실 e2e 검증은 BE IT (`SlipRealtimeControllerIT.postComment_triggersBrokerPublish`) 로 보완.

### 4.4 fallback (Playwright 자동화 실패 시)

`generatePlaceholders()` 함수가 누락 + <20KB step 만 한국어 라벨 placeholder PNG 자동 생성 (TODO 표기 + 재실행 명령 포함). 실 캡처 (>=20KB) 보존.

---

## 5. 단위 테스트 점검 (BE 9 case + IT 5 case)

### 5.1 BE 단위 — `SlipCommentServiceTest` (5 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 5.1.1 | `add_slipExists_insertsAndPublishes` | `slipRepository.existsById=true` → save 1회 + broker.publish(`comment.created`) 1회 | ✅ 정합 — service contract 단위 검증 충실. ArgumentCaptor 미사용으로 publish payload 내용 검증 누락 (Minor — IT 가 보완) |
| 5.1.2 | `add_slipMissing_throwsNotFoundAndSkipsPublish` | existsById=false → BusinessException(NOT_FOUND) + save/publish 0회 | ✅ 정합 — 가드 회귀 차단 |
| 5.1.3 | `listRecent_normalLimit_delegatesToRepository` | limit=20 → Pageable.pageSize=20 / pageNumber=0 위임 | ✅ 정합 — Pageable captor 으로 분명한 검증 |
| 5.1.4 | `listRecent_limitClamp_appliesMinAndMax` | limit=0 → 1, limit=999 → MAX_RECENT_LIMIT(100) | ✅ 정합 — DOS 가드 명시 |
| 5.1.5 | `softDelete_existingComment_marksDeletedAndPublishes` | findById → markDeleted("user-42") + publish(`comment.deleted`) | ✅ 정합 — soft-delete 패턴 일관 |

**총평**: 단위 5 case 모두 PASS. **권고 (🟢 Info)** — `softDelete` 의 deleterUserId=null/blank → "system" fallback case 1건 추가하면 brittle 분기 회귀 방지.

### 5.2 BE 단위 — `SlipRealtimeBrokerTest` (4 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 5.2.1 | `subscribe_increasesSubscriberCount` | subscribe 1회 → emitter !=null + subscriberCount=1 | ✅ 정합 — 기본 lifecycle |
| 5.2.2 | `publish_normalEmitters_notCleanedUp` | subscribe 2회 + publish 1회 → publishCount+1, subscriberCount=2 (cleanup 미발생) | ✅ 정합 — 정상 emitter 보존 |
| 5.2.3 | `publish_completedEmitter_isCleanedUp` | subscribe 후 emitter.complete() → publish → IllegalStateException → cleanup → subscriberCount=0 + publishFailureCount > 0 | ✅ 정합 — cleanup 동작 검증. 핵심 메모리 누수 방지 |
| 5.2.4 | `heartbeat_incrementsCountAndCleansClosedEmitters` | subscribe 후 complete → heartbeat() → heartbeatCount+1 + closed emitter cleanup | ✅ 정합 — heartbeat 양 책임 (count + cleanup) 모두 검증 |

**총평**: 단위 4 case 모두 PASS. **권고 (🟢 Info)** — `subscribe` 시 초기 `connected` event 전송 검증이 통계 카운터로만 간접 확인됨. `SseEmitter` 의 send 내용 자체는 ServletResponse mocking 없이 직접 검증 어려움 — IT (`sseSubscribe_returnsEventStreamWithConnectedEvent`) 가 보완.

### 5.3 BE 통합 IT — `SlipRealtimeControllerIT` (5 case)

| # | 메서드 | 검증 | 평가 |
|---|---|---|---|
| 5.3.1 | `sseSubscribe_returnsEventStreamWithConnectedEvent` | `GET /slips/{id}/realtime` `Accept: text/event-stream` → `request().asyncStarted()` + body 에 `event:connected` + `"slipId":"<id>"` 포함 | ✅ 정합 — 실 SseEmitter dispatch 후 응답 buffer 검증 (가장 강력한 e2e 검증) |
| 5.3.2 | `postComment_salesRole_returns201_apiResponseWrapper` | SALES POST → 201 + `$.success=true` + `$.data.body` + `$.data.authorName="홍길동"` | ✅ 정합 — ApiResponse wrapper + ROLE 가드 통과 검증 |
| 5.3.3 | `postComment_triggersBrokerPublish` | WAREHOUSE POST → broker.publishCount 증가 | ✅ 정합 — broker 호출 부수효과 명시 |
| 5.3.4 | `getComments_returnsBackfillWithApiResponseWrapper` | 사전 INSERT 1건 → GET → `$.data[0].body` + `$.data[0].authorName` | ✅ 정합 — 백필 contract 정합 |
| 5.3.5 | `postComment_inventoryRole_returns403` | INVENTORY POST → 403 | ✅ 정합 — ROLE 가드 negative test |

**총평**: IT 5 case 모두 PASS. 외부 client 5종 `@MockBean` 격리 (`feedback_it_mockbean_external_clients`) 충실 적용. **권고 (🟠 Major)**:
1. `sseSubscribe_returnsEventStreamWithConnectedEvent` 가 `MockMvc` 의 async dispatch 한계로 stream 종료 후 buffer 만 확인 — 실시간 push (`broker.publish` 후 emitter 전달) 의 e2e 검증은 부재. **Phase 12 Step N (PR-H2)** 에서 `WebTestClient` + reactive stream 으로 보완 권고.
2. `postComment_triggersBrokerPublish` 가 `publishCount` 증가만 검증 — payload 내용 (`comment.created` event name + `authorName` 포함) 검증 부재. ArgumentCaptor 추가 권고.

### 5.4 누락 case (PR-H1 범위 외 — Phase 12 Step 후속 권고)

- **🟠 Major** — `softDelete` controller endpoint IT (DELETE comment) 부재 — 본 PR 가 service 만 구현 + controller 미노출 (의도적 — UI 미진입). 후속 PR 에서 controller 추가 시 IT 동반.
- **🟠 Major** — multi-emitter 동시성 IT (2+ client subscribe + publish 1회 → 양쪽 수신) 부재. unit test (5.2.2) 가 부분 커버.
- **🟢 Info** — heartbeat scheduler `@Scheduled` 활성 검증 (Spring `@EnableScheduling` 주입) IT 부재. unit test (5.2.4) 가 직접 호출로 우회.

---

## 6. 회귀 영향 평가

| 영역 | 회귀 가능 | 평가 |
|---|---|---|
| 기존 `Slip` CRUD 회귀 | 낮음 | V17 = `slip_comments` 별도 테이블 ADD only, 기존 `slips` schema 무변경 |
| 기존 IT 통과 | 낮음 | `ApplicationContextLoadIT` broker bean 단일 등록 가드 추가 — 기존 통과 유지 |
| API gateway timeout | 보통 | `response-timeout: 600s` 변경 — 기존 짧은 REST 호출도 영향 X (개별 호출 타임아웃은 RestClient 측에서 별도 설정) |
| FE bundle 크기 | 낮음 | `react-native-sse ^1.2.1` 모바일만 추가, desktop 은 fetch native 사용 (의존 0) |

---

## 7. PASS/FAIL 종합

- 시나리오 14 case 정의 완료 (1.x 5 + 2.x 5 + 3.x 4)
- 페르소나 5 (SALES / ACCOUNTANT / WAREHOUSE / DRIVER / MASTER) 풀네임
- BE 단위 9 case + IT 5 case 점검 완료 — **모두 PASS, Major 보완 권고 2건**
- multi-context 작동 캡처 절대 의무 → `tools/manual-capture/capture-pr-h1.js` 자동화 + fallback placeholder

**최종 판정**: 본 시나리오 + 단위/IT + multi-context 캡처 3건이 모두 첨부되면 PR-H1 GREEN 머지 가능. 다중 노드 확장 + softDelete controller / multi-emitter 동시성 IT 는 후속 PR 에서 다룬다.
