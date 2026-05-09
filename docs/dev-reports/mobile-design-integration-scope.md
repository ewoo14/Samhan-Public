# Mobile v4 디자인 통합 fix — Scope 정의서

> 작성일: 2026-05-05
> 브랜치: `chore/mobile-v4-design-integration-legacy-match` (base = `origin/main` `ccb7f42`)
> 회고 PR: PR #66 (textColor 4행 hotfix → close 됨)
> 회고 사용자 피드백: "PR66의 경우 전체적으로 디자인이 모두 다름" / "PR은 한 번에 통합해서 QA 확인 후 TM 승인하게 업로드 요청"

---

## §1. 배경

PR #66 은 `HomeScreen.tsx` 의 4 카테고리 textColor 만 한 줄(line) 단위로 패치했으나,
사용자 (개발책임자) 가 "전체적으로 디자인이 모두 다름" 으로 reject. 단일 hotfix 패턴이
회고 패턴 위반 (`feedback_integrated_pr_pattern.md`).

본 통합 fix 는 **legacy `migration/source/scripts/partner-order/index.html` 의 모바일 viewport
(`@media (max-width: 1280px)` + `body.mobile-mode .top { display:none !important }`) 1:1 일치**
를 단일 PR 로 적용.

---

## §2. legacy 출처 (1:1 매핑 대상)

| legacy line | CSS / HTML | RN 적용 위치 |
|---|---|---|
| line 11 | `--c-strong:#111827` | `legacyVars.cStrong` — selectBigText.color |
| line 119 | `.mobile-gate { display:flex; flex-direction:column; gap:16px; margin:20px 0 12px }` | `legacyMobileGateStyles.mobileGate` |
| line 121 | `.select-big { width:100%; height:150px; border:1px solid #000; border-radius:18px; font-weight:800; font-size:36px }` | `legacyMobileGateStyles.selectBig` |
| line 122 | `.select-home {background:#eef2ff;border-color:#c7d2fe} ...` | `legacyMobileGateStyles.selectHome/Single/Comm/Old` |
| line 195 | `body.mobile-mode .top { display:none !important }` | titleBar 삭제 |
| line 685~689 | `<div class="mobile-gate"><button class="select-big select-home">홈멀티</button>...</div>` | HomeScreen.tsx CATEGORIES.map() |

---

## §3. P0 4건 (legacy 1:1 일치 의무)

### P0 #1 — 4 카테고리 textColor 통일 (#111827)

