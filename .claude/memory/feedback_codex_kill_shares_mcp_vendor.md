---
name: feedback_codex_kill_shares_mcp_vendor
description: codex exec 프로세스 일괄 종료가 MCP codex 서버 vendor 바이너리까지 죽여 세션 MCP 이탈
metadata:
  type: feedback
---

codex exec 백그라운드를 중단할 때 `Name=codex.exe` 로 일괄 `Stop-Process` 하면, **MCP codex 서버(`codex mcp-server`)가 공유하는 vendor 바이너리**(`@openai/codex-win32-x64/vendor/...codex.exe`)까지 함께 종료되어 현재 세션의 MCP codex 도구가 도구 레지스트리에서 이탈한다(ToolSearch 미검색). `claude mcp list` 는 별도 health-probe라 `✔ Connected` 로 보이지만 **인세션 `/mcp` 재연결로도 세션 도구 레지스트리는 복구 안 됨** → **세션 재시작만이 확실**(디스크/git/stash/scratchpad 는 재시작 무관 보존).

**Why:** codex exec 와 codex mcp-server 가 같은 vendor `codex.exe` 바이너리를 프로세스로 띄워, 이름 기반(`Name -eq 'codex.exe'`) 일괄 kill 이 둘을 구분하지 못한다.

**How to apply:** codex exec 만 중단하려면 **그 exec 의 특정 PID 트리만** 종료하라 — TaskStop 으로 셸 래퍼 종료 후 남는 detached 프로세스는 내가 launch 한 exec 의 sh/node/codex 자식 PID만 지정 kill(commandline 에 `exec` 포함분). MCP 서버의 codex.exe(commandline `mcp-server`)는 **반드시 제외**. 실수로 MCP 를 끊었으면 무리한 인세션 복구 시도 말고 즉시 세션 재시작 안내(handoff 에 재개 지점 박제 선행). [[feedback_codex_mcp_session_limit]] [[feedback_codex_permission_new_session]] [[feedback_codex_rescue_unreliable_use_mcp]]
