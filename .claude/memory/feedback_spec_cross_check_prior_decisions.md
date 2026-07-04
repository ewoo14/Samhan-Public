---
name: feedback_spec_cross_check_prior_decisions
description: 새 slice spec 작성 시 관련 에픽의 기존 확정 결정(dev-report·메모리)과 교차검증 — 기획 오류가 확정 정책과 상충할 수 있음
metadata:
  type: feedback
---

2026-07-05 박제 (#730 E3 S4b 회고).

**새 slice spec 을 쓸 때 관련 에픽의 기존 확정 결정을 반드시 교차검증**하라. Opus 기획이 사실 확인 없이 결정을 적으면, 같은 에픽의 앞선 슬라이스에서 개발책임자가 이미 확정한 결정과 정면 상충할 수 있다.

**실측(#730 S4b)**: spec D3 에 "확정(CONFIRMED) 입금보고서 편집 비활성" 이라 기획했으나, 이는 **D-E3-04(S2 #710 확정)="CONFIRMED 수정=역분개+재게시"** + 정책 메모리 [[project_accounting_ledger_edit_policy]] "입금보고서=편집대상" 과 정면 상충. BE `CashReceiptService.updateConfirmed` 가 이미 구현한 1급 기능을 UI 에서 막아버림. **라운드2 Design 리뷰가 dev-report 대조로 적발** → fix2 로 CONFIRMED 편집 노출 정정.

**How to apply**:
- slice spec 의 각 결정을 적기 전에 해당 에픽의 **dev-report·`.claude/memory/project_*`·기존 spec 을 grep** 해 상충 결정 유무 확인(특히 편집가부·상태전이·계정·권한 등 무결성 도메인).
- 리뷰 에이전트에게 "spec 자체가 기존 결정과 상충하는지" 검증 항목 명시 — spec 준수 검사만으로는 spec 오류를 못 잡는다.
- 무결성 도메인 결정은 [[feedback_integrity_domain_policy_preconfirm]] — 단 이미 확정된 결정(D-E3-04 류)을 따르는 것은 새 결정 아님(개발책임자 재확인 불요).
