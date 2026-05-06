# Phase 7 4차 작업 — dev report

PR #83 (Phase 7 3차) 머지 후 후속 항목을 1 PR 통합 산출.

## 1. 작업 범위

| # | 영역 | 산출 | 분포 |
|---|---|---|---|
| 1 | Designer — dark-mode 정식 도입 | DS 토큰 10 신규 + body 바인딩 + transition + toggle helper | `clients/web/design-system/src/tokens/tokens.css` |
| 2 | FE — order-app body[data-theme] | head 동기 preload + body init script + `window.toggleTheme` | `clients/web/order-app/index.html` |
| 3 | FE — estimate-app body[data-theme] | head 동기 preload + body init script + `window.toggleTheme` | `clients/web/estimate-app/views/index.ejs` |
| 4 | QA Visual — dark-mode baseline 6 활성 | skip 가드 제거 + 동일 페이지 attribute 토글 방식 | `qa/playwright/tests/visual/dark-mode-toggle.visual.spec.ts` |
| 5 | QA Edge — api-5xx 가드 강도 통일 | 502/503 가드 length + UUID 풀형식 일관 | `qa/playwright/tests/edge/api-5xx-fallback.spec.ts` |

총 산출: DS 1 file + FE 2 file + QA 2 file = **5 file 변경/추가** + dev-reports 1 신규.

## 2. Designer — dark-mode 정식 도입

### 2.1 DS 토큰 10 신규

3차까지의 dark theme stub 은 brand/neutral 팔레트 swap 만 제공. 4차에서 **bg/surface/text/border 정식 토큰 10종** 신규.

`:root` (light 기본):
- `--color-bg-primary` `#ffffff` / `--color-bg-secondary` `#f5f5f5` / `--color-bg-tertiary` `#ebebeb`
- `--color-surface-primary` `#ffffff` / `--color-surface-secondary` `#f9f9f9`
- `--color-text-primary` `#111827` / `--color-text-secondary` `#4b5563` / `--color-text-tertiary` `#6b7280`
- `--color-border-primary` `#e5e7eb` / `--color-border-secondary` `#d1d5db`

`[data-theme="dark"]` (override):
- bg: `#1a1a1a` / `#242424` / `#2d2d2d`
- surface: `#2d2d2d` / `#383838`
- text: `#f5f5f5` / `#c0c0c0` / `#888888`
- border: `#404040` / `#303030`

### 2.2 body 바인딩 + transition

```css
body {
  background-color: var(--color-bg-primary);
  color: var(--color-text-primary);
  transition: background-color 0.2s ease, color 0.2s ease;
}
```

토큰 변수 단일 swap 으로 즉시 dark 전환. transition 200ms 로 attribute toggle 시 부드러운 색상 보간.

### 2.3 stub 보강 호환

기존 `[data-theme="dark"]` stub 의 `--color-brand-*` `--color-neutral-*` `--color-bg/--color-bg-subtle/--color-bg-muted/--color-border/--color-text/--color-text-muted/--color-text-subtle` 13 변수는 그대로 유지. 신규 10 토큰을 추가 append 하여 기존 화면 시각적 회귀 0.

## 3. FE — body[data-theme] 바인딩

### 3.1 order-app/index.html

`<head>` 에 동기 preload script — body 렌더 전 localStorage 또는 prefers-color-scheme 우선순위로 `data-theme-preload` 결정 → FOUC 방지.

`<body class="no-active" data-theme="light">` (초기값) → 직후 inline script 가 preload 값을 body 의 `data-theme` 으로 반영. `window.toggleTheme()` helper 등록.

```js
window.toggleTheme = function() {
  var next = document.body.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
  document.body.setAttribute('data-theme', next);
  try { localStorage.setItem('samhan.theme', next); } catch (e) {}
};
```

### 3.2 estimate-app/views/index.ejs

동일 패턴 적용. legacy DOM 변형은 `<body>` 의 `data-theme="light"` attribute 추가 + 직후 inline script 1 블록 만 — 기존 1.28만 lines 의 view template 구조 변경 0.

### 3.3 dark-mode toggle UI 채택 보류

본 PR 은 token + 바인딩 + helper 까지. 별도 hamburger menu / floating button UI 추가는 legacy 디자인 보존을 위해 **`window.toggleTheme()` 호출 가능 helper** 만 노출하고 UI 진입점 추가는 후속 슬라이스로 분리. 현재 prefers-color-scheme 자동 감지로 사용자 OS 설정 즉시 반영.

## 4. QA Visual — dark-mode baseline 6 활성

### 4.1 skip 가드 제거

3차에서 추가한 `body[data-theme] 미구현 — order-app/estimate-app dark-mode 도입 후 활성화` 가드를 4차 본 PR 에서 정식 도입 완료 → 가드 제거.

### 4.2 동일 페이지 attribute toggle 방식

이전: `page.emulateMedia({ colorScheme: 'dark' })` + `page.reload()` — reload 비용 + media query 감지 의존.

정정: `page.evaluate(() => document.body.setAttribute('data-theme', 'dark'))` — DOM attribute 만 토글 → CSS variable 즉시 swap. snapshot 비교가 token-only 차이로 명확.

### 4.3 baseline 6 자동 생성

`playwright.config.ts` 의 mobile-chrome / mobile-safari / electron-desktop 3 project × light/dark 2 = 6 baseline. `npx playwright test --update-snapshots` 으로 자동 생성 후 commit. baseline 파일 자체는 backend 가용 환경에서 생성 후 commit 필요 (CI 환경 backend 미가용 시 `test.skip`).

## 5. QA Edge — api-5xx 가드 강도 통일

### 5.1 502 가드 강화

이전 (3차):
- `bodyText.length > 0` → 빈 문자열만 차단, 단문 OK 허용
- `[0-9a-f]{8}-[0-9a-f]{4}` → UUID 부분 매칭, false-positive 가능 (16진수 ID 일반)

정정 (4차):
- `bodyText502.length > 10` — 503 동일. 의미 있는 안내 문구 검증
- `[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}` — UUID 풀형식. 부분 매칭으로 인한 false-positive 0
- 스택트레이스 `at\s+\w+\.\w+:\d+` 패턴 동일 유지

### 5.2 503 가드 통일

503 케이스의 UUID 패턴도 풀형식으로 정정 (이전 `{8}-{4}-{4}` → `{8}-{4}-{4}-{4}-{12}`). 502/503 두 케이스 모두 동일한 정규식 사용 → 가드 일관성.

## 6. 후속 작업

- baseline 6 PNG 생성 — backend 가용 staging 환경에서 `npx playwright test visual/dark-mode-toggle --update-snapshots` 후 commit
- dark-mode toggle UI 진입점 추가 — hamburger menu / settings drawer (별도 슬라이스, legacy 디자인 검토 필요)
- Phase 8 진입 — 14 backend MSA 별도 호스팅 결정 (Render / Fly.io / 자체 운영 비교)
- staging/dev 환경 IaC (terraform 또는 render Blueprint v2)
- k6 부하 테스트 + OWASP ZAP 정기 스캔 자동화
