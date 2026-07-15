---
name: feedback-codex-cli-version-model-mismatch
description: codex CLI 이중 설치(PATH npm vs 데스크톱 앱 번들) — MCP는 PATH 쪽을 쓰므로 config.toml 모델 상향 시 PATH codex도 같이 업그레이드해야 400 안 남
metadata:
  type: feedback
---

2026-07-15 실증(#809 R3). `~/.codex/config.toml` 의 `model` 을 상향(예: `gpt-5.6-sol`)하면 **MCP 의 전 codex 호출이 `400 The '<model>' model requires a newer version of Codex` 로 차단**될 수 있다. 원인 = **codex CLI 이중 설치 + 버전 격차**.

**Why**: 이 PC 에 codex 가 두 곳에 있고 버전이 다르다.
- **PATH**: `C:\Users\<user>\AppData\Roaming\npm\codex.ps1` (npm 전역) — **MCP 서버(`codex mcp-server`)가 쓰는 것**
- 데스크톱 앱 번들: `C:\Users\<user>\AppData\Local\OpenAI\Codex\bin\<hash>\codex.exe`

config.toml 모델은 **최신 CLI 를 전제**하는데 PATH 쪽이 뒤처져 있으면(실측 0.131.0 vs 앱 번들 0.144.2 vs npm 최신 0.144.4) 400 이 난다. `model` 파라미터를 명시하든 생략하든 **동일하게 차단**된다(config 기본값이 먹기 때문).

**How to apply**:
1. 증상(`400 ... requires a newer version of Codex`) 시 **버전부터 대조**: `codex --version`(PATH) vs `Get-ChildItem "$env:LOCALAPPDATA\OpenAI\Codex\bin" -Directory` 하위 `codex.exe --version` vs `npm view @openai/codex version`.
2. `npm i -g @openai/codex@latest` 로 **PATH codex 업그레이드**.
3. ⚠️ **업그레이드해도 실행 중인 MCP 서버는 구버전 프로세스로 상주** → **세션 재시작 후에야 반영**된다. 인세션 재연결로는 복구 안 됨(= [[feedback_codex_kill_shares_mcp_vendor]] 와 동일 계열).
4. 🚫 **codex.exe kill 로 재시작 시도 금지** — MCP vendor 공유 바이너리까지 종료돼 세션 도구 레지스트리에서 이탈한다.
5. 재시작 직후 **연결 테스트 1줄**(모델/effort 확인)로 반영 검증 후 본 작업 디스패치.

**부수 교훈**: MCP tool **idle timeout 1800s** 로 대형 디스패치가 abort 돼도 **산출물은 디스크에 남는다**(#809 R1 fix 26파일 실증). `git status` + diff 해시 2회 비교로 **쓰기 종료(STABLE)** 를 확인하고 이어가면 된다 — abort=미수행으로 단정 금지([[feedback_codex_detached_write_settle]] 동일 원칙).
