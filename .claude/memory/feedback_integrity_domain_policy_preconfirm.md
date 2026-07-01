---
name: feedback_integrity_domain_policy_preconfirm
description: 무결성 민감 도메인(회계 원장 등)은 정책[편집 가부] 착수 전 개발책임자 확인
metadata:
  type: feedback
---

2026-07-02 회계 full-form 슬1(#697) 폐기 회고. 무결성 민감 도메인(회계 원장·감사대상 문서·권한 등)의 **편집/수정 가부 정책은 코드 착수 전 개발책임자 확인** 필수.

**Why**: 회계 full-form coedit 슬1에서 원장필드(계정/차대변) 라이브 동시편집을 D-ACC-03 결정지점으로 표기하고 **야간 권장방향(허용 A)으로 진행**했으나, 개발책임자가 "원장은 수정 금지"로 확정 → 슬1 전체(BE PUT + blocking 11 fix)를 **폐기**. 순차 듀얼이 적발한 원장 동시편집 리스크(낙관락 라인편집 no-op·partnerId A4 채권채무 오염·soft-delete importer 42P10/restore CTE)가 정확히 개발책임자가 차단한 위험이었음 — 방향 판단 근거는 됐으나 선확인이 작업 낭비를 막았을 것.

**How to apply**: 정책 결정지점(회계 무결성·감사·권한 widening 등 민감 도메인)은 "권장방향 야간진행+오전확정" 대신 **착수 전 개발책임자 확인**. [[feedback_epic_scope_no_narrowing]](스코프 축소 금지)와 균형 — 스코프 임의축소는 금지지만, 도메인 무결성 편집 정책은 선확인이 우선. 또한 감사 보완 시 후속 라운드도 5-agent 완주+Codex 대칭(축소 금지, R3 2-agent 축소가 감사 지적). [[project_accounting_ledger_edit_policy]] [[feedback_canonical_workflow]]
