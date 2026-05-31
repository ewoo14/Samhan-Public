## qa-tester 사이클 5 리뷰 (head `86842c67`)

### 사이클 4 QA 잔존 해소 표

| 항목 | 내용 | 결과 |
|---|---|---|
| C4-N1 dev-report §9.4~9.6 | 사이클 3.5 / 4 양쪽 TM 통합 / 4.5 일괄 fix 서술 3절 추가 | PASS — §9.4~9.6 각 절 서술 정합 확인 |
| C4-N2 orphanRemoval=false | `PartnerOrder.lines` 어노테이션 변경 + Javadoc 명시 | PASS — `orphanRemoval = false` 코드 확인 |
| Playwright T6 신규 | 409 reload 후 재저장 흐름 정적 계약 | PASS — T6 4개 assert 모두 확인 |

### IT 9 + Playwright 6 회귀 표

| # | 검증 결과 |
|---|---|
| IT-1 update_success | PASS |
| IT-2 update_optimistic_lock_conflict_returns_409 | PASS |
| IT-3 testVerifyVersionAllowsFirstUpdateWhenModifiedAtIsNull | PASS |
| IT-4 update_soft_deleted_order_returns_404 | PASS |
| IT-5 update_partner_role_is_forbidden | PASS |
| IT-6 update_negative_quantity_returns_422 | PASS |
| IT-7 update_master_role_can_use_order_number_path | PASS |
| IT-8 testConcurrentUpdateRejectsStaleVersion | PASS |
| IT-9 testReplaceLinesSoftDeletesOldLines | PASS (orphanRemoval=false 변경 후 `is_deleted=TRUE` 1건 / active 2건 SQL 검증 회귀 없음 추론) |
| PW T1~T5 | PASS (사이클 4.5 commit stat 기준 회귀 없음) |
| PW T6 | PASS — setConflictMessage(null), setReloadSuccessMessage, handleConflictReload refetch(), syncFormFromData(result.data) 4개 assert 확인 |

### dev-report §9.4~9.6 서술 정합

| 절 | 기술 내용 | 정합 |
|---|---|---|
| §9.4 | 사이클 3.5 종합 fix 5건 | 정합 |
| §9.5 | 사이클 4 양쪽 TM 통합 결과 — blocker 0, Nit 8건 목록 | 정합 |
| §9.6 | 사이클 4.5 일괄 fix — orphanRemoval=false, currentModifiedAt fallback, handleConflictReload deps 축소, line key 안정성, design-system CSS 3건 | 정합 |

### 사이클 5 신규 발견

신규 blocker 없음. 아래 Nit 1건 기록.

- **C5-Nit-1 (Nit)**: `testReplaceLinesSoftDeletesOldLines`에서 `lineRepository.findAllByPartnerOrder_Id`가 active line 2건만 반환하는지 검증하지만, `orphanRemoval=false` 이후 JPA 컬렉션 `getLines()` 필터(`deletedAt == null`)와 repository 필터(`@SQLRestriction`)가 동일 조건으로 중복 검증되는 구조. 기능상 결함 아님. 둘 중 한 레이어 통일 고려 권고.

PNG 4장 UUID 미노출 재확인: 4장 전부 이상 없음.

### 종합

APPROVE. blocker 0건, 사이클 4 QA 잔존 해소 완료, IT 9건 회귀 없음, Playwright 6건 정합. Nit 1건은 다음 슬라이스 권고.

**qa-tester agent — 2026-05-17**
