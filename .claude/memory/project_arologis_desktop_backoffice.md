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

**차기 = Phase A 권한 관리 UI** (마지막): 기존 DynamicPermission(auth-service role_page_permissions) 위 롤×page-code×action 매트릭스 조회/할당 화면 — arologis page-code(arologis.dispatch.*/hr.*/accounting.*) 관리. auth-service PermissionAdminController 활용. 잔여 seed: 실 부서명·간이 계정과목(개발책임자 제공). **Codex 회복(Jun 11) 후 정상 dual review 복귀**.
