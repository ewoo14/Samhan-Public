# @samhan/mobile — SamhanLogis 거래처 주문서 (React Native Expo) — v4

> Phase 6 frontend Sub-team Mobile **v4 (legacy 임베드)**.
> DECISIONS Phase 6 v4 commit `b15fa12` — 사용자 명시 "코드 임베드 방식".

## 개요 (v4)

거래처가 모바일에서 legacy `migration/source/scripts/partner-order/index.html` (9427 라인)
을 그대로 보면서 주문 작성/조회/저장. **react-native-webview** 로 legacy HTML 을
모바일 viewport 임베드, RN 프레임워크 (BizGate native + Bottom Tab + safe area) 보존.

- v3 (React 변환 11 화면) **폐기** → v4 (legacy 임베드) 채택
- legacy 시각/기능 100% 일치 — RN 측은 navigation routing / token 전달 만 책임
- 외부 호출 (e-Count + Notion) 모두 noop — SamhanLogis MS 단독

## 화면 구조 (v4)

| 영역 | 화면 | 구현 |
|---|---|---|
| 인증 (Auth Stack) | BizGate / TempPassword / Register | RN native (보존) |
| 주문 (Order Stack) | **LegacyOrder** | react-native-webview (legacy index.html) |
| 홈 | Home | RN native (4 카테고리 + 추가 5 메뉴) |
| 알림 | NotificationList | RN native (보존) |
| 프로필 (Profile Stack) | Profile / Settings | RN native (보존) |

Bottom Tab (4): 홈 / 주문 / 알림 / 프로필 (탭 전환 시 stack 보존)

## WebView 통합

- `clients/mobile/src/screens/order/LegacyOrderWebViewScreen.tsx` — `<WebView>` 단일 화면.
- `clients/mobile/src/webview/legacyShim.ts` — `window.google.script.run` shim → SamhanLogis MS fetch.
- `clients/mobile/src/webview/legacySource.ts` — dev / prod URL.

dev URL: `http://localhost:5180/legacy/index.html` (web/order-app v4 의 Vite dev server).
prod URL: `https://order.samhan-air.com/legacy/index.html`.

token 전달: BizGate native 인증 후 `webViewRef.injectJavaScript(setAuthScript({...}))`.

## 디자인 시스템 — token only

DS 컴포넌트 (`@samhan/design-system/components/*`) 는 RN 미호환이므로 **import 금지**.

`src/tokens/tokens.ts` 가 DS `tokens.css` / `tokens/index.ts` 의 색상·spacing·fontSize
값을 RN 호환 형태 (number / hex string) 로 hard-code 하여 export.

DS 와 동기화 시점:
- DS 의 색상값 변경 → `src/tokens/tokens.ts` 동시 업데이트 의무

## 실행

```sh
cd clients/mobile
npm install
npm run start          # Expo Dev Server (QR 코드)
npm run ios            # iOS 시뮬레이터
npm run android        # Android 에뮬레이터
npm run web            # web preview (mobile viewport)
npm run typecheck      # TypeScript 검증
npm run doctor         # expo-doctor 검증
npm run export:web     # web preview build (CI)
```

## API endpoint (RN native — BizGate 등 인증 만 직접 호출)

| 영역 | endpoint | 출처 |
|---|---|---|
| BizGate | `POST /api/v1/auth/biz-gate` | M2 partner-service |
| 임시 PW | `POST /api/v1/auth/login-temp` | M2 |
| 가입 | `POST /api/v1/auth/register` | M2 |
| DC config | `GET /api/v1/partners/{partnerCode}/config` | M2 partner-dc-service |

주문/품목 등 그 외 모든 endpoint = WebView 안 legacy 가 fetch (Authorization Bearer token).
매핑 표는 `docs/dev-reports/migration-fe-mobile-v4.md` §2.3 참조.

## UUID 미노출 (`feedback_uuid_no_user_visibility.md`)

화면에는 다음 만 노출:
- `orderNumber` (PO-YYYYMMDD-NNNN)
- `partnerCode` (사업자번호 10자리)
- `partnerName` (거래처명)
- `modelCode` (품목코드)

UUID (`id`) 는 navigation params 와 `id->orderNumber` 매핑 의 내부 전달 용도 만.

## 캡처 / QA

`docs/qa/migration-fe-mobile/` 참조.

## 한국 path 트랩

worktree path 가 한글이면 npm install / Metro bundler 실패 가능 (`feedback_korean_path_jdk.md`
의 RN 변형). 본 worktree 는 영문 path (`agent-a142a5f8954eda83f`) 라 OK.
