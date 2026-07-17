---
name: feedback_design_system_playwright_mock_suite
description: design-system 공용 컴포넌트(AsyncAutocomplete·PartnerAutocomplete 등) 변경은 vitest·타입체크만으론 행동 회귀를 못 잡는다. desktop Playwright mock 회귀 스위트(ac-*·listbox 계열)를 반드시 로컬 실행해야 하며 CI "mock 회귀 hard gate"가 최종 권위다. 2026-07-17 #825 슬1.
metadata:
  type: feedback
---

**사건(2026-07-17 #825 슬1)**: AsyncAutocomplete 매치 하이라이트 + false-empty fix 를 넣고 **design-system vitest 61 + desktop vitest 810 + typecheck 0 + 타깃 라이브 QA(하이라이트) 전부 green** 이라 수렴 선언·머지 직전까지 갔으나, **CI "Desktop Playwright (mock 회귀 hard gate)" 잡의 ac-2/ac-3 autocomplete 테스트가 FAILURE**. 근본원인 = handleChange 가 debounce 대기 중 후보를 비우고 status='loading' 즉시 전환 → listbox 가 빈 후보로 표시 → 테스트가 loading 중 `toBeVisible` 통과 후 ArrowDown+Enter → 후보 없어 미선택 → listbox 미닫힘("listbox 표시 ⟹ 후보 존재" 불변식 파괴). **vitest·정적 적대검증(4렌즈)·타깃 QA 전부 이 행동 회귀를 못 잡았고, CI Playwright mock 스위트만 포착**.

**무엇이 잘못이었나**: design-system 공용 컴포넌트(전 소비처 blast radius)를 바꾸면서 **행동(키보드 네비·드롭다운 개폐·debounce 타이밍·선택) 회귀를 검증하는 Playwright mock 스위트를 안 돌림**. vitest 는 렌더 단위라 실 브라우저의 debounce/loading/select 타이밍 상호작용을 재현 못 한다. [[feedback_changed_module_full_test_before_push]] 의 "변경 모듈 전체 test" 를 design-system 은 **Playwright mock gate 까지** 포함해야 함.

**How to apply**:
1. **design-system 컴포넌트(특히 AsyncAutocomplete/PartnerAutocomplete/ProductAutocomplete/Select 계열·dropdown/listbox) 변경 시**: push·수렴선언 전 반드시 로컬 실행 —
   `cd clients/web/design-system && npm run build`(dist 사전빌드, desktop 이 dist 참조) →
   `cd clients/desktop && npx playwright test playwright/ac-2-product-autocomplete playwright/ac-3-partner-autocomplete`(+ 영향 listbox 스펙: bundle-set-options·journal-form-dropdown·codef-fe-bc3·groupware-approval-line-config). webServer :5173 자동기동(VITE_MOCK_MODE=1)·`playwright.config.ts`.
2. **광범위 영향 시 mock gate 전체**: `cd clients/desktop && npx playwright test`(testIgnore 로 real-qa/manual/full-qa 제외됨). CI 와 동일.
3. **vitest green = 행동 무결 아님**. 실 브라우저 상호작용(포커스·키보드·debounce·async 응답 타이밍·개폐)은 Playwright 만 잡는다.
4. 적대검증(OPUS/CODEX) 렌즈에 "design-system 변경이면 Playwright mock 스위트 실행 결과 확인" 항목 추가. 정적분석·vitest 만으로 "수렴" 선언 금지.

→ [[feedback_realqa_run_and_false_red]](고아 vite·false-RED)·[[feedback_changed_module_full_test_before_push]] 연장.
