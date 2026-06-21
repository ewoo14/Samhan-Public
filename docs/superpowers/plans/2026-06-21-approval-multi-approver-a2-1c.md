# A2-1c 다중 결재자(그룹+개인) 캡슐 Implementation Plan

> **For agentic workers:** Codex(danger-full-access)가 구현([[feedback_codex_implements_claude_reviews]]), Claude 는 리뷰/검증. Steps `- [ ]`.

**Goal:** 결재 역할당 결재자를 단일 그룹 → 그룹·개인 다중(캡슐)으로 확장.

**Architecture:** A2-1 `approval_line_config`(역할) + 신규 `approval_line_approver`(역할당 N 결재자, GROUP|USER). 칩 UI=§7 `GroupwareApprovalCreatePage`(AsyncAutocomplete+TagChip) 재사용. shared StepType 불변.

**Tech Stack:** Spring Boot 3.3 / Java 17 / JPA / Flyway V62 / Testcontainers · Electron React / react-query / design-system AsyncAutocomplete·TagChip.

## Global Constraints
- BaseEntity 7 audit + Soft Delete, 한국어 Javadoc, 도메인 메서드 chain.
- 실HTTP IT @MockBean 금지. UUID 비공개(displayName 만). page-code admin.approval-line-config.
- V61 불변, V62 fresh probe 검증([[feedback_migration_fresh_postgres_probe]]). 자동저장 낙관/롤백(#553).
- spec: docs/superpowers/specs/2026-06-21-approval-multi-approver-a2-1c-design.md

---

### Task 1: BE 모델 + V62 마이그레이션

**Files:**
- Create: `services/auth-service/.../domain/ApprovalLineApprover.java`, `.../domain/ApproverType.java`(enum GROUP|USER, auth 로컬)
- Modify: `.../domain/ApprovalLineConfig.java`(actionKey 필드 + getter, assignGroup/clearGroup deprecate)
- Create: `.../repository/ApprovalLineApproverRepository.java`
- Create: `.../resources/db/migration/V62__approval_line_approver.sql`
- Test: probe(수동) + `.../it/AuthFlywayV62SeedIT.java`

**Interfaces:**
- Produces: `ApprovalLineApprover{id, configRoleId, approverType, approverRefId}` (BaseEntity), `ApprovalLineConfig.getActionKey()`.

- [ ] **Step 1:** V62 작성 — `ALTER approval_line_config ADD action_key VARCHAR(40)`; UPDATE action_key=`OUTBOUND_DISPATCH` WHERE document_type='SLIP_OUTBOUND' AND step_type='GROUP' AND label='출고인'(seed), `OUTBOUND_INSPECT` WHERE label='검수인'; CREATE TABLE approval_line_approver(id uuid pk, config_role_id uuid not null, approver_type varchar(10) not null CHECK IN('GROUP','USER'), approver_ref_id uuid not null, +7 audit, is_deleted); CREATE UNIQUE INDEX uq_approver_active ON (config_role_id, approver_type, approver_ref_id) WHERE is_deleted=false; INSERT INTO approval_line_approver(...) SELECT ... FROM approval_line_config WHERE approver_group_id IS NOT NULL (type='GROUP', ref=approver_group_id).
- [ ] **Step 2:** fresh Postgres probe — DROP/CREATE db_probe, V1..V62 적용(`cat *.sql | psql ON_ERROR_STOP`), action_key 2행·approval_line_approver 테이블·이관행 확인. BUILD/적용 성공.
- [ ] **Step 3:** ApprovalLineApprover 엔티티(BaseEntity 상속, ApproverType enum @Enumerated STRING) + repository(findByConfigRoleIdAndIsDeletedFalse, existsByConfigRoleIdAndApproverTypeAndApproverRefIdAndIsDeletedFalse). ApprovalLineConfig actionKey 필드(@Column updatable=false) + assignGroup/clearGroup @Deprecated(또는 제거 — service 에서 미사용 전환).
- [ ] **Step 4:** AuthFlywayV62SeedIT — action_key 매핑 + 이관행 단언(immutable seed, [[feedback_spec_sync_full_db_distribution_check]] 공유DB 주의).
- [ ] **Step 5:** commit `feat(auth): A2-1c V62 approval_line_approver + actionKey 모델·이관`.

---

### Task 2: BE 서비스 + API (결재자 add/remove + 사원 검색)

**Files:**
- Modify: `.../service/ApprovalLineConfigService.java`(updateRole→required 전용, toView=approvers 배열) + Create `.../service/ApprovalLineApproverService.java`
- Modify: `.../web/ApprovalLineConfigController.java`(approver POST/DELETE, users GET) + `.../web/dto/`(ApprovalLineRoleView approvers 배열, ApproverView, AddApproverRequest, AccountSearchResult)
- Test: `.../service/ApprovalLineApproverServiceTest.java`, `.../it/ApprovalLineConfigControllerIT.java`(확장)

**Interfaces:**
- Produces: `addApprover(UUID roleId, ApproverType type, UUID refId)`, `removeApprover(UUID roleId, UUID approverId)`, `List<AccountSearchResult> searchUsers(String q, int limit)`. `ApprovalLineRoleView.approvers: List<ApproverView{id,type,refId,displayName}>`.

- [ ] **Step 1: 실패 테스트** — addApprover(GROUP 정상·USER 정상·중복 거부·system-master 그룹 거부·미존재 ref 거부·CREATOR 역할 거부) · removeApprover · searchUsers(이름 contains·활성·limit). updateRole 이 required 만 변경(approverGroupId 제거).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — ApprovalLineApproverService: addApprover(role CREATOR 거부, type=GROUP 시 groupRepo 존재+system-master 거부, type=USER 시 accountRepo 존재 검증, 중복 거부, save). removeApprover(soft delete). searchUsers(accountRepo 이름 LIKE 활성, displayName=이름+부서, UUID 비공개). ApprovalLineConfigService.toView 가 approvers 배열(displayName resolve: GROUP→groupName, USER→account 이름). updateRole→required 전용(approverGroupId 파라미터 제거). controller: POST `/{roleId}/approvers` `@RequirePermission(UPDATE)`, DELETE `/{roleId}/approvers/{approverId}`, GET `/users?q=&limit=` `@RequirePermission(VIEW)`.
- [ ] **Step 4: PASS(단위).**
- [ ] **Step 5: IT** — 비-MASTER MANAGER 실HTTP: approver POST(GROUP)/POST(USER) 200·DELETE 200·CREATOR approver POST 4xx·users 검색 200. @MockBean 없음.
- [ ] **Step 6: commit** `feat(auth): A2-1c 결재자 add/remove + 사원 검색 API`.

---

### Task 3: FE 칩 다중입력 (결재라인 설정 메뉴)

**Files:**
- Modify: `clients/desktop/src/renderer/api/approvalLineConfigApi.ts`, `routes/ApprovalLineConfigPage.tsx`, `api/mock.ts`
- Test: `routes/__tests__/ApprovalLineConfigPage.test.ts`, `api/approvalLineConfigApi.test.ts`

**Interfaces:**
- Consumes: BE approver POST/DELETE, users GET. ApprovalLineRole.approvers 배열.
- Produces: `searchApprovalLineUsers(q)`, `addApprovalLineApprover(roleId,type,refId)`, `removeApprovalLineApprover(roleId,approverId)`.

- [ ] **Step 1: 실패 테스트** — api test(approver POST/DELETE URL·body, users GET URL). 순수 헬퍼(칩 추가/제거 onChange 계약, CREATOR 호출 안 함).
- [ ] **Step 2: FAIL 확인** (npm run typecheck + lint + vitest — [[feedback_desktop_typecheck_command]] **lint 포함**).
- [ ] **Step 3: 구현** — `GroupwareApprovalCreatePage`(AsyncAutocomplete<ApproverOption> + TagChip) 패턴 정독·재사용. APPROVER 역할 행: "권한 그룹" Select → "결재자" 칩 컬럼(AsyncAutocomplete: 그룹+사원 검색 통합, 옵션에 타입배지; 선택→addApprover; TagChip 목록 `[그룹]/[사원] 라벨 ✕`→removeApprover). 낙관 setQueryData(approvers 배열 갱신) + onError 롤백(#553). 작성자=정적. A2-1b 드래그/라벨/필수 유지. api 함수 + mock stateful(approvers 배열·users 검색).
- [ ] **Step 4: PASS** — typecheck + **lint 0 error** + vitest.
- [ ] **Step 5: commit** `feat(desktop): A2-1c 결재자 칩 다중입력(그룹+사원, §7 패턴)`.

---

## Self-Review
- 스펙 커버: 자식테이블+actionKey(T1)·add/remove/검색 API(T2)·칩 UI(T3)·V62 이관(T1)·실HTTP IT(T2)·UUID 비공개(T2 displayName). ✅
- 타입 일치: addApprover/removeApprover/searchUsers BE ↔ FE 정합. ApproverType GROUP|USER 양쪽.
- 마이그: V62 fresh probe(T1). V61 불변.
- placeholder: 없음.

## 검증 게이트
auth 전체 test + V62 probe + FE typecheck/**lint**/vitest → 🐳 라이브 QA(그룹+사원 칩 추가/제거/persist·작성자 불가) → 🔵Opus 5-agent+QA → 🟣Codex 5-agent+QA → 양쪽 0 수렴 → 머지.
