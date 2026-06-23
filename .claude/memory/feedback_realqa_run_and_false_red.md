---
name: realqa-run-and-false-red
description: 데스크톱 real-qa Playwright 실행법(렌더러 vite mock off + VITE_API_BASE_URL + AUDIT_BASE_URL) + 스펙 false-RED 함정(행 첫 토큰=드래그 핸들 글리프 ⠿ 추출, 스펙 실패도 스펙 버그)
metadata:
  type: feedback
---

2026-06-17 PR #495(에픽 #18 슬2) 회고.

## 데스크톱 real-qa 실행법 (실 게이트웨이 실서버 QA — mock 아님)
real-qa config(`clients/desktop/playwright.real-qa.config.ts`)엔 **webServer 없음** → 렌더러 dev 를 **수동 기동** 후 실행.
1. 렌더러(web, mock OFF): `cd clients/desktop && VITE_API_BASE_URL=http://localhost:8080 npx vite src/renderer --host 127.0.0.1 --port 5175` (백그라운드). **VITE_API_BASE_URL 필수** — 없으면 axios baseURL 미설정으로 API 미도달. VITE_MOCK_MODE 미설정=mock off.
2. 실행: `cd clients/desktop && AUDIT_BASE_URL=http://127.0.0.1:5175 node_modules/.bin/playwright test --config=playwright.real-qa.config.ts <spec> --reporter=line --timeout=90000` ([[playwright-local-version-skew]] — node_modules/.bin 직접).
3. 로그인: spec 이 `POST :8080/auth/login {dev_master, dev_p05_pass!}` → `window.samhanAuth` stub `addInitScript` 주입(client.ts interceptor 가 토큰 사용). 스크린샷 `docs/qa/<slug>/*.png`.

## 🪤 real-qa 스펙 false-RED 함정
구성품/행 코드 추출 시 `row.innerText().trim().split(/\s+/)[0]` 는 **행 선두의 design-system DragHandle 글리프 `⠿`** 를 잡음(모델코드 아님) → 모든 행 코드가 `⠿` 동일 → 이동/순서 단언이 **항상 실패**(기능 정상인데 스펙이 false-RED). → 모델코드 span 직접 추출.
- 헤드리스 dnd-kit **키보드 드래그(Space/Arrow)는 flaky** → `boundingBox()` 기반 **마우스 드래그**(down→소폭 이동 activation→target→up)가 신뢰성 높음.
- **스펙 실패도 스펙 버그일 수 있다** — 실 DOM(`test-results/.../error-context.md`)·스크린샷으로 기능 동작을 교차 확인한 뒤 판단(false-RED 를 기능 결함으로 오인 금지). [[no-fake-data-ever]] 실 캡처 원칙과 양립.

## 가드 모수 대칭 (D-PCE-09)
서버측 "전체 포함 강제" 가드의 모수는 **FE 가 실제 전송하는 모수와 집합이 동일**해야 함 — BE 가 전체(usageScope 무관), FE 가 부분(usageScope≠NONE)만 보내면 정상 요청도 거부(영구 400). 가드 쿼리에 FE 와 동일 필터(`usageScope IN (ESTIMATE/PARTNER_ORDER/BOTH)`) 적용. Opus BE 리뷰 단독 적발.

## Git Bash 도구 함정
`jq` 미설치(Git Bash) → 토큰은 `grep -oE '"token":"[^"]+"'` 추출. 한글 model_code(자재 운임/절삭/발통세트)는 Git Bash 파이프가 UTF-8 멀티바이트를 깨뜨림(0xb9) → 서버 500(JSON parse). docker exec 출력은 **`docker cp` + `curl --data-binary @file`** 로 바이트 보존. **curl URL 쿼리의 raw 한글도 깨짐** → `q=서울` 대신 **URL-인코딩**(`q=%EC%84%9C%EC%9A%B8`)으로 보낼 것.

## 🪤 거짓 "플랫폼 갭/라우트 끊김" 오진 (2026-06-23 슬F #576)
거래처 검색이 "404/500 라우트 끊김 = 플랫폼 사전 갭"이라 오진 → 하마터면 **불필요한 partner-service/gateway 변경**을 할 뻔. 실제론 **내 QA 도구 오류 2개**가 만든 거짓 신호:
1. **curl에 `/api` 접두를 임의로 붙임**(`/api/admin/partners/search`=404). FE apiClient `baseURL=http://localhost:8080`(게이트웨이, **/api 없음**)이고 FE 경로는 `/admin/partners/search`(200). → **FE가 실제 호출하는 정확한 URL**(baseURL+path)을 client.ts에서 확인하고 그대로 재현할 것. 경로에 /api 등 임의 접두 금지.
2. **raw 한글 쿼리**(`q=서울`)가 Git Bash에서 깨져 0건(`¼­¿ï` 모지바케) → "이름 검색 안 됨"으로 오인. URL-인코딩하니 정상(서울→2건, 에어→15건).
**교훈**: "라우트/플랫폼이 끊겼다" 결론 전에 — (a) FE 실호출 URL 정확 재현(접두 임의 추가 X), (b) 한글 쿼리 URL-인코딩, (c) `q` 없는 호출로 200·데이터 유무 먼저 확인. 플랫폼 변경 escalation 전 자기 QA 도구부터 의심. (Opus 리뷰의 진짜 BLOCKING=거래처 UUID 비공개 의존은 별개로 유효 → partnerCode 재설계로 해소.)

관련: [[temp-multimodel-workflow]] [[qa-docker-real-test]] [[real-server-check-screenshot]] [[local-stack-qa-gotchas]] [[uuid-no-user-visibility]].
