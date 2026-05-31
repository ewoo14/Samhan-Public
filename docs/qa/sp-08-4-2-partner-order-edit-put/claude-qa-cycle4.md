## qa-tester 사이클 4 리뷰 (head `be54f206`)

### 사이클 3.5 fix 검증 표

| 항목 | 기대 | 실제 | 판정 |
|---|---|---|---|
| `verifyVersion` modifiedAt null 시 createdAt fallback | `order.getModifiedAt() == null ? order.getCreatedAt() : order.getModifiedAt()` | `PartnerOrderUpdateService.java:80` 정확 구현 | PASS |
| `testVerifyVersionAllowsFirstUpdateWhenModifiedAtIsNull` Green 경로 | `clearModifiedAt` 후 `createdAt` 전달 → 200 OK | IT:169-172 확인 | PASS |
| `testVerifyVersionAllowsFirstUpdateWhenModifiedAtIsNull` Red 경로 | `clearModifiedAt` 후 `2020-01-01T00:00:00` 전달 → 409 `PARTNER_ORDER_OPTIMISTIC_LOCK_CONFLICT` | IT:177-181 확인 | PASS |
| `PartnerOrderIdResolver.findByUuid` — `RuntimeException` → `IllegalArgumentException` 축소 | catch 블록 타입 `IllegalArgumentException` | `PartnerOrderIdResolver.java:58` 확인 | PASS |
| `saveAndFlush` 후 중복 `flush()` 제거 | flush 1회만 호출 | `PartnerOrderUpdateService.java:63` — `saveAndFlush` 단독, 별도 flush 없음 | PASS |
| dev-report §6 IT 수치 갱신 | Spring IT 9 | `sp-08-4-2-partner-order-edit-put.md:66` "PASS: 9 tests / 0 failed" | PASS |

### IT 9 case 회귀 표

| # | 메서드명 | 검증 포인트 | 상태 |
|---|---|---|---|
| 1 | `update_success_changes_header_and_lines_and_writes_audit_log` | 헤더+라인 수정 + audit log 납기/요청사항/주문라인 3필드 | 회귀 없음 |
| 2 | `update_optimistic_lock_conflict_returns_409` | stale `updatedAt` → 409 + code 검증 | 회귀 없음 |
| 3 | `testVerifyVersionAllowsFirstUpdateWhenModifiedAtIsNull` | null modifiedAt Green/Red 분기 | 신규 PASS |
| 4 | `update_soft_deleted_order_returns_404` | soft-deleted 주문 → 404 + code 검증 | 회귀 없음 |
| 5 | `update_partner_role_is_forbidden` | PARTNER role → 403 | 회귀 없음 |
| 6 | `update_negative_quantity_returns_422` | 음수 수량 → 422 + code 검증 | 회귀 없음 |
| 7 | `update_master_role_can_use_order_number_path` | MASTER role + 주문번호 path → 200 + line count | 회귀 없음 |
| 8 | `testConcurrentUpdateRejectsStaleVersion` | JPA `@Version` 동시 수정 → `ObjectOptimisticLockingFailureException` | 회귀 없음 |
| 9 | `testReplaceLinesSoftDeletesOldLines` | 기존 라인 soft-delete 1건 + 신규 active 2건 | 회귀 없음 |

@MockBean 격리: DcConfigClient / ProductClient / InventoryClient / SlipServiceClient / PartnerAuthClient / PartnerLookupClient / ProductCatalogLookupClient — 7개 전부 선언 확인.

### dev-report 수치 정합

| 항목 | dev-report 기재 | 코드 실제 |
|---|---|---|
| Spring IT | 9 tests / 0 failed | IT 파일 내 `@Test` 9개 |
| Playwright | 5 passed | spec T1~T5 5개 test 확인 |
| §6 갱신 커밋 | be54f206 commit diff 내 dev-report 4줄 변경 포함 | 일치 |

§9.2 사이클 3.5 추가 서술 없음. dev-report 내용은 §9 Cycle 2.5 범위까지만 서술. §9.3에 사이클 3 진입 사유만 기재. 사이클 3.5 적용 내용은 dev-report에 반영되지 않음 — 사이클 5 전 보완 권고.

### 사이클 4 신규 발견

| 등급 | ID | 위치 | 내용 |
|---|---|---|---|
| Nit | C4-N1 | dev-report §9 | 사이클 3.5 픽스 5건의 서술 섹션 부재. §9.3 이후 §9.4 추가 권고 (기능 동작에 무영향) |
| Nit | C4-N2 | `PartnerOrder.java:102` | `@OneToMany(orphanRemoval = true)` 가 선언 유지 중이며, Javadoc은 "외부 제거 trigger 없음" 으로 정정되었으나 orphanRemoval 자체는 그대로임. 현재는 안전하지만, 향후 `lines.remove()` 호출 시 hard delete 위험 잠재. 제거 또는 주석 강화 권고 |

결함 없음. P1/P2 급 없음.

### 종합

**APPROVE**

사이클 3.5 fix 5건 전부 정합 확인. IT 9 case 모두 회귀 없음. dev-report §6 수치 일치. @MockBean 7개 격리 완전. Playwright T1~T5 spec 코드 이상 없음.

위 2건은 Nit 등급(기능 무영향)으로 차기 슬라이스 또는 docs cleanup PR에서 처리 가능. 사이클 5 불필요.

**qa-tester agent — 2026-05-17**
