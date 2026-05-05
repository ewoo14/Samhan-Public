# @samhan/mobile — SamhanLogis 거래처 주문서 (React Native Expo) — v4 (회고 #2)

> Phase 6 frontend Sub-team Mobile **v4 (legacy WebView 단일화)**.
> 회고 #2 (2026-05-05) — 사용자 명시:
> "주문서는 ... 처음 모바일 게이트를 제외한 나머지는 모두 다름을 확인."
> mobile-staff v3 의 `EstimateWebViewScreen` 패턴 1:1 적용.

## 개요 (v4 / 회고 #2 정정)

거래처가 모바일에서 order-legacy v4 (Node + Express + EJS, 9427 라인 1:1 포팅) 를
react-native-webview 로 통째 임베드. RN 측은 SafeAreaProvider + StatusBar +
WebView wrapper 만.

- 이전 v4 (RN HomeScreen + 4 카테고리 + extra-menu 5개 + BottomTab) **폐기** —
  legacy 의 모바일 게이트만 일치, 나머지 모두 별도 noise.
- 신규 v4 = mobile-staff v3 와 동일한 단일 WebView wrapper. 모바일 게이트 / 페이지 메뉴
  drawer / 4 카테고리 진입 / 임시저장 / 확정 / 과거 발송내역 / 자동 로그아웃 timer 모두
  WebView 안 legacy 가 자체 표시.
- legacy 시각/기능 100% 일치 — RN 측은 wrapper 만 책임.

## 화면 구조 (v4 / 회고 #2)

| 영역 | 화면 | 구현 |
|---|---|---|
| 단일 메인 | `MobileOrderWebViewScreen` | react-native-webview (order-legacy v4 임베드) |

navigation / BottomTab / AuthStack / HomeScreen / NotificationListScreen / ProfileScreen
모두 폐기 — `<NavigationContainer>` 자체 미사용.

## WebView 통합

- `clients/mobile/src/screens/MobileOrderWebViewScreen.tsx` — `<WebView>` 단일 화면.
- `clients/mobile/src/webview/legacyOrderShim.ts` — fetch monkey-patch (X-Samhan-Partner header) +
  mobile-mode 자동 활성 검증 + postMessage bridge. `google.script.run` shim 은 order-legacy v4
  inline 이 자체 제공 → RN 중복 X.
- `clients/mobile/src/webview/legacyOrderSource.ts` — dev / prod URL.

dev URL: `http://localhost:5185/` (clients/web/order-legacy v4 의 Express 서버).
prod URL: `https://order.samhan-air.com/`.

인증: WebView 안 legacy `tryLogin` (Apps Script 1:1) 가 cookie 로 처리. RN 측 BizGate native 폐기.

## 모바일 분기 자동 활성

- order-legacy v4 의 views/index.ejs:
  - line 119  : `.mobile-gate { display:none; flex-direction:column; gap:16px; margin:20px 0 12px }`
  - line 120  : `body.no-active .mobile-gate { display:flex }` (인증 통과 후 default 4 카테고리 노출)
  - line 121  : `.select-big { width:100%; height:150px; ... font-size:36px }`
  - line 4480 : `document.body.classList.toggle('mobile-mode', isMobile)`
- react-native-webview 의 device width (iPhone 14 Pro = 390 < 1280) → mobile-mode 자동.

## 실행

```sh
cd clients/mobile
npm install --legacy-peer-deps
npm run start          # Expo Dev Server (QR 코드)
npm run ios            # iOS 시뮬레이터
npm run android        # Android 에뮬레이터
npm run web            # web preview (mobile viewport)
npm run typecheck      # TypeScript 검증
npm run doctor         # expo-doctor 검증
npm run export:web     # web preview build (CI)
npm run capture:v4     # Playwright QA 캡처 (dev server 5185 필요)
```

## QA 캡처

`docs/qa/migration-fe-mobile-v4-design-audit/` —
mobile-staff v3 의 `capture-v3.cjs` 패턴 1:1, mock HTML overlay 폐기.

| 캡처 | 사용자 첨부 | 화면 |
|---|---|---|
| 01-mobile-gate.png | Screenshot 20.17.37.JPG | 모바일 게이트 4 카테고리 |
| 02-page-menu.png | Screenshot 20.17.55.JPG | ▼ 페이지 메뉴 drawer + 자동 로그아웃 timer |
| 03-home-active.png | — | 홈멀티 진입 직후 라인 grid + 옵션·필터 sidebar |
| 04-page-history.png | — | 과거 발송내역 페이지 |
| 05-bizgate.png | — | 인증 게이트 (#pageBizGate) |

## UUID 미노출 (`feedback_uuid_no_user_visibility.md`)

본 RN wrapper 자체에서는 UUID 노출 없음. 화면에 노출되는 식별자는 모두 WebView 안
order-legacy v4 가 표시 — `orderNumber` (PO-YYYYMMDD-NNNN) / `partnerCode` (사업자번호 10자리) /
`partnerName` (거래처명) / `modelCode` (품목코드) 만.

## 한국 path 트랩

worktree path 가 한글이면 npm install / Metro bundler 실패 가능 (`feedback_korean_path_jdk.md`
의 RN 변형). 본 worktree 는 영문 path (`integrated-rn-client`) 라 OK.
