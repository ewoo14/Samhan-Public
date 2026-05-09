# Phase 10 Step 8 — 매뉴얼 안내 미구현 UI 9 슬라이스 통합 (PR #114)

> 브랜치: `feature/integrated-phase-10-step-8-ui-9-slice`
> 통합일: 2026-05-09
> 통합 PR: [#114](https://github.com/ewoo14/SamhanLogis/pull/114)
> 5-team 패턴 + TM 종합 (BE / FE / Designer / QA / DevOps + TM)

---

## 1. 범위

매뉴얼 (`docs/manual/*`) 에서 안내되었으나 실 구현 누락이었던 9 슬라이스를 단일 통합 PR 로 채택. 각 슬라이스 = 별도 도메인 + 별도 우선순위 (P0 4건 / P1 2건 / P2 3건). `feedback_integrated_pr_pattern.md` § 통합 PR 의무 적용 (단편 PR 금지).

| 슬라이스 | 우선순위 | 도메인 | 산출 |
|---|---|---|---|
| 1. 비밀번호 재설정 | P0-2 🔴 | auth-service + desktop | 정책 / 잠금 / 토큰 / history reuse 금지 + LoginPage `PasswordResetDialog` modal STEP1/STEP2 + `/password/change` (`PasswordChangePage`) |
| 2. 세금계산서 | P0-4 🔴 | accounting-service + desktop | DRAFT/ISSUED/CANCELLED + 자동 분개 (110/255/400) + e-Tax 인쇄 |
| 3. 인쇄 5건 | P0-4 🔴 | clients/desktop | 거래명세서 / 출고전표 / 입고전표 / 견적서 / 세금계산서 — A4 / 88mm |
| 4. 관리자 UI | P0-5 🔴 | user/partner/warehouse + desktop admin | 5 페이지 (`/admin/users` `/admin/partners` `/admin/warehouses` `/admin/roles` `/admin/org-chart`) — `admin-users-*` testid prefix |
| 5. arologis 수동 배차 | P1-5 🟠 | arologis-service + desktop | manual create + driverAutoMatch (mock) + 카톡 preview |
| 6. 모바일 사진 | P1-8 🟠 | slip-service + mobile-staff RN Expo | 카메라 권한 / 압축 / EXIF / public token / `attachment-*` testID prefix |
| 7. 견적서 | P2-1 🟠 | slip-service + desktop | DRAFT → SENT → ACCEPTED → CONVERTED + 슬립 자동 변환 |
| 8. 매출 마감 | P2-4 🔴 | accounting-service + desktop | DAILY/MONTHLY 마감 + 분개 차단 + 역마감 (MASTER 만) |
| 9. 재고 실사 | P2-6 🔴 | inventory-service + desktop | PLANNED → IN_PROGRESS → COMPLETED + 차이 자동 분개 (150/919) |

---

## 2. Backend Flyway 변경

### accounting-service (V2 / V3 / V4 신규)
- **V2** — `add_tax_invoice` (TaxInvoice 도메인)
- **V3** — `add_accounting_period` (AccountingPeriod, OPEN ↔ CLOSED, MASTER reverse)
- **V4** — `seed_inventory_audit_accounts` — **TM 신규** — 150 재고자산 + 919 재고감모손실 (한국 일반기업회계기준 표준 코드, V1 130 상품 별개 시드)

### inventory-service (V3 신규)
- **V3** — `add_inventory_audit` (InventoryAudit + InventoryAuditLine + audit_no `AU-YYYYMMDD-NNN` 채번)

### user-service (V3 신규)
- **V3** — `add_role_change_history` (admin role 변경 audit_log)

### slip-service (V?)
- 견적서 도메인 + 사진 첨부 metadata + lock-by-period

---

## 3. TM 종합 fix (PR #114 5-team 리뷰 + CI fail 반영)

### 3-1. accounting V4 seed 신규 (BE Major)

**문제**: inventory `AccountingClient.createAuditAdjustmentJournal` 가 차이 분개 시 `accountCode = 150 (재고자산)` / `919 (재고감모손실)` 사용. 그러나 accounting V1 seed 에는 130 (상품) 만 존재 — leaf 검증 실패로 차이 분개 항상 fail.

**fix**: `services/accounting-service/src/main/resources/db/migration/V4__seed_inventory_audit_accounts.sql` 신규.
- `('150', '재고자산', 'ASSET', '100', TRUE, 1500, ...)`
- `('919', '재고감모손실', 'NON_OPERATING', '900', TRUE, 9190, ...)`

memory `project_korean_accounting.md` (한국 일반기업회계기준 표준 계정과목 코드) 준수.

### 3-2. CI fail (accounting+partner) — MonthEndCloseControllerIT 409 → 201

**증상**: `Status expected:<409> but was:<201>` (line 93). 마감 후 동일 일자 분개 POST 가 guard 통과.

**진단**: `AccountingPeriodGuard` interceptor 가 `CachedBodyRequestWrapper` 의존. 그러나 wrapper 는 `WebMvcConfig.cachedBodyFilter()` `FilterRegistrationBean` 으로 등록되며 — Spring `@AutoConfigureMockMvc` 는 `FilterRegistrationBean` filter 를 자동 등록하지 않음 → guard 의 `readBody` 는 항상 null → 통과.

**fix**: service-layer guard 도입. `JournalService.create` 안에서 `MonthEndCloseService.findClosedPeriodCovering(journalDate)` 직접 호출 + CONFLICT throw. interceptor 는 보조 fail-fast 로 유지 (production 에서는 둘 다 작동).

```java
public JournalDetailResponse create(CreateJournalRequest request) {
    monthEndCloseService.findClosedPeriodCovering(request.journalDate())
            .ifPresent(p -> {
                throw new BusinessException(ErrorCode.CONFLICT,
                        "마감된 회계 기간입니다 — 해당 일자(" + request.journalDate()
                                + ")는 변경할 수 없습니다");
            });
    // ... 기존 로직
}
```

`JournalServiceTest` 도 `@Mock MonthEndCloseService` 추가 + `lenient()` 기본 stub `Optional.empty()`.

### 3-3. CI fail (user+product+inventory+logging) — InventoryAuditControllerIT.auditList_byYear_returnsPage 500

**증상**: `Status expected:<200> but was:<500>` (line 195). list endpoint 가 PostgreSQL `SQLState 42P18 — could not determine data type of parameter $3`.

**진단**: `InventoryAuditRepository.findByFilters` 의 JPQL `(:fromDate IS NULL OR a.auditDate >= :fromDate)` 패턴이 PostgreSQL JDBC 에서 fail. `?` 의 타입을 다른 절에서 추론 못하면 type 추론 실패.

**fix**: boolean flag + non-null sentinel 패턴.

```java
// repository
@Query("""
        SELECT a FROM InventoryAudit a
        WHERE (:hasWarehouse = false OR a.warehouse.id = :warehouseId)
          AND (:hasFromDate = false OR a.auditDate >= :fromDate)
          AND (:hasToDate = false OR a.auditDate <= :toDate)
          AND (:hasStatus = false OR a.status = :status)
        ORDER BY a.auditDate DESC, a.createdAt DESC
        """)

// service
boolean hasWarehouse = warehouseId != null;
boolean hasYear = year != null;
boolean hasStatus = status != null;
int yearValue = hasYear ? year.intValue() : 1970;
LocalDate fromSentinel = LocalDate.of(yearValue, 1, 1);
// 모든 파라미터에 non-null sentinel 부여 → JDBC 타입 추론 안전
```

### 3-4. testid 명명 정합 (QA scenarios + DevOps spec → 실 FE 표준)

**기준**: 실 FE 가 표준. QA scenarios 와 DevOps spec 양쪽을 실 FE 와 일치.

| 슬라이스 | 시나리오/spec 의 표기 | 실 FE 표준 |
|---|---|---|
| 1. password reset | (path) `/login/reset/confirm` | `PasswordResetDialog` modal STEP 2 (LoginPage 내) |
| 1. password change | (path) `/admin/profile/password` | `/password/change` (`PasswordChangePage`) |
| 1. password change testid | `profile-password-change-{current,new,submit}` | `password-change-{current,new,submit}` |
| 4. admin users | `users-admin-*` | `admin-users-*` |
| 6. mobile photo | `mobile-photo-*` (web data-testid) | `attachment-*` (RN testID) |
| 8. 매출 마감 | `period-lock-sales-*` | `closing-{new,reverse,list-table}-button` |
| 9. 재고 실사 | `stock-take-*` | `audit-{form,list,detail,start,complete,cancel,line}-*` |

**1.2.6 신규 case** — 본인 변경 흐름 (`/password/change`) 기존 누락 → 추가.
- 슬라이스 1 합계 21 → 22, 9 슬라이스 합계 160 → **161** case.

### 3-5. docs sync (Designer 권고)

- `ROADMAP.md` § Phase 10 — `W10-step-8` row 추가
- `migration/decisions/DECISIONS.md` § D-P10-16 — Flyway V 번호 sequence + 단일 PR 채택 + inventory 차이 분개 코드 결정 추가
- 본 dev-report 신규

---

## 4. 처리하지 않은 항목 (별도 단계 위임)

| 항목 | 처리 시점 | 근거 |
|---|---|---|
| GitGuardian dashboard mark | 사용자 직접 (TM 코멘트만 게시) | memory `feedback_gitguardian_false_positive.md` |
| SmtpEmailAdapter NoOp → 실 SMTP | P0-2 본 PR 은 NotificationStub 충분 | 정식 fix 별도 단계 |
| 인쇄 5건 2~5차 디자인 iteration | 사용자 Edge 캡처 검토 후 | memory `feedback_print_design_iteration.md` |

---

## 5. CI 검증 결과 (TM fix 후)

- `./gradlew assemble -x test` → BUILD SUCCESSFUL
- `./gradlew :services:accounting-service:test :services:inventory-service:test :services:user-service:test :services:product-service:test :services:logging-service:test :services:partner-service:test` → BUILD SUCCESSFUL (단위 PASS, IT는 Docker 미가용 환경에서 SKIP, CI Linux runner 가 실행)
- 본 commit push 후 CI 재실행 자동 → 두 fail job 모두 GREEN 기대

---

## 6. 후속 (Phase 11 진입 대비)

- 인쇄 5건 — 사용자 Edge 캡처 검토 → 2~5차 iteration (별도 단계)
- SmtpEmailAdapter — Phase 11 cutover 시점 AWS SES 활성
- GitGuardian dev-only 비밀번호 — 사용자가 dashboard mark
- `qa/playwright/tests/nine-slice/*.spec.ts` (slice 별 spec, 161 case 자동화) — 후속 PR
- Detox lane (slice 6 모바일 사진) — 후속 PR

---

**작성**: TM (Tech Manager)
**메모리 가드**: `feedback_continuous_docs_sync.md` § 매 작업 PR 에 README + ROADMAP + DECISIONS + dev-report 갱신 의무
