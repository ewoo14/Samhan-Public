---
name: realqa-proxy-glob-resourcetype
description: 데스크톱 real-qa Playwright page.route 프록시는 백엔드 호출만 좁게 가로채라 — 넓은 글롭(**/accounting/**)은 앱 lazy 라우트 청크(/routes/accounting/*.tsx)까지 매칭→게이트웨이 404→앱 #root 백지. resourceType(xhr/fetch) 가드 + /collab//api/v1/ 전용 글롭.
metadata:
  type: feedback
---

2026-06-20 collab presence 4문서 롤아웃 PR #545 2세션 real-qa 캡처 회고. [[realqa-run-and-false-red]] 의 프록시 구체화 (Codex 협업 진단).

## 🪤 증상 / 근본원인
2세션 PresenceIndicator 캡처 스펙이 첫 `page.goto` 에서 ~180s hang + 스크린샷 백지(#root rootHtmlLen=0, title 만 로드, "404 Failed to load resource" 다수, pageerror 없음). 원인: `page.route('**/*')` 또는 `page.route('**/accounting/**')` 처럼 넓게 가로채면 **렌더러 자원·앱 lazy 라우트 청크**(`/routes/accounting/SupplierProfilePage.tsx` 등 — 경로에 `/accounting/`·`/slips/` 세그먼트 포함)까지 route 핸들러를 거쳐 게이트웨이로 `route.fetch`→404→앱 모듈 로드 실패→React 미마운트(백지). working reference estimate-collab-real-qa 는 `'**/api/v1/**'` 만 가로채서 무사했던 것.

## How to apply (real-qa 2세션/단일 캡처 프록시)
1. **백엔드 호출만 좁게 가로채기**: `PROXY_GLOBS = ['**/api/v1/**', '**/collab/**', '**/admin/groupware/approvals/**']`. collab/presence 는 모두 `/collab/` 세그먼트 보유(앱 모듈엔 없음) → 충돌 무. **`**/accounting/**`·`**/slips/**`·`**/partner-orders/**` 같은 도메인 글롭 금지**(앱 라우트 청크와 충돌).
2. **resourceType 가드 이중방어**: 핸들러 첫 줄 `const rt = route.request().resourceType(); if (rt !== 'xhr' && rt !== 'fetch') return route.continue()` — 모듈/문서/스타일은 절대 프록시 안 함.
3. **SSE `/collab/stream` 은 `route.abort()`** (route.fetch 하면 끝나지 않는 스트림이라 hang; FE EventSource 가 graceful 백오프).
4. **렌더러**: `cd clients/desktop && VITE_API_BASE_URL=http://localhost:8080 node_modules/.bin/vite --config vite.renderer.dev.config.ts --port 5175 --host 127.0.0.1`(별칭 보유 config 필수; bare `vite src/renderer` 는 별칭 미해결 404). mock off.
5. **`networkidle` 대기 금지** — presence SSE 재시도로 네트워크가 영원히 idle 안 됨(시간 sink). 준비완료 신호 = `page.getByTestId('presence-indicator').first().waitFor({state:'visible', timeout:60_000})` (콜드 vite dev 컴파일 수용).
6. **2세션 2칩**: PresenceIndicator 는 `displayName|color` dedup → 서로 다른 사용자 2명 필요(같은 user 2세션은 1칩). 문서별 VIEW 권한 보유 2차 사용자(dev_sales/dev_accountant/dev_manager) 선택. 후행 join 세션이 mount list 로 2명 즉시 표시(money shot), 선행 세션은 reload 로 갱신.

관련: [[realqa-run-and-false-red]] [[standalone-boot-real-qa]] [[local-stack-qa-gotchas]] [[no-fake-data-ever]] [[temp-multimodel-workflow]].
