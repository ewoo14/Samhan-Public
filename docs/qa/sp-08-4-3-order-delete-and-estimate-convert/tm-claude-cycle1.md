## Claude 5-agent 사이클 1 통합 리뷰 (head `97afca70`)

> tech-manager agent 가 BE / FE / Designer / QA (후공정) / DevOps 5 agent 결과 종합. 총 결함 **33건** (P0 3 / P1 13 / P2 10 / Nit 7 / Info 2).

### 결함 종합 표 (P0 → P1 → P2 → Nit, 33건)

| # | 출처 | 우선순위 | 위치 | 내용 | 처리 권고 |
|---|---|---|---|---|---|
| 1 | Designer | **P0** | docs/qa PNG 01/02/03/04 | 4장 모두 한글 인코딩 깨짐 (`二쉼ц????젤 ?뺐씰`) — Pretendard 미로드 + locale 미설정 | Playwright `use.locale: 'ko-KR'` + font preload + 재캡처 |
| 2 | Designer | **P0** | PNG 02 | 실제 UI 가 아닌 Playwright 디버그 패널 (DELETE 204 raw) | `/sales/partner-orders` 목록 redirect 화면 캡처 |
| 3 | Designer | **P0** | PNG 03/04 | raw API JSON (`source_estimate_id`, 409) — desktop UI 0건 + PNG 03 내용 자체 오류 | 견적→주문 전환 성공/오류 UI 화면 캡처 |
| 4 | BE | P1 | PartnerOrderDeleteService L49 | `markDeleted(DELETE_ACTOR)` hard-coded — audit log actorName 과 불일치 | `markDeleted(actorName)` caller 전달 |
| 5 | BE/QA | P1 | FromEstimateService L40–48 | `findBySourceEstimateId` 동일 path 2중 체크 | L47 dead code 제거 |
| 6 | BE | P1 | FromEstimateService L83–89 | `nextOrderNo()` race condition | DB sequence 또는 `SELECT FOR UPDATE SKIP LOCKED` |
| 7 | BE | P1 | DeleteIT L57–69 | `EstimateClient` `@MockBean` 누락 | `@MockBean EstimateClient` 추가 |
| 8 | FE/Designer | P1 | SalesPartnerOrderDetailPage L209 | 삭제 버튼 `variant="secondary"` | `variant="danger"` |
| 9 | FE/Designer | P1 | SalesPartnerOrderDetailPage L524–534 | Modal footer 확인 버튼 `variant="primary"` | `variant="danger"` |
| 10 | FE | P1 | mock.ts L3295–3296 | DELETE mock 404/422 시나리오 누락 | `mockDelete404` / `mockDelete422` query param |
| 11 | FE | P1 | SalesPartnerOrderDetailPage L541 | `orderNumber ?? ''` → "주문서 를…" | `?? '조회 중'` fallback |
| 12 | Designer | P1 | L540–542 | 받침 조사 오류 + 주문번호 강조 부재 | `<strong>{orderNumber}</strong>을(를)` |
| 13 | QA | P1 | PNG 03 | 내용이 success 가 아닌 409 Conflict | success 201 재캡처 |
| 14 | QA | P1 | DeleteIT | CANCELED 422 IT case 누락 | D5b case 추가 |
| 15 | BE | P2 | FromEstimateService L51–58 | `idempotency_key` full unique 우려 | partial unique 또는 timestamp suffix (Codex 평가 필요) |
| 16 | BE | P2 | FromEstimateService L70 | `recomputeTotal` 이중 책임 | 주석 또는 메서드 분리 |
| 17 | BE | P2 | FromEstimateIT testFromEstimateSuccess | `source_estimate_id` + audit log 직접 단언 없음 | jdbcTemplate 검증 |
| 18 | FE | P2 | mock.ts | DELETE / audit URL pattern 경합 가능 | regex `$` anchor 강화 |
| 19 | FE | P2 | L345 | audit row key 충돌 가능 | + index 보조 |
| 20 | FE | P2 | L192–218 | 수정/삭제 버튼 블록 중복 | Fragment 통합 |
| 21 | Designer | P2 | PARTNER 시나리오 PNG | 비노출 캡처 누락 | PNG 05 추가 |
| 22 | Designer | P2 | sales.module.css L989–998 | `--color-success-*` ↔ `--state-success-bg` 토큰 혼용 | DS 토큰 통일 |
| 23 | QA | P2 | testFromEstimateAlreadyConvertedReturns409 | 첫 요청 body 미검증 | `$.data.orderNumber.exists()` |
| 24 | QA | P2 | FromEstimateService L40/47 | TOCTOU 이중 체크 (#5 동일) | #5 와 일괄 |
| 25 | BE | Nit | Controller L26 | `X-User-Name` 상수 양쪽 중복 | `HttpHeaderConstants` 추출 |
| 26 | BE | Nit | FromEstimateService L14–15 | import 순서 알파벳 역순 | Checkstyle 정렬 |
| 27 | FE | Nit | L539 | Modal testid 내부 div 부여 | Modal root prop |
| 28 | FE | Nit | mock.ts L3307 | `fieldName` / `field` 키 통일 | 단일 키 |
| 29 | Designer | Nit | L64–65 | 삭제 ↔ "← 목록" 시각 분리 부재 | gap / margin-left auto |
| 30 | QA | Nit | PNG 01 | 한글 깨짐 (#1 통합) | #1 과 일괄 |
| 31 | QA | Nit | testDeleteSuccess | `findById` assertion 약함 | JDBC raw count 또는 제거 |
| 32 | DevOps | Info | V6 partial index | H2 단위 테스트 호환성 확인 권장 | 확인만 |
| 33 | DevOps | Info | reviewDecision | 리뷰어 미배정 | 5-team 리뷰 후 TM 승인 |

### Designer P0 3건 (PNG 재생성 필수)

- **#1**: PNG 4장 한글 mojibake. Playwright headless Chromium Pretendard 미로드 + locale 누락. `use: { locale: 'ko-KR' }` + font preload + `--font-render-hinting=none` 후 재캡처.
- **#2**: PNG 02 는 DELETE 204 raw 응답. 사용자 화면 = 목록 redirect 후. `await page.waitForURL('/sales/partner-orders')` 후 screenshot.
- **#3**: PNG 03/04 raw API JSON. PNG 03 success 가 아닌 409 — 시나리오 자체 오류. 견적→주문 success 201 UI + 409 안내 UI 각각 캡처.

### Cross-cutting P1 묶음

- **BE-2 (#5) ↔ QA-P2-02 (#24)** 동일 결함, L47 dead code 일괄 제거.
- **FE-1/2 (#8/9) ↔ Designer D-04/D-05** — 양쪽 `variant="danger"` 필수.
- **QA-P1-01 (#13) ↔ Designer D-03 (#3)** — PNG 03 내용 오류, Designer P0 재생성 시 일괄 해소.

### 각 agent 종합 판정

| Agent | 판정 |
|---|---|
| BE | 사이클 2 필요 (P1 4 / P2 3 / Nit 2) |
| FE | 사이클 2 필요 (P1 4 / P2 3 / Nit 2) |
| Designer | 사이클 2 필요 (**P0 3** / P1 3 / P2 2 / Nit 1) |
| QA | 사이클 2 필요 (P1 2 / P2 2 / Nit 2) |
| DevOps | APPROVE (Info 2 — 수정 불필요) |

### TM 결정

- **종합: 사이클 1.5 대규모 일괄 fix 필수** — 사용자 정책 N=3 제한 적용. **33건 중 DevOps Info 2건 제외 31건 모두 사이클 1.5 에서 처리 권고** (Codex CLI MCP workspace-write 위임).
- **P0 우선순위**: PNG 4장 재생성 (Playwright locale ko-KR + Pretendard preload + 실제 UI). raw API 응답 캡처 영구 금지.
- **P1 묶음 fix (13건)**: BE actor 통일 + estimateId 2중 제거 + nextOrderNo race + DeleteIT @MockBean / FE variant danger 2건 + mock 404/422 + Modal fallback / Designer 조사 처리 / QA PNG 교체 + CANCELED IT
- **P2/Nit 묶음 fix (17건)**: idempotency_key + recomputeTotal + IT DB 단언 + mock regex + audit key + Fragment + PARTNER PNG + 토큰 통일 + assertion + 헤더 상수 + import + Modal testid + mock 키 + gap + findById
- **사이클 2 목표**: 0 P0 / 0 P1 도달

**tech-manager — 2026-05-17**
