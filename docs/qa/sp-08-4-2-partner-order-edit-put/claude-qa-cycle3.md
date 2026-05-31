## qa-tester 사이클 3 리뷰 (head `232c5637`)

### 1. IT 8 case 검증

**testConcurrentUpdateRejectsStaleVersion (L208)**

`copyA.saveAndFlush` 후 `entityManager.clear()` + `copyB.saveAndFlush` 패턴으로 실제 JPA `@Version` race를 재현한다. `assertThatThrownBy` 대상을 `ObjectOptimisticLockingFailureException | StaleObjectStateException` 양쪽으로 열어두어 Hibernate 버전 차이 대응도 포함하였다. `@WithMockUser` 없이 순수 도메인/JPA 레이어만 검증하므로 MockMvc 없이 repository 직접 호출하는 구성이 적절하다. **PASS**.

**testReplaceLinesSoftDeletesOldLines (L232)**

PUT 후 `is_deleted = TRUE AND deleted_at IS NOT NULL` raw SQL 카운트 = 1, active count = 2, `lineRepository.findAllByPartnerOrder_Id` = 2를 3중 검증한다. `PartnerOrder.replaceLines`가 `markDeleted` 호출 경로를 타는지, `recomputeTotal`이 `deletedAt != null` 라인을 합산에서 제외하는지 도메인 코드(L203-211, L162-169)에서 직접 확인하였다. **PASS**.

**기존 6 case 회귀**

- `update_success_changes_header_and_lines_and_writes_audit_log`: audit log 3-tuple 검증 (`납기 / 요청사항 / 주문 라인`) + 2회 연속 PUT에서 revisionNo 증가 패턴. PASS.
- `update_optimistic_lock_conflict_returns_409`: stale timestamp 409 + error code 검증. PASS.
- `update_soft_deleted_order_returns_404`: `@SQLRestriction` 자동 제외 → 404. PASS.
- `update_partner_role_is_forbidden`: PARTNER role → 403. PASS.
- `update_negative_quantity_returns_422`: quantity -1 → 422 + `PARTNER_ORDER_UPDATE_INVALID_LINE`. PASS.
- `update_master_role_can_use_order_number_path`: MASTER + 주문번호 path → 200. PASS.

@MockBean 7개 (DcConfigClient / ProductClient / InventoryClient / SlipServiceClient / PartnerAuthClient / PartnerLookupClient / ProductCatalogLookupClient) 전부 선언. SP-08-4-1 회고 `feedback_it_mockbean_external_clients` 가드 충족.

---

### 2. Playwright T5 정합성 검증

`expect(tsx).toContain('reloadSuccessMessage')` — TSX L56에 선언 확인.
`expect(tsx).toContain('partner-order-edit-reload-success')` — TSX L369 testid 확인.
`expect(tsx).toContain("'조회 중'")` — TSX L124, L160 확인.
`expect(tsx).not.toMatch(/orderNumber \?\? id/)` — `orderNumber ?? id` 패턴 없음 확인 (UUID fallback 완전 제거). **PASS**.

T1~T4 계약 검증 대상(controller / service / dto / errorCode / mock)도 head 기준 파일 존재 확인. 정적 계약 검증 + browser 미실행 의도 명확.

---

### 3. dev-report §9 수치 정합

`docs/dev-reports/sp-08-4-2-partner-order-edit-put.md` §9.1에 사이클 2.5 추가 2 case + T5가 서술되어 있다. 그러나 §6 표 자체는 "6 tests / 0 failed" / "Playwright 4 passed" 원문 그대로 남아 있어 IT 8 / Playwright 5로 수치 갱신이 누락되어 있다. 머지 전 §6 Verification 행 수치 동기화 권장 (**non-blocker**).

---

### 4. QA 스크린샷 UUID 미노출 회귀

| 파일 | 확인 결과 |
|---|---|
| 01-edit-form.png | 거래처 코드 `1234567890`, 품목/모델코드/가격만 노출. UUID 없음. |
| 02-reload.png | 충돌 안내 문구 + `최신 내용 불러오기` 버튼. UUID 없음. |
| 03-audit-timeline.png | actorName(영업담당자/관리자/오병승) + 일시 + 필드명만 노출. UUID 없음. |
| 04-role-guard-partner.png | `주문서 상세` + 거래처명 + 합계 + 라인 건수. UUID 없음. |

4장 전부 UUID 미노출 가드 통과.

---

### 5. SP-08-4-1 회고 5항목 회귀 없음

| 항목 | 확인 |
|---|---|
| outbox FK cleanup | `@BeforeEach` `outboxRepository.deleteAll()` 선행 |
| reflection 최소 | `saveOrder`가 도메인 메서드 중심 |
| DTO null 정책 | T1 에서 `@JsonInclude` 검증 |
| design-system | TSX L11 `import { Button, Input, Modal, Select } from '@samhan/design-system'` |
| 한국어 라벨 | 충돌/이력/필드명 운영 한국어 |

---

### 6. 종합 판정

| 항목 | 결과 |
|---|---|
| IT 8 case (6 기존 + 2 신규) | PASS |
| Playwright 5 case (T1~T5) | PASS |
| @MockBean 7개 외부 client 격리 | PASS |
| 4 PNG UUID 미노출 회귀 | PASS |
| SP-08-4-1 회고 5항목 | PASS |
| dev-report §6 수치 | non-blocker — 8/5로 갱신 권장 |

**결론: blocker 0건. dev-report §6 수치 동기화만 갱신 후 개발책임자 머지 진행 가능합니다.**

**qa-tester agent — 2026-05-17**
