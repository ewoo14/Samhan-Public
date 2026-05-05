# Migration FE Mobile v4 — 개발 보고서

> Phase 6 frontend Sub-team Mobile v4 (FE + Designer + QA + DevOps 통합)
> 작업 일자: 2026-05-05
> 브랜치: `feature/migration-fe-mobile-v4` (base = main `2e600bc`)
> base v3 = `feature/migration-fe-mobile-v3` commit `431d9aa`
> DECISIONS Phase 6 v4 commit = `b15fa12`

---

## 1. 핵심 결정 — 사용자 명시 임베드

> 사용자 (개발책임자) 지시: **"코드 임베드 방식으로 진행 / legacy 와 거의 일치하도록 진행 요청"**.
>
> v3 (React 변환 11 화면 + 6 Order Stack screen) 폐기 → v4 (legacy index.html 임베드).

### v3 → v4 변경 요약

| 영역 | v3 (React 변환) | v4 (legacy 임베드) |
|---|---|---|
| 메인 견적/주문 화면 | OrderListScreen / OrderFormScreen / OrderDetailScreen / ProductPickerScreen / BranchCalcScreen / DraftListScreen (6 RN 화면) | **단일 LegacyOrderWebViewScreen** (react-native-webview) |
| BizGate 인증 | RN native (보존) | RN native (보존) |
| Bottom Tab 네비 | RN native (보존) | RN native (보존) |
| 알림 / 프로필 / 설정 | RN native (보존) | RN native (보존) |
| 데이터 fetch | RN axios (`api/partnerOrder.ts` / `api/product.ts`) | WebView 안 fetch (CORS allow + Bearer) |
| 외부 호출 (e-Count + Notion) | noop (이미 폐기) | noop (shim 안 명시) |

---

## 2. WebView 통합 방식

### 2.1 react-native-webview 패키지 추가

`package.json`:
```json
"react-native-webview": "13.13.5"
```
Expo SDK 53 호환 — `npx expo install react-native-webview` 권장 버전.

### 2.2 LegacyOrderWebViewScreen — `clients/mobile/src/screens/order/LegacyOrderWebViewScreen.tsx`

핵심 props:
- `source={{ uri }}` — `getLegacyUri({ category })` (dev: `http://localhost:5180/legacy/index.html`, prod: `https://order.samhan-air.com/legacy/index.html`)
- `injectedJavaScriptBeforeContentLoaded` — `getInjectedShim({ apiBaseUrl, token, partnerCode })` (legacy 첫 줄 실행 전에 주입)
- `applicationNameForUserAgent` — `' SamhanMobileApp/0.4.0 (samhan-mobile)'` (legacy `isMobileNow()` 분기 보강)
- `originWhitelist={['*']}` (개발 시 모든 origin 허용)
- `domStorageEnabled` / `sharedCookiesEnabled` / `thirdPartyCookiesEnabled` — token sessionStorage / cookie 공유

### 2.3 shim 구조 — `clients/mobile/src/webview/legacyShim.ts`

```js
window.google.script.run
  .withSuccessHandler(...)
  .withFailureHandler(...)
  .<RPC name>(args)
```

→ Proxy 로 가로채서 `RPC[name]?.(...args)` (fetch chain) 으로 라우팅.

**12 RPC site (Web v4 의 `legacy-rpc-mapping-partner-order.md` 와 1:1 동일)**:

| legacy 함수 | SamhanLogis MS endpoint | service |
|---|---|---|
| `getProducts` | `GET /api/v1/products?all=true` | product-service (M1a) |
| `getHomeMulti` | `GET /api/v1/products?category=HOME_MULTI` | product-service |
| `getSingleSets` | `GET /api/v1/products?category=SINGLE_SET` | product-service |
| `getSingleParts` | `GET /api/v1/products?category=SINGLE_PART` | product-service |
| `getSingleMatPrices` | `GET /api/v1/material-prices` | product-service |
| `getCommercialMulti` | `GET /api/v1/products?category=COMMERCIAL_MULTI` | product-service |
| `getCommercialParts` | `GET /api/v1/products?category=COMMERCIAL_PART` | product-service |
| `getHomeDefaults` | `GET /api/v1/products/defaults?category=HOME_MULTI` | product-service |
| `getSingleDefaults` | `GET /api/v1/products/defaults?category=SINGLE_SET` | product-service |
| `getCustomers` | `GET /api/v1/partners?all=true` | partner-service |
| `searchCustomerByBizOrCode` | `GET /api/v1/partners/search?q=...` | partner-service |
| `getManagers` | `GET /api/v1/employees?role=MANAGER` | user-service |
| `saveOrderSnapshot` | `POST /api/v1/partner-orders/snapshots` | partner-order-service (M4) |
| `getOrderSnapshotHistory` | `GET /api/v1/partner-orders/snapshots?bizNo=&date=` | partner-order-service |
| `sendOrderFromUi` | `POST /api/v1/partner-orders` | partner-order-service |
| `saveBranchCalc` | `POST /api/v1/branch-calcs` | partner-order-service |
| `applyConfigFromServer` | `GET /api/v1/partners/{partnerCode}/config` | partner-dc-service |
| `requestAuthApproval` | `POST /api/v1/auth/biz-gate/request` | auth-service |
| `setAuthPassword` | `POST /api/v1/auth/biz-gate/set-password` | auth-service |
| `tryLogin` | `POST /api/v1/auth/biz-gate/login` | auth-service |
| `getGateImages` | `GET /api/v1/assets/gate-images` | file-service |
| `getLogoImage` | `GET /api/v1/assets/logo` | file-service |
| `logFrontEvent` | `POST /api/v1/logs/front-events` | log-service |

