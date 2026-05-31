## qa-tester 사이클 1 리뷰 (head `97afca70`)

### IT 9 case 시나리오 정합 표

| # | 클래스 | 케이스 | HTTP | ErrorCode | 판정 |
|---|---|---|---|---|---|
| D1 | DeleteIT | testDeleteSuccess | 204 | — | 통과 |
| D2 | DeleteIT | testDeleteSoftDeletedAlreadyReturns404 | 404 | PARTNER_ORDER_NOT_FOUND | 통과 |
| D3 | DeleteIT | testDeletePartnerRoleForbidden | 403 | — | 통과 |
| D4 | DeleteIT | testDeleteConfirmedOrderReturns422 | 422 | PARTNER_ORDER_DELETE_FORBIDDEN_STATUS | 통과 |
| D5 | DeleteIT | testDeleteAuditLogRecorded | 204 | tuple("DELETE","soft-deleted") | 통과 |
| C1 | FromEstimateIT | testFromEstimateSuccess | 201 | lines/partnerCode/status | 통과 |
| C2 | FromEstimateIT | testFromEstimateNotFoundReturns404 | 404 | FROM_ESTIMATE_NOT_FOUND | 통과 |
| C3 | FromEstimateIT | testFromEstimateAlreadyConvertedReturns409 | 409 | FROM_ESTIMATE_ALREADY_CONVERTED | 통과 |
| C4 | FromEstimateIT | testFromEstimatePartnerRoleForbidden | 403 | — | 통과 |

### Playwright 5 case 정합

T1~T5 모두 정적 계약 검증 통과 (controller / service / V6 / domain method / ErrorCode / desktop button / audit log mock).

### PNG 4장 UUID 비노출

| 파일 | UUID 노출 | 이상 |
|---|---|---|
| 01-delete-confirm-dialog | 없음 | **한글 깨짐** |
| 02-delete-success | 없음 | 정상 (raw API 표시) |
| 03-from-estimate-success | 없음 | **P1-01: 내용이 409 — success(201) 시나리오 아님** |
| 04-from-estimate-already-converted | 없음 | 정상 |

### SP-08-4-1/2 회고 회귀 없음

| 항목 | 확인 |
|---|---|
| outbox + audit + lines @BeforeEach 선삭제 | 두 IT 적용 |
| @MockBean 외부 client 격리 | DeleteIT 7건, FromEstimateIT 8건 (EstimateClient 포함) |
| orphanRemoval=false | PartnerOrder.java L106 확인 |
| UUID 비공개 | 응답 DTO + testid 주문번호 기반 |

### 사이클 1 신규 발견

| # | 심각도 | 내용 |
|---|---|---|
| QA-P1-01 | P1 | PNG 03 내용 오류 — `03-from-estimate-success.png` 실제 내용이 409 Conflict (`PARTNER_ORDER_FROM_ESTIMATE_ALREADY_CONVERTED`). success 201 시나리오 (주문번호, CONFIRMING status, lines 2건) 캡처 없음. `feedback_pr_qa_screenshots` 가드 위반 |
| QA-P1-02 | P1 | CANCELED 422 IT case 누락 — `DELETABLE_STATUSES = DRAFT / CONFIRMING` 만 허용. CANCELED 도 422 반환하나 IT case 없음. CONFIRMED → 422(D4) 와 대칭 CANCELED D5b 추가 필요 |
| QA-P2-01 | P2 | `testFromEstimateAlreadyConvertedReturns409` 첫 번째 요청 assertion 부족 — line 117 `isCreated()` 만, body 미검증. `$.data.orderNumber.exists()` 추가 |
| QA-P2-02 | P2 | `PartnerOrderFromEstimateService` TOCTOU 이중 체크 — line 40/47 모두 `findBySourceEstimateId(estimateId)` 동일 path 2번. V6 partial unique index 가 race 방어하므로 L47 dead code. BE BE-2 결함과 동일 — 제거 권장 |
| QA-Nit-01 | Nit | PNG 01 한글 깨짐 — PowerShell UTF-16 렌더 아티팩트. `-Encoding utf8` 또는 HTML charset 명시 |
| QA-Nit-02 | Nit | `testDeleteSuccess` `findById` assertion 약함 — `@SQLRestriction("is_deleted = false")` 로 hard-delete 와 무관하게 empty. JDBC raw count assertion 보완 또는 제거 |

### 종합

**사이클 2 필요** — P1 2건 (PNG 03 내용 오류, CANCELED IT 누락) + P2 2건 + Nit 2건 사이클 1.5 일괄 fix.

**qa-tester agent — 2026-05-17**
