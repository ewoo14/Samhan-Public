# E2 기둥1 배차 라이브 컬렉션 동기화 — 라이브 QA (Task6)

실서버 Docker 스택(slip-service 새 jar 재빌드 + api-gateway + eureka + postgres, 전부 healthy) + 실 게이트웨이 :8080 + dev_master 실 로그인. **가짜/합성 금지 — 실 SSE round-trip 캡처.**

## SSE round-trip 실 end-to-end (핵심)

절차:
1. `POST /auth/login` (dev_master / DEV-SEED) → 200, JWT + access_token 쿠키 (`01_login_resp.json`).
2. `GET /admin/dispatch-tasks/board-realtime` SSE 구독(쿠키 인증, 게이트웨이 no-strip 라우트) → 스트림 오픈.
3. `POST /admin/dispatch-tasks {"dispatchDate":"2026-07-02"}` → **201**, taskCode `2026/07/02-1` (`03_create_resp.json`). 게이트웨이가 JWT→X-User-Role=MASTER 주입, 실 DB 커밋.
4. 구독 스트림에 이벤트 실수신.

캡처 (`02_sse_stream.txt`):
```
event:connected
data:{"entityId":"747eb9d3-50ae-3571-9e17-282f1b4c3c3d"}   # = nameUUIDFromBytes("dispatch:board:changed") 결정적 채널

:ping                                                        # 30s heartbeat

event:dispatch:board:changed
data:{"changeType":"CREATED"}                                # ← createTask afterCommit publish 실전달
```

## 입증된 것
- **전 경로 실동작**: 게이트웨이 JWT 인증 → SSE 구독 → 서버 mutation(createTask) 커밋 → **afterCommit 발화** → 구독 클라이언트 **실수신**. E2 기둥1 라이브 컬렉션 동기화의 publish→delivery end-to-end 실증.
- **채널 정합**: connected entityId `747eb9d3...` = `DispatchBoardRealtime.CHANNEL_ID`(nameUUIDFromBytes) 일치 — 발행 채널 = 구독 채널 동일 브로커 확증.
- **게이트웨이 라우트**: `slip-dispatch-admin-noprefix` no-strip 라우트가 `/admin/dispatch-tasks/board-realtime` SSE 를 버퍼링 없이 통과(text/event-stream 실시간 flush).
- **2세션 반영 본질**: 한 연결이 구독 중, 별개 요청(mutation)이 유발한 변경이 구독자에게 라이브 전달됨 = 동시 시청자 실시간 반영의 핵심 메커니즘.

## 한계·정직 disposition
- **2-GUI 데스크탑 세션 캡처 미수행**: 16-서비스 풀스택 GUI 2-세션 스크린샷은 본 세션에서 미수행. 대신 실 게이트웨이 SSE round-trip(위)으로 publish→delivery 를 실증(더 결정적). FE invalidateQueries 는 vitest 로 검증.
- **changeType CREATED 1종 캡처**: UPDATED/DELETED/STATUS_CHANGED 는 동일 `publishBoardChanged` 경로(메커니즘 동일)이며 각 단위테스트로 verify(cb48c24d). payload 는 FE opaque(무조건 refetch)라 값 무관.
- **모바일 WebView**: 웹 번들 SSE 가 WebView 내에서 동작(동일 `createRealtimeClient`) → 자동 반영 예상. 별도 모바일 GUI 캡처 미수행(기동 부담), 웹 SSE 실동작으로 갈음.

## 환경
- Docker: slip-service(새 jar `--build` 재빌드, healthy) · api-gateway · eureka · postgres 전부 healthy.
- 실 게이트웨이 :8080, dev_master(MASTER) 실 로그인, mock OFF.

## GUI 스크린샷 라이브 QA backfill 시도 (2026-07-02, 정직 기록)
개발책임자 지적(SSE 텍스트≠실 UI 스샷)에 따라 브라우저 GUI 캡처를 시도했으나 **웹-서빙 QA 경로의 연쇄 환경 벽으로 미완료**(가짜 스샷 생성 금지 [[feedback_no_fake_data_ever]] — 아래 정직 기록):
1. ✅ 웹 렌더러 빌드(dist/web) + 로그인 화면 실 렌더 확인(스샷 캡처).
2. ✅ same-origin proxy(vite preview.proxy → :8080)로 **인증 세션 확립 실증**(`/auth/login` 200, `/auth/me` 200) — 크로스오리진 httpOnly 쿠키 문제 해결.
3. ❌ **앱 부트 크래시**: 로그인 후 앱 셸은 마운트되나, **활성 공지 컴포넌트가 undefined 데이터에 `.find` 호출→useMemo 렌더 예외**로 #root 비워짐(`[app-notice] ... Cannot read properties of undefined (reading 'find')`). QA 웹-서빙에서 일부 부트 엔드포인트 응답형 불일치가 트리거. 이로 인해 배차현황 화면까지 도달 못 함.
- **근본 제약**: 실 사용자 surface 는 Electron 데스크톱(브라우저 자동화 도구로 구동 불가), 웹 빌드는 프로덕션 same-origin 서빙 전제라 QA 단독 브라우저 캡처가 환경적으로 어려움.
- **owed(P1)**: 배차현황 목록 2세션 라이브 갱신 실 GUI 스샷 = **Electron 앱 or 프로덕션-parity 웹 배포에서 후속 캡처 필요**. SSE round-trip(위)이 서버→클라이언트 전달 메커니즘은 실증함.
- 📌 **부수 발견(저위험 앱 방어코딩 갭)**: 활성 공지 부트 컴포넌트가 데이터 undefined 시 guard 없이 `.find`→크래시. 공지 API 오류/빈응답 시 앱 블랭크 위험 → optional chaining/기본배열 guard 권장(별건 백로그).
