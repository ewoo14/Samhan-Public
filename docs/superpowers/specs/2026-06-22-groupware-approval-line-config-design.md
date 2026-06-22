# 그룹웨어 문서종류별 결재라인 설정 — 설계 spec (동적 결재라인 에픽 슬4)

> 작성일: 2026-06-22 · 작성: PM(Opus) brainstorming(superpowers) + Workflow 전표/그룹웨어 정찰 종합 · 상태: **설계 승인(개발책임자 2026-06-22) → plan 대기**
>
> 상위 에픽: [2026-06-22-dynamic-approval-line-config-rendering-design.md](2026-06-22-dynamic-approval-line-config-rendering-design.md). 그 §4 "슬4 전 전표 확대"를 **개발책임자 방향 전환**으로 그룹웨어 결재로 재초점(전표 입고 확대는 후속 — 정찰상 입고=easy로 대기).
>
> 관련 메모리: [[rereview-converge-after-fix]] · [[temp-multimodel-workflow]] · [[project_sales_slip_naming]] · [[chip-ui-multi-input]] · [[fe-canaccess-pagecode-be-match]] · [[applied-migration-immutable]] · [[testcontainers-windows-docker]] · [[identity-header-authz-antipattern]]

---

## 0. 검증 출처 (정찰 — 추측 아님)

Workflow(wf_19636e5a, 7-agent) 전표/그룹웨어 정찰:
- **그룹웨어 결재 "문서 종류" = `ApprovalTemplate`**(groupware-service, DB 정의·관리자 추가가능. code/name/active/displayOrder + 동적필드. 시드 `EXPENSE_REPORT`(지출결의서)·`LEAVE_REQUEST`(휴가신청서). `V5__add_approval_templates_and_attachments.sql:110-126`). 고정 enum 아님.
- **현 결재라인 = 생성 시 사용자 수동 칩 선택**(ad-hoc, `GroupwareApprovalCreatePage.tsx` + `ApprovalLineService.create` for-loop `appendStep(approverUserId)`). **문서종류별 기본 결재라인 없음** ← 본 슬4가 메우는 갭.
- **그룹웨어 결재 = approval-core 순차 chain**(`ApprovalLineBase`/`ApprovalStepBase`, 개별 `approverUserId` step). A2 `approval_line_config`(auth, documentType=SLIP_* 전용)와 별개.
- **`ApprovalReferenceDocType`**(OUTBOUND_SLIP/JOURNAL 등) = 그룹웨어 결재가 **참조**하는 외부 전표 종류(첨부용). 그룹웨어 "문서 종류" 아님.
- 템플릿 목록 엔드포인트: `GET /admin/groupware/approval-templates`(page-code `groupware.approval-templates` VIEW).

---

## 1. 목표

그룹웨어 결재 문서를 **문서 종류(ApprovalTemplate)별로 기본 결재라인을 관리자가 설정** → 생성 시 그 기본값이 결재자 칩에 **자동 프리필**되고 생성자가 **override** 가능.

### 비목표 (v1)
- **GROUP 결재자의 chain 전개**(그룹 누구나 = 개별 step 의미 불일치): v1은 **USER 결재자(특정 인물)** 중심. GROUP 처리는 후속.
- 전표(입고 등) 확대(정찰 입고=easy로 별도 후속 슬라이스).
- approval-core 모델 변경(순차 chain 그대로). 기본 결재라인은 **생성 프리필 소스**일 뿐 enforcement 게이트 아님.

---

## 2. 확정 결정 (브레인스토밍 — 개발책임자 2026-06-22)

| # | 항목 | 확정 |
|---|---|---|
| D-G1 | 저장/관리 | **Option A — auth `approval_line_config` 확장**. documentType=`GROUPWARE_<템플릿 code>`(예 `GROUPWARE_EXPENSE_REPORT`). 스키마 변경 없음(documentType VARCHAR). 슬1~3 결재라인 설정 메뉴·단계 CRUD·결재자 칩·구조 엔드포인트 **재사용**. |
| D-G2 | 생성 시 동작 | **기본값 자동채우 + 수동 override 허용**. 종류 선택→기본 결재라인 칩 프리필, 생성자 추가/삭제/순서변경 가능. 미설정 종류=현행(빈 수동). |
| D-G3 | 결재자 성격 v1 | **USER(특정 인물) 중심**. 그룹웨어 chain=개별 approverUserId 라 USER 칩 직매핑. GROUP은 v1 보류. |
| D-G4 | enforcement | **없음**(프리필 소스만). 그룹웨어 결재는 approval-core 순차 chain 자체 진행. A2 게이트(authorize) 미적용. |

---

## 3. 아키텍처

**① 설정 측(관리자, 슬1~3 재사용)**: `approval_line_config`에 `GROUPWARE_<code>` 행. 결재라인 설정 메뉴에서 그룹웨어 종류 선택→단계(USER 결재자) 설정. **DOC_TYPES 동적화**(전표 3종 고정 + 그룹웨어 템플릿 N종 페치).

**② 생성 측(사용자)**: `GroupwareApprovalCreatePage` 템플릿 선택→ **default-approvers resolve 엔드포인트** 호출→ 결재자 칩 프리필→ override→ 기존 create(approverIds) 흐름.

