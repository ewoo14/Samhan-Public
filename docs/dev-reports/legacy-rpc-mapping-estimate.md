# legacy estimate index.html — google.script.run RPC 매핑 표 (Phase 6 v4)

본 문서는 `migration/source/scripts/estimate/index.html` (18,614 라인) 의
`google.script.run.<fnName>(args)` 호출을 Samhan Public MS 의 endpoint 로 매핑한 표.

Electron `<webview>` preload (`clients/desktop/src/preload/legacyShim.ts`) 의
`samhanApi.call(fnName, args)` 가 본 표를 사용하여 fetch 라우팅한다.

## RPC site (legacy index.html 의 11 호출 site → 9 distinct fnName)

| line     | fnName                  | args                              | 호출 컨텍스트 |
|----------|-------------------------|------------------------------------|--------------|
| 8726     | `checkUserAuth`         | `(USER_EMAIL)`                    | `startAuth()` — 가입 거래처 인증 게이트 |
| 10084    | `sendOrderFromUi`       | `(orderData)`                     | mobile 진행 모달 — 주문 전송 |
| 12879    | `getGateImages`         | `()`                              | 인증 화면 이미지 로드 (`prepareGateImages`) |
| 13218    | `getNotionHistory`      | `(sDate, eDate)`                  | 과거 발송내역 조회 |
| 13942    | `logFrontEvent`         | `(group, msg, isMob, mgr)`        | front 액션 로그 |
| 15049    | `sendOrderFromUi`       | `(orderData)`                     | desktop 진행 모달 — 주문 전송 (10084 의 PC variant) |
| 15091    | `getCustomerDataAsync`  | `()`                              | 거래처 검색 input mount 시 |
| 15228    | `getCustomerDataAsync`  | `()`                              | 동기화 버튼 — 거래처 캐시 갱신 |
| 15506    | `getInventoryTable`     | `(dateVal, items)`                | 재고 조회 (innerHTML = 응답 HTML) |
| 16434    | `getQuoteHistory`       | `(sDate, eDate)`                  | 견적 저장내역 조회 |
| 16717    | `saveQuoteSnapshot`     | `({ data, summary, image })`      | 현재 견적 저장 |

## 함수명 → Samhan Public MS endpoint 매핑

| fnName                 | method | path                                                                | M-단계 | 응답 변환                                          |
|------------------------|--------|---------------------------------------------------------------------|--------|----------------------------------------------------|
| `checkUserAuth`        | GET    | `/api/v1/auth/me?email={email}`                                     | M2     | `{ authorized, managerName }` 형태로 reshape       |
| `getCustomerDataAsync` | GET    | `/api/v1/partners?withDc=true&size=9999`                            | M2     | Page → `array<Customer>` 평탄화                    |
| `getInventoryTable`    | GET    | `/api/v1/products?usageScope=ESTIMATE&date={...}&items={...}`       | M1a    | array → `<table>` HTML 합성 (legacy `innerHTML=`)  |
| `getNotionHistory`     | GET    | `/api/v1/partner-orders?from={...}&to={...}&size=9999`              | M4     | Page → `array<PartnerOrder>` 평탄화                |
| `logFrontEvent`        | POST   | `/api/v1/audit-logs/front`                                          | 공통   | body: `{ group, message, isMobile, manager }`      |
| `getQuoteHistory`      | GET    | `/api/v1/estimates/snapshots?from={...}&to={...}&size=9999`         | M3     | Page → `array<Snapshot>` 평탄화                    |
| `saveQuoteSnapshot`    | POST   | `/api/v1/estimates/snapshots`                                       | M3     | body: `args[0]` 그대로 (data + summary + image)    |
| `sendOrderFromUi`      | POST   | `/api/v1/estimates/finalize`                                        | M3+M4  | `{ slipNo }` 형태 보장 (string ↔ object 호환)      |
| `getGateImages`        | GET    | `/api/v1/files/gate-images`                                         | files  | array → array (raw)                                |

매핑 표는 `clients/desktop/src/preload/samhanApi.ts` 의 `RPC_MAPPINGS` 와 1:1.

## 외부 호출 폐기

legacy estimate Code.js 의 server-side 외부 호출 — webview client-side 코드 (`index.html` inline script) 에서는 발생 안 함. 그러나 안전망으로 shim 의 매핑 표에 등록되지 않은 임의 fnName 호출은 noop + console.warn 으로 처리.

| 외부 의존    | legacy 위치 (Code.js)                                                   | Samhan Public 대체                              |
|--------------|--------------------------------------------------------------------------|-----------------------------------------------|
| **e-Count**  | `UrlFetchApp.fetch('http://152.69.228.109:3000/proxy/ecount/...')`       | slip-service 자동 출고전표 생성 (M4 EventListener) |
| **Notion**   | `UrlFetchApp.fetch('https://api.notion.com/...')` (9 token)              | Samhan Public MS DB 직접 (M2~M5)                |

## shim 동작 흐름

```
[webview iframe (legacy index.html)]
   |
   | window.google.script.run.checkUserAuth("user@samhan.com")
   v
[preload (legacyShim.ts) — Proxy 가로채]
   |
   | samhanApi.call('checkUserAuth', ['user@samhan.com'])
   v
[samhanApi.ts]
   |
   | mapping = RPC_MAPPINGS['checkUserAuth']
   | fetch(GET 'http://localhost:8080/api/v1/auth/me?email=user@samhan.com',
   |        { Authorization: Bearer <token from auth-store IPC> })
   v
[Samhan Public api-gateway → user-service]
   |
   v
{ data: { authorized: true, managerName: "오병승" } }
   |
   | mapping.fromResponse(data) — reshape
   v
[preload returns to legacy callback]
   |
   | onSuccess({ authorized: true, managerName: "오병승" })
   v
[legacy index.html UI 업데이트]
```

## 매핑 누락 함수 처리

매핑 표에 없는 fnName 호출 (e.g. 신규 함수, e-Count/Notion 외부 호출 잔존) 은:

1. `samhanApi.call(fnName, args)` 가 `console.warn(...)` + `null` 반환
2. `legacyShim.ts` 의 Proxy 가 success handler 를 `null` 결과로 호출
3. legacy callback 이 정상 분기 (실패 핸들러 호출 안 됨) — UI 가 깨지지 않음

신규 함수가 발견되면 본 문서 + `samhanApi.ts` 의 `RPC_MAPPINGS` 에 추가.

## partner-order 와의 차이

`partner-order/index.html` (9,427 라인) 은 본 문서 범위 외. v4 web/order-app sub-team 이 별도 매핑 표 작성 (`docs/dev-reports/legacy-rpc-mapping-partner-order.md`).
