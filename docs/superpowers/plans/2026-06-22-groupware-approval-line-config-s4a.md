# 슬4a — 그룹웨어 결재라인 설정 BE (doc-type 수용·카탈로그·프리필 resolve) Implementation Plan

> **For agentic workers:** 구현=**Codex**([[codex-implements-claude-reviews]]). Opus 계획/리뷰/PR.
> **🚨 듀얼리뷰 순차 + fix 후 0-수렴 재리뷰 엄격([[rereview-converge-after-fix]])**: 🔵 Opus 5-agent 완료·게시 → 🟣 Codex 5-agent 완료·게시 → PM 종합. **어떤 fix든(Opus/Codex/CI) 그 fix 포함 최종상태를 재리뷰해 Opus·Codex 양쪽 새 fix 없이 0 수렴 확인한 뒤에만 머지. CI-green만으로 머지 금지.**

**Goal:** 그룹웨어 문서종류(ApprovalTemplate)별 기본 결재라인을 auth approval_line_config로 설정·생성 프리필할 수 있게 하는 BE 토대(슬4b 설정 FE·슬4c 생성 FE 가 소비).

**Architecture:** auth approval_line_config가 `GROUPWARE_<template code>` documentType 수용(스키마 변경 없음) + 설정 메뉴용 doc-type 카탈로그(전표+활성 그룹웨어 템플릿) + 생성 프리필용 default-approvers resolve 엔드포인트.

**Tech Stack:** Spring Boot 3/Java 17 (auth-service, groupware-service, api-gateway). Flyway 변경 없음(documentType=VARCHAR 기존).

## Global Constraints
- spec: `docs/superpowers/specs/2026-06-22-groupware-approval-line-config-design.md` §2(D-G1~4)·§4 슬4a.
- **USER v1**(D-G3): default-approvers 는 USER 결재자(approverRefId=userId)+표시명. GROUP은 v1 제외(있어도 프리필 비대상).
- **신규 auth 엔드포인트=게이트웨이 라우트 동반 필수**([[identity-header-authz-antipattern]]·슬3 B1): default-approvers 인증 라우트를 `application.yml`에 추가(JwtAuthentication, /auth/admin 아래 아님) + **route 계약 IT**(ApiGatewayContextLoadIT). 누락 시 실 게이트웨이 catch-all→401.
- **auth 직접(MockMvc) 비인증=403 / 게이트웨이=401**(슬3 교훈): IT 비인증 단언은 403(auth 직접).
- **실HTTP IT**([[restclient-contract-test-false-green]]·[[enforcement-real-http-test]]): @MockBean 우회 금지. cross-service client 추가 시 계약 테스트.
- **Testcontainers Windows skip 주의**([[testcontainers-windows-docker]]): 로컬 BUILD SUCCESSFUL≠실행 → **CI 결과 확인 의무**([[changed-module-full-test-before-push]]).
- **Flyway 없음**(documentType 신규 값만, 스키마 무변).

---

### Task 1: approval_line_config가 GROUPWARE_<code> documentType 수용

**Files:** `ApprovalLineConfigService.java`/`ApprovalLineConfigController.java`(검증 확인), `ApprovalLineConfigControllerIT.java`(IT)
- 현 add/delete/rename/reorder/approver 쓰기가 documentType을 SLIP_* 화이트리스트로 **막지 않는지 확인**. 막으면 `GROUPWARE_*` 허용(또는 검증 완화 — documentType 자유 문자열 유지). 막지 않으면 변경 0(검증 IT만).
- [ ] Step 1: documentType 검증 경로 확인(grep SLIP_OUTBOUND/화이트리스트). 제약 있으면 GROUPWARE_ prefix 허용.
- [ ] Step 2: IT — `GROUPWARE_EXPENSE_REPORT` 단계 추가(addStep)→조회→삭제 200, USER 결재자 추가/조회.
- [ ] Step 3: `./gradlew :services:auth-service:test`(CI 확인). 커밋 `feat(approval): approval_line_config GROUPWARE_<code> documentType 수용 (슬4a)`

### Task 2: 활성 그룹웨어 템플릿 목록 — 비-admin 엔드포인트 (설정 메뉴·생성 공용)

