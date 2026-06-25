---
name: responsive-drawer-offscreen-a11y
description: 반응형 Drawer 닫힘을 transform-only(translateX(-100%))로 하면 오프스크린 nav가 Tab순서·스크린리더에 잔존(a11y 회귀). visibility:hidden+transition delay로 제거+슬라이드 보존. aria-modal은 focus trap/inert 동반 (2026-06-25 PR #597 모바일 슬2)
metadata:
  type: feedback
---

# 반응형 Drawer 오프스크린 focusable a11y (2026-06-25 PR #597)

모바일 슬2(반응형 셸 Drawer)에서 닫힌 Drawer(`.app-sidebar`)를 `@media(max-width:768px)`에서 `position:fixed; transform:translateX(-100%)`로만 화면 밖에 두었다(슬1은 `display:none`이었음).

**문제 (⑤ Codex 독립라운드 단독 적발, Opus 5차원 미적발):** `transform`은 요소를 시각적으로만 밀어낼 뿐 DOM에 렌더된 상태라, 닫힌 Drawer 내부 nav 링크(`a[href]`)/버튼이 **Tab 순서·스크린리더 탐색에 잔존**한다. 키보드 사용자가 보이지 않는 오프스크린 메뉴로 Tab 진입하고, AT는 가려진 메뉴를 announce한다. `display:none`(슬1)은 탭순서·AT에서 완전 제거했는데, 슬라이드 애니를 위해 transform으로 바꾸며 도입된 a11y 회귀.

**Why:** `transform`/`opacity`/`visibility:visible`로 숨긴 요소는 접근성 트리·포커스 순서에 남는다. 진짜 숨김은 `display:none`(애니 불가) 또는 `visibility:hidden`/`inert`/`aria-hidden`이다.

**How to apply:**
- 슬라이드 Drawer 닫힘 상태에 `visibility:hidden` 부여(탭순서·AT 제거) + `.is-open`에 `visibility:visible`. 슬라이드아웃 애니 보존을 위해 transition에 visibility delay: `transition: transform .25s ease, visibility 0s linear .25s`(닫힘=transform 슬라이드 후 visibility 숨김), `.is-open`은 `transition: transform .25s ease, visibility 0s`(열림 즉시 visible).
- **`aria-modal="true"`/`role="dialog"`는 focus trap(Tab/Shift+Tab wrap-around) 또는 배경 `inert`/`aria-hidden` 동반 필수** — 둘 없이 aria-modal만 선언하면 "배경 inert" 계약을 어기는 ARIA 안티패턴(④ Opus 적발). dialog는 accessible name(`aria-labelledby`→제목 id) 동반.
- Playwright mock spec으로 **닫힘 시 Drawer `not.toBeVisible()`** 회귀 가드(visibility:hidden이면 Playwright 비가시 판정). transform-only면 false-green(여전히 visible/focusable).
- 데스크탑(>768px) 정적 사이드바는 visibility 무관 — 신규 visibility는 `@media(max-width:768px)` 안에만.
- 모바일/데스크탑 크롬 에뮬레이션 라이브 QA는 노치/홈바 inset=0이라 safe-area/오프스크린 a11y 갭을 시각 재현 못함 → 코드리뷰(듀얼모델)+키보드/AT 검증 분담.

관련: [[feedback_platform_branch_build_time_flag]](신규 셸 mock gate 필수), [[feedback_qa_docker_real_test]](리뷰마다 라이브QA).