**외부 호출 폐기 (noop)**:
- `sendToECount` → `Promise.resolve({ noop: true })` + RN `postMessage('noop', { fn: 'sendToECount' })`
- `notionUpsert` / `notionFetch` → 동일 noop

### 2.4 RN ↔ WebView Bridge

| 방향 | 메커니즘 | 사용처 |
|---|---|---|
| WebView → RN | `window.ReactNativeWebView.postMessage(JSON.stringify({type, payload}))` | `shim-installed`, `legacy-loaded`, `rpc-error`, `rpc-missing`, `noop`, `host-close` |
| RN → WebView | `webViewRef.current.injectJavaScript(setAuthScript({...}))` | token 갱신 (BizGate native 인증 후 자동), 카테고리 진입 (`enterMobile(which)`) |

### 2.5 token 전달 흐름

1. RN 의 BizGate native (v3 보존) — 사용자가 사업자번호 입력 → `POST /api/v1/auth/biz-gate/login` → token 받음.
2. `useAuthStore.login(...)` 호출 → AsyncStorage 저장 + zustand state.
3. RootNavigator 가 `Main` (BottomTab) 으로 전환.
4. 사용자가 '주문' 탭 누름 → `LegacyOrderWebViewScreen` 진입.
5. `useEffect([token, partnerCode])` → `webViewRef.current.injectJavaScript(setAuthScript({apiBaseUrl, token, partnerCode}))`.
6. WebView 안 `__SAMHAN_BRIDGE__.setAuth(...)` → `__SAMHAN_AUTH__` 갱신 → 모든 fetch 가 `Authorization: Bearer <token>` 자동 첨부.

---

## 3. 신규/변경/폐기 파일

### 신규 (4)
- `clients/mobile/src/screens/order/LegacyOrderWebViewScreen.tsx` — WebView 단일 화면 + bridge.
- `clients/mobile/src/webview/legacyShim.ts` — google.script.run shim + 12 RPC 매핑 + auth bridge.
- `clients/mobile/src/webview/legacySource.ts` — dev/prod URL + category hash 헬퍼.
- `docs/dev-reports/migration-fe-mobile-v4.md` (본 파일)

### 변경 (4)
- `clients/mobile/package.json` — `react-native-webview` 추가, version `0.4.0`, description 갱신.
- `clients/mobile/src/navigation/types.ts` — `OrderStackParamList` 단일 `LegacyOrder`.
- `clients/mobile/src/navigation/BottomTabNavigator.tsx` — Order Stack = LegacyOrderWebViewScreen.
- `clients/mobile/src/screens/home/HomeScreen.tsx` — 5 추가 메뉴 모두 `OrderTab/LegacyOrder` 진입 통일.

### 폐기 (9)
- `clients/mobile/src/screens/order/OrderListScreen.tsx`
- `clients/mobile/src/screens/order/OrderFormScreen.tsx`
- `clients/mobile/src/screens/order/OrderDetailScreen.tsx`
- `clients/mobile/src/screens/order/ProductPickerScreen.tsx`
- `clients/mobile/src/screens/order/BranchCalcScreen.tsx`
- `clients/mobile/src/screens/order/DraftListScreen.tsx`
- `clients/mobile/src/api/partnerOrder.ts`
- `clients/mobile/src/api/product.ts`
- `clients/mobile/src/stores/orderDraftStore.ts`

