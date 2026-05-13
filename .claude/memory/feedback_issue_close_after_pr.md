---
name: PR 머지/close 완료 시 연관 Issue 자동 close 의무
description: PR 이 머지되거나 close 되어 작업이 종료되면, 연관 Issue 도 즉시 close. PR body 의 'Issue: #N' / Issue body 의 후속 PR 추적 모두 정리
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
PR 작업 워크플로우의 마지막 단계 — Issue close.

1. **PR 머지 시**: 연관 Issue (PR body 의 `연관 Issue: #N` / `Closes #N` 등) 즉시 close
2. **PR close 시**: 연관 Issue 도 함께 close (단 같은 작업이 신규 PR 로 이어지면 보존 — 명시적 판단)
3. **상태 정리 의무**: 모든 작업이 완료된 Issue 는 open 상태로 방치하지 말 것

PM (Claude) 가 PR 발행 시 Issue 와 PR 양방향 link 명시:
- Issue body: 후속 PR # 등재
- PR body: `연관 Issue: #N` (필수)
- PR 머지/close 시: `gh issue close N --comment "PR #M {머지/close} 완료"` 자동 실행

**Why:** 사용자 명시 (2026-05-05 v4 작업 회고) — "이슈는 모두 완료되었으면 닫을 것". Open Issue 누적 시 backlog 시각화 / 우선순위 판단 어려워짐. PR 워크플로우의 후속 정리 자동화.

**How to apply:** 
1. PM 의 PR 머지 요청 가드 (`feedback_pr_ci_monitoring.md`) 다음 단계 — 머지 완료 알림 받으면 즉시 연관 Issue 도 close
2. 현 open Issue 정기 점검 — 머지/close 된 PR 의 연관 Issue 가 아직 open 이면 즉시 close
3. PR 발행 시 PR body 의 `연관 Issue: #N` 명시 의무 (link 보존)

본 가드는 `feedback_pr_ci_monitoring.md` (PR 발행 후 PM 자동 CI watch) + `feedback_github_pr_workflow.md` (TM/PM/대표 승인 체인) 보강.
