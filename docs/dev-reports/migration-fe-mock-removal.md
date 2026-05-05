# client mock fallback 일괄 제거

## 배경

Phase 6 backend (PR #76 통합 — M2 partner-auth-service / M3 dc-config-service /
M4 partner-order-service / M5 slip-service + product-service google sheets sync)
가 main 머지되어, frontend client 들이 backend 미가동 가정으로 깔아두었던
`USE_MOCK_FALLBACK` silent 분기를 폐기하고 실 endpoint 호출로 전환한다.

silent fallback (mock 응답을 정상 응답처럼 반환) 은 endpoint 회귀 시점을 가려
잘못된 데이터로 사용자 흐름이 진행되는 위험이 있어, PM 추천 (A 완전 폐기 — silent
fallback 회귀 위험 제거) 를 적용한다.

## 변경 매트릭스 (분류 × 클라이언트)

| 분류 | 의미 | clients/web/order-app | clients/web/estimate-app | clients/desktop | clients/mobile* |
|---|---|---|---|---|---|
| A. 인증 / 등록 / 잠금 (M2) | partner-auth | (mock 부재 — 직접 throw) | `code.js#checkUserAuth` USE_MOCK 분기 제거 | (variant E 보존) | n/a |
| B. DC config / Partner master (M3) | dc-config | (mock 부재) | `code.js#initDcConfigFromNotion` fallbackValue 인자 제거, 호출 실패는 default DC config 환원 (legacy 비즈니스 로직) | (variant E 보존) | n/a |
| C. 주문 drafts / confirm / history (M4) | partner-order | `samhanApi.ts#fetchBootstrap` 빈 객체 silent fallback 제거 (호출 실패 throw) | `code.js#getNotionHistory` `_msGet` fallbackValue 인자 제거 | (variant E 보존) | n/a |
| D. slip 발행 silent fail (M5) | slip-service | (해당 없음 — RPC handler 가 catch 없이 throw) | `slip-bridge.js#postSlip` USE_MOCK MOCK-{ts} silent fallback 제거 (실패는 `{ ok: false, error }` 반환). `code.js#saveQuoteSnapshot/logFrontEvent` mock 응답 인자 제거 (logFrontEvent 만 silent swallow 유지 — 사용자 흐름 차단 회피) | (variant E 보존) | n/a |
| E. 영구 보존 | dev-only / safe fallback | `samhanApi.ts#logFrontEvent` silent .catch (frontend audit 실패 무시), `main.ts#fetchBootstrap.catch` (카탈로그 없이 진입) | jest 테스트의 axios mock (실 endpoint 응답 stub 으로 전환) | `desktop/src/renderer/api/mock.ts` (1603줄, `VITE_MOCK_MODE=1` 빌드 시점 분기, PR #18 자동 캡처용) + `client.ts#isMockMode` 인터셉터 + `session.ts#MOCK_AUTH` | n/a |

(mobile / mobile-staff 는 WebView wrapper 로 자체 mock 부재 — 본 PR 변경 없음.)

## 환경변수 변경

`USE_MOCK_FALLBACK` 환경변수 폐기:

- `clients/web/estimate-app/.env.example` — `USE_MOCK_FALLBACK=true` 라인 삭제 +
  `SLIP_SERVICE_URL` default 8084 → 8086 (M5 PR #76 실 포트), `PARTNER_SERVICE_URL`
  default 8082 → 8089 (M3 dc-config-service 실 포트) 정정
- `clients/web/estimate-app/server.js` — boot log 의 `USE_MOCK_FALLBACK` 출력 →
  `SLIP_SERVICE_URL` 출력으로 교체
- `clients/web/estimate-app/lib/apps-script-shim.js` — `USE_MOCK` 상수 + `_config`
  내 노출 제거
- `clients/web/estimate-app/lib/code.js` — `_msGet/_msPost` 의 fallbackValue 분기
  제거 (실패 시 throw), 모든 caller 의 fallbackValue 인자 제거
- `clients/web/estimate-app/lib/slip-bridge.js` — `USE_MOCK` 상수 + 두 분기 제거

## 회귀 위험 + roll-back

### 회귀 위험

1. **slip-service 5xx → 견적 finalize 실패 사용자 노출**
   기존: silent `MOCK-{ts}` slipNo 반환 → 사용자는 정상 처리로 인지
   변경: `{ ok: false, error: 'HTTP 5xx' }` 반환 → estimate-app 의 sendOrderFromUi
   가 `{ ok: false, error }` 그대로 EJS UI 에 반환, alert 노출
   — 의도된 변경 (silent 통과 차단)

2. **dc-config 미설정 거래처 → default DC config 환원 동작 보존**
   `initDcConfigFromNotion` 의 `_msGet` 호출 실패 (네트워크 / 5xx / 404) 는 try/catch
   로 잡아 default config 환원. 신규 거래처가 default 율로 견적되는 legacy 동작
   보존.

3. **partner-orders/bootstrap 5xx → 빈 카탈로그 진입**
   `samhanApi.fetchBootstrap` 은 throw 전파; `main.ts` 의 `.catch` 가 console.warn
   후 진입 — 카탈로그 없이도 BizGate / 로그인 / mobile-gate 동작 (기존과 동일).

### roll-back

문제 발생 시 본 PR revert 만으로 silent fallback 복원.
복원 후 `USE_MOCK_FALLBACK=true` 환경변수 재설정 필요 (default false 가 폐기됐기
때문이 아니라, 본 PR 이 환경변수 자체를 삭제했으므로 환경 재설정 필요).

## 검증

### 코드 syntax

- `node --check` × 5 file (lib/code.js, lib/slip-bridge.js, lib/apps-script-shim.js,
  server.js, test/code.test.js) → all PASS

### estimate-app jest

```
Test Suites: 1 passed, 1 total
Tests:       17 passed, 17 total
Snapshots:   0 total
Time:        1.6 s
```

`USE_MOCK_FALLBACK=true` env 가정 테스트 → axios mock 으로 전환 (실 endpoint 응답
stub). 모든 17 테스트 통과.

### order-app build

- `npm run typecheck` PASS
- `npm run build` PASS — 60 modules transformed, dist/index.html 351.69 kB

### desktop typecheck

본 PR scope 외 (desktop 코드 변경 없음). origin/main 도 동일 위치 typecheck 가
114 errors 로 사전 존재 (design-system 모듈 미빌드 + sharedness issue) — 본 PR
도입 회귀 아님.

### backend 가용성 (PR #76 머지 후 main 가정)

- product-service:8084 (M1a + google sheets sync)
- partner-auth-service:8091 (M2)
- dc-config-service:8089 (M3)
- partner-order-service:8088 (M4 — bootstrap / drafts / confirm)
- slip-service:8086 (M5 — sync REST 발행 + idempotency)

`.env.example` 의 default port 와 backend `application.yml` 일관 정정.

## 후속 작업

- E 카테고리 (desktop QA 캡처 mock 1603줄) 영구 보존 검토 — `VITE_MOCK_MODE=1`
  빌드 분기로 prod 빌드에는 tree-shake 되므로 회귀 위험 없음. 단, design-system
  storybook fixture 와 중복되는 부분은 후속 슬라이스에서 storybook 으로 통합 가능.
- partner-order-service `/api/v1/partner-orders/bootstrap` 응답 envelope 형태
  (ApiResponse&lt;BootstrapResponse&gt;) 와 order-app `samhanApi#fetchBootstrap`
  의 `data` 추출 로직 — backend response shape 합의 1회 검증 후 안정.
