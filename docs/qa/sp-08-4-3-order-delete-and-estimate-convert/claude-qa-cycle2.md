## qa-tester 사이클 2 리뷰 (head `d6364d4b`)

### 사이클 1 QA 잔존 해소 표

| # | 항목 | 해소 | 근거 |
|---|---|---|---|
| QA-P1-01 | PNG 03 success 시나리오 (409 → 201) | 해소 | 201 Created + 견적에서 주문 생성 + 주문번호 + status CONFIRMING + slipPublishStatus NOT_REQUIRED + lines 2건 |
| QA-P1-02 | CANCELED 422 IT case | 해소 | testDeleteCanceledOrderReturns422 L145-162 + JDBC raw count 보조 |
| QA-P2-01 | AlreadyConverted 첫 요청 body 단언 | **미완** | L129 `isCreated()` 단독, orderNumber.exists() 미추가 |
| QA-P2-02 | FromEstimateService L47 dead code | 해소 |
| QA-Nit-01 | PNG 01 한글 깨짐 | 해소 |
| QA-Nit-02 | testDeleteSuccess raw SQL 보완 | 해소 (jdbcTemplate is_deleted=TRUE + deleted_by='영업담당자') |

### IT 11 / Playwright 5 회귀 표

| # | 결과 |
|---|---|
| D1 testDeleteSuccess | PASS (JDBC deleted=1/lines=2/by='영업담당자') |
| D2 testDeleteSoftDeletedAlreadyReturns404 | PASS |
| D3 testDeletePartnerRoleForbidden | PASS |
| D4 testDeleteConfirmedOrderReturns422 | PASS |
| D5 testDeleteAuditLogRecorded | PASS |
| D6 testDeleteCanceledOrderReturns422 (신규) | PASS |
| C1 testFromEstimateSuccess | PASS (source_estimate_id + due_date + slipPublishStatus=NOT_REQUIRED) |
| C2 testFromEstimateNotFoundReturns404 | PASS |
| C3 testFromEstimateAlreadyConvertedReturns409 | PASS |
| C4 testFromEstimatePartnerRoleForbidden | PASS |
| C5 testFromEstimateSuccessRecordsAuditLog (신규) | PASS (field_name='FROM_ESTIMATE' + new_value='견적-2026-0001') |
| Playwright T1~T5 | PASS |

### dev-report 수치 정합

| 항목 | dev-report 기재 | 실제 | 정합 |
|---|---|---|---|
| IT 케이스 수 | "신규 IT 9건" §6 | 11건 (Delete 6 + FromEstimate 5) | **불일치** |
| QA PNG 수 | "4 PNG" §6 | 5장 | **불일치** |
| §4 IT 목록 | DELETE audit 까지 5행 | D6 누락 | 설명 누락 |
| Playwright | 5 case | 5건 | 정합 |

### 사이클 2 신규 발견

| # | 심각도 | 내용 |
|---|---|---|
| QA2-P2-01 | P2 | testFromEstimateAlreadyConverted L129 첫 요청 body 단언 미추가 (사이클 1.5 처리 선언 vs 실제 미완) |
| QA2-P2-02 | P2 | dev-report §6 "신규 IT 9건" → 11, "4 PNG" → 5 2행 미갱신 (continuous_docs_sync 가드 위반) |
| QA2-Nit-01 | Nit | PNG 02 mock 목록 행 — 삭제된 `2026/05/17-1` 잔존 표시. soft delete 후 목록 제외 정책과 시각 불일치 |
| QA2-Nit-02 | Nit | resolveActorName null fallback "system" Javadoc 권장 |

### 종합

사이클 1 6건 중 5건 해소, 1건 (QA-P2-01) 선언 대비 미완. 신규 4건 P2 2건 + Nit 2건 — P1 이하. IT 11건 구조 정합, Playwright 통과, PNG 5장 UUID 비노출 + 한글 정상.

**APPROVE 조건부 — 사이클 2.5 fix 2건 (dev-report §6 수치 + C3 첫 요청 body 단언) 후 최종 승인**

**qa-tester agent — 2026-05-17**
