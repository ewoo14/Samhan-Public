# @samhan/mobile — SamhanLogis 거래처 주문서 (React Native Expo) — v4

> Phase 6 frontend mobile client (거래처용). **legacy WebView 단일화 패턴 채택**.
> mobile-staff v3 의 `EstimateWebViewScreen` 패턴 1:1 적용.

## 개요

거래처가 모바일에서 order-app v4 (Vite + legacy `partner-order/index.html` 9427 라인 임베드) 를
react-native-webview 로 통째 임베드. RN 측은 SafeAreaProvider + StatusBar + WebView wrapper 만.

- 단일 WebView wrapper = `MobileOrderWebViewScreen`. 모바일 게이트 / 페이지 메뉴 drawer /
  4 카테고리 진입 / 임시저장 / 확정 / 과거 발송내역 / 자동 로그아웃 timer 모두 WebView 안
  legacy 가 자체 표시.
- legacy 시각/기능 100% 일치 — RN 측은 wrapper 만 책임.

## 화면 구조

| 영역 | 화면 | 구현 |
|---|---|---|
| 단일 메인 | `MobileOrderWebViewScreen` | react-native-webview (order-app v4 임베드) |

navigation / BottomTab / AuthStack / HomeScreen / NotificationListScreen / ProfileScreen
모두 폐기 — `<NavigationContainer>` 자체 미사용.

## WebView 통합

- `clients/mobile/src/screens/MobileOrderWebViewScreen.tsx` — `<WebView>` 단일 화면.
- `clients/mobile/src/webview/legacyOrderShim.ts` — fetch monkey-patch (X-Samhan-Partner header) +
  mobile-mode 자동 활성 검증 + postMessage bridge. `google.script.run` shim 은 order-app v4
  inline 이 자체 제공 → RN 중복 X.
- `clients/mobile/src/webview/legacyOrderSource.ts` — dev / prod URL.

dev URL: `http://localhost:5185/` (`clients/web/order-app` v4 dev server).
prod URL: `https://order.samhan-air.com/`.

인증: WebView 안 legacy `tryLogin` (Apps Script 1:1) 가 cookie 로 처리. RN 측 BizGate native 폐기.

## 모바일 분기 자동 활성

- order-app v4 의 legacy `partner-order/index.html`:
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

| 캡처 | 화면 |
|---|---|
| 01-mobile-gate.png | 모바일 게이트 4 카테고리 |
| 02-page-menu.png | 페이지 메뉴 drawer + 자동 로그아웃 timer |
| 03-home-active.png | 홈멀티 진입 직후 라인 grid + 옵션·필터 sidebar |
| 04-page-history.png | 과거 발송내역 페이지 |
| 05-bizgate.png | 인증 게이트 (#pageBizGate) |

Phase 7 추가: `qa/playwright/` 의 `mobile-chrome` (Pixel 7) / `mobile-safari` (iPhone 14) project
가 본 client 의 dev URL `http://localhost:5184` 에 대해 happy + edge 시나리오를 자동 검증한다.

## UUID 미노출

본 RN wrapper 자체에서는 UUID 노출 없음. 화면에 노출되는 식별자는 모두 WebView 안
order-app v4 가 표시 — `orderNumber` (PO-YYYYMMDD-NNNN) / `partnerCode` (사업자번호 10자리) /
`partnerName` (거래처명) / `modelCode` (품목코드) 만.

UUID 가 필요한 backend 호출 (예: stock 조회) 은 Phase 7 3차 추가된 product-service
`GET /api/products/by-code/{modelCode}` 로 modelCode → productId 변환을 거쳐 진행한다.

## 한국 path 트랩

worktree path 가 한글이면 npm install / Metro bundler 실패 가능 — JDK 17 `@argfile`
인코딩 한계의 RN 변형. ASCII 전용 경로 (예: `C:\dev\SamhanLogis`) 사용 권장.
