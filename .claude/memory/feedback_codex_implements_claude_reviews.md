---
name: codex-implements-claude-reviews
description: Codex 가 implementer 의무. Claude 직접 구현 금지. Plan/Spec → mcp__codex__codex (sandbox=danger-full-access) implement → Claude 5-team + Codex 5-section review → 머지.
metadata:
  type: feedback
---

# Codex 가 구현 의무 — Claude 직접 구현 금지

> **🚨 사용자 4차 재지적 (2026-05-22, PR #297 직후)**: "왜 자꾸 워크플로우를 어기는 건지 이해가 안됨. 컨텍스트에 무조건 메모리 등의 필수 문서를 확인 후 뭐든 진행했으면 함. **코덱스 없이 개발 진행한 것으로 보임**"

## 핵심 규칙

본 repo 의 **모든 구현은 Codex 가 수행한다** (`mcp__codex__codex` MCP 도구, `sandbox=danger-full-access`).

Claude 의 역할은:
1. **Brainstorming** (spec 결정)
2. **Plan 작성** (task 분해)
3. **Codex 호출** — 각 task 또는 slice 를 implementer 로 디스패치
4. **5-team review** (BE/FE/Designer/QA/DevOps subagent)
5. **TM 통합 PR comment**
6. **Codex review fix** 호출
7. **Verify** (단계 9, BE+QA spot-check)
8. **PM 자동 머지**

**Claude 가 직접 코드를 작성하지 않는다.** Slice/Sprint 시작 시점에 즉시 Codex 디스패치.

## 위반 사례 (2026-05-22 회고)

PR #297 (Issue 4 Slice 1 — 통합 알림 센터 BE) 에서 Claude 가 직접 9 task 구현 + 단위 test 작성 + 컴파일 검증 후 PR 발행. Codex 미사용. 사용자 4차 재지적.

근거 메모리:
- [[codex-plugin-setup]] — Codex CLI MCP 사용 (`mcp__codex__codex`)
- [[dual-5agent-review]] — Claude review + Codex review 양쪽 cross-check
- [[cycle-n2-mandatory]] — 사이클 1 의 implementer 가 누구인지 명시 부족 → 본 메모리로 보강

## How to apply

### Plan 작성 후 즉시 Codex 디스패치

```yaml
mcp__codex__codex:
  prompt: |
    Plan: docs/superpowers/plans/<plan>.md 의 Task 1~N 일괄 구현.
    각 task 의 step 따라 commit + push.
    완료 후 PR 발행 또는 head SHA 보고.
  sandbox: "danger-full-access"
  approval-policy: "never"
  cwd: "C:\\dev\\SamhanLogis"
```

### Claude 의 역할 매트릭스

| 단계 | 도구 | 비고 |
|---|---|---|
| Brainstorm | `Skill brainstorming` | Claude 직접 |
| Spec 작성 | Write | Claude 직접 |
| Plan 작성 | `Skill writing-plans` + Write | Claude 직접 |
| **구현** | **`mcp__codex__codex`** | **Codex 의무, Claude 금지** |
| 5-team review | `Agent subagent_type=general-purpose` | Claude subagent 5 병렬 |
| Codex review | `mcp__codex__codex` (sandbox=danger-full-access) | 5 section |
| Codex fix | `mcp__codex__codex` (sandbox=danger-full-access) | direct commit + push |
| Claude verify | `Agent` | BE + QA spot-check |
| PM 머지 | `gh pr merge` | Claude 직접 |

## 예외 (Claude 직접 구현 허용)

다음 경우만 Claude 직접 작업 가능:
- **memory/docs/spec/plan 문서 작성** (`.claude/memory/*.md`, `docs/superpowers/specs/*`, `docs/superpowers/plans/*`)
- **git workflow 명령** (commit, push, merge, branch)
- **gh CLI 호출** (PR create, comment, watch, merge)
- **사용자 직접 명시 시** (예: "1줄 fix 라 직접 해도 됨")

코드 / test / config 파일은 모두 Codex 가 작성.

## 시작 전 필수 확인 (사용자 4차 지적)

**Sprint/Slice 시작 시 의무**:
1. [[codex-plugin-setup]] — sandbox=danger-full-access 호출 패턴
2. [[dual-5agent-review]] — 사이클 워크플로우
3. [[cycle-n2-mandatory]] — 옵션 C 사이클 N=2 의무
4. **[[codex-implements-claude-reviews]]** (본 메모리) — Codex 구현 의무

위 4 메모리 모두 mental check 한 후 진행. 위반 시 사용자 재지적 + 사이클 재진입.

## 관련 메모리

- [[codex-plugin-setup]]
- [[dual-5agent-review]]
- [[cycle-n2-mandatory]]
- [[multi-agent-team-pattern]]
- [[continuous-docs-sync]]
