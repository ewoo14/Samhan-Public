# 슬4b — 결재라인 설정 메뉴 동적 DOC_TYPES (전표 + 그룹웨어 종류) Implementation Plan

> **For agentic workers:** 구현=**Codex**. Opus 계획/리뷰/PR.
> **🚨 듀얼리뷰 순차 + fix 후 0-수렴 재리뷰([[rereview-converge-after-fix]])**: 🔵 Opus 5-agent 완료·게시 → 🟣 Codex 5-agent 완료·게시 → PM 종합. 어떤 fix든 그 fix 포함 최종상태 재리뷰해 양쪽 0 수렴 후에만 머지. CI-green만으로 머지 금지.

**Goal:** 결재라인 설정 메뉴(ApprovalLineConfigPage)의 doc-type 선택을 하드코딩 3종 → **동적**(전표 3종 + 그룹웨어 활성 템플릿 GROUPWARE_<code>)으로. 그룹웨어 문서종류별 기본 결재라인을 슬1~3 단계 CRUD·USER 결재자 칩으로 설정.

**Architecture:** `approvalLineConfigApi` 에 `fetchConfigurableDocTypes()` 신설(전표 static + `GET /groupware/approval-templates/active`(슬4a) 조합 → GROUPWARE_<code>). `ApprovalLineConfigPage` doc-type 셀렉터가 이 동적 목록 사용. 그룹웨어 종류 선택 시 기존 단계 CRUD·approver 칩·구조/CRUD 엔드포인트가 GROUPWARE_<code> documentType로 그대로 동작(슬4a BE 수용 완료).

**Tech Stack:** React+TS / @samhan/design-system / react-query (clients/desktop). BE 변경 없음(슬4a 엔드포인트 소비).

## Global Constraints
- spec: `docs/superpowers/specs/2026-06-22-groupware-approval-line-config-design.md` §4 슬4b.
- **USER v1**(D-G3): 그룹웨어 종류는 USER 결재자 위주(GROUP 입력 가능하나 슬4c 프리필 제외). 설정 UI는 슬2 그대로 재사용(GROUP/USER 칩).
- doc-type 라벨: 전표=고정(판매전표/입고전표/주문), 그룹웨어=템플릿 name(지출결의서 등). value=전표 키 / GROUPWARE_<template.code>.
- FE green = typecheck+lint+vitest([[desktop-typecheck-command]]). mock 3원칙([[inprocess-mock-principles]]).
- 머지 전 Docker 라이브 **UI 실QA**(설정 메뉴 그룹웨어 종류 노출+기본 결재라인 설정 화면 캡처 — 개발책임자 "스크린샷" 요구). config 페이지 admin-게이트는 dev_manager(materialize됨)로 시도([[local-stack-qa-gotchas]]).

---

### Task 1: api — fetchConfigurableDocTypes + 타입
**Files:** `clients/desktop/src/renderer/api/approvalLineConfigApi.ts`, `clients/desktop/src/renderer/api/mock.ts`
- `ConfigurableDocType = { value: string; label: string; kind: 'SLIP' | 'GROUPWARE' }`.
- `fetchConfigurableDocTypes(): Promise<ConfigurableDocType[]>` — 전표 static 3종(판매전표/입고전표/주문) + `GET /groupware/approval-templates/active` 페치 → active 템플릿을 `{value:'GROUPWARE_'+code, label:name, kind:'GROUPWARE'}`. 그룹웨어 페치 실패 시 전표 3종만(graceful).
- 기존 `DOC_TYPES` 상수는 전표 static fallback로 유지(또는 SLIP_DOC_TYPES로 rename).
- mock: `/groupware/approval-templates/active` 핸들러(EXPENSE_REPORT/LEAVE_REQUEST active) — 이미 슬4a mock 있으면 재사용.
- [ ] Step 1: 타입 + fetchConfigurableDocTypes + mock 핸들러. vitest(조합·graceful 폴백).
- [ ] Step 2: FE green. 커밋 `feat(desktop): 결재라인 설정 동적 doc-type 카탈로그 api (슬4b Task1)`

### Task 2: ApprovalLineConfigPage 동적 doc-type
**Files:** `clients/desktop/src/renderer/routes/ApprovalLineConfigPage.tsx`
- `DOC_TYPES` static → `useQuery(fetchConfigurableDocTypes)` 동적 목록. 초기 docType=첫 항목(로딩 중 placeholder). 셀렉터(:353) 동적 렌더(전표/그룹웨어 그룹 구분 optgroup 권장).
- 그룹웨어 종류 선택 시 기존 로직(structure/단계 CRUD/approver) 그대로 — documentType만 GROUPWARE_<code>. 미설정 그룹웨어 종류=빈 결재라인(작성자 CREATOR 없음, 첫 단계 추가 가능, 슬4a 삭제가드 완화로 seq0 삭제 가능).
- [ ] Step 1: 동적 docType useQuery + 셀렉터 + 초기값/로딩 처리.
- [ ] Step 2: 그룹웨어 종류 선택→단계 추가/삭제/approver 동작 확인(기존 mutation 재사용, documentType 전달).
- [ ] Step 3: vitest 갱신(동적 doc-type 렌더·그룹웨어 종류 선택). FE green. 커밋 `feat(desktop): 결재라인 설정 메뉴 동적 doc-type(전표+그룹웨어) (슬4b Task2)`

### Task 3: Docker 라이브 UI 실QA + 순차 듀얼리뷰 + 머지
- [ ] Step 1: 렌더러(:5175, mock off) + 실 게이트웨이. dev_manager(admin.approval-line-config materialize)로 결재라인 설정 메뉴 진입. (admin 403 한계 시 mock 모드 UI 캡처 — 명시.)
- [ ] Step 2: UI 캡처 — (1) doc-type 셀렉터에 전표(판매전표/입고전표/주문) + 그룹웨어(지출결의서/휴가신청서) 노출 (2) 그룹웨어 종류 선택→단계 추가+USER 결재자 칩 설정 화면. `docs/qa/groupware-approval-line-config-s4b/`.
- [ ] Step 3: PR([[open-pr-early]]·[[pr-title-caps-bracket]]) `[FEAT] 결재라인 설정 메뉴 동적 doc-type(그룹웨어 종류) (슬4b)`. QA 인라인. CI watch.
- [ ] Step 4: **🔵 Opus 5-agent 순차→게시 → 🟣 Codex 5-agent 순차→게시 → PM 종합. fix 후 0-수렴 재리뷰 후 머지.**

## Self-Review
- Spec coverage: §4 슬4b = 동적 doc-type api(T1)·설정 페이지 동적(T2)·UI QA(T3). ✅
- Type 일관: ConfigurableDocType(value/label/kind) ↔ mock ↔ groupware active 응답(code/name). 
- BE 변경 0(슬4a 엔드포인트 소비). FE-only.
