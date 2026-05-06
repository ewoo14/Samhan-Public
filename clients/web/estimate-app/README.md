# estimate-app v2 — Node.js + Express + EJS

삼한공조시스템 종합견적서 web app — legacy Apps Script 코드 (Code.js 2837 라인 + index.html 18614 라인) 100% 보존 + Google 의존성 폐기 + 외부 데이터만 SamhanLogis MS DB 로 전환.

## 채택 이유 (B2 옵션)

DECISIONS Phase 6 v4 후속 정정 § 결정 — Apps Script 와 가장 가까운 환경 (Node.js + Express + EJS) 으로 1:1 마이그레이션.

- **v1 (Vite/React) 폐기 사유**: server-side template 변환 + Vite bundling 결과가 legacy server-side render 와 시각/동작 차이 발생.
- **v2 (Node.js/Express/EJS) 채택 사유**: legacy 백엔드 (Code.js) + 프론트엔드 (index.html with `<?!= ?>`) + UI/UX 100% 보존 가능.

## 디렉토리 구조

```
clients/web/estimate-app/
├── package.json            # express + ejs + axios + dotenv (+ jest, playwright dev)
├── .env.example            # 환경변수 템플릿 (PORT/SAMHAN_API_BASE_URL/SLIP_SERVICE_URL 등)
├── server.js               # Express bootstrap (port 5183)
├── routes/
│   ├── index.js            # GET / — bootstrap data prefetch + EJS render
│   └── rpc.js              # POST /rpc/:fnName — google.script.run 호환
├── views/
│   └── index.ejs           # legacy index.html → EJS 변환본 (~13MB, 폰트 inlined)
├── public/
│   └── assets/             # legacy 5 HTML 자산 (logo/samhan/stamp/NanumGothic*/Bold)
├── lib/
│   ├── apps-script-shim.js # SpreadsheetApp/DriveApp/UrlFetchApp/CacheService 호환 layer
│   ├── code.js             # legacy Code.js 76 함수 1:1 포팅 (logic 보존)
│   └── slip-bridge.js      # sendOrderFromUi → SamhanLogis slip-service POST
├── scripts/
│   ├── convert-template.mjs # index.html → index.ejs 자동 변환 (재실행 가능)
│   └── qa-capture.mjs       # QA 5장 캡처 (Edge/Playwright)
└── test/
    └── code.test.js         # jest 17 테스트 (utility / 캐시 / 부트스트랩 / slip-bridge / RPC)
```

## 빠른 시작

```bash
cd clients/web/estimate-app
cp .env.example .env       # 필요시 endpoint URL 조정
npm install
npm run dev                # http://localhost:5183
```

Phase 6 backend (PR #76 — M2 partner-auth / M3 dc-config / M4 partner-order / M5 slip-service + product-service google sheets sync) 머지 후 모든 catalog/auth/snapshot RPC 는 실 endpoint 를 호출한다. backend 미가동 환경에서는 RPC 가 5xx/네트워크 오류로 실패한다.

## 변환 룰 (Apps Script → EJS)

| Apps Script | EJS | 비고 |
|---|---|---|
| `<?!= var ?>` | `<%- var %>` | HTML escape 안함 (raw) |
| `<?= var ?>` | `<%= var %>` | HTML escape 됨 |
| `'<?= var ?>'` (JS 문자열 안) | `'<%- var %>'` | Apps Script contextual escaping 흉내 |
| `<? if (...) { ?>` | `<% if (...) { %>` | control flow |
| `<?!= include('logo') ?>` | `public/assets/logo.html` raw 직접 inline | 5 자산 변환 |

자세한 logic 은 `scripts/convert-template.mjs` 참조 — 재실행하면 최신 `migration/source/scripts/estimate/index.html` 로부터 `views/index.ejs` 를 재생성한다.

## google.script.run RPC

legacy index.html 의 11 RPC 호출 사이트 (lines 8726/10084/12879/13218/13942/15049/15091/15228/15506/16434/16717) 는 모두 EJS 안 inline shim (Proxy 기반) 으로 가로채서 `POST /rpc/:fnName` 로 라우팅된다.

```js
google.script.run
  .withSuccessHandler(cb)
  .withFailureHandler(cb)
  .checkUserAuth(email);
// → fetch('/rpc/checkUserAuth', { method: 'POST', body: '{"args":["email"]}' })
```

`routes/rpc.js` 가 `lib/code.js` 의 export 함수를 dispatch.

## 견적 finalize → slip-service 즉시 호출

legacy `sendOrderFromUi` (Code.js line 1762) 가 `e-Count /proxy/ecount/sale` 호출했던 동작을, B2 마이그레이션 결정에 따라 SamhanLogis `slip-service POST /api/v1/slips` 로 1:1 대체.

흐름:
1. 사용자 견적 finalize 클릭
2. legacy SaleList 조립 (logic 보존 — VAT/원단가/창고 결정 등)
3. `lib/slip-bridge.postSlip(order, SaleList)` 호출
4. slip-service 가 즉시 출고전표 생성, `slipNo` 응답
5. 응답을 sendOrderFromUi 결과로 클라이언트에 반환

slip-service 호출 실패 (네트워크/5xx) 는 `{ ok: false, error }` 응답으로 호출자에게 전파되며, UI 가 사용자에게 alert 한다 (silent mock 환원 폐기).

## 외부 의존성 폐기

| 폐기 대상 | 대체 |
|---|---|
| e-Count `http://152.69.228.109:3000/proxy/ecount/*` | slip-service POST + getInventoryTable mock |
| Notion API `https://api.notion.com/v1/*` | SamhanLogis MS DB axios (estimate-snapshot, audit-log, partner-order) |
| Google Drive (logo/gate images) | files-service GET `/api/v1/files/*` |
| Google Sheets (27탭) | product-service GET `/api/v1/products` (M1a) |

## 검증

```bash
npm test                                     # jest 17/17 PASS
node server.js                               # 서버 가동
curl http://localhost:5183/healthz           # {"ok":true,...}
curl http://localhost:5183/                  # 13.5MB EJS render
node scripts/qa-capture.mjs                  # docs/qa/migration-fe-estimate-app-v2/*.png 5장
```

## 주요 파일 라인 수

| 파일 | 라인 |
|---|---|
| lib/apps-script-shim.js | ~330 |
| lib/code.js | ~620 (legacy 2837 → 압축 — pure utility 보존, MS 위임 함수 simplified) |
| lib/slip-bridge.js | ~150 |
| views/index.ejs | 18,614 (legacy index.html 1:1 변환) |
| test/code.test.js | ~180 (17 테스트) |

## 한계 / 모호 항목

- product-service 의 `/api/v1/products` 응답 shape 가 legacy SpreadsheetApp 의 row format 과 다르면 클라이언트 측 데이터 바인딩 코드 (`HM_RAW.map(...)` 등) 가 잘 동작하지 않을 수 있음 → backend 가 legacy `getHomeMulti()` 의 정규화 출력 (object array — model/name/spec/price 등) 을 그대로 emit 하도록 M1a 후속에서 매퍼 추가 필요.
- 관리자 화면 (예: 야적/지방 적용, 단위 처리) 은 client-side 로직만으로 동작 — server 변경 불필요.
- 현재 mock fallback 은 빈 catalog 반환으로 진입 시 화면이 비어보이는 문제 — staging 환경에서 product-service 가 가동되면 자연 해결.
