---
name: arologis-desktop-backoffice
description: arologis-desktop = 아로로지스 행정직원 전용 백오피스 — 자체 마스터/권한/인사/회계 (Samhan Public 축소판)
metadata:
  type: project
---

2026-06-08 개발책임자 방향: **arologis-desktop = 아로로지스 행정직원 전용** 백오피스. 자체 마스터 계정 + 인사시스템 + 권한설정 + 회계시스템 필요. **Samhan Public Desktop 보다 규모는 작지만 동일 부류 서브시스템**. [[arologis-independent]](독립 운영 단위·자체 auth·monorepo 유지) 연장.

**현재 보유 (2026-06-08 정찰)**:
- ✅ **자체 마스터 계정·인증 이미 구축**: `AdminUser` entity + `RefreshToken`, V7/V8/**V9 seed_arologis_master**, `POST /auth/admin/login`(loginId+password, arologis-desktop) vs `POST /auth/driver/login`(phoneNumber passwordless, arologis-mobile).
- ✅ 권한 기반: `DynamicPermissionClient` + `ArologisAdminPermissionGuard` (단 관리 UI 는 미확인).
- 도메인: Dispatch/Driver/Vehicle/VehicleStop/DriverLocation/Signature/RegionDispatchClassification/ArologisAuditLog/ArologisEditRequest. arologis-desktop 화면 = dispatches/drivers/login.

**로그인 모델 (개발책임자 정정 2026-06-08)**:
- **arologis-desktop = 행정직원, 일반 인증(loginId+password)** — 휴대폰 로그인 아님.
- **arologis-mobile(기사용)만 휴대폰번호 passwordless**.

**신규 필요 (미구축)**:
- 🔴 **인사(HR) 시스템** — 행정직원 employee registry/부서 등 (Samhan Public hr-service 패턴 축소).
- 🔴 **회계 시스템** — arologis 독립 단위 회계 (Samhan Public accounting-service 패턴, 규모 축소).
- 🟠 **권한 관리 UI** — 기존 DynamicPermission 위 관리/매트릭스 화면.

**확정 범위 (개발책임자 2026-06-08)**: 인사=직원·부서 기본 / 회계=간이 수입·지출 / 순서 **B(인사)→C(간이회계)→A(권한UI)** / 권한 grant=중앙 auth-service 공유(arologis.* 네임스페이스, role_page_permissions 시드) / 직원↔계정 1:1 통합 / 롤=기존 2롤 page-code 통제 / RoleChangeHistory 포함. spec=`docs/superpowers/specs/2026-06-08-arologis-desktop-backoffice-spec.md`.

**✅ Phase B 인사 BE 완료 (PR #426 머지 `3f3cf464`, 2026-06-08)**: ArologisEmployee(↔AdminUser 1:1 provisioning)/ArologisDepartment/ArologisRoleChangeHistory + ArologisHrController(page-code arologis.hr.*) + V14 + auth V50(role_page_permissions). 권한상승/강등 가드 = **actor persisted role DB 조회**(X-User-Role 미신뢰). dev-report=`docs/dev-reports/arologis-hr-phase-b.md`, DECISIONS=D-AROLO-HR-01~04.

**✅ Phase B FE 완료 (PR #427 머지 `fdedf4d6`, 2026-06-08)**: arologis-desktop EmployeesPage/DepartmentsPage(DataGrid+Modal) + api/arologisHr.ts + 라우트/네비. roleLabel 한국어, FE 권한게이팅(canManageHr/canGrantMaster, AROLOGIS_MASTER 옵션 비마스터 숨김). **풀스택 Docker 실화면 QA 통과**(실 auth+arologis+Postgres+admin 로그인, 직원 provisioning/롤이력/퇴직 실증, 증빙 docs/qa/arologis-hr-phase-b/). ⚠️ 사이클2부터 Codex 사용량 한도 다운(~Jun 11) → dual review/fix Claude 대체.

**✅ Phase C 간이회계 완료 (BE PR #428 `6cf0c14f` / FE PR #429 `09fea061`, 2026-06-08)**: ArologisSimpleAccount(계정과목 14 seed)+ArologisCashTxn(수입/지출 단식, 분개/차대/마감/세금 0) + ArologisAccountingController(arologis.accounting.cashbook/summary) + V15/auth V51 + CashbookPage(집계 카드+거래 DataGrid+입력 Modal). 풀스택 실화면 QA 통과(거래 4건/월집계 -770,000, docs/qa/arologis-accounting-phase-c/). Codex 다운(~Jun 11) → Claude 에이전트 구현+리뷰 대체.

**✅ Phase A 권한 관리 UI 완료 (BE PR #430 `f0a13b42` / FE PR #431 `1a4fd151`, 2026-06-08) ⇒ 백오피스 B·C·A 3축 완결**: auth-service `PermissionInternalController` GET `/role-matrix?pagePrefix=` + PUT `/role-grant`(X-Internal-Token, **도메인 무제한 write = 호출측 스코프 책임**) + V52(arologis.admin.permissions MASTER-only). arologis-service `ArologisPermissionAdminController`(arologis. prefix 스코프 가드 + 중앙 MASTER 거부 + X-User-Id audit). FE `PermissionsPage`(롤×page-code 매트릭스, 희소셀 가상 그리드 신규 grant, 낙관 setQueryData+cancelQueries 롤백, 중앙 MASTER 읽기전용, edit→view 자동). **매트릭스 롤 = 11 중앙롤 전체**(V10/V50/V51 이 모든 롤에 arologis.* grant 시드 → getRoleMatrix 전부 반환, ROLE_LABELS=Samhan Public ADMIN_ROLE_LABEL 정합). **실QA 가 코드리뷰 미검출 4롤 미라벨 적발**(실화면 가치). 풀스택 실화면 QA 통과(HTTP round-trip persist + 보안 2중 403 가드 + 11롤 실화면, `docs/qa/arologis-permission-phase-a/`).

**✅ arologis 6-롤 모델 완료 (PR #432 `8de0fe25`, 2026-06-08)**: 개발책임자 "마스터/매니저/개발자/영업사원/회계사원/배송기사 6롤만". AdminUserRole enum 2→6 + `normalize()` AROLOGIS_ prefix-strip(6롤 전부 중앙코드 일치) + **V16**(auth_admin_user/role_change_history CHECK 제약 6롤 확장) + auth **V53**(무관 5롤 DISPATCH/INVENTORY/PARTNER/STAFF/WAREHOUSE arologis.* grant 제거 + 신규 4롤 재적재). **개발자=인사(HR)·권한관리 제외 전권**(개발책임자 정책=직원생성 권한 전파 차단). enforcement = @RequirePermission(page-code, @PreAuthorize 코드 게이트 없음). FE 매트릭스/HR 드롭다운 6롤. 풀스택 실 QA(6롤 매트릭스 + page-code enforcement 4종 + 실화면, `docs/qa/arologis-6-role-model/`). 🔑 정적 dual review 통과한 **CHECK 제약 회귀를 실 QA(실 Postgres INSERT)가 적발** → V16.

**✅ 표준 계정과목 + 부서 확정 + 활성상태 관리 완료 (PR #433 `dec8997c`, 2026-06-09) ⇒ arologis 백오피스 완결**: 개발책임자 지시로 임시 seed → 실 운영값 확정.
- **부서 3개 확정**: 대표실(EXEC)/행정팀(ADMIN)/회계팀(ACCOUNTING). 배차/운영 soft-delete (V17). ⚠️ ADMIN 부서명 "행정"→"행정팀" 개명 → `ArologisEmployeeServiceIT` 단언 회귀(Windows Testcontainers skip false-green을 Linux CI 적발, [[changed-module-full-test-before-push]] 실증).
- **표준계정과목 101개**(일반기업회계기준 5유형: 자산35/부채15/자본8/수익11/비용32, V17). `arologis_simple_account.type` CHECK **4→5유형(자본 EQUITY 추가)** ([[enum-expansion-check-constraint]]). 코드 4자리(1xxx 자산·2xxx 부채·3xxx 자본·4xxx 수익·8xxx 비용). 운송업 상용만 active=TRUE(46 활성/55 비활성).
- **활성상태 관리(신규 기능)**: page-code **`arologis.accounting.accounts`**(현금출납장 cashbook 과 **분리** — 거래 입력 권한과 계정 마스터 관리 권한 격리). GET /accounts/all(VIEW)+PUT /accounts/{code}/active(UPDATE). 권한 = **마스터+회계사원만**(V54, 대표실=마스터·회계팀=회계사원 매핑, 매니저 제외). FE AccountsPage(canManageAccounts=MASTER|ACCOUNTANT, **'active' 미노출 "활성상태" 표기**, 낙관적 토글). 
- dual 리뷰 P1(IT 부서명)+P2(enforcement HTTP 매트릭스 누락, [[enforcement-real-http-test]])+P3(복사포맷) 전건 fix. CI 29/29. 실 Docker 풀스택 QA PASS(101계정/EQUITY/토글 DB persist/매니저 403·회계사원 200 격리, `docs/qa/arologis-accounting-standard-chart/`).

**arologis 백오피스 완결** (인사 B / 간이회계 C / 권한 A / 6롤 / 표준차트). **잔여**: 실 부서 추가 시 seed 갱신, Phase 11 AWS / 알리고 SMS 외부 의존. ⚠️ 로컬 dev 스택: QA 가 V17/V54 재빌드 적용 + 프로비저닝 계정 2건(qa_acct/qa_mgr) 잔존.
