# collab presence MVP — 인프라 + 슬립 1문서 (동시 접속자+색상)

> §7 전역 협업 후속(코멘트/수정완료 6문서 완결, presence 미구현). 개발책임자 "모두 해소" 선택. 정찰: presence는 기존 realtime-abstraction(RealtimeBroker SSE+heartbeat) + collab 컨트롤러/FE 패널 토대 위 구현. 에픽 7슬 → **본 슬라이스=MVP(인프라+슬립)**, 나머지 5문서는 후속 반복.

## 0. 토대 (기구현 — 재사용)
- `shared/realtime-abstraction`: RealtimeBroker(InMemory/Redis) SSE pub/sub + 30s heartbeat + entityId 구독.
- 6문서 collab 컨트롤러(slip/accounting/partner-order/estimate/dispatch/groupware) + FE `createRealtimeClient` + collab 패널.
- presence/viewing/occupant grep 0 = 미구현.

## 1. 인프라 (Codex)
### BE — `shared/realtime-abstraction` presence
- `PresenceEntry`(userId, displayName, color) + `PresenceColor` enum(8색, userId hash→결정적).
- `PresenceService`(generic, RealtimeBroker 활용): `join(entityId,userId,displayName)`·`leave`·`list(entityId)` + TTL(5분, FE heartbeat 30s 보다 길게) 정제. SSE 이벤트 `presence:join`/`presence:leave` 기존 채널 발행. thread-safe(ConcurrentHashMap).
- 단위 테스트(join/leave/list/TTL).

### FE — `clients/desktop/src/renderer/realtime`
- `createPresenceClient`(createRealtimeClient 패턴: backoff/heartbeat/SSE parse) + `usePresence` hook(subscribe + join/leave + 30s heartbeat + unmount leave).
- `components/collab/PresenceIndicator.tsx`(design-system Avatar+Tooltip): 시청자 아바타(displayName+색상), 최대 3 + "+N", hover "현재 보고 있음". UUID 비공개(displayName만).

## 2. 슬립 1문서 배선 (MVP)
- slip-service `SlipCollabController` 에 presence endpoint(POST `/slips/{id}/collab/presence/join`·`/leave`, GET `/presence`) — PresenceService 위임. 기존 collab stream 채널에 presence 이벤트 함께 발행(또는 동일 SSE).
- `SlipCollaborationPanel` 상단 PresenceIndicator + usePresence(join on mount, leave on unmount, userId/displayName=auth). 기존 comment stream 과 병행.

## 3. parity/안전
- 순수 추가(협업 코멘트/수정완료·금액 무관). presence 실패해도 기존 collab 동작 보존(graceful). UUID 비공개. self-only join/leave(token).

## 4. 검증
- 단위(PresenceService join/leave/list/TTL). FE typecheck + design-system 빌드.
- **실QA**: 슬립 상세를 2 세션(브라우저/유저) 동시 진입 → PresenceIndicator 상호 아바타 표시 + 1 세션 이탈 시 제거. Docker/로컬 실 캡처.

## 5. 리뷰 / 후속
조기 PR → Codex → Opus+Codex 교차 → 실QA(2세션 presence) → 머지. 후속: 나머지 5문서(회계/주문/견적/배차/그룹웨어) 동일 패턴 배선(반복, presence endpoint+패널 PresenceIndicator).
