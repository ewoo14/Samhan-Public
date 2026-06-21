# A2-1b 결재라인 순서변경 + 라벨변경 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:subagent-driven-development 또는 executing-plans. 단 본 repo 규약상 **Codex 가 구현**([[feedback_codex_implements_claude_reviews]]), Claude 는 리뷰/검증. Steps 는 `- [ ]` 체크박스.

**Goal:** 결재라인 설정 메뉴에서 비-작성자 역할의 순서를 드래그로 바꾸고 라벨을 인라인 편집(작성자 고정).

**Architecture:** A2-1 `auth.approval_line_config` 재사용. 스키마 변경 0(label/sequence `updatable=true` 전환). rename + reorder 엔드포인트 신설. FE 드래그/인라인 + 자동저장(낙관/롤백, #553 일관).

**Tech Stack:** Spring Boot 3.3 / Java 17 / JPA / Testcontainers · Electron React / @tanstack/react-query / design-system 드래그.

## Global Constraints
- BaseEntity 7 audit + Soft Delete, 한국어 Javadoc, 도메인 메서드 chain(직접 set 금지).
- 실HTTP IT @MockBean 금지([[feedback_enforcement_real_http_test]]). page-code=admin.approval-line-config(UPDATE).
- UUID 사용자 비공개. 자동저장(저장 버튼 없음). 부분요청 가드([[feedback_defect_family_sweep_fix]]).
- spec: docs/superpowers/specs/2026-06-21-approval-line-reorder-rename-a2-1b-design.md

---

### Task 1: BE — 라벨 이름 변경 (rename)

**Files:**
- Modify: `services/auth-service/.../domain/ApprovalLineConfig.java` (label `updatable=true` + `rename(String)`)
- Modify: `services/auth-service/.../service/ApprovalLineConfigService.java` (`renameRole`)
- Modify: `services/auth-service/.../web/ApprovalLineConfigController.java` (PUT `/{id}/label`)
- Create: `services/auth-service/.../web/dto/RenameApprovalLineRoleRequest.java` (`{ String label }`)
- Test: `services/auth-service/.../service/ApprovalLineConfigServiceTest.java`

**Interfaces:**
- Produces: `ApprovalLineRoleView renameRole(UUID id, String label)` — CREATOR 거부, blank 거부(trim), label 갱신.

- [ ] **Step 1: 실패 테스트** — `renameRole_은_GROUP역할_라벨을_변경한다` / `_blank_거부` / `_CREATOR_거부`(INVALID_INPUT "작성자 역할은 변경할 수 없습니다").
- [ ] **Step 2: FAIL 확인** (`gradlew :services:auth-service:test --tests *ApprovalLineConfigServiceTest`).
- [ ] **Step 3: 구현** — `ApprovalLineConfig.label @Column(nullable=false, updatable=true)`; 도메인 `void rename(String label){ if(blank) throw; this.label=label.trim(); }`. service `renameRole`: findById → stepType==CREATOR 거부 → `rename` → save → `ApprovalLineRoleView` 반환. controller PUT `/auth/admin/approval-line-configs/{id}/label` `@RequirePermission(page="admin.approval-line-config", action=UPDATE)`.
- [ ] **Step 4: PASS 확인.**
- [ ] **Step 5: commit** `feat(auth): 결재라인 역할 라벨 rename 엔드포인트 (CREATOR 거부)`.

---

### Task 2: BE — 순서 변경 (reorder, 2-phase swap)

**Files:**
- Modify: `domain/ApprovalLineConfig.java` (sequence `updatable=true` + `changeSequence(int)`)
- Modify: `service/ApprovalLineConfigService.java` (`reorderRoles`)
- Modify: `repository/ApprovalLineConfigRepository.java` (이미 `findByDocumentTypeOrderBySequenceAsc` 존재)
- Modify: `web/ApprovalLineConfigController.java` (PUT `/reorder`)
- Create: `web/dto/ReorderApprovalLineRequest.java` (`{ List<UUID> orderedIds }`)
- Test: `service/ApprovalLineConfigServiceTest.java` + `it/ApprovalLineConfigControllerIT.java`

**Interfaces:**
- Produces: `List<ApprovalLineRoleView> reorderRoles(String documentType, List<UUID> orderedIds)`.

- [ ] **Step 1: 실패 테스트(단위)** — `reorderRoles_는_순서를_재할당한다`(출고인↔검수인 swap 후 sequence 0/1/2 검증) / `_CREATOR가_1순위_아니면_거부`(INVALID_INPUT "작성자는 항상 첫 순서여야 합니다") / `_부분요청_거부`(누락/잉여/타documentType id → INVALID_INPUT "결재라인 역할 전체를 순서대로 전달해야 합니다") / `_unique제약_무충돌`(swap 시 예외 없음).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — 도메인 `changeSequence(int)`. service `@Transactional reorderRoles`:
  ```
  active = repo.findByDocumentTypeOrderBySequenceAsc(documentType)
  // 부분요청 가드: orderedIds 집합 == active id 집합 (size+동일성)
  if (Set(orderedIds) != Set(active.id)) throw INVALID_INPUT(전체 전달)
  // CREATOR-first: orderedIds[0] 의 역할 stepType==CREATOR
  if (byId(orderedIds[0]).stepType != CREATOR) throw INVALID_INPUT(작성자 첫 순서)
  // 2-phase: 충돌 회피 음수 오프셋
  active.forEach(r -> r.changeSequence(-(r.getSequence()+1))); saveAllAndFlush
  for (i, id : orderedIds) byId(id).changeSequence(i); saveAllAndFlush
  return findByDocumentTypeOrderBySequenceAsc(documentType).map(view)
  ```
  controller PUT `/auth/admin/approval-line-configs/reorder?documentType=` `@RequirePermission(UPDATE)`.
- [ ] **Step 4: PASS(단위).**
- [ ] **Step 5: IT** — `ApprovalLineConfigControllerIT`(실HTTP @MockBean 없음): 비-MASTER MANAGER(a000…0003) reorder PUT 200 + 응답 sequence 순서 검증 + CREATOR rename PUT 4xx. fresh Postgres 의존(공유 DB 오염 주의 [[feedback_spec_sync_full_db_distribution_check]] — IT 내 documentType 격리 또는 cleanup).
- [ ] **Step 6: commit** `feat(auth): 결재라인 reorder 엔드포인트 (2-phase swap·작성자 1순위·부분요청 가드)`.

---

### Task 3: FE — 라벨 인라인 편집

**Files:**
- Modify: `clients/desktop/src/renderer/api/approvalLineConfigApi.ts` (`renameApprovalLineRole`)
- Modify: `clients/desktop/src/renderer/routes/ApprovalLineConfigPage.tsx` (비-CREATOR 라벨 인라인)
- Modify: `clients/desktop/src/renderer/api/mock.ts` (PUT `/{id}/label` stateful)
- Test: `clients/desktop/src/renderer/routes/__tests__/ApprovalLineConfigPage.test.ts` + `api/approvalLineConfigApi.test.ts`

**Interfaces:**
- Consumes: BE PUT `/{id}/label`.
- Produces: `renameApprovalLineRole(id: string, label: string): Promise<ApprovalLineRole>`.

- [ ] **Step 1: 실패 테스트** — api test(URL `/auth/admin/approval-line-configs/{id}/label` PUT) + page 순수 핸들러 `notifyApprovalRoleLabelChange`(blur/Enter→onRename(label), CREATOR 호출 안 함, blank 무시).
- [ ] **Step 2: FAIL 확인** (`npx vitest run ...ApprovalLineConfigPage.test.ts api/approvalLineConfigApi.test.ts`).
- [ ] **Step 3: 구현** — api `renameApprovalLineRole`. page: 비-CREATOR 행 라벨을 인라인 텍스트 입력(또는 편집 ✎), blur/Enter 시 renameMutation(낙관 setQueryData + onError 롤백, #553 패턴). CREATOR 라벨=정적. mock stateful(label 갱신). 자동저장(저장 버튼 없음).
- [ ] **Step 4: PASS + `npm run typecheck`.**
- [ ] **Step 5: commit** `feat(desktop): 결재라인 역할 라벨 인라인 편집 (작성자 고정·자동저장)`.

---

### Task 4: FE — 드래그 순서 변경

**Files:**
- Modify: `api/approvalLineConfigApi.ts` (`reorderApprovalLineRoles`)
- Modify: `routes/ApprovalLineConfigPage.tsx` (비-CREATOR 드래그 핸들 + 작성자 잠금)
- Modify: `api/mock.ts` (PUT `/reorder` stateful)
- Test: `__tests__/ApprovalLineConfigPage.test.ts` + `api/approvalLineConfigApi.test.ts`

**Interfaces:**
- Consumes: BE PUT `/reorder?documentType=`.
- Produces: `reorderApprovalLineRoles(documentType: string, orderedIds: string[]): Promise<ApprovalLineRole[]>`.

- [ ] **Step 1: 실패 테스트** — api test(URL + body `{orderedIds}`) + 순수 reorder 헬퍼(드롭 결과 orderedIds 계산: 작성자 항상 index 0 고정, 비-CREATOR 만 재배치).
- [ ] **Step 2: FAIL 확인.**
- [ ] **Step 3: 구현** — api `reorderApprovalLineRoles`. page: 비-CREATOR 행에 드래그 핸들(⠿, #495 `ProductSetComponentReorder`/design-system 드래그 재사용). 작성자 행 드래그 비활성 + 작성자 위로 드롭 불가(orderedIds[0]=작성자 강제). 드롭 시 reorderMutation(낙관 setQueryData 재정렬 + onError 롤백). mock stateful(sequence 재할당). 자동저장.
- [ ] **Step 4: PASS + typecheck.**
- [ ] **Step 5: commit** `feat(desktop): 결재라인 드래그 순서 변경 (작성자 1순위 잠금·낙관 롤백)`.

---

## Self-Review
- 스펙 커버: rename(T1/T3)·reorder(T2/T4)·CREATOR 고정(T1 거부/T2 1순위/T3·T4 잠금)·2-phase swap(T2)·부분요청 가드(T2)·자동저장 낙관롤백(T3/T4)·실HTTP IT(T2). ✅
- 타입 일치: `renameRole`/`reorderRoles` BE 시그니처 ↔ FE `renameApprovalLineRole`/`reorderApprovalLineRoles` 정합.
- 마이그레이션: 신규 Flyway 없음(updatable JPA 레벨, V61 불변 [[feedback_applied_migration_immutable]]).
- placeholder: 없음.

## 검증 게이트 (구현 후)
auth 전체 test + FE typecheck/vitest → 🐳 라이브 QA(드래그 순서 교환 + 라벨 변경 + 작성자 고정 캡처) → 🔵Opus 5-agent+QA → 🟣Codex 5-agent+QA → 양쪽 0 수렴 → 머지.