**Files:** `GroupwareApprovalTemplateController.java`(또는 신규), `ApprovalTemplateService.java`, IT
- 설정 메뉴(page-code admin.approval-line-config)·생성 페이지가 **활성 템플릿(code/name)** 을 읽을 수 있어야 함. 현 `GET /admin/groupware/approval-templates`는 page-code `groupware.approval-templates` VIEW 게이트 → 위임 MANAGER 403 위험([[fe-canaccess-pagecode-be-match]]).
- [ ] Step 1: 생성 페이지가 이미 쓰는 활성-템플릿 엔드포인트 있는지 확인. 있으면(인증-only) 재사용·이 Task 축소.
- [ ] Step 2: 없으면 `GET /groupware/approval-templates/active`(인증-only·@RequirePermission 없음, active=true code/name/displayOrder) 추가. 게이트웨이 라우트(JwtAuthentication) 동반 + route 계약 IT(있으면 gateway IT).
- [ ] Step 3: IT(인증 200·active만·비인증 403). `./gradlew :services:groupware-service:test`. 커밋 `feat(groupware): 활성 결재 템플릿 비-admin 목록 엔드포인트 (슬4a)`

### Task 3: 생성 프리필 default-approvers resolve 엔드포인트 (auth)

**Files:** `ApprovalLineStructureController.java`(또는 신규 controller), `ApprovalLineConfigService.java`, DTO `ApprovalLineDefaultApproverView`, `application.yml`(gateway route), `ApiGatewayContextLoadIT.java`, IT
- `GET /auth/approval-line-configs/{documentType}/default-approvers` — **인증-only**(@RequirePermission 없음) → sequence 순 단계별 **USER 결재자**(approverRefId=userId, 표시명, label, sequence). GROUP 결재자는 v1 제외(스킵 또는 type 표기). 구조 엔드포인트(슬3)와 달리 **결재자 신원 포함**(생성자가 기본 결재자 확인 필요 — 저민감).
- 표시명 resolve: userId→fullName(user-service 또는 auth 계정 조회). 기존 approver 조회 패턴 재사용.
- [ ] Step 1: DTO + service(documentType의 active config 단계별 USER approver + 표시명 resolve).
- [ ] Step 2: 컨트롤러 엔드포인트(인증-only). **게이트웨이 application.yml** `auth-service-admin-authenticated` Path에 `/auth/approval-line-configs/*/default-approvers` 추가(JwtAuthentication). (슬3 structure 경로 옆.)
- [ ] Step 3: **route 계약 IT**(ApiGatewayContextLoadIT — default-approvers 경로 JwtAuthentication·no-strip·catch-all 선행).
- [ ] Step 4: auth IT — 인증 200(GROUPWARE_<code> USER 결재자 sequence 순·표시명)·비인증 403·미설정 documentType 빈 목록. 실HTTP.
- [ ] Step 5: `./gradlew :services:auth-service:test :services:api-gateway:test`(CI 확인). 커밋 `feat(approval): 그룹웨어 생성 프리필 default-approvers resolve 엔드포인트 + 게이트웨이 라우트 (슬4a)`

### Task 4: Docker 라이브 실QA + 순차 듀얼리뷰 + 머지
- [ ] Step 1: auth+gateway(+groupware) 재빌드. 실 게이트웨이 검증: GROUPWARE_<code> 설정 후 default-approvers 200(USER 결재자)·비인증 401·카탈로그에 그룹웨어 템플릿 노출. 라이브 캡처(`docs/qa/groupware-approval-line-config-s4a/`).
- [ ] Step 2: PR([[open-pr-early]]·[[pr-title-caps-bracket]]) `[FEAT] 그룹웨어 결재라인 설정 BE — doc-type 수용·카탈로그·프리필 resolve (슬4a)`. spec/plan·QA 인라인. CI watch.
- [ ] Step 3: **🔵 Opus 5-agent 순차→게시 → 🟣 Codex 5-agent 순차→게시 → PM 종합. 어떤 fix든 0-수렴 재리뷰 후 머지([[rereview-converge-after-fix]]).**

## Self-Review
- Spec coverage: §4 슬4a = doc-type 수용(T1)·카탈로그 활성템플릿(T2)·프리필 resolve+게이트웨이(T3)·QA/듀얼리뷰(T4). ✅
- 슬3 교훈 반영: 신규 인증 엔드포인트 게이트웨이 라우트+route 계약 IT, auth직접 403, Testcontainers Windows skip CI 확인. ✅
- Type 일관: ApprovalLineDefaultApproverView(sequence/label/userId/displayName) BE↔FE(슬4c) 계약 — 슬4c plan에서 매칭.
