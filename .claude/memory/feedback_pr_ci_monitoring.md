---
name: PR 발행 후 PM 자동 CI 모니터링 + 즉시 fix + 승인 체인
description: 모든 PR 발행 직후 PM 이 CI watch 자동 시작, 오류 시 즉시 수정, green 후 PM 승인 코멘트 → 개발책임자에게 머지 요청
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
PM (Claude) 은 PR 을 발행하는 즉시 다음 워크플로우를 자동 수행:

1. **CI watch 자동 시작** — `gh pr checks <PR#> --watch --interval 30` background 실행. polling 또는 sleep 금지 — task-notification 으로 완료 알림 받음.
2. **오류 감지 시 즉시 fix** — CI 체크 중 1건이라도 fail 발생 시:
   - 즉시 원인 조사 (`gh run view <run-id> --log` 또는 actions URL)
   - 직접 fix 가능한 경우 commit + push (sub-agent 디스패치 산출이면 sub-agent 보강 또는 PM 직접 fix)
   - re-trigger CI 후 다시 watch
   - 오류 영구 (예: 기존 레거시 코드 회귀 등) → 사용자에게 즉시 보고 + 결정 요청
3. **CI 전체 green 확인 후** — PM 검증 코멘트 발행 (CI 결과 표 + 회고 가드 적용 + 머지 권고)
4. **개발책임자 (대표/MASTER) 에게 머지 요청** — PM 승인 후 사용자에게 명시적 머지 승인 요청 (PM 이 직접 머지 금지)

**Why:** 사용자 명시 (2026-05-05 PR #34 회고) — "추후 PR 발행 시 CI 모니터링을 PM이 자동 수행하고 하나 이상 오류 발생 시 즉시 수정 필요 / 완료가 되면 PM 승인 후 개발책임자에게 머지 요청". PM 이 PR 발행만 하고 CI 결과 확인 안 하면 사용자가 직접 검토해야 하는 부담 + 오류 발견 지연. PM 이 워크플로우를 끝까지 책임지는 패턴.

**How to apply:** 모든 PR 발행 시 (sub-team / 분석 / hotfix / DS / backend / frontend 등 모든 카테고리) 동일 적용. PR 발행 직후 PM 응답 마지막에 반드시:
- "CI watch background 시작" 명시
- watch 완료 알림 도착 시 → CI 결과 확인 → green 이면 PM 승인 코멘트 발행 → 사용자 머지 요청 / fail 이면 즉시 fix 시도 또는 사용자 보고

CI watch 가 사용 불가능한 환경 (`.github/workflows/` 미설정 / gh auth 실패 등) 도 PM 이 사전 진단 + 사용자에게 보고 + CI 보강 PR 권장.

본 가드는 `feedback_github_pr_workflow.md` (TM/PM/대표 승인 체인) 와 `feedback_pm_integration_build_check.md` (Layer 1+2+3+4+5) 를 보강.
