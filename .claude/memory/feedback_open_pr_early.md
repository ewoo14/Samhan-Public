# PR은 구현 첫 push 직후 즉시 연다 (로컬 리뷰 사이클 전)

2026-06-01 개발책임자 지적 ("PR은 왜 안열고 진행 중?").

**규칙**: 슬라이스 구현이 1차 완료되면 **곧바로 브랜치 push + PR 발행**한다. 5-team 사이클 N=2 리뷰·CI·Docker 실 QA는 **열린 PR 위에서** 진행(후속 커밋으로 fix 반영). 로컬에서 사이클 N=2 수렴까지 본 뒤 PR 여는 패턴(D2 #334 / 2.6d #335) **금지**.

**Why:** 개발책임자가 진행 상황(리뷰·CI·커밋·diff)을 PR에서 실시간으로 보길 원함. 로컬 리뷰만 돌면 PR이 안 보여 진행이 불투명.

**How to apply:**
- 구현 에이전트 산출 → PM 통합 첫 커밋 → **push + `gh pr create` 즉시**.
- 그 다음 5-team 리뷰(사이클 1→fix→사이클 2) + CI watch + Docker 실 QA 를 PR 에 후속 커밋/코멘트로. QA 실 캡처는 PR 코멘트 첨부([[feedback_pr_qa_screenshots]]).
- PR 본문에 "사이클 N=2 진행 중" 등 현재 단계 명시 가능.
- 머지는 5팀 APPROVE + CI green + 실 QA PASS 후([[feedback_user_merge_authority]]).

관련: [[feedback_pr_ci_monitoring]], [[feedback_dual_5agent_review]], [[feedback_pr_qa_screenshots]].