### 보존 (RN native — v3 그대로)
- `clients/mobile/App.tsx` (root entry)
- `clients/mobile/src/navigation/RootNavigator.tsx` (auth 분기)
- `clients/mobile/src/navigation/AuthStackNavigator.tsx` (BizGate / TempPassword / Register)
- `clients/mobile/src/screens/auth/*.tsx` (3 화면 — 사업자번호 인증 native 처리)
- `clients/mobile/src/screens/home/HomeScreen.tsx` (legacy `.mobile-gate` 4 카테고리 native 모방)
- `clients/mobile/src/screens/notifications/NotificationListScreen.tsx`
- `clients/mobile/src/screens/profile/*.tsx`
- `clients/mobile/src/api/auth.ts` + `clients/mobile/src/api/client.ts`
- `clients/mobile/src/stores/authStore.ts` + `clients/mobile/src/stores/dcConfigStore.ts`
- `clients/mobile/src/components/*` (RN 공통 — Auth/Notifications/Profile 에서 사용)

---

## 4. 검증

| 명령 | 결과 | 비고 |
|---|---|---|
| `npm install --legacy-peer-deps` | PASS | react-native-webview 13.13.5 설치 |
| `npx tsc --noEmit` | PASS | strict mode + paths alias 통과 |
| `npx expo-doctor` | PASS | Expo SDK 53 호환 (react-native-webview = SDK 53 권장 버전) |
| `npx expo export --platform web` | PASS | web 빌드 — react-native-webview 의 web fallback (iframe) 정상 |

상세 로그는 `docs/qa/migration-fe-mobile-v4/verify.log`.

---

## 5. QA 캡처 (390 × 844 mobile viewport)

`docs/qa/migration-fe-mobile-v4/`:
1. `01-mobile-bizgate-native.png` — RN native BizGate (v3 동일, 어두운 layout)
2. `02-mobile-home-webview-loading.png` — WebView 진입 (legacy mobile-gate 4 카테고리)
3. `03-mobile-order-form-webview-legacy.png` — legacy 카테고리 active 후 라인 입력
4. `04-mobile-cardOrderInfo-webview.png` — legacy cardOrderInfo
5. `05-mobile-bottom-tab-with-webview.png` — Bottom Tab Navigator + 주문 탭 활성
6. `06-mobile-bizgate-token-webview-bridge.png` — BizGate → WebView token 전달 흐름

---

## 6. 모호 / 결정 대기

### 6.1 react-native-webview Expo 호환성
- Expo SDK 53 권장 = `react-native-webview@13.13.5`. 일반 `expo install` 시 자동 채택.
- iOS / Android native build 시 추가 native module 컴파일 필요 — Expo Go 안에서는 기본 포함.
- web 환경 (`expo export --platform web`) — react-native-webview 가 iframe 으로 자동 fallback.

### 6.2 Production hosting 결정 (보류)
- 본 코드는 prod URL 을 `https://order.samhan-air.com/legacy/index.html` 로 가정.
- 실제 배포 인프라 (Web v4 의 hosting 결정) 와 정렬 필요.
- 대안: Expo asset bundle (legacy index.html 을 RN bundle 안에 packaging) — `originWhitelist` 와 `WebView.source = { html: '...' }` 조합 검토 (offline 지원 시).

### 6.3 BizGate native vs WebView 인증
- 현재: BizGate = RN native, 인증 후 token 만 WebView 로 전달.
- 대안: BizGate 도 WebView 안 legacy 처리 (`#pageBizGate`).
- v4 결정: native 보존 — 사용자가 RN 앱 진입 시 즉시 native UX 노출 + biometric 등 확장 여지.

### 6.4 폐기 화면의 잔존 상수
- `LegacyCategory` 타입 (`home|single|comm|old`) 은 `navigation/types.ts` 에 보존.
- HomeScreen 의 `legacyMobileGateStyles` 도 보존 — RN native 4 카테고리 버튼 UI.

---

## 7. 회고 가드 준수

- **함수 단위 문서화 3-layer**: 한국어 Javadoc 모든 신규 파일 적용 + 본 dev-report 작성.
- **UUID 미노출**: WebView 안 legacy index.html 자체가 사업자번호/거래처코드/모델명 만 노출 (UUID 절대 노출 안 함).
- **PR CI Monitoring**: PR 발행은 PM 수동.
- **PowerShell UTF-8 트랩**: 모든 신규 파일 Write 도구로 작성 (UTF-8 LF, BOM 없음).
- **권한 표기 풀네임**: 본 문서 내 권한 표기 (대표/개발책임자/PM) 풀네임 사용.
