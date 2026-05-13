---
name: agent 작업 시작 시 origin/main 동기화 의무
description: background agent 가 작업 시작 전 git fetch origin + git log origin/main 검증 의무 — stale 로컬 main 본 채 잘못된 결론 도출 방지
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
## 규칙

모든 background agent (특히 worktree isolation 모드) 는 작업 시작 직후 다음을 실행 의무:

```sh
git fetch origin
git log origin/main --oneline -20
```

파일 존재 검증 시에도 `git log origin/main -- <path>` 또는 `git show origin/main:<path>` 로 origin 기준 확인. 로컬 main 만으로 판단 금지.

## Why

3 건의 background agent 보고가 stale 로컬 main 을 본 채 잘못된 결론 도출:

1. **주문서 재확인 agent** (`ac0444488070dcd50`) — "PR #50/#52/#53 main 미반영 의심" 보고 → 실제는 origin/main 에 모두 머지됨 (`ccb7f42`)
2. **M4 partner-order-service 설계 agent** — `clients/web/order-app/` 가 `.claude/worktrees/...` 만 존재한다고 보고 → 실제 origin/main 에 PR #50 머지됨
3. **DEVOPS hosting plan agent** (`a913a638966bcb948`) — estimate-app v2 + Desktop legacySource 가 main 미머지라고 보고 → 실제 origin/main 에 PR #58/#54 모두 머지됨 (`6a36710` / `891511d`)

각 경우 PM 이 `git log origin/main` 으로 정정 확인. 동일 실수 반복 → 메모리 가드 의무.

원인: agent 가 spawn 되는 worktree 또는 기본 작업 디렉토리의 로컬 main 이 origin 보다 뒤처진 상태에서 `git log` / `ls` 로 판단. 결과: 잘못된 후속 작업 권고 (예: "PR 미머지 의심" — 실제 머지된 작업을 다시 dispatch 위험).

## How to apply

- **모든 agent prompt 에 의무 단계 명시** — 작업 1번째 step:
  ```
  ## 0. origin/main 동기화 검증
  git fetch origin
  git log origin/main --oneline -20  # 최근 머지 PR 확인
  ```
- 파일 존재 검증 시: `git show origin/main:<path>` 우선, 로컬 `ls` 는 보조
- 보고서에 "main 미머지 의심" 표기 시 **반드시 origin/main 에서 grep 으로 재검증** + 결과 명시
- agent 가 isolated worktree 모드라면 worktree 자체가 base branch 기준이지만, 그 base 가 stale 인 경우 동일 문제 → fetch 의무

## 예외

- 순수 read-only 디자인/UI 분석 (legacy 원본 만 참조) 는 origin 동기화 불요
- 단 **결정 권고를 포함하는 모든 보고서** 는 origin 검증 의무
