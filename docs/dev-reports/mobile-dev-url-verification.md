# Mobile v4 + mobile-staff v3 dev URL 검증

## 1. 범위

PR #69 (RN client 통합 — Mobile v4 + mobile-staff v3) 머지 후, 두 RN 앱이 가리키는
WebView source URL 을 코드 정본 기준으로 검증한다.

대상 파일:

- `clients/mobile/src/webview/legacyOrderSource.ts` (Mobile v4 → order-app v4)
- `clients/mobile-staff/src/webview/legacyEstimateSource.ts` (mobile-staff v3 → estimate-app v2)

## 2. 검증 결과

### 2.1 mobile-staff v3 → estimate-app v2

| 환경 | URL | 출처 |
|---|---|---|
| dev | `http://localhost:5183/` | `legacyEstimateSource.ts#DEFAULT_DEV_URL` |
| dev override | `EXPO_PUBLIC_ESTIMATE_APP_URL` env | `legacyEstimateSource.ts#resolveBaseUrl` |
| production | `https://estimate.samhan-air.com/` | `legacyEstimateSource.ts#DEFAULT_PROD_URL` |

estimate-app v2 server.js 가 `process.env.PORT || '5183'` 로 listen → dev URL 일치 (PORT 5183).

production 도메인은 `estimate.samhan-air.com` (DECISIONS Phase 6 Section 4 sub-domain). `quote.samhan-air.com` 은 후보로 거론된 적 있으나 채택 X.

### 2.2 Mobile v4 → order-app v4

| 환경 | URL | 출처 |
|---|---|---|
| dev | `http://localhost:4173` | `legacyOrderSource.ts#DEFAULT_DEV_URL` |
| dev 변종 | `http://localhost:5180` (vite dev) / `http://localhost:5181` (vite preview default) | `legacyOrderSource.ts` line 22~25 코멘트 |
| dev override | `EXPO_PUBLIC_ORDER_APP_URL` env | `legacyOrderSource.ts#resolveBaseUrl` |
| production | `https://order.samhan-air.com` | `legacyOrderSource.ts#DEFAULT_PROD_URL` |

dev default 는 `:4173` (vite preview 명시 override). vite config 의 `server.port = 5180` 또는 `preview.port = 5181` 환경에서는 환경변수 override 권장.

production 도메인은 `order.samhan-air.com` (DECISIONS Phase 6 Section 4 sub-domain).

## 3. Samhan Public 자체 stack 일관성 검증

PR #67 머지 → PR #70 revert 로 legacy-v2 (이카운트/노션 살린 변종 — clients/web/order-legacy 의 Express + EJS 포팅, port 5185) 가 main 에서 제거됨. 본 PR 시점 기준 Samhan Public 의 client 5개는 모두 자체 stack:

| client | stack | 비고 |
|---|---|---|
| order-app v4 | Vite + React + PWA | legacy partner-order/index.html 9427 라인 임베드 |
| Desktop v4 | Electron + Vite + React | mock 모드 dev-only 보존 |
| Mobile v4 | Expo RN + WebView | order-app v4 wrapper |
| mobile-staff v3 | Expo RN + WebView | estimate-app v2 wrapper |
| estimate-app v2 | Node.js + Express + EJS | legacy Code.js 76 함수 1:1 포팅 |

legacy-v2 변종 (이카운트/노션 살린 버전) 은 별도 프로젝트로 이전 — 본 Samhan Public 범위 외.

## 4. 자동 모바일 분기 (코드 변경 0)

두 RN 앱 모두 WebView 의 device width 로 legacy CSS / JS 가 자동 mobile-mode 토글:

- estimate-app v2 (views/index.ejs line 7157) — `window.matchMedia('(max-width: 1280px)')` → `body.mobile-mode` toggle
- order-app v4 (legacy partner-order/index.html line 4491 / 8435) — `document.body.classList.toggle('mobile-mode', isMobile)`

iPhone 14 Pro = 390 / Galaxy S22 = 360 < 1280 → 자동 mobile-mode 진입. RN 측 추가 코드 없음.

## 5. UUID 미노출 검증

- mobile-staff v3 → estimate-app v2 query: `?email=` 자리에 사번 기반 식별자 (e.g. `S001@samhan-air.com`) 전달, default 는 query 미부여
- Mobile v4 → order-app v4: 임베드된 legacy 자체가 사업자번호/거래처코드/모델명만 노출

UUID 노출 금지 정책 충족.

## 6. 미가동 항목

- production URL (`estimate.samhan-air.com` / `order.samhan-air.com`) 은 Phase 6 시점 미가동.
  - `order.samhan-air.com` 은 PR #77 의 Cloudflare Pages workflow 활성화 후 가동 예정.
  - `estimate.samhan-air.com` 은 호스팅 결정 (Workers / Render / 카페24 SSH 3안 — `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md` 참조) 후 가동 예정.

본 PR 시점은 dev URL (`localhost:5183` / `localhost:4173`) 만 가동 가능.
