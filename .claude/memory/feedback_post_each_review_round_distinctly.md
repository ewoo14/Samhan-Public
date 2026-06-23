---
name: feedback_post_each_review_round_distinctly
description: 듀얼리뷰 각 라운드(Opus/Codex/수렴재검증 포함)는 완료 즉시 PR에 독립 코멘트로 게시. 다른 라운드에 녹여서 합치기 금지.
metadata:
  type: feedback
---

🚨 2026-06-23 개발책임자 반복 지적(PR #585에서 2연속). **듀얼리뷰의 모든 라운드를 각각 독립 PR 코멘트로, 그 라운드 완료 즉시 게시한다.**

**Why**: PM이 라운드를 실행만 하고 게시를 누락하거나(예: Codex 라운드 미게시), 한 라운드(예: Opus 3라운드 수렴 재검증)를 다른 라운드의 코멘트나 최종 수렴 노트에 녹여 합치면 — 개발책임자가 순차 듀얼리뷰 진행/cross-check를 PR에서 추적 불가. "라운드 실행"과 "라운드 게시"는 별개이며 둘 다 의무.

**How to apply**:
- Opus 5-agent 라운드, Codex 5-agent 라운드, **그리고 fix 후 수렴 재검증 라운드(Opus R2/R3, Codex 최종 cross-check)** 각각을 **개별 `gh pr comment`** 로 게시.
- 각 라운드 코멘트 = 그 라운드의 5-agent verdict + finding(severity·location) + fix(주체 명시) + QA 증빙 + 다음 단계.
- 라운드 완료 직후 게시(batch 보류·다음 라운드와 합치기 금지). [[feedback_canonical_workflow]] "제때 게시" 강화.
- 수렴 재검증도 1개 라운드 = 1개 코멘트. "최종 수렴 노트"는 라운드 코멘트와 별개의 종합일 뿐, 라운드 코멘트를 대체하지 않는다.

관련: [[feedback_rereview_converge_after_fix]] [[feedback_defect_family_sweep_fix]]
