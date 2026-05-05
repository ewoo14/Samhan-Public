# dev-report — migration-fe-legacy-v2-quote-order

- 작성일: 2026-05-05
- 슬라이스: legacy-v2 quote/order Node.js 포팅 + Docker + 카페24 배포 인프라
- 브랜치: `feature/migration-fe-legacy-v2-quote-order`
- 관련 plan: `docs/migration/phase-legacy-v2/M-LEGACY-V2-deployment.md`

## 1. 배경 (개발책임자 결정 2026-05-05)

SamhanLogis MSA backend 가 정식 운영에 투입되기 전 시간순 1차 스왑 — legacy Apps Script (estimate + partner-order) 의 비즈니스 로직을 한 줄도 바꾸지 않은 채 Node.js + Docker 컨테이너에 그대로 옮겨 카페24 가상서버 (RAM 1G / 203.245.41.148) 에서 즉시 운영한다.

- **품목 마스터** = Google Sheets 직접 (`SRC_SHEET_ID = 1RJqO3jT-...vNQ`)
- **출고전표** = 이카운트 proxy 직접 (`http://152.69.228.109:3000/proxy/ecount/*`)
- **이력 저장** = Notion API 직접 (DC / ORDER / AUTH / SNAPSHOT 등 다중 DB)
- 도메인: `quote.samhan-air.com` + `order.samhan-air.com`
- 배포: 옵션 X1 — samhan 공식 홈페이지 server.js 가 host header 분기 reverse proxy (Nginx 미도입). 두 컨테이너는 `127.0.0.1` 만 노출.

## 2. 변경 매트릭스

| 영역 | 위치 | 비고 |
|---|---|---|
| **estimate-legacy** | `clients/web/estimate-legacy/` | port 5184, 신규 디렉토리 |
| **order-legacy** | `clients/web/order-legacy/` | port 5185, 신규 디렉토리 |
| **Docker compose** | `infrastructure/cafe24/docker-compose.legacy-v2.yml` | mem_limit 256m + 128m, healthcheck 30s |
| **.env.example** | `infrastructure/cafe24/.env.example` + 양 web app | placeholder 만 |
| **카페24 가이드** | `infrastructure/cafe24/README.md` | SSH 배포 절차, certbot SAN, 회귀 |
| **dev-report** | `docs/dev-reports/migration-fe-legacy-v2-quote-order.md` | 본 문서 |

## 3. 신규 라이브러리

### 3.1 lib/google-sheets-client.js
- googleapis SDK 사용 (Service Account JWT 인증)
- `readSheet(spreadsheetId, sheetName)` — legacy `SpreadsheetApp.openById(...).getSheetByName(...).getDataRange().getValues()` 와 동등 결과
- in-memory cache (TTL 기본 5분 = `SHEET_CACHE_TTL_SEC`)
- `clearCache()`, `healthz()`

### 3.2 lib/ecount-client.js
- axios + retry 3회 (1s/3s/9s)
- `getZone / login / sendSale / sendSaleOrder / getInventory / rawProxy`
- 세션 5분 cache + 멱등성 키 (`Idempotency-Key`) → e-Count 중복 발송 방지
- legacy `UrlFetchApp.fetch('http://152.69.228.109:3000/proxy/ecount/*', ...)` 1:1 위임 대상

### 3.3 lib/notion-client.js
- axios (raw HTTP — Notion property shape 보존)
- 8 종 토큰 (`NOTION_TOKEN_DC/ORDER/AUTH/SNAPSHOT/QUOTE/SEND/SHIPPING/LOG`) 별 axios instance
- `createPage / queryDatabase / queryDataSource / retrieveDatabase / rawCall`
- legacy `UrlFetchApp.fetch('https://api.notion.com/v1/...', ...)` 1:1 위임 대상

