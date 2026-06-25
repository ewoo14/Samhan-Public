---
name: platform-branch-build-time-flag
description: 플랫폼 분기(라우터 등)는 런타임 감지 대신 빌드타임 플래그로. mock/dev 렌더러는 브라우저지만 Electron 거동 emulate. 신규 라우팅/플랫폼 분기는 mock gate 필수 검증 (2026-06-25 PR #596 모바일 슬1)
metadata:
  type: feedback
---

# 플랫폼 분기는 빌드타임 플래그 + mock gate 검증 (2026-06-25 PR #596)

모바일 슬1(데스크탑→웹 Dual-mode)에서 `routes/index.tsx` 가 라우터를 **런타임** `isElectronPlatform`(`typeof window.samhanAuth?.getToken === 'function'`)으로 선택(`isElectronPlatform ? createHashRouter : createBrowserRouter`)했다.

**문제:** Playwright mock gate 의 dev server(`npx vite src/renderer` + `VITE_MOCK_MODE=1`)는 **브라우저**라 `window.samhanAuth` 가 없어 `isElectronPlatform=false` → BrowserRouter 로 오전환. 그런데 27개 mock spec 이 **해시 URL**(`http://127.0.0.1:5173/#/...`)로 navigate → BrowserRouter 가 해시 무시 → 홈으로 빠짐 → 대상 element 미존재 → 실패+retry 누적 → **30분 job 타임아웃**(`cancelled`). slice 1 Task4 라우터 분기 회귀로, PR 첫 커밋은 통과하고 이후 커밋부터 timeout.

**Why:** mock/dev 렌더러는 물리적으로 브라우저지만 **Electron 렌더러를 emulate**하는 환경(해시 라우팅·mock 인증). 런타임 감지는 이 환경을 "웹"으로 오분류한다. 빌드타임 플래그는 의도된 배포 대상만 정확히 식별한다.

**How to apply:**
- **라우터/배포 분기는 빌드타임 플래그**로: `import.meta.env.VITE_PLATFORM === 'web'`(웹 배포 빌드 `vite.web.config.ts` 만 주입) → BrowserRouter. Electron 빌드 + mock/dev 렌더러(미설정=undefined) → HashRouter.
- **인증 provider 선택은 런타임 감지 유지**가 맞다(`isElectronPlatform`: Electron=IPC / web·mock=webProvider, mock 은 bootstrap bypass). 즉 "이 코드가 도는 환경의 능력"(IPC 존재)은 런타임, "어느 대상으로 배포됐나"(웹/Electron)는 빌드타임으로 구분.
- **신규 라우팅/플랫폼 분기 도입 시 mock gate(Desktop Playwright) 필수 검증.** unit/typecheck/build:web 은 이 회귀를 못 잡는다(해시 navigate 는 실 브라우저 라우팅 런타임). 로컬 재현: 실패 spec 1개(`npx playwright test <spec> -g <test> --workers=1`)로 빠르게 확증.
- **mock gate 30분 타임아웃 = 실패+retry 누적 신호**(assertion 실패 아님). 로그에서 `[N/총]` 진행 + `The operation was canceled` 확인, 어느 spec 에서 retry 도는지 추적.

관련: [[feedback_qa_docker_real_test]](리뷰마다 라이브QA), [[feedback_ci_test_filter_false_green]], [[feedback_changed_module_full_test_before_push]].
