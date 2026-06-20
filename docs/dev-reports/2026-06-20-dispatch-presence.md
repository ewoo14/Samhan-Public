# 배차(dispatch) presence — dev-report (PR2)

> 브랜치 `feat/dispatch-presence` · 2026-06-20. presence 4문서 롤아웃(PR #545) 후속 — 배차 상세 모달에 동시 접속자 presence additive 배선. §7 collab presence 6번째(마지막 패널-보유 문서) 완결.

## 1. 정찰 정정
- 당초 "배차는 FE collab 패널 미존재 → 새 패널 신설 필요"로 PR1에서 분리했으나, 정찰 결과 **`DispatchTaskDetailModal` 상세 모달이 이미 존재**(수정이력 + 댓글 `DispatchCommentThread` collab 배선). → **새 패널 불요, 4문서와 동일 clean additive**.

## 2. BE (slip-service — DispatchCollabCommentController, 슬립 1:1 복제)
- `DispatchCollabCommentController`(base `/admin/dispatch-tasks/{taskId}/collab`)에 presence `POST /presence/join`·`POST /presence/leave`·`GET /presence`(200) + `DispatchPresenceRequest` DTO + helper(resolvePresenceUserId/SessionId/DisplayName) + `@ExceptionHandler(MissingRequestHeaderException)`(X-User-Id 누락 401, 그 외 400).
- `PresenceService` 자동 빈(realtime-abstraction). entityId=taskId(UUID). 가드 = 댓글 VIEW `@RequirePermission(page="dispatch.board", action=VIEW)`. 엔티티 존재검증 `ensureTaskExists`(existsByIdAndIsDeletedFalse). presence:join/leave 이벤트는 기존 `/collab/stream` 채널.
- IT: `DispatchCollabCommentIT.presence_join_list_validates_...`(401/400/200-payload `{sessionId,displayName,color}`/list/403). 🪤 기존 `DispatchCollabConfigTest` 3개 생성자 직접호출 site 에 PresenceService 7번째 arg 추가(통합 컴파일 가드 적발).

## 3. FE (desktop)
- `createPresenceClient.ts`: `DispatchPresenceClient`(기존 `DispatchCollabRealtimeClient` streamPath mirror, `/admin/dispatch-tasks/${id}/collab/presence`·`/stream`).
- `DispatchTaskDetailModal.tsx`: `usePresence({entityId: task.id, client: DispatchPresenceClient, enabled})` + 수정이력 섹션 헤더에 `<PresenceIndicator/>`(client override).
- `mock.ts`: 배차 presence mock(join/leave/list) + dispatch collab stream mock(usePresence subscribe 용) — 공용 helper 재사용.

## 4. parity/안전 / 검증
- 신규 권한 page-code·시드·Flyway 0(dispatch.board 재사용). UUID 비공개(payload `{sessionId,displayName,color}`). 순수 additive.
- slip-service compileTestJava SUCCESSFUL + desktop typecheck 0.
- QA: 배차 상세 모달 2세션 presence 상호 표시(Docker 라이브) — 후속 커밋.
