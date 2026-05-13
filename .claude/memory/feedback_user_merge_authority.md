---
name: PM 자동 머지 — 5-team 리뷰 통과 + CI 100% green + 리뷰 결함 0건 시
description: 2026-05-10 사용자 결정 변경. 미구현 항목 P0/P1/P2 20+ 슬라이스 일괄 진행 효율화. PM 이 5-team 리뷰 0결함 + CI 100% green 확인 후 즉시 자동 머지 → 다음 슬라이스 바로 진행. UNSTABLE / 결함 발견 시는 사용자 결정 대기.
type: feedback
originSessionId: 8176df80-8c4b-4d57-8bfc-6fd47fd94b6b
---
**규칙 (2026-05-10 갱신)**: 다음 3개 조건 **모두** 만족 시 PM 이 자동 머지 가능, 즉시 다음 슬라이스 진행.

1. **5-team 리뷰 결함 0건** — BE / FE / Designer / QA / DevOps reviewer agent 모두 "문제 없음" / approve 댓글
2. **CI 100% green** — 모든 GitHub Actions check 통과 (UNSTABLE / queued 잔여 0)
3. **메모리 가드 위반 0건** — 한국어 commit/PR / Layer 4 도메인 메서드 / UUID 비공개 / 풀네임 ROLE / docs 동기화 등

**Why**: 사용자 명시 (2026-05-10) — "PR 여러개의 경우 한 개씩 머지 후 진행하면 너무 오래걸리므로 리뷰에 문제가 전혀 없고 CI 오류가 전혀 없는 경우 PM 이 자동 머지 그 후 바로 다음 작업 진행". P0+P1+P2 20+ 슬라이스 일괄 진행 결정으로 효율화 필요. 기존 PR #100 회고 (PM 자의적 머지 금지) 는 사용자 결정으로 명시 변경.

**How to apply**:
1. PR 발행 → 5-team reviewer agent 디스패치 → CI watch
2. 5-team 모두 approve + CI green + 가드 위반 0건 → PM `gh pr merge --squash` 즉시 실행
3. 머지 직후 main pull → 다음 슬라이스 branch 생성 + 작업 시작
4. 사용자에게 "PR #N 머지 완료, 다음 슬라이스 진행" 한 줄 보고만

**예외 (사용자 결정 대기)**:
- 5-team 중 1팀이라도 "결함" / "수정 필요" 댓글 → 사용자 결정 대기
- CI fail / UNSTABLE → 사용자 결정 대기
- 리뷰 통과해도 메모리 가드 위반 (한국어 X / 직접 set / UUID 노출 등) → PM 이 추가 commit 으로 자가 fix 후 재시도. fix 어려우면 사용자 결정 대기
- `gh pr merge --admin` 강행 머지는 사용자 명시 시만

**관련 메모리**:
- feedback_pr_review_workflow.md (5-team 리뷰 워크플로우)
- feedback_pr_ci_monitoring.md (PR 발행 후 CI 자동 watch)
- feedback_integrated_pr_pattern.md (통합 PR 패턴)

**이전 정책 (PR #100 회고) 변경 이력**:
- 2026-05-10 이전: "모든 머지는 개발책임자 직접" — admin 강행 머지 우려
- 2026-05-10 갱신: "5-team 0결함 + CI green 시 PM 자동 머지" — 20+ 슬라이스 일괄 진행 효율화
