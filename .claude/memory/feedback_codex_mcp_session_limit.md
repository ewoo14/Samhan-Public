---
name: codex-mcp-session-limit
description: Codex MCP 도구 (`mcp__codex__codex`) 는 본 Claude Code 세션의 deferred tool registry 가 한 번 close 후 ToolSearch 로 재등록 불가. MCP 서버 자체는 Connected 정상. 새 세션 시작 시 자동 해소. 본 세션 한정 환경 한계로 사이클 review 시 [feedback_dual_5agent_review] line 188 예외 적용 가능.
metadata:
  type: feedback
---

## 핵심 규칙

**Codex MCP 도구가 "MCP error -32000: Connection closed" 또는 ToolSearch `select:mcp__codex__codex` 가 `No matching deferred tools found` 반환 시**:

1. **본 세션의 deferred tool registry 한계** — Claude Code 세션 한정. MCP 서버 자체는 정상.
2. **회복 방법** = 새 Claude Code 세션 시작 (자동 해소). 본 세션에서는 회복 불가.
3. **본 세션에서 fallback** = `codex exec` Bash 우회 (codex CLI 직접 호출) 또는 Agent (general-purpose) 가 Codex 5-agent 역할 대체.

## 진단 체크리스트

```powershell
# 1. MCP 서버 자체 상태
claude mcp list
# 기대: codex: codex mcp-server - ✓ Connected

# 2. codex CLI 정상
codex --version  # 기대: codex-cli 0.131.0 또는 이상
codex exec --help  # `codex exec [PROMPT]` 사용 가능

# 3. deferred tool registry 재등록 시도 (보통 실패)
# ToolSearch select:mcp__codex__codex → No matching deferred tools found
```

→ 1+2 정상 + 3 실패 = **본 세션 한정 한계 확정**

## Why

- **PR #271 (MIG-3) 사이클 1 후반** (2026-05-20) — Codex QA reviewer 호출 중 `MCP error -32000: Connection closed` 발생
- 이후 ToolSearch 가 `No matching deferred tools found` 반환 → 본 세션에서 Codex 도구 사용 불가
- 사이클 2/3 Codex re-review = Agent (general-purpose) 가 Claude 통합 입장 (BE+QA+DevOps) 대체
- [feedback_dual_5agent_review] line 188 예외 ("Codex sandbox EPERM 등 환경 한계로 사이클 fix 불가능한 항목") 적용

## How to apply

**MCP error -32000 발생 시**:
1. 즉시 `claude mcp list` + `codex exec --help` 로 서버/CLI 정상 확인
2. 정상이면 본 세션 한정 한계 확정 → 사용자에게 보고
3. fallback 선택:
   - (A) **새 세션 권장** (사용자 동의 시) — 가장 확실, 9회차 워크플로우 정상 진행 가능
   - (B) **본 세션 codex exec Bash 우회** — `codex exec --sandbox workspace-write -- "prompt"` 형태로 codex CLI 직접 호출
   - (C) **Agent (general-purpose) 로 Codex 5-agent 역할 대체** — [feedback_dual_5agent_review] line 188 환경 한계 예외 적용, TM 통합 시 명시
4. 핸드오프 [docs/handoff/CURRENT-WORK.md](../../docs/handoff/CURRENT-WORK.md) 에 진행 상태 갱신 의무

## 2026-06-30 추가 환경 실패 모드 (codex exec, S3-0 PR #680)

`codex exec` Bash 우회(fallback B)도 이 Windows 환경에서 3종 실패 — **Agent 대체(fallback C)가 유일 안정**:

1. **sub-agent fork 에러**: 프롬프트에 "5-agent 병렬"/sub-agent 요청 시 codex가 내부 SpawnAgent fork 시도 → `Full-history forked agents inherit the parent agent type, model, and reasoning effort` 에러로 세션 중단. → codex exec 프롬프트는 **단일 리뷰어(sub-agent/SpawnAgent/fork 금지 명시)** 로 작성.
2. **desktop heap 고갈**: codex exec가 다수 프로세스 fork(리뷰 1회에 ~19 codex 프로세스) + gradlew/vitest 실행 누적 → Windows desktop heap 고갈로 신규 Node 프로세스가 `-1073741502`(0xC0000142 STATUS_DLL_INIT_FAILED)로 즉사. codex가 vitest 실행 못해 무한 retry→kill. → 잔존 codex 프로세스 정리(`taskkill //F //IM codex.exe`)로 Node 회복(`node --version` 검증). codex exec 반복 실행 회피.
3. **codex MCP 연결 끊김**: 본 세션 중 `mcp__codex__codex` deferred tool 소실(ambient 알림).

→ **결론: 이 환경에서 듀얼리뷰 Codex측은 general-purpose Agent(harness 추적·프로세스 누적 없음)로 대체가 가장 안정.** codex exec는 짧은 단발(구현 1회)은 완주하나 리뷰 반복엔 부적합. PM 종합·핸드오프에 "Codex 대체(환경예외)" 투명 고지. [[feedback_emit_real_tool_calls]]·[[temp-multimodel-workflow]] 연계.

## 관련 메모리

- [[dual-5agent-review]] — 9회차 워크플로우 (사이클 review/fix)
- [[codex-cli-mcp]] — Codex CLI MCP 서버 사용 기본
- [[arologis-extract-autopilot]] — 자율 진행 권한