### 3.4 lib/apps-script-shim.js
- estimate-app v2 (PR #58) 의 동명 모듈 패턴 복제 + 외부 호출 정책만 변경
- estimate-app v2: 외부 (e-Count/Notion) noop+warn (SamhanLogis MS 가 흡수)
- legacy-v2 (본 모듈): 외부 직접 호출 위임 (legacy 100% 보존)
- 추가 호환: `Utilities.computeDigest` (SHA_256), `Utilities.DigestAlgorithm`, `Utilities.Charset`, `Utilities.newBlob`, `MailApp / GmailApp` stub
- `preloadSheets(spreadsheetId, sheetNames[])` — Apps Script 동기 시그니처 보존을 위해 bootstrap 단계에서 모든 탭 prefetch

### 3.5 lib/code.js (자동 생성)
- estimate: 2,837 라인 + preamble + footer = 약 3,026 라인 / 94KB
- order: 3,303 라인 + preamble + footer = 약 3,500+ 라인 / 115KB
- 변환 규칙 (`scripts/build-code.mjs` 참조):
  1. 외부 호출 함수 (estimate 14개 / order 21개) → `async`
  2. `UrlFetchApp.fetch(...)` → `await UrlFetchApp.fetch(...)`
  3. 내부 caller 의 호출 사이트에 `await` 추가
  4. `await` 포함 inline arrow function 들도 `async` 화
  5. `REDACTED_NOTION_*` 토큰 → `process.env.NOTION_TOKEN_*` 치환
  6. 모든 top-level function 을 module.exports 에 export (RPC dispatch 호환)

## 4. 핵심 호환 결정

### 4.1 비동기 처리

Apps Script `UrlFetchApp.fetch(...)` 는 동기 시그니처. Node.js 에서는 Promise 반환이 강제됨.

**해결**: legacy 의 fetch 사용 함수만 async 화 (시그니처는 유지). 내부 caller 도 await 추가. 비-fetch 함수 (54+개) 는 sync 그대로 — Apps Script `SpreadsheetApp.openById(...).getDataRange().getValues()` 는 bootstrap 시점에 prefetch 한 in-memory 데이터를 동기 반환하도록 shim 구성.

### 4.2 시트 사전 prefetch

`bootstrap(userEmail)` 함수가 모든 시트 탭을 병렬 로드 후 EJS render 데이터 (homemulti / singleSets / commercialMulti / ...) 를 생성. cache TTL 5분 → 사용자가 ↻ 새로고침 5분 내 재진입 시 즉시 응답.

### 4.3 google.script.run 클라이언트 shim

`views/index.ejs` 마지막에 inline `<script>` 로 `window.google.script.run` 을 Proxy 로 래핑 → `fetch('/rpc/<fnName>')` POST 로 우회. legacy index.html 의 모든 RPC 호출 사이트는 0줄 변경.

## 5. Docker + 카페24

- 옵션 X1 채택 — samhan server.js 가 vhost reverse proxy (별도 PR)
- 두 컨테이너 `127.0.0.1:5184` / `127.0.0.1:5185` 만 노출 → 외부 직접 접근 차단
- mem_limit 강제: estimate 256m + order 128m = 384m (1G 한도 내 안전)
- healthcheck: `wget -qO- http://localhost:PORT/healthz` 30s 간격, 5s timeout, 3회 retry

## 6. 시크릿 정책

- 본 PR 의 `.env.example` 은 placeholder 만 (`__REPLACE_WITH_*`).
- 실 시크릿 (Service Account JSON / e-Count creds / Notion 토큰 8종) 은 카페24 SSH 직접 입력. git commit 절대 금지.
- legacy Code.js 의 하드코딩 토큰은 모두 `REDACTED_NOTION_*` placeholder 로 사전 치환되어 있으며, build-code.mjs 가 이를 `process.env.NOTION_TOKEN_*` 로 자동 치환.
- GitGuardian Security Checks 통과 의무 — 본 PR 안 시크릿 유출 0.

## 7. 검증 (로컬)

| 항목 | 결과 |
|---|---|
| estimate-legacy `npm install` | success (117 packages) |
| estimate-legacy `node --check lib/code.js` | success (구문 OK) |
| estimate-legacy `npm test` (jest) | 3 tests pass |
| estimate-legacy `node server.js` boot + healthz | 503 (SA 미설정 — 예상), boot OK |
| estimate-legacy EJS compile + render | 165ms / 13.4MB output |
| order-legacy `npm install` | success (동일 packages) |
| order-legacy `node --check lib/code.js` | success |
| order-legacy `npm test` (jest) | 3 tests pass |
| order-legacy `node server.js` boot + healthz | 503 (SA 미설정), boot OK |
| order-legacy EJS compile + render | 8ms / 343KB output |

## 8. legacy 1:1 보존 검증

| 검증 | 방법 | 결과 |
|---|---|---|
| Code.js 라인 수 보존 | `wc -l lib/_legacy-code-raw.js` | estimate 2,837 / partner-order 3,303 (legacy 와 동일) |
| index.html → index.ejs DOM 보존 | line count + Apps Script tag count | estimate 18,677 / partner-order 9,463 (legacy + shim 추가만) |
| RPC 함수 export 수 | `Object.keys(require('./lib/code')).filter(k => typeof code[k] === 'function').length` | estimate 70+ / partner-order 80+ (legacy 전체 보존) |
| 비즈니스 로직 변경 0% | build-code.mjs 변환 규칙 | logic 0% (시그니처/식별자/문자열 보존, async/await 추가만) |
| e-Count 응답 schema | 위임 응답 그대로 반환 | `Data.SlipNos[0]` 위치 보존 (ecount-client.rawProxy → axios passthrough) |
| Notion property schema | 위임 응답 그대로 반환 | properties 필드명/타입 보존 (notion-client.rawCall) |

## 9. 회고 가드

| 가드 (memory) | 본 PR 적용 |
|---|---|
| `feedback_korean_commits` | commit + PR 본문 한국어 (prefix/trailer 만 영문) |
| `feedback_role_naming_full` | PM / DevOps / QA / BE / FE 풀네임 |
| `feedback_pm_integration_build_check` | PR 발행 전 양 web app boot + healthz + jest 검증 완료 |
| `feedback_function_documentation` | lib/* 5개 모듈 한국어 Javadoc + 본 dev-report |
| `feedback_pr_qa_screenshots` | (선택) Playwright Edge 캡처 — D3 단계 (TODO) |
| `feedback_uuid_no_user_visibility` | legacy 가 슬립번호/거래처명만 노출 — 보존 |
| `feedback_powershell_utf8_writes` | .env 작성은 카페24 SSH (Linux) — UTF-16 BOM 트랩 무관 |
| `feedback_pr_ci_monitoring` | PR 발행 후 `gh pr checks --watch` |
| `feedback_issue_close_after_pr` | PR 본문에 `연관 Issue: (없음)` 명시 |

## 10. 미결 / 후속

- O1 카페24 SSH 접속 + 사용자 N3-2 (Service Account JSON) / N4 (e-Count creds) / N5 (Notion 토큰 4종 회전) 답변 후 D4 단계 (배포) 진입.
- O2 e-Count proxy IP whitelist (152.69.228.109 운영자 협의).
- O3 samhan 공식 홈페이지 server.js 정정 PR (옵션 X1) — samhan repo 별도 작업.
- O4 `certbot --expand` SAN 발급 (samhan-air.com + www + quote + order 4 도메인 일괄).
- O5 Playwright QA 캡처 (5장 — estimate 진입/finalize 2장 + order 진입/주문 2장 + healthz 1장) — Docker 환경 또는 실 시크릿 확보 후.
