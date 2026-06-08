# arologis-desktop 백오피스 확장 — 전체 spec (인사·회계·권한관리 UI)

> 2026-06-08 개발책임자 방향. arologis-desktop = **아로로지스 행정직원 전용** 백오피스. Samhan Public Desktop 축소판. 범위 확정(개발책임자): **인사 = 직원·부서 기본 / 회계 = 간이 수입/지출 / 시작 = 전체 spec 먼저**. [[arologis-desktop-backoffice]] · [[arologis-independent]].

## 0. 현황(정찰 2026-06-08) — 이미 보유

| 항목 | 상태 |
|---|---|
| 자체 마스터 계정·인증 | ✅ `AdminUser`+`RefreshToken`, V7/V8/V9(`seed_arologis_master`), `POST /auth/admin/login`(loginId+pw) |
| 권한 기반 | ✅ `DynamicPermissionClient`+`ArologisAdminPermissionGuard`, page-code `arologis.*`(예 `arologis.dispatch.admin/ops`), 롤 `AROLOGIS_MASTER/AROLOGIS_MANAGER`(+상위 MASTER/MANAGER) |
| 로그인 모델 | ✅ **arologis-desktop=행정직원 일반인증(loginId+pw)** / arologis-mobile(기사)=휴대폰 passwordless |
| 도메인 | Dispatch/Driver/Vehicle/Signature/AuditLog/EditRequest |
| FE | arologis-desktop = `@samhan/design-system`(DataGrid/Modal/Badge/Tabs) + routes(dispatches/drivers/login) |
| Flyway | 최신 V13 → 신규 **V14+** |

**핵심**: "자체 마스터 계정"은 이미 구축됨. 신규 = **인사·회계·권한관리 UI** 3종.

## 1. 패턴 출처 (정찰 차용)

- **인사** ← `user-service` Employee/Department/RoleChangeHistory (별도 hr-service 아님). FE 선례 = desktop `routes/admin/{Departments,Users,Roles}Page.tsx`(DataTable+Modal+필터+이력 모달). [[role-naming-full]] 풀네임 롤 표기.
- **회계(간이)** ← accounting-service `CashReceipt/CashDisbursement` 단순 원형. 복식부기(Journal/JournalLine/차대검증/역분개/마감/세금계산서) **전부 제거**. 금액 `NUMERIC(15,2)` BigDecimal, `LocalDate`, soft-delete. 한국 계정과목(100/200/400/800) seed 는 **간이 4그룹 ~20코드만** 차용.
- **권한** ← shared `@RequirePermission(page,action)` + 7 action(VIEW/CREATE/UPDATE/DELETE/RESTORE/DOWNLOAD/PRINT). DynamicPermission account_page_permissions 모델.

## 2. Phase 분해 (순차 — 각 phase = 독립 PR, 풀사이클)

### Phase A — 권한 관리 UI (enabler, 선행)
**이유**: 인사·회계 화면을 게이트할 page-code 를 관리·할당할 화면이 먼저 있어야 함.
- **BE**: 신규 권한 없음(기존 DynamicPermission 재사용). `ArologisAdminController` 에 권한 매트릭스 조회/할당 endpoint 보강 — `GET arologis.admin.permissions`(롤/계정별 page×action 매트릭스), `PUT`(할당). page-code `arologis.admin.permissions`.
- **FE**: arologis-desktop 신규 라우트 `routes/admin/PermissionsPage.tsx` — 롤×page-code×action 매트릭스(DataGrid + 토글), AROLOGIS_MASTER 한정.
- **seed**: 신규 page-code(`arologis.hr.*`, `arologis.accounting.*`) 를 권한 카탈로그에 등재(다음 phase 대비).
- **QA**: 매트릭스 조회/토글 실 HTTP, 권한 없는 롤 403.

### Phase B — 인사(HR): 직원·부서 기본
- **entity**(신규, arologis-service):
  - `ArologisDepartment` { id, code(uniq active), name, displayOrder } — BaseEntity 7 audit + soft-delete.
  - `ArologisEmployee` { id, adminUserId(→AdminUser, nullable), loginId, fullName, position(직급 문자열), department(@ManyToOne), hireDate, terminationDate(null=현직), email, phone } — soft-delete, `terminationDate IS NULL`=현직.
  - (옵션) `ArologisRoleChangeHistory` — 롤 변경 추적. **MVP 제외 가능**(개발책임자 확인).
- **Flyway V14** `add_arologis_hr` — departments/employees 테이블 + partial unique(code/login_id active) + dept FK + 부서 seed(행정/배차/회계 등 소수).
- **endpoint**: `ArologisHrController` page-code `arologis.hr.employees`(CRUD), `arologis.hr.departments`(VIEW/관리). 퇴직=terminate(terminationDate+soft-delete).
- **권한 연결**: ArologisEmployee.adminUserId ↔ AdminUser(로그인 계정) 연결 — 직원이 권한 주체. role 은 AdminUser 측 유지(중복 금지).
- **FE**: `routes/admin/{EmployeesPage,DepartmentsPage}.tsx` — DataGrid + 등록/수정/퇴직 Modal + 부서/재직 필터. UUID 비노출([[uuid-no-user-visibility]]) — loginId/부서명만.
- **QA**: Docker 실서버 직원 등록→부서배속→퇴직 실화면 + 실 HTTP 권한 회귀.

