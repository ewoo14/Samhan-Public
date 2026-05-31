## qa-tester 사이클 1 리뷰 (head `1248cdc1`)

### IT 8 case 정합

`SlipUpdateIT` 8 method (DisplayName `U1:` 8개) 전체 확인. `AbstractPostgresIT` 싱글턴 상속.

- `testUpdateSuccess` — 200 + `$.data.slipType`, `$.data.partnerName`, `$.data.lines[0].quantity/unitPrice`, `$.data.updatedAt`. **결함 D-01**: PR 본문 "lockVersion increment" 요구사항 대비 `$.data.version` 단언 누락.
- `testUpdateOptimisticLockConflict` — stale ISO `2026-01-01T00:00:00` 고정 + 409 + `SLIP_OPTIMISTIC_LOCK_CONFLICT`
- `testUpdateSoftDeletedReturns404` — `markDeleted` + flush 후 404
- 403 × 3 (`testUpdateForbiddenForInventory/Sales/Accountant`) — 공용 헬퍼 처리
- `testUpdateInvalidLineReturns422` — quantity=0 + `SLIP_UPDATE_INVALID_LINE`
- `testUpdateAuditLogRecorded` — `findBySlipIdOrderByRevisionNoDescChangedAtDesc` + `allMatch(revisionNo == 1)` + `anyMatch("SLIP_EDIT")` + `anyMatch("창고담당자")` 3중. `@BeforeEach deleteAll()` 격리.
- `testUpdateNonInboundForbidden` — OUTBOUND 슬립 PUT 시도 403

### Playwright 5 case

T1 BE 계약 정적 — controller `@PutMapping("/{id}")`, `hasAnyRole('WAREHOUSE','MANAGER','MASTER')`, `verifyVersion`, INBOUND guard, ErrorCode 2종, `"SLIP_EDIT"`, `orphanRemoval=false` + `markDeleted`. 소스 일치.

T2 FE 계약 — `PURCHASE_EDIT_ROLES`, `mode === 'INBOUND'`, data-testid 3종, `Modal/Input`, `updatePurchaseSlip`, PUT envelope.

T3 409 — 한국어 문구 `'다른 사용자가 먼저 수정했습니다. 최신 내용 불러오기 후 다시 저장해 주세요.'` + `handlePurchaseConflictReload` → `refetchDetail` + `syncPurchaseFormFromData(result.data)`.

T4 audit — `"SLIP_EDIT"`, `/audit-logs`, `slipAuditLogs`, `slip-detail-revision-count`, `actorName`. 부정 단언 `not.toMatch(/actorId.*/)` — UUID 가드 검증.

T5 권한 — controller + IT method명 4종.

### PNG 4장

01 form: 구매번호 `2026/05/18-1`, `삼한공조`, `123-45-67890`, 수량/단가/합계 — UUID 미노출, 한국어
02 conflict: 409 배지 + reload 버튼 + 비공개 주석
03 audit: `SLIP_EDIT` 배지 + actorName + `2026-05-18 14:24` + 변경 필드 — UUID 비공개 가드
04 guard: 403 + 허용 역할 `WAREHOUSE/MANAGER/MASTER` + 버튼 미렌더 주석

### dev-report

`§6 Verification` "9 tests / 9 failed (RED)" → "9 tests / 0 failed (PASS)" 기재. 실 method 수 **8건**. **결함 D-02**: 수치 불일치 정정.

### 사이클 1 신규 발견

**D-01 [MEDIUM]** `testUpdateSuccess` `$.data.version` 증가 단언 누락. `@Version` saveAndFlush 후 +1 보장. `.andExpect(jsonPath("$.data.version", is(1)))` 추가.

**D-02 [LOW]** dev-report §6 IT count 8 vs 9 정정.

**D-03 [LOW]** `testUpdateOptimisticLockConflict` stale timestamp 하드코딩 주석 보강 (minor).

SP-08-4-1/2/3/4 + SP-08-5-1 회고 (orphanRemoval=false, createdAt fallback, HttpHeaderConstants, @JsonInclude NON_NULL) 모두 반영, 회귀 없음.

### 종합

8 IT 구조, MockBean 6종, Playwright 5 정적, PNG 4 UUID 비공개·한국어, dev-report 구조 정상. D-01 + D-02 사이클 1c 수정 권장.

**qa-tester agent — 2026-05-18**
