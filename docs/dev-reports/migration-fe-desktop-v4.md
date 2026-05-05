# Phase 6 Frontend Sub-team Desktop v4 — 슬라이스 보고서

## 결정 (DECISIONS Phase 6 v4 §)

사용자 최종 명시: "코드 임베드 방식으로 진행 / 가급적이면 가장 유사하게 legacy와 거의 일치하도록 진행요청".

v3 (React 변환) → v4 (legacy 코드 그대로 임베드) 전환.

| 화면 | 방식 |
|---|---|
| **종합견적서** | Electron `<webview src="file:///out/legacy-assets/estimate/index.built.html">` — legacy 18,614 라인 그대로 임베드. CSS/HTML/JS 변경 0. preload (`legacyShim.mjs`) 가 `window.google.script.run` shim 주입. |
| **주문서 조회 / 상세** | React (Desktop v3 cherry-pick) — `SalesPartnerOrderListPage` / `SalesPartnerOrderDetailPage` |
| **주문서 승인 (status 6종)** | React (Desktop v3 cherry-pick) — `SalesOrderApprovalsPage` |
| **거래처 DC 설정** | React (Desktop v3 cherry-pick) — `SalesPartnerDcConfigPage` |
| **견적 목록** | React (Desktop v3 cherry-pick) — `SalesEstimateListPage` + `+ 새 견적 (legacy)` 버튼 → `/sales/estimates/legacy` (webview 진입) |

## 작업 산출물

### 신규 파일
- `clients/desktop/scripts/build-legacy-estimate.cjs` — Apps Script `<?!= include() ?>` 디렉티브 inline 해소 + shim bootstrap 삽입 빌드 스크립트
- `clients/desktop/scripts/capture-v4-sales.cjs` — Edge headless 캡처 스크립트 (6 화면)
- `clients/desktop/src/main/legacy-asset.ts` — webview src 용 file:// URL 해석
- `clients/desktop/src/preload/legacyShim.ts` — webview preload, `google.script.run` Proxy
- `clients/desktop/src/preload/samhanApi.ts` — fnName → SamhanLogis MS endpoint 매핑 + fetch
- `clients/desktop/src/renderer/routes/EstimateLegacyWebviewPage.tsx` — `<webview>` 컴포넌트 wrapper + browser-mode placeholder

### v3 cherry-pick (변경 없음)
- `clients/desktop/src/renderer/api/sales.ts` (620 라인)
- `clients/desktop/src/renderer/api/slipNumber.ts` (60 라인)
- `clients/desktop/src/renderer/components/sales/SalesSubNav.tsx` (38 라인)
- `clients/desktop/src/renderer/components/sales/sales.module.css` (963 라인)
- `clients/desktop/src/renderer/routes/SalesEstimateListPage.tsx` (사소한 추가: legacy webview 진입 버튼)
- `clients/desktop/src/renderer/routes/SalesPartnerOrderListPage.tsx` (121 라인)
- `clients/desktop/src/renderer/routes/SalesPartnerOrderDetailPage.tsx` (163 라인)
- `clients/desktop/src/renderer/routes/SalesOrderApprovalsPage.tsx` (225 라인)
- `clients/desktop/src/renderer/routes/SalesPartnerDcConfigPage.tsx` (243 라인)

### 변경 파일
- `clients/desktop/package.json` — `build:legacy` / `capture:v4` 스크립트 추가, `dev` / `build` 가 prebuild 로 `build:legacy` 호출
- `clients/desktop/electron.vite.config.ts` — preload 2 entry (`index` + `legacyShim`)
- `clients/desktop/src/main/index.ts` — `webPreferences.webviewTag: true` + `legacy:get-estimate-url` IPC 등록
- `clients/desktop/src/preload/index.ts` — `window.samhanLegacy` contextBridge 노출
- `clients/desktop/src/renderer/types/electron.d.ts` — `<webview>` JSX intrinsic + `samhanLegacy` 타입
- `clients/desktop/src/renderer/components/AppLayout.tsx` — 사이드바 [판매] 그룹 4 NavLink 추가
- `clients/desktop/src/renderer/routes/index.tsx` — `/sales/estimates/{legacy,new}`, `/sales/partner-orders{,/:id}`, `/sales/order-approvals`, `/sales/partner-dc-config` 라우트 추가
- `clients/desktop/src/renderer/api/mock.ts` — `/api/v1/{estimates,partner-orders,partner-approvals,partner-dc-configs}` mock 응답 추가 (캡처용)

## 함수 매핑 11+ RPC (dev-reports/legacy-rpc-mapping-estimate.md)

estimate `index.html` 의 `google.script.run.<fnName>` 호출 11 site → 9 distinct fnName:

1. `checkUserAuth` → `GET /api/v1/auth/me`
2. `getCustomerDataAsync` → `GET /api/v1/partners?withDc=true`
3. `getInventoryTable` → `GET /api/v1/products?usageScope=ESTIMATE`
4. `getNotionHistory` → `GET /api/v1/partner-orders`
5. `logFrontEvent` → `POST /api/v1/audit-logs/front`
6. `getQuoteHistory` → `GET /api/v1/estimates/snapshots`
7. `saveQuoteSnapshot` → `POST /api/v1/estimates/snapshots`
8. `sendOrderFromUi` → `POST /api/v1/estimates/finalize`
9. `getGateImages` → `GET /api/v1/files/gate-images`

전체 매핑 표 + 응답 변환 spec: `docs/dev-reports/legacy-rpc-mapping-estimate.md`.

## 검증 결과

