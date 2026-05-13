---
name: GitHub PR + Issues Workflow with Team-Lead Approval
description: All work goes through GitHub PRs with TL → PM → 대표 approval per plan §12; track via Issues
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**Rule**: Once a GitHub remote is set up, all subsequent work must flow through GitHub Pull Requests and Issues. The approval chain follows plan §12:

```
개발자 → TL(팀장) 승인 → PM 승인 → 대표 최종 승인 → main 머지
```

**Why**: User explicitly directed this ("깃에 있는 pull requests 및 issues 기능도 활용하여 각 팀의 팀장 승인 후 사용할것"). Plan §12 already specifies this workflow in detail (branch strategy, merge checklist, label scheme).

**중요 단순화 (2026-05-04)**: 사용자(대표)가 명시 — "TM과 PM 모두 에이전트이므로 알아서 승인하도록 하고, 나만 승인의 의미로 머지하도록 할게." 따라서:
- **TM 승인 / PM 승인 = Claude 가 PR 코멘트로 자동 작성** (gh pr comment, "[TM/Team-XXX 승인] ...", "[PM 승인] ..." 형태)
- **대표 결재 = 대표가 GitHub 에서 머지 버튼 클릭** (유일한 머지 행위 = 유일한 승인 머지)
- **PR review approve API 사용 불가**: gh-인증 계정이 PR author 와 동일하면 GitHub가 self-approval 거부함. 따라서 review approve 가 아니라 일반 issue comment 로 승인 의사 기록.
- **결과**: 슬라이스당 PR 1건, 머지 1번. 중간 통합 브랜치(`team/<name>`, `develop`)는 의미 약화됨 — 한 사람 + 에이전트 환경에서는 단계별 통합 가치가 없음. **PR base 는 항상 `main`**.
- **`develop` / `team/<name>` 브랜치**: 옵션. 대규모 리팩터처럼 여러 슬라이스를 하나로 묶어 결재받고 싶을 때만 사용. 일반 슬라이스는 직접 main 으로 PR.
- **Branch 전략 단순화**: `<team>/feature/<slug>` → PR → `main`. 머지 후 feature branch 삭제.

**How to apply**:
- **Branches**: per plan §12.1
  - `main` — 대표 승인 후만 머지
  - `develop` — PM 승인 후 팀 브랜치에서 머지 (CI 통과 + 교차 QA PASS 필수)
  - `team/<name>` — 각 TL이 관리 (team/infra, team/auth, team/product, team/inventory, team/slip, team/acct, team/partner, team/grpw, team/noti, team/log, team/dash, team/migrate, team/uiux, team/devops)
  - **개인 브랜치 네이밍**: `<team>/feature/<name>` 에서 `<team>` 은 팀명만 (예: `auth`), `team/auth` 가 아님. 이유: git ref가 파일/디렉터리 구조라 `team/auth` 브랜치와 `team/auth/feature/...` 브랜치는 동시 존재 불가. 따라서 실제 사용 형태는:
    - `auth/feature/<name>`, `auth/bugfix/<issue#>` (Team-Auth 개인 작업)
    - `product/feature/<name>` (Team-Product 개인 작업)
    - `infra/feature/<name>` (Team-Infra 개인 작업)
    - … 각 팀명/feature/<name>
  - `hotfix/<issue#>` — PM 직접 관리

- **PRs**: every change opens a PR. PR description must reference the relevant Issue (Closes #N or Refs #N). PR title = Korean conventional commit format. Requested reviewer = TL. After TL approves, retarget to PM, then to 대표 if main-bound.

- **Issues**: every feature, bug, or task starts as an Issue with the labels from plan §12.5 (`team:<name>`, `priority:<level>`, `phase:<n>`, `doc:<type>`, etc.). Discussion happens on the Issue; PR closes it.

- **Labels** to create on the repo on first remote-setup:
  - team:infra, team:auth, team:product, team:inventory, team:slip, team:acct, team:partner, team:grpw, team:noti, team:log, team:dash, team:migrate, team:uiux, team:devops
  - cross-qa
  - priority:critical, priority:high, priority:normal
  - status:review, status:approved, status:rejected
  - doc:plan, doc:qa-report
  - phase:1, phase:2, phase:3, phase:4, phase:5, phase:6, phase:7

- **Initial bootstrap commit** (Phase 1 first slice + A) goes directly on `main` since no team yet exists. After that point, every subsequent change MUST go through the PR workflow.

- **Tools**: prefer `gh` CLI for issue/PR creation. If not installed, install via winget. If not authenticated, the user needs to run `gh auth login` (interactive — Claude can't do that).