### Phase C — 간이 회계: 수입/지출
- **entity**(신규):
  - `ArologisSimpleAccount` { code(PK), name, type(ASSET/LIABILITY/INCOME/EXPENSE), displayOrder, active } — 간이 계정과목.
  - `ArologisCashTxn` { id, txnDate(LocalDate), type(INCOME/EXPENSE), partnerName, amount(NUMERIC 15,2), accountCode(→SimpleAccount), description } — soft-delete. **분개/차대 없음**(단식).
- **Flyway V15** `add_arologis_accounting` — simple_account/cash_txn 테이블 + 간이 계정과목 seed(현금/보통예금/매출/급여/복리후생/임차료/통신비 등 ~15코드).
- **endpoint**: `ArologisAccountingController` page-code `arologis.accounting.cashbook`(CRUD) + `arologis.accounting.summary`(VIEW, 월별 수입/지출/잔액 집계).
- **FE**: `routes/admin/CashbookPage.tsx` — 거래 입력(수입/지출 토글+계정 select+금액+거래처+일자) + 기간 필터 목록 + 월별 집계 카드. 금액 천단위 콤마.
- **QA**: Docker 실서버 수입·지출 입력→월집계 실화면, 권한 회귀.

## 3. 공통 가드 (전 phase)
- BaseEntity 7 audit + Soft Delete only. 한국어 Javadoc + commit/PR. springdoc.
- @RequirePermission 실 HTTP 회귀([[enforcement-real-http-test]]) — 권한 경로 mock false-green 금지.
- 게이트웨이 라우트(arologis-service 기존 패턴) 신규 page 경로 등록.
- Docker 실서버 실 QA + 실화면 스크린샷([[qa-docker-real-test]],[[no-fake-data-ever]]). dual review N=2([[dual-5agent-review]],[[cycle-n2-mandatory]]). 조기 PR([[open-pr-early]]). Codex 구현([[codex-implements-claude-reviews]]).
- arologis-desktop FE = design-system DataGrid/Modal 일관, data-testid 표준, mock 핸들러([[inprocess-mock-principles]]) + Playwright.

## 4. 설계 결정 (개발책임자 확정 2026-06-08)
1. ✅ **직원↔계정 = 1:1 통합**: ArologisEmployee 생성 시 AdminUser(로그인 계정) 자동 provisioning + 연결(adminUserId NOT NULL). 행정직원=계정 일치. 퇴직 시 양쪽 비활성.
2. ✅ **롤 = page-code 권한만**: 기존 `AROLOGIS_MASTER/AROLOGIS_MANAGER` 2롤 유지. 인사/회계 접근은 신규 page-code(`arologis.hr.*`/`arologis.accounting.*`) 권한으로만 통제(롤 세분화 안 함).
3. ✅ **RoleChangeHistory = MVP 포함**: Phase B 에 `ArologisRoleChangeHistory`(previousRole→newRole·reason·changedBy) + 이력 모달 포함.

6. ✅ **권한 grant 저장소 = 중앙 auth-service 공유 유지** (2026-06-08 개발책임자): arologis 는 이미 권한 체크를 auth-service(`DynamicPermissionClient → /permissions/check`)에 위임 중 — 자체 독립은 계정(AdminUser)뿐. 신규 page-code(`arologis.hr.*`/`arologis.accounting.*`) grant 는 **auth-service `role_page_permissions` 에 시드**(V10 `arologis.admin` 선례), 롤 `AROLOGIS_MASTER/AROLOGIS_MANAGER` + page-code `arologis.*` **네임스페이스 분리**. 향후 "auth-service 없이 단독 운영" 필요 시 `arologis.*` 행만 arologis-service 자체 store 로 이관(문 열어둠). **권한 관리 UI(Phase A)는 auth-service `PermissionAdminController` 위에서 arologis page-code 관리.**

### 구현 순서 (개발책임자 확정 2026-06-08): **B(인사) → C(회계) → A(권한 UI)**
- Phase A 가 권한저장소 결정 의존적이었으나 해소. 단 권한 매트릭스 UI 는 B/C page-code 가 존재한 뒤가 유의미 + 자족적 B 가 foundation → **B 선착수**. (각 phase 신규 page-code 는 auth-service grant 시드로 게이트, 관리 UI 없이 동작.)

### 잔여 seed 데이터 (구현 중 개발책임자 제공 — 차단 아님)
4. **회계 간이 계정과목 목록**(~15코드 실 운영 항목) — 표준 축약 seed 로 시작 후 조정 가능.
5. **부서 seed 목록**(아로로지스 실 부서명) — 임시 seed 후 조정 가능.

## 5. 권장 진행
Phase A(권한 UI) → B(인사) → C(회계) 순차, 각 독립 PR. 본 spec 승인 후 Phase A spec 상세화 + Codex 디스패치.