| 단계 | 결과 |
|---|---|
| `npm install` (clients/desktop) | OK (683 packages) |
| `npm install` (clients/web/design-system) | OK (380 packages) |
| `npm run build` (clients/web/design-system) | OK (87 modules → dist/index.js + dist/style.css) |
| `npm run typecheck` (clients/desktop) | **PASS** (0 errors) |
| `npm run lint` (clients/desktop) | PASS (0 errors, 1 pre-existing warning in SlipDetailPage.tsx) |
| `npm run build:legacy` | OK (5 includes inlined: NanumGothic 6.2MB / NanumGothicBold 6.2MB / logo 200KB / stamp 20KB / samhan 110KB → index.built.html 13.4MB) |
| `npm run build` | OK (electron-vite main + preload `index.mjs` + preload `legacyShim.mjs` + renderer `index.html`+JS+CSS) |
| `npm run capture:v4` | OK (6 PNG 산출, dev server mock 모드 + Edge headless) |

## 캡처 6 파일 경로

`docs/qa/migration-fe-desktop-v4/` 아래:
1. `01-desktop-sales-menu-v4.png` — sidebar [판매] 4 sub-route + sub-nav
2. `02-desktop-estimate-legacy-webview-init.png` — 견적서 진입 (placeholder, 4 카드 grid + cardFinal + cardOrderInfo 영역 표시)
3. `03-desktop-estimate-legacy-webview-after-add.png` — 라인 3건 추가 후 (홈멀티 활성 + 합계 4,850,000 + cardOrderInfo 자동 채움 + shim 활성 표시)
4. `04-desktop-estimate-legacy-print.png` — legacy `pageFinal` 인쇄 미리보기 (NanumGothic + 인감)
5. `05-desktop-order-approvals.png` — SamhanLogis React 신규 메뉴 (status 6종 + 비밀번호 초기화 + 상태 dropdown)
6. `06-desktop-partner-dc-config.png` — SamhanLogis React 신규 메뉴 (DC 222 row + 인라인 입력 + 검색)

## 모호 항목 (후속 작업 / 검토 필요)

### 1. webview 보안 (강화 가능)

현재 webview tag 의 보안:
- ✅ contextIsolation 활성 (분리된 컨텍스트)
- ✅ nodeIntegration false
- ✅ preload 만 contextBridge 로 google.script.run 노출
- ✅ src 는 file:// 만 (외부 URL X)
- ⚠️ allowpopups true — legacy 창업 동작 호환. 후속 단계에서 popup 제어 IPC 추가 가능
- ⚠️ webview 와 BrowserWindow 가 같은 session 사용 — 후속 단계에서 partition 분리 검토

### 2. asset 경로 통합 (production packaging)

현재 dev / built (`out/`) 환경에서는 `clients/desktop/legacy-assets/estimate/index.built.html` 를 file:// 로 로드.

production packaging (`electron-builder`) 시:
- `electron-builder.yml` 의 `extraResources` 에 `legacy-assets/` 등록 필요
- packaged 경로에서 `process.resourcesPath/legacy-assets/...` 후보가 동작
- 본 PR 에서는 dev / built 환경만 검증 — packaging 검증은 후속 단계

### 3. shim 누락 함수 (legacy code grep 추가)

본 매핑 표는 `index.html` 의 `google.script.run.<fnName>` grep 결과 (11 site / 9 distinct).

- `samhan.html` / `logo.html` / `stamp.html` / `NanumGothic*.html` 등 부속 파일은 단순 base64 데이터 + 폰트, RPC 호출 0 (확인 완료).
- 후속 단계: legacy partner-order/index.html (9427 라인) 은 별도 sub-team (web/order-app v4) 이 동일 분석 + 매핑 작성. estimate 와 일부 함수 (e.g. `sendOrderFromUi`) 는 동일하나 **endpoint 가 다름** (estimate → estimates/finalize, partner-order → partner-orders/confirm). preload shim 은 webview 별로 다른 매핑 표 주입 가능 (현재는 estimate 한정).

### 4. backend endpoint 미구현 (M2~M5 진행 중)

매핑 표의 endpoint 중 다수가 M2~M5 backend 미구현 — 본 PR 머지 시점에 webview 가 실 fetch 호출 시 404/connection refused. 회귀 방지 가드:
- mock 모드 (`VITE_MOCK_MODE=1`) 에서는 axios interceptor 가 short-circuit
- 실 호출 시 shim 의 failure handler 가 legacy `withFailureHandler` 로 routing → legacy code 의 alert/console.error 로 표시
- M2~M5 머지 후 단계적으로 endpoint 활성

### 5. 빌드 산출물 크기

`legacy-assets/estimate/index.built.html` = **13.4 MB** (NanumGothic + NanumGothicBold base64 임베드).

대안:
- 폰트를 별도 woff2 파일로 분리 → CSS `@font-face` 로 로드 (size 80% 절감, font subset 적용 시 추가 절감)
- 본 PR 에서는 legacy 100% 보존 + 단일 HTML entry 우선 — 후속 단계 최적화

### 6. CI 환경 — legacy assets 부재

본 PR 만 머지 시 `migration/` 디렉토리는 main 에 없음 (별도 PR `feature/legacy-migration-discovery`).

회귀 방지 가드:
- `build-legacy-estimate.cjs` 가 source 없음 시 `index.fallback.html` 자동 생성 (안내 placeholder)
- CI `npm run build` 가 fail 하지 않음 (graceful fallback)
- 머지 순서 권장: legacy assets PR → desktop v4 PR → 실 webview 검증
