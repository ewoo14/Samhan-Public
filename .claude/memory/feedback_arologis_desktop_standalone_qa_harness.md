---
name: feedback_arologis_desktop_standalone_qa_harness
description: arologis-desktop 브라우저 standalone 실서버 QA 하네스 구동법(프록시 rewrite·인증·playwright 위치)
metadata:
  type: feedback
---

arologis-desktop(Electron)을 **브라우저 단독 렌더러**로 띄워 실서버 GUI QA 하는 법 (#784 strict QA서 구축·2026-07-11).

**Why:** arologis-desktop은 desktop과 달리 renderer 전용 vite config·playwright 디렉터리가 없어 real-qa가 비자명. 프로덕션 리버스프록시(`arologis.samhan-air.com`)가 `/api/arologis/**`를 rewrite하는 데 의존해 로컬 standalone에선 그냥 안 뜬다.

**How to apply:**
- 렌더러 config = `clients/arologis-desktop/vite.renderer.dev.config.ts`(electron.vite renderer 블록 재사용). **프록시 필수**: `^/api/arologis/.*`(정규식+슬래시 — `/api/arologis.ts` 소스모듈까지 가로채지 않게) → target `:8097`, rewrite `/api/arologis`→`/admin/arologis`. `^/auth/.*`도 `:8097`.
- 구동: `VITE_AROLOGIS_API_BASE='' node_modules/.bin/vite dev --config vite.renderer.dev.config.ts --port 5291 --strictPort` (빈 base라야 apiClient가 상대경로→프록시 경유). 실 arologis-service = `:8097`(별개 게이트웨이 아님).
- **@playwright/test는 arologis-desktop에 미설치** → 스펙을 `clients/desktop/playwright/arologis-warning-aa-real-qa/`에 두고 **desktop의 playwright 바이너리**로 구동(SHOTS 4-up 경로 동일).
- 인증: `POST :8097/auth/admin/login {loginId:"admin",password:"admin1234"}`(AROLOGIS_MASTER 시드) → `page.addInitScript`로 `window.arologisAuth.getToken()`=AuthSnapshot(accessToken/refreshToken/userId=JWT sub/role/loginId/fullName/expiresAt) 스텁.
- ⚠️ **DispatchDetailPage는 현재 브라우저 렌더서 크래시**(`NotifyResultSection` undefined.length·**#785**) — 배차 상세 QA는 #785 fix 후 가능. 실 엔드포인트=`GET /admin/arologis/dispatches/{id}`(200 실데이터).

관련: [[feedback_realqa_run_and_false_red]] · [[feedback_css_var_token_not_fallback]]
