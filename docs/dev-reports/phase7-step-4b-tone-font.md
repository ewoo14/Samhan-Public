# Phase 7 4차 잔여 — 통일 톤 + 폰트 (dev report)

PR #84 (dark-mode 정식 도입) 머지 후 ROADMAP 4차의 **잔여** 항목 1 PR 통합.

ROADMAP `Phase 7 진행 중 / 다음 단계` § "4차 — UI 통합 디자인 (estimate-app v2 / order-app v4 / desktop / mobile / mobile-staff 통일된 톤 / 다크모드 / 폰트)" 의 통일된 톤 + 폰트 부분.

## 1. 작업 범위

| # | 영역 | 산출 | 분포 |
|---|---|---|---|
| 1 | DS — 통일 톤/폰트 alias 토큰 | font-family-base / size 6 / weight 4 / line-height 3 / spacing 8 / radius 5 / shadow 3 (light + dark) + html-level base | `clients/web/design-system/src/tokens/tokens.css` |
| 2 | FE — order-app v4 Pretendard CDN | head 안 jsdelivr `<link>` 추가 (legacy `<style>` 이전 위치) | `clients/web/order-app/index.html` |
| 3 | FE — estimate-app v2 Pretendard CDN | head 안 jsdelivr `<link>` 추가 (legacy `<style>` 이전 위치) | `clients/web/estimate-app/views/index.ejs` |
| 4 | RN — mobile graceful 폰트 hook | `usePretendardFontGuarded()` + App.tsx 통합 | `clients/mobile/{App.tsx, src/theme/usePretendardFontGuarded.ts}` |
| 5 | RN — mobile-staff graceful 폰트 hook | `usePretendardFontGuarded()` + App.tsx 통합 | `clients/mobile-staff/{App.tsx, src/theme/usePretendardFontGuarded.ts}` |
| 6 | dev-reports | 본 파일 신규 | `docs/dev-reports/phase7-step-4b-tone-font.md` |

총 산출: DS 1 + FE 2 + RN 4 + dev-reports 1 = **8 file 변경/추가**.

## 2. DS — 통일 톤/폰트 alias 토큰

기존 토큰 (1차~3차에서 추가된 `--font-family-sans` / `--space-N` / `--radius-{sm..xl,full}` / `--shadow-{sm,md,lg,modal}`) 은 **그대로 보존**. 본 슬라이스는 5 client wrapper / shim UI 가 직접 참조하는 일관된 alias 추가.

### 2.1 폰트 family

```css
--font-family-base: 'Pretendard Variable', 'Pretendard', -apple-system, BlinkMacSystemFont,
                    'Segoe UI', Roboto, 'Helvetica Neue', Arial, 'Noto Sans KR', sans-serif;
--font-family-mono-base: 'D2Coding', ui-monospace, 'SFMono-Regular', 'Cascadia Mono',
                          Menlo, Consolas, 'Liberation Mono', monospace;
```

### 2.2 폰트 size 6 단계

`--font-size-alias-{xs:12, sm:14, base:16, lg:18, xl:22, 2xl:28}px`

### 2.3 폰트 weight 4 단계

`--font-weight-{normal:400, medium-alias:500, semibold-alias:600, bold-alias:700}`

### 2.4 line-height 3 단계

`--line-height-{tight-alias:1.25, base:1.5, relaxed-alias:1.75}`

### 2.5 spacing 8 단계 (4px scale)

`--spacing-{1:4, 2:8, 3:12, 4:16, 5:20, 6:24, 8:32, 10:40}px`

### 2.6 radius 5 단계

`--radius-alias-{sm:4, md:8, lg:12, xl:16, full:9999}px`

### 2.7 shadow 3 단계 (light + dark)

light:
- sm: `0 1px 2px 0 rgba(0,0,0,0.05)`
- md: `0 4px 6px -1px rgba(0,0,0,0.10), 0 2px 4px -1px rgba(0,0,0,0.06)`
- lg: `0 10px 15px -3px rgba(0,0,0,0.10), 0 4px 6px -2px rgba(0,0,0,0.05)`

dark (alpha ~4-5x 보강):
- sm: `0 1px 2px 0 rgba(0,0,0,0.40)`
- md: `0 4px 6px -1px rgba(0,0,0,0.40), 0 2px 4px -1px rgba(0,0,0,0.30)`
- lg: `0 10px 15px -3px rgba(0,0,0,0.50), 0 4px 6px -2px rgba(0,0,0,0.30)`

### 2.8 html-level base font 적용

```css
html {
  font-family: var(--font-family-base);
  font-size: var(--font-size-alias-base);
  line-height: var(--line-height-base);
}
```

= cascade 최하위 위치. legacy 임베드 가 자체 `body { font-family: ... }` 명시 시 (order-app 의 `system-ui,sans-serif` / estimate-app 의 자체 stack) selector specificity 동일 + cascade 후순위로 legacy 가 우선. 신규 wrapper / shim UI (body font 미명시) 는 본 declaration 적용.

## 3. FE — Pretendard web font CDN

### 3.1 적용 위치

order-app `index.html` 의 head 와 estimate-app `views/index.ejs` 의 head, 둘 다 `<style>` 블록 **이전** 위치 (cascade 후순위 = legacy CSS 가 우선 보존).

```html
<link rel="preconnect" href="https://cdn.jsdelivr.net" crossorigin>
<link rel="stylesheet" href="https://cdn.jsdelivr.net/gh/orioncactus/pretendard@v1.3.9/dist/web/static/pretendard.min.css">
```

### 3.2 효과

