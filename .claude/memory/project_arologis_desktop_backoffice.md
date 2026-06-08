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

차기 작업 = 위 3종을 phase/slice 로 분해, 개발책임자와 시작 슬라이스·HR/회계 범위(payroll 포함? 복식부기? 규모) 확정 후 진행.
