# 슬4c — 그룹웨어 생성 시 기본 결재라인 프리필 + override Implementation Plan

> **For agentic workers:** 구현=**Codex**. Opus 계획/리뷰/PR.
> **🚨 듀얼리뷰 순차 + fix 후 0-수렴([[rereview-converge-after-fix]]) + 라운드별 실 라이브 QA([[per-round-live-qa]])**: 🔵 Opus 5-agent(**QA agent가 Docker 실 라이브 QA 수행→그 라운드 코멘트 인라인**) → fix → 🟣 Codex 5-agent(QA agent 실 라이브 QA→코멘트 인라인) → fix → 양쪽 0 수렴 → PM 종합(합성만). **QA는 각 리뷰 라운드가 수행(PM 종합 아님). CI-green만으로 머지 금지.**

**Goal:** 그룹웨어 결재 생성 시 문서종류(템플릿) 선택→설정된 기본 결재라인(USER 결재자)이 결재자 칩에 자동 프리필되고 생성자가 override(추가/삭제/순서변경).

**Architecture:** `GroupwareApprovalCreatePage` 에서 templateId 변경 시 `fetchDefaultApprovers('GROUPWARE_'+template.code)`(슬4a 엔드포인트) 호출 → 결과(sequence순 USER 결재자)를 `approvers` 칩 state 로 프리필. 기존 칩 add/remove(searchApprovers/TagChip) override 유지. 미설정 종류=빈(현행).

**Tech Stack:** React+TS / @samhan/design-system / react-query (clients/desktop). BE 변경 없음(슬4a default-approvers 소비).

## Global Constraints
- spec: `docs/superpowers/specs/2026-06-22-groupware-approval-line-config-design.md` §4 슬4c, D-G2(자동채우+override)·D-G3(USER v1).
- 기존 create 계약(approverIds/templateId/fieldValues/title/content) 유지. 프리필은 `approvers`(ApproverOption[]) state 초기화일 뿐.
- 프리필 매핑: default-approver {userId, displayName} → ApproverOption{userId, name(=displayName), department?}. sequence 순서 보존.
- 프리필 트리거: 템플릿 변경 시 default 결재라인으로 **교체**(생성자가 아직 손 안 댄 기본값). default 없으면 빈(현행). 페치 실패=빈(graceful, throw 금지).
- FE green = typecheck+lint+vitest. mock 3원칙([[inprocess-mock-principles]]).
- **라운드별 Docker 실 라이브 QA**: 생성 페이지(groupware.approvals — 일반 사용자 페이지)는 mock 모드/실 게이트웨이 렌더 가능성 높음. 템플릿 선택→칩 프리필 화면 캡처. 각 리뷰 라운드 QA agent가 수행.

---

### Task 1: api fetchDefaultApprovers + 타입
**Files:** `clients/desktop/src/renderer/api/`(approvalLineConfigApi.ts 또는 groupware api), mock.ts
- `ApprovalLineDefaultApprover = { sequence: number; label: string; userId: string; displayName: string }`
- `fetchDefaultApprovers(documentType): Promise<ApprovalLineDefaultApprover[]>` — `GET /auth/approval-line-configs/{documentType}/default-approvers`(슬4a). 실패 시 빈 배열(graceful).
- mock: default-approvers 핸들러(GROUPWARE_<code>별 USER 결재자). in-process 3원칙.
- [ ] Step 1: 타입+함수+mock. vitest(파싱·graceful 빈).
- [ ] Step 2: FE green. 커밋 `feat(desktop): default-approvers 조회 api (슬4c Task1)`

### Task 2: GroupwareApprovalCreatePage 프리필 + override
**Files:** `clients/desktop/src/renderer/routes/GroupwareApprovalCreatePage.tsx`
- templateId 변경 시(onChange/useEffect) selectedTemplate.code → `fetchDefaultApprovers('GROUPWARE_'+code)` → 결과를 ApproverOption[]로 매핑(sequence순) → `setApprovers`. default 없거나 실패=빈.
- 기존 칩 add(searchApprovers AsyncAutocomplete)/remove(TagChip ×)/순서 override 유지. 프리필 후 생성자 수정 자유.
- 프리필 표시(예: "기본 결재라인 적용됨" 안내 선택). approverIds/create 계약 무변경.
- [ ] Step 1: 템플릿 선택 시 default-approvers 페치+프리필(useQuery 또는 onChange). 칩 매핑(userId/displayName).
- [ ] Step 2: override(add/remove/reorder) 유지·미설정 빈·페치 실패 빈 확인. vitest(프리필·override·빈).
- [ ] Step 3: FE green. 커밋 `feat(desktop): 그룹웨어 생성 기본 결재라인 프리필+override (슬4c Task2)`

### Task 3: 라운드별 Docker 실 라이브 QA + 순차 듀얼리뷰 + 머지
- [ ] Step 1: 렌더러(mock off 가능 시 실 게이트웨이, 아니면 mock 모드 + 사유) + GROUPWARE_EXPENSE_REPORT default 결재라인 시드(실 DB 또는 mock).
- [ ] Step 2: **각 리뷰 라운드 QA agent가** 생성 페이지 진입→템플릿 "지출결의서" 선택→**기본 결재라인 칩 프리필** 화면 캡처(+ override 추가/삭제 1컷). `docs/qa/groupware-approval-line-config-s4c/`. 그 라운드 코멘트에 인라인.
- [ ] Step 3: PR([[open-pr-early]]·[[pr-title-caps-bracket]]) `[FEAT] 그룹웨어 생성 기본 결재라인 프리필 (슬4c)`. CI watch.
- [ ] Step 4: 🔵 Opus 5-agent(QA 라이브 캡처 인라인)→게시 → 🟣 Codex 5-agent(QA 라이브 캡처 인라인)→게시 → PM 종합(합성·수렴). fix 후 0-수렴 재리뷰 후 머지.

## Self-Review
- Spec coverage: §4 슬4c = default-approvers api(T1)·생성 프리필+override(T2)·라운드별 QA(T3). ✅
- Type 일관: ApprovalLineDefaultApprover(sequence/label/userId/displayName) ↔ 슬4a BE ApprovalLineDefaultApproverView 동일. ↔ ApproverOption 매핑.
- BE 변경 0(슬4a 소비).