이전: 4 entry 가 각각 `#3730A3 / #0E7490 / #9A3412 / #6B21A8` (legacy 미존재)
변경: `legacyMobileGateStyles.selectBigText.color = legacyVars.cStrong` (#111827) 1곳에서만 정의.
`CATEGORIES` array entry 의 `textColor` 필드 폐기.

### P0 #2 — DC notice 박스 완전 삭제

이전: `dcError` 가 있을 때 `View styles.dcErrorBox` 노출 (PR #61 에서 정상 안내는 삭제됨)
변경: View 자체 폐기. `dcError` 는 `useEffect` 안 `console.warn` 만.
`styles.dcNotice / dcNoticeText / dcErrorBox / dcErrorText` stylesheet 객체 제거.

근거: PM 결정 U3 — dcConfigStore 의 backend 적용 (calcDcPrice) 은 그대로 유지, **RN 시각 노출만 제거**.

### P0 #3 — 상단 titleBar 삭제

이전: HomeScreen.tsx 에 자체 `titleBar` View (주문서 / 거래처명+코드) 표시
변경: View + styles 통째 제거.

근거:
- legacy line 195 `body.mobile-mode .top { display:none !important }` 모바일에서 .top 숨김
- 거래처명/사업자번호는 WebView 안 legacy 가 표시 (이중 표시 방지 + UUID 비공개 원칙)

### P0 #4 — mobile-gate paddingBottom 제거

이전: `legacyMobileGateStyles.mobileGate.paddingBottom: 30` (legacy 미존재)
변경: `paddingBottom` 제거 → `marginTop: 20, marginBottom: 12, paddingHorizontal: 16` 만 유지.

근거: legacy line 119 `margin: 20px 0 12px` 일관 — 본 styled 패딩 30 은 추가 메뉴 영역 위 과다 여백 원인.

---

## §4. P1 보존 (정정 #17 의도 그대로)

`extraMenuSection` View + 5 Pressable + style 모두 보존:
- 임의 분기계산 (`#F5F3FF / #C4B5FD`)
- 견적·주문하기 (`#ECFEFF / #67E8F9`)
- 과거 발송내역 확인 (`#F0FDF4 / #86EFAC`)
- 주문저장 (`#FFFBEB / #FCD34D`)
- 저장내역 (`#FFF7ED / #FDBA74`)

근거: 거래처 사용성 (legacy `#btnOpenBranch / #btnSendOrder / #btnHistory / #btnSaveDraft / #btnDraftList`
의 모바일 진입 우회) — PM 결정 U1 보존.

---

## §5. capture script 신규 (PM 결정 U5)

### §5.1 폐기

`clients/mobile/scripts/capture-v4.cjs` 삭제 (mock overlay 6장 — 사용자 reject 패턴).

### §5.2 신규

`clients/mobile/scripts/capture-home.cjs` — mobile-staff v3 의 `capture-v3.cjs` 패턴 1:1 참조:

- iOS viewport 390x844 + Android viewport 412x915 (총 2 viewport)
- 각 viewport 5장 = **총 10장**:
  1. `01-bizgate` — 실 expo export bundle 진입 (RN web 자체 렌더 — 어두운 .biz-box layout)
  2. `02-home-after-fix` — HomeScreen mock (P0 fix 결과 — 4 카테고리 검정 + paddingBottom 0)
  3. `03-home-extra-menu` — HomeScreen mock 스크롤 (extraMenuSection 5 메뉴)
  4. `04-webview-order` — LegacyOrder WebView placeholder (legacy 임베드 영역)
  5. `05-bottom-tab` — Home + Bottom Tab (홈/주문/알림/프로필)
- expo dev server 미가동 시 abort + 사용자 안내
- 출력: `docs/qa/migration-fe-mobile-v4-design-audit/{iOS,Android}-{01..05}-*.png`

본 환경 (Windows / external backend 미가동) 에서 BizGate axios POST 가 cross-origin + backend 미가동
으로 실패 → BottomTab 진입 불가. 따라서 mock HTML 로 P0 fix 의 시각 결과를 직접 검증.

---

## §6. mobile-staff v3 reference (비교용)

`docs/qa/migration-fe-mobile-staff-v3/{01,02,03}-*.png` 3장 — `origin/feature/migration-fe-mobile-staff-v3-rewrite`
브랜치에서 cherry-pick. 본 통합 PR 에는 비교 reference 로만 첨부 (mobile-staff v3 PR 은 별도 발행).

---

## §7. 변경 매트릭스 요약

| File | 변경 | 라인 | P# |
|---|---|---|---|
| `clients/mobile/src/screens/home/HomeScreen.tsx` | 전체 rewrite (titleBar/DC notice 삭제, textColor 단순화) | -50 +30 | P0 #1 #2 #3 |
| `clients/mobile/src/styles/legacyMobile.ts` | mobileGate.paddingBottom 제거 + selectBigText.color 추가 | -1 +12 | P0 #1 #4 |
| `clients/mobile/scripts/capture-home.cjs` | 신규 (mobile-staff v3 패턴) | +280 | U5 |
| `clients/mobile/scripts/capture-v4.cjs` | 삭제 | -417 | U5 |
| `clients/mobile/package.json` | `capture:home` script 추가 | +1 | U5 |
| `docs/dev-reports/mobile-design-integration-scope.md` | 본 정의서 신규 | +200 | docs |
| `docs/qa/migration-fe-mobile-v4-design-audit/*.png` | QA 캡처 10장 신규 | +10 files | QA |
| `docs/qa/migration-fe-mobile-staff-v3/*.png` | reference 3장 cherry-pick | +3 files | ref |

---

## §8. 미결 항목 — 통합 PR 적용 결과 (2026-05-05)

| 미결 | 결정 | 적용 결과 |
|---|---|---|
| U1 추가 메뉴 5개 | 보존 (정정 #17 의도, 거래처 사용성) | extraMenuSection 그대로 보존 |
| U2 titleBar 후 거래처명 | legacy 만 (WebView 안 표시) | titleBar View 삭제, partnerCode/Name 노출 X |
| U3 dcConfigStore | 유지 (backend 적용 그대로 + RN 시각만 제거) | dcConfigStore import 유지, dcNotice/dcError View 제거, error 는 console.warn 만 |
| U5 capture-v4.cjs | 폐기 + capture-home.cjs 신규 대체 | capture-v4.cjs git rm, capture-home.cjs 신규 (mobile-staff v3 패턴 1:1) |
| U6 origin/main 통합 검증 | 완료 — PR #50/#52/#53/#54/#58/#61 모두 머지됨 | base = `ccb7f42` 확인 |

---

## §9. 검증

- `npm run typecheck` (Mobile v4) — PASS
- `npx expo export --platform web` — PASS (dist 생성)
- `node scripts/capture-home.cjs` — 10장 모두 정상 생성
- 라인 수: HomeScreen.tsx 265 → 220 (-45), legacyMobile.ts 변경 +11/-2

---

## §10. 회고 가드

본 PR 발행 시 다음 memory 가드 준수:
- `feedback_integrated_pr_pattern.md` — 통합 PR 1개 + QA 캡처 + TM 승인
- `feedback_pr_qa_screenshots.md` — QA 캡처 인라인 첨부 13장 (Mobile v4 10 + mobile-staff v3 reference 3)
- `feedback_pr_ci_monitoring.md` — PR 발행 후 즉시 `gh pr checks --watch`
- `feedback_korean_commits.md` — 한국어 commit/PR 본문
- `feedback_role_naming_full.md` — role 풀네임
- `feedback_powershell_utf8_writes.md` — PR body Write tool 만

---

## §11. 회고 #2 — Mobile v4 RN UI 최소화 (2026-05-05)

### 11.1 사용자 피드백 (개발책임자)

> "종합견적서 모바일용 앱은 구글 스크립트를 거의 그대로 계승한 것으로 보이나,
> 주문서는 여전히 구글 스크립트 모바일 버전의 UI와 처음 모바일 게이트를 제외한
> 나머지는 모두 다름을 확인."

이전 §1~§10 의 통합 fix (4 카테고리 textColor / DC notice / titleBar / mobile-gate paddingBottom)
는 **모바일 게이트의 4 카테고리 큰 진입 버튼 시각만** 일치시킴. 그 외 화면 (extra-menu 5개 /
BottomTab 4 탭 / RN HomeScreen 자체) 은 legacy 미존재 → 전체 일치 위반.

### 11.2 원인 분석

| 영역 | 이전 v4 | 사용자 첨부 (legacy) | 일치 여부 |
|---|---|---|---|
| 모바일 게이트 4 카테고리 | RN HomeScreen + legacyMobile.ts | `body.no-active .mobile-gate` 4 카테고리 | 시각만 일치 (entry path 다름) |
| 페이지 메뉴 drawer (▼) | RN extra-menu 5 Pressable (정정 #17) | `#drawerTop` + 자동 로그아웃 timer | **불일치** (legacy 미존재 5 메뉴) |
| BottomTab 4 탭 | RN BottomTab (홈/주문/알림/프로필) | legacy 미존재 | **불일치** (legacy 모두 단일 화면) |
| BizGate 인증 | RN AuthStack (BizGate/TempPassword/Register) 3 screen | `#pageBizGate` biz-box (legacy 안) | **불일치** (RN 측이 legacy 인증 전 추가 layer) |
| 과거 발송내역 | extra-menu 1개 (placeholder) | `#pageHistory` (legacy 안) | **불일치** (RN 측에서 진입 라우팅) |

→ mobile-staff v3 (영업직원 견적서) 와 본질 차이:
- mobile-staff v3 = `App.tsx` → `<EstimateWebViewScreen />` 단일 (RN wrapper 만)
- Mobile v4 (이전) = `App.tsx` → NavigationContainer → RootNavigator → AuthStack/BottomTab → 7+ screen

### 11.3 정정 결정 — 옵션 A 전면 폐기 + WebView 단일화

mobile-staff v3 의 `EstimateWebViewScreen` 패턴 1:1 적용:

| 영역 | 신규 v4 |
|---|---|
| `App.tsx` | `SafeAreaProvider + StatusBar + <MobileOrderWebViewScreen />` |
| 단일 메인 screen | `MobileOrderWebViewScreen` = `<WebView source={{uri: 'http://localhost:4173'}} />` |
| WebView shim | `legacyOrderShim.ts` = fetch monkey-patch + mobile-mode 검증 + postMessage bridge (google.script.run 폐기 — order-app v4 의 main.ts/legacyShim.ts 가 자체 제공) |
| WebView source | `legacyOrderSource.ts` = dev `:4173` / prod `https://order.samhan-air.com` |

dev URL 변경: `http://localhost:5180/legacy/index.html` (web/order-app v4 Vite, sub-path 임베드) →
`http://localhost:4173` (web/order-app v4 root 직접 진입 — `index.html` 자체가
`migration/source/scripts/partner-order/index.html` 9427 라인 그대로 +
`<script type="module" src="/src/main.ts">` 한 줄 (shim) 만 추가).

정정 #2 (2026-05-05 PR #70 revert 후속) — 이전 #Z 가 `:5185` (legacy-v2 의 order-legacy Express + EJS 포팅,
별도 디렉토리) 채택했으나 PR #70 머지로 main 에서 제거되어 운영 시 작동 X. main 에 존재하는
order-app v4 (Vite + PWA) 로 정정. default port 4173 = `vite preview --strictPort` (PM 환경 가동 중 포트).
vite dev (`npm run dev`) 사용 시 5180 — 환경변수 `EXPO_PUBLIC_ORDER_APP_URL` 또는 `QA_ORDER_BASE_URL` 로 override.

근거: order-app v4 의 root `index.html` 이 legacy partner-order/index.html 그대로 임베드 → 인증 / RPC /
mobile-mode CSS 분기 / 모바일 게이트 / 페이지 메뉴 drawer / 4 카테고리 진입 / 임시저장 / 확정 /
과거 발송내역 / 자동 로그아웃 timer 모두 legacy 자체 처리. order-app v4 의 `main.ts` 가
`google.script.run` shim → samhanApi axios 호출로 RPC dispatch (Apps Script → Samhan Public MS REST 변환).

### 11.4 폐기 파일 + 신규 파일

| 변경 | 파일 |
|---|---|
| **신규** | `clients/mobile/src/screens/MobileOrderWebViewScreen.tsx` |
| **신규** | `clients/mobile/src/webview/legacyOrderShim.ts` |
| **신규** | `clients/mobile/src/webview/legacyOrderSource.ts` |
| **신규** | `clients/mobile/scripts/capture-v4.cjs` |
| **수정** | `clients/mobile/App.tsx` (전면 rewrite — 5라인 wrapper) |
| **수정** | `clients/mobile/package.json` (deps 폐기 — react-query / axios / react-navigation / async-storage / zustand) |
| **수정** | `clients/mobile/README.md` (v4 회고 #2 갱신) |
| **폐기** | `clients/mobile/src/screens/home/HomeScreen.tsx` |
| **폐기** | `clients/mobile/src/screens/auth/{BizGate,TempPassword,Register}Screen.tsx` |
| **폐기** | `clients/mobile/src/screens/notifications/NotificationListScreen.tsx` |
| **폐기** | `clients/mobile/src/screens/order/LegacyOrderWebViewScreen.tsx` |
| **폐기** | `clients/mobile/src/screens/profile/{Profile,Settings}Screen.tsx` |
| **폐기** | `clients/mobile/src/navigation/{Root,AuthStack,BottomTab}Navigator.tsx` + `types.ts` |
| **폐기** | `clients/mobile/src/webview/legacyShim.ts` |
| **폐기** | `clients/mobile/src/webview/legacySource.ts` |
| **폐기** | `clients/mobile/src/styles/legacyMobile.ts` |
| **폐기** | `clients/mobile/src/stores/{auth,dcConfig}Store.ts` |
| **폐기** | `clients/mobile/src/api/{auth,client}.ts` |
| **폐기** | `clients/mobile/src/components/{RNBadge,RNButton,RNCard,RNFormField,ScreenContainer}.tsx` |
| **폐기** | `clients/mobile/src/tokens/tokens.ts` |
| **폐기** | `clients/mobile/src/utils/{calcDcPrice,formatSlipNumber}.ts` |
| **폐기** | `clients/mobile/scripts/capture-home.cjs` (mock HTML overlay 6장 — homeMockHtml/webviewMockHtml 함수) |
| **폐기** | `clients/mobile/scripts/capture.cjs` |

### 11.5 mobile-staff v3 와 1:1 일치 검증

| 영역 | mobile-staff v3 | Mobile v4 (회고 #2) | 일치 |
|---|---|---|---|
| `App.tsx` 라인 수 | 25 | 25 | ✓ |
| 단일 main screen | `EstimateWebViewScreen` | `MobileOrderWebViewScreen` | ✓ |
| WebView shim 패턴 | `buildShim()` (default null token) | `buildOrderShim()` (default null token) | ✓ |
| WebView source 패턴 | `getEstimateAppUrl()` (env / dev 5183 / prod estimate.samhan-air.com) | `getOrderAppUrl()` (env / dev 4173 / prod order.samhan-air.com) | ✓ |
| Android BackHandler | hardware back → `webview.goBack()` | hardware back → `webview.goBack()` | ✓ |
| `package.json` deps | 9 (expo / RN / safe-area / webview / web) | 9 (동일) | ✓ |
| capture script | `capture-v3.cjs` (실 dev server, 3장) | `capture-v4.cjs` (실 dev server, 5장) | ✓ |
| dev-reports | `migration-fe-mobile-staff-v3.md` | `mobile-design-integration-scope.md §11` | ✓ |

### 11.6 사용자 첨부 캡처 vs 신규 캡처 매핑

`docs/qa/legacy-original/partner-order/`:

| 사용자 첨부 | 신규 캡처 | 100% 일치 의무 |
|---|---|---|
| Screenshot 2026-05-05 at 20.17.37.JPG (모바일 게이트 4 카테고리) | `01-mobile-gate.png` | ✓ |
| Screenshot 2026-05-05 at 20.17.55.JPG (페이지 메뉴 drawer + 자동 로그아웃) | `02-page-menu.png` | ✓ |

추가 캡처 (사용자 첨부 외):
- `03-home-active.png` (홈멀티 진입 직후 라인 grid + 옵션·필터 sidebar)
- `04-page-history.png` (과거 발송내역 페이지)
- `05-bizgate.png` (인증 게이트 #pageBizGate)

### 11.7 검증

- `npm install --legacy-peer-deps` (Mobile v4) — 의존성 9개 (이전 19개에서 -10)
- `npx tsc --noEmit` (Mobile v4) — PASS (타입 에러 0)
- `npx expo export --platform web` (Mobile v4) — PASS (dist/index.html 생성)
- `node scripts/capture-v4.cjs` — 5장 모두 정상 생성 (order-app v4 dev/preview server 4173 가동 시)
- 라인 수: 이전 v4 약 1500 (HomeScreen 220 + nav 4 + auth 3 + comp 5 + store 2 + api 2 + util 2 + token 1 + style 1 + shim 280 + screen 285) → 신규 v4 약 350 (App 25 + screen 105 + shim 200 + source 100)

### 11.8 회고 가드

본 회고 #2 추가 commit + push 시:
- `feedback_integrated_pr_pattern.md` — 본 fix 는 PR #69 추가 commit (단편 PR 발행 X)
- `feedback_pr_qa_screenshots.md` — 신규 5장 + mobile-staff v3 reference 3장 + 사용자 첨부 비교 2장 인라인 첨부
- `feedback_pr_ci_monitoring.md` — push 후 즉시 `gh pr checks --watch`
- `feedback_korean_commits.md` — 한국어 commit + PR 본문 갱신
- `feedback_powershell_utf8_writes.md` — PR body Write tool 만