**경계**: 기본 결재라인 정의=auth approval_line_config(설정 진실원). 결재 실행=groupware approval-core(불변). 둘은 **생성 시점 프리필**로만 연결(런타임 결합 최소).

---

## 4. 슬라이스 분해

### 슬4a — BE (auth + 카탈로그/resolve) [먼저 착수]
- **(1) documentType GROUPWARE_<code> 수용**: approval_line_config 쓰기(추가/삭제/rename/reorder/approver)가 `GROUPWARE_*` documentType 허용(검증이 SLIP_* 화이트리스트로 막지 않는지 확인·필요시 완화). **스키마/Flyway 변경 없음**.
- **(2) configurable doc-type 카탈로그 엔드포인트**: 설정 메뉴용 — 전표 고정 3종 + **활성 그룹웨어 템플릿**(GROUPWARE_<code>, label=템플릿명)을 반환. page-code `admin.approval-line-config` 접근자(MANAGER 포함)가 호출 가능해야 함([[fe-canaccess-pagecode-be-match]] — groupware.approval-templates 게이트 재사용 시 위임 MANAGER 403 위험 → admin.approval-line-config 게이트 또는 인증-only 신규 엔드포인트). auth가 groupware 템플릿을 가져오는 cross-service vs FE가 두 소스 조합 — 구현 시 결정(권장: auth에 카탈로그 엔드포인트, 내부적으로 groupware 템플릿 client 조회 또는 FE 조합. 결합 최소 우선).
- **(3) 생성 프리필 resolve 엔드포인트**: `GET /auth/approval-line-configs/{documentType}/default-approvers`(인증-only, @RequirePermission 없음·게이트웨이 라우트 동반 [[identity-header-authz-antipattern]] 슬3 B1 교훈) → sequence 순 **USER 결재자(approverRefId=userId + 표시명)**. 구조 엔드포인트(슬3)는 결재자 제외라 프리필엔 부족 → 본 엔드포인트는 결재자 신원 포함(생성자가 기본 결재자를 봐야 하므로 적정 노출).
- IT: GROUPWARE_<code> 추가/삭제·카탈로그(그룹웨어 템플릿 포함)·default-approvers(USER id+명, 비인증 403/게이트웨이 401). 실HTTP. **게이트웨이 라우트 계약 IT**(default-approvers 인증 라우트).

### 슬4b — FE 설정 메뉴 동적 DOC_TYPES
- `approvalLineConfigApi.DOC_TYPES` 하드코딩 → **동적**(카탈로그 엔드포인트 페치: 전표 3종 + 그룹웨어 템플릿). 그룹웨어 종류 선택 시 슬2 단계 CRUD + 결재자 칩(USER)로 기본 결재라인 설정. mock 동기화.

### 슬4c — FE 생성 프리필 + override
- `GroupwareApprovalCreatePage` 템플릿 선택 → `fetchDefaultApprovers(GROUPWARE_<code>)` → 결재자 칩 자동 프리필(USER). 생성자 추가/삭제/순서변경(override). 미설정=빈 수동(현행). 기존 create(approverIds) 흐름 유지.

---

## 5. 테스트/QA
- **실HTTP**(슬4a): 카탈로그·default-approvers 계약, GROUPWARE_<code> CRUD, 비인증 403/게이트웨이 401. @MockBean 우회 금지([[restclient-contract-test-false-green]]). **게이트웨이 라우트 계약 IT**(슬3 B1 교훈 — 신규 인증 엔드포인트).
- **Testcontainers Windows skip 주의**([[testcontainers-windows-docker]]): 로컬 BUILD SUCCESSFUL≠실행 → CI 결과 확인 의무.
- **Docker 라이브 실QA**: 설정 메뉴에서 그룹웨어 종류 기본 결재라인 설정(슬4b) + 생성 화면 프리필(슬4c) 실캡처. config 설정 페이지 admin-게이트 한계는 dev_manager(materialize됨)로 캡처 시도([[local-stack-qa-gotchas]]).
- **듀얼리뷰 순차 + fix 후 0-수렴 재리뷰 엄격**([[rereview-converge-after-fix]]): Opus 5-agent 완료·게시 → Codex 5-agent 완료·게시 → PM 종합, **어떤 fix든 그 fix 포함 최종상태 재리뷰 0-수렴 후에만 머지**(CI-green만으로 머지 금지).

## 6. 미해결 / 스펙 리뷰 확인
1. **카탈로그 엔드포인트 위치**(auth cross-service vs FE 조합) — 슬4a 구현 시 결합 최소 기준 결정.
2. **그룹웨어 doc-type 식별자 규칙**: `GROUPWARE_<template.code>` (code 대문자·영문 가정). 템플릿 code에 특수문자 시 처리.
3. **default-approvers 결재자 노출**: 생성 프리필 위해 USER id+표시명 노출(구조 엔드포인트와 달리 결재자 포함) — 저민감 판단(기본 결재자는 생성자가 봐야 함). GROUP은 v1 비노출/보류.
4. **GROUP 결재자**(v1 보류) — 설정 UI에서 그룹웨어 종류는 USER 위주 안내할지, GROUP 입력 시 프리필 제외할지.
