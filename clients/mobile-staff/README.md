# @samhan/mobile-staff — SamhanLogis 영업직원 견적 (React Native Expo) — v3

> Phase 6 frontend Sub-team **mobile-staff v3** — v2 코드 보존 + 캡처 script 재작성.
> PR #65 회고 후속 (사용자 명시: "다른 에이전트는 견적서의 스크린샷을 참조하여 앱버전 재작성 요망").

## 개요 (v3)

(주)삼한공조시스템 **영업직원** 이 모바일에서 estimate-app v2 (Node + Express + EJS 임베드된 legacy
estimate 18675 라인) 를 그대로 사용. **react-native-webview** 단일 screen 으로 estimate-app v2 를
모바일 viewport 임베드.

- 사용자 명시 (PR #65 회고): "다른 에이전트는 견적서의 스크린샷을 참조하여 앱버전 재작성 요망".
- v2 (PR #65 close) 의 RN code 13 파일 그대로 보존 — 캡처 script 만 재작성.
- 기존 v2 의 `capture-v2.cjs` = mock HTML overlay (Playwright web fallback) → 사용자 불만의 원인.
- v3 = mock 일체 폐기 + PM 의 estimate-app v2 dev server (port 5183) 직접 진입 캡처.

## v2 → v3 변경표

| 항목 | v2 (PR #65 close) | v3 (신규) |
|---|---|---|
| RN code (13 파일) | WebView only wrapper | **그대로 보존** |
| capture script | `capture-v2.cjs` (expo web export + iframe.srcdoc mock HTML) | **`capture-v3.cjs`** (실 dev server 직접 진입) |
| mock HTML overlay | 4 카드 mockup (estimateMockHtml 함수) | **폐기** — 진짜 estimate-app v2 화면 |
| 캡처 시나리오 | 01 init / 02 mobile-ui / 03 after-add | **사용자 첨부 캡처 1:1 매핑** (전표작성 form / 페이지 메뉴 / 라인 grid) |
| 출력 디렉토리 | `docs/qa/migration-fe-mobile-staff-v2/` | `docs/qa/migration-fe-mobile-staff-v3/` |
| dev server 의존 | 없음 (mock 자체 충족) | **있음** — 미가동 시 abort + 안내 |

## 사용자 첨부 캡처 1:1 매핑

`docs/qa/legacy-original/estimate/` 의 사용자 직접 캡처 3장이 v3 캡처의 절대 기준:

| v3 캡처 | 사용자 첨부 | 화면 내용 |
|---|---|---|
| `01-staff-app-init.png` | `Screenshot 2026-05-05 at 19.54.05.JPG` | 전표작성 거래처 form (거래처 / 대표자 / 대표번호 / 사업자주소 / 거래처분류 / 특이사항 / 출고일 / 출고창고 + 하단 버튼 4개) |
| `02-staff-app-page-menu.png` | `Screenshot 2026-05-05 at 19.55.07.JPG` | ▼ 페이지 메뉴 dropdown (전표작성 / 홈멀티 / 싱글세트 / 상업멀티 / 구형 / 견적서(기본) / 견적서(세트상세) / 전표업로드목록 / 장비스펙 / 발송내역 / 견적저장 / 저장내역 / 다크모드 + 자동 로그아웃 + 닫기 ▲) |
| `03-staff-app-card-line.png` | `Screenshot 2026-05-05 at 19.55.29.JPG` | 홈멀티 카테고리 진입 후 라인 grid (품목명 / 모델명 / 수량 / 납품가 + 좌측 옵션 tab + 우측 필터 tab + 하단 검색/조합비/초기화) |

## 디렉토리 구조

```
clients/mobile-staff/
├── package.json (Expo SDK 53 + react-native-webview + react-native-safe-area-context, v0.3.0)
├── app.json (name "삼한공조 견적", bundleId com.samhan.estimate, v0.3.0)
├── tsconfig.json
├── babel.config.js
├── App.tsx (단일 SafeAreaProvider + StatusBar + EstimateWebViewScreen — v2 그대로)
├── .env.example (EXPO_PUBLIC_ESTIMATE_APP_URL + EXPO_PUBLIC_API_BASE_URL)
├── src/
│   ├── screens/
│   │   └── EstimateWebViewScreen.tsx — 단일 WebView + RN 뒤로가기 + status bar (v2 그대로)
│   └── webview/
│       ├── legacyEstimateShim.ts (X-Samhan-Staff header 보존, v2 그대로)
│       └── legacyEstimateSource.ts (dev:5183 / prod:estimate.samhan-air.com + override + validate, v2 그대로)
├── scripts/
│   └── capture-v3.cjs (Playwright 3 캡처 — 실 dev server 직접 진입, mock 폐기)
└── README.md
```

## 인증 흐름 (v2 그대로)

```
[App 시작]
  ↓
[App.tsx → SafeAreaProvider → EstimateWebViewScreen]
  ↓
[WebView source = estimate.samhan-air.com / localhost:5183]
  ↓
[shim 사전 주입 (X-Samhan-Staff header 안전망 only — default 무인증)]
  ↓
[WebView 안 legacy estimate index.ejs 실행]
  ↓
[lib/code.js 의 checkUserAuth(USER_EMAIL) 자동 호출 — Apps Script Code.js line 16495 1:1]
  ↓
[iam-service GET /api/v1/auth/me?email= (mock fallback) → 영업직원 식별]
  ↓
[token 은 WebView 안 cookie / sessionStorage 에 저장 — RN 미관여]
  ↓
[견적 작성 RPC 11종 — 모두 WebView 안 inline google.script.run shim 가 처리]
```

→ RN 측 인증 코드 0줄. shim 의 `X-Samhan-Staff` header 첨부는 후속 (RN push notification + SSO 통합) 을
   위한 안전망으로만 보존 (default = `token=null, employeeCode=null`).

## legacy mobile UI 자동 활성

estimate-app v2 의 views/index.ejs 가 line 7187 에서:

```js
document.body.classList.toggle('mobile-mode', isMobile);
```

react-native-webview 의 device width (iPhone 14 Pro = 390, Galaxy S22 = 360) → 자동 활성.

→ 4 카드 grid (홈멀티/싱글세트/상업멀티/구형) 가 1열 stack 으로 자동 변환.
→ `.mobile-only` class 의 desktop 숨김 컬럼들이 자동 노출 (품목명, 모델 상세 등).
→ `#handleTop` (▼ 페이지 메뉴) drawer 가 활성 — 모바일 전용 메뉴 진입점.
→ `#handleLeft` (옵션) / `#handleRight` (필터) drawer 가 카테고리 진입 시 측면 sidebar 로 노출.

mobile-staff v3 의 RN wrapper 는 viewport 만 제공. mobile UI 활성은 100% legacy estimate 자체 책임.

## 환경변수

`.env.example` 참고. Expo SDK 53 의 `EXPO_PUBLIC_*` prefix 만 client 노출.

```
EXPO_PUBLIC_ESTIMATE_APP_URL=https://estimate.samhan-air.com/
EXPO_PUBLIC_API_BASE_URL=https://api.samhan-air.com
```

미정의 시:
- dev (`__DEV__ === true`): `http://localhost:5183/` + `http://localhost:8080`
- prod: `https://estimate.samhan-air.com/` + `https://api.samhan-air.com`

## 검증

```sh
cd clients/mobile-staff
npm install --legacy-peer-deps
npx tsc --noEmit
npx expo-doctor
npx expo export --platform web

# capture (PM 의 estimate-app v2 dev server 가 port 5183 에서 가동 중 필수)
curl http://localhost:5183/healthz   # 200 = 가동 중
npm run capture:v3                    # node scripts/capture-v3.cjs
```

→ `docs/qa/migration-fe-mobile-staff-v3/` 에 3장 PNG 생성.

dev server 미가동 시 capture script 가 abort + 안내 출력:

```
[abort] estimate-app v2 dev server 미가동: http://localhost:5183/healthz 응답 없음.
        먼저 다음 명령으로 dev server 를 시작하세요:
        cd c:/dev/SamhanLogis/clients/web/estimate-app && node server.js
```

## QA 캡처 3장

| 파일 | 설명 | 사용자 첨부 매핑 |
|---|---|---|
| `01-staff-app-init.png` | 진입 직후 — 전표작성 거래처 form (mobile-mode default) | Screenshot 19.54.05 |
| `02-staff-app-page-menu.png` | ▼ 페이지 메뉴 dropdown 활성 (13 메뉴 + 자동 로그아웃 + 닫기 ▲) | Screenshot 19.55.07 |
| `03-staff-app-card-line.png` | 홈멀티 카테고리 진입 후 라인 grid (옵션·필터 sidebar) | Screenshot 19.55.29 |

## RN 뒤로가기 (v2 그대로)

- Android = `BackHandler.addEventListener('hardwareBackPress')` 가 WebView 의 `canGoBack` state 확인.
  - history 있음 → `webview.goBack()` 우선 (event consumed).
  - history 끝 → default (앱 종료).
- iOS = native swipe-back gesture 는 single screen 이므로 미적용 (기본 OS 흐름).

## 후속 (모호 항목)

- Android push notification → WebView 안 영업직원 알림 routing 통합 (M3 이후).
- iOS App Store 배포 시 `bundleIdentifier` / 인증서 / Apple sign-in 정책.
- 영업직원 SSO (Google Workspace / Naver Works) → shim 의 `setEstimateAuthScript` 부활 가능성.
- DEVOPS — `https://estimate.samhan-air.com` 배포 (현재 dev only).

## 참고

- Mobile v4 (`clients/mobile`, 거래처용 — partner-order WebView, BizGate 인증, 4-tab) 와 분리.
- v2 코드는 `feature/migration-fe-mobile-staff-v2-webview` branch (close PR #65) 에서 cherry-pick.
- v1 코드는 `feature/migration-fe-mobile-staff-v1` branch (close PR #63) 에서 참조 가능.
- 사용자 첨부 캡처: `docs/qa/legacy-original/estimate/Screenshot 2026-05-05 at 19.5{4.05,5.07,5.29}.JPG`.