1. `Pretendard` font-face 가 OS-level 등록 — `var(--font-family-base)` / 인쇄 양식 (`global.css` 의 `.invoice-page` / `.dispatch-page` 안 `'Pretendard'` 명시) 가 fallback 없이 즉시 사용.
2. legacy 의 body font-family (system-ui) 는 그대로 — 한국어 fallback (Malgun Gothic / Apple SD Gothic Neo / Noto Sans KR) 변형 X.
3. preconnect 로 CDN 핸드셰이크 미리 — FCP/LCP 최소 영향.

## 4. RN — graceful 폰트 hook

### 4.1 architecture context

mobile (v4) + mobile-staff (v3) 모두 100% WebView wrapper:
- `App.tsx` = `SafeAreaProvider + StatusBar + <screen>`
- `<screen>` = WebView 단일 자식 (`MobileOrderWebViewScreen` / `EstimateWebViewScreen`)
- RN native `Text`/`View` 가 사실상 0개 → native font 등록 자체가 무영향

따라서 본 슬라이스의 통일 폰트는 **WebView 안 web font** (§ 3) 로 일원화.

### 4.2 graceful guard hook

향후 RN native UI (오류 화면 / 로딩 인디케이터 텍스트 등) 추가 시 5 client 통일 톤 (Pretendard) 을 유지하기 위한 **진입점**으로 본 hook 추가.

```typescript
// clients/mobile/src/theme/usePretendardFontGuarded.ts
export function usePretendardFontGuarded(): boolean {
  // 현재 (Phase 7 4차 잔여) — RN native UI 부재로 즉시 ready.
  // 후속 슬라이스에서 expo-font 추가 시 useFonts hook 도입.
  return true;
}
```

`App.tsx` 에서:

```typescript
const fontsReady = usePretendardFontGuarded();
if (!fontsReady) {
  return <SafeAreaProvider><StatusBar style="dark" /></SafeAreaProvider>;
}
return <SafeAreaProvider><StatusBar style="dark" /><Screen /></SafeAreaProvider>;
```

### 4.3 graceful 가드 정책

- `expo-font` 패키지 추가 강요 X
- `assets/fonts/Pretendard-*.otf` 추가 강요 X
- Hooks Rules 안전 (조건부 hook 호출 X)
- bundler 정적 require 추적 fail 위험 X

후속 슬라이스에서 native screen 신규 추가 시 본 hook 본문에 `useFonts` 로 교체.

## 5. desktop — 기존 토큰 chain 유지

desktop (`clients/desktop/src/renderer/index.html`) 은 strict CSP (`style-src 'self' 'unsafe-inline'`) 로 CDN `<link>` 차단. 그러나 `clients/desktop/src/renderer/styles/global.css` 가 이미 `@samhan/design-system/tokens.css` import 하며 `body { font-family: var(--font-family-sans) }` 적용 중. 토큰의 `--font-family-sans` 는 1차부터 `'Pretendard Variable', Pretendard, ..., 'Noto Sans KR', sans-serif` chain — Electron 호스트 OS 에 Pretendard 설치 시 사용, 미설치 시 fallback. 본 슬라이스에서 desktop 변경 X.

## 6. legacy 보존 검증

| 검증 | 결과 |
|---|---|
| `clients/web/order-app/index.html` 의 9460 라인 inline CSS / DOM | 변형 X (head 의 `<link>` 2 라인 추가만) |
| `clients/web/estimate-app/views/index.ejs` 의 18699 라인 inline CSS / DOM | 변형 X (head 의 `<link>` 2 라인 추가만) |
| legacy `body { font-family: system-ui,sans-serif }` (order-app line 35) | 변형 X (cascade 후순위 우선) |
| 한국어 fallback chain (Malgun Gothic / Apple SD Gothic Neo / Noto Sans KR) | 변형 X (system-ui 가 OS 별 한국어 default 폰트로 자동 매핑) |
| 인쇄 양식 (invoice-page / dispatch-page) 안 `'Pretendard'` 명시 | Pretendard 등록 후 즉시 사용 (fallback 의존 제거) |
| 다크 모드 토큰 (4차 PR #84) | 변형 X (alias 토큰 신규 = 기존 토큰과 독립) |
| desktop 의 `var(--font-family-sans)` 참조 | 변형 X (alias 와 별개) |

## 7. 5 client 적용 결과

| client | 적용 방식 | 폰트 source |
|---|---|---|
| `clients/web/order-app` (v4) | head `<link>` to jsdelivr CDN | Pretendard via web font (legacy body 는 system-ui 우선) |
| `clients/web/estimate-app` (v2) | head `<link>` to jsdelivr CDN | Pretendard via web font (legacy body 는 system-ui 우선) |
| `clients/desktop` (Electron) | 기존 design-system tokens.css import | `var(--font-family-sans)` chain (Pretendard 우선) |
| `clients/mobile` (RN v4) | graceful guard hook (현재 no-op) | WebView 안 web font (estimate/order CDN) |
| `clients/mobile-staff` (RN v3) | graceful guard hook (현재 no-op) | WebView 안 web font (estimate CDN) |

## 8. 통일 토큰 분포 요약

| 카테고리 | 신규 alias 수 | 기존 보존 |
|---|---|---|
| 폰트 family | 2 | `--font-family-sans` / `--font-family-mono` 보존 |
| 폰트 size | 6 | `--font-size-{xs..4xl}` 9 종 보존 |
| 폰트 weight | 4 | `--font-weight-{regular..bold}` 4 종 보존 |
| line-height | 3 | `--line-height-{tight,normal,relaxed}` 3 종 보존 |
| spacing | 8 | `--space-{0..20}` 13 종 보존 |
| radius | 5 | `--radius-{none..full}` 6 종 보존 |
| shadow | 3 (× 2 = light + dark) | `--shadow-{sm,md,lg,modal}` 4 종 보존 |
| **합계** | **31 신규 + 6 dark override = 37 declaration** | 기존 토큰 100% 보존 |
