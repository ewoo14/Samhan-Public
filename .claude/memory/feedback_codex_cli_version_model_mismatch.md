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

**부수 교훈**: MCP tool **idle timeout 1800s** 로 대형 디스패치가 abort 돼도 **산출물은 디스크에 남고 Codex 는 계속 돈다**(#809 R1 fix 26파일 · R3 fix 28+9파일 실증). abort=미수행으로 단정 금지([[feedback_codex_detached_write_settle]] 동일 원칙).

🚨 **정정 (2026-07-15 #809 R3 실증) — `git diff` 해시 2회 비교는 false-STABLE 을 준다**
이전 판의 "diff 해시 2회 비교로 쓰기 종료(STABLE) 확인" 지침은 **틀렸다**. Codex 가 **검증/사고 중인 구간엔 파일을 안 쓰므로** 20초 간격 해시가 동일하게 나온다 → "종료" 오판. 실제로는 그 시점에 컴파일·IT 실행·promtool 검증이 진행 중이었고 이후에도 계속 썼다.

**진짜 종료 신호 (이걸 쓸 것)**:
1. **rollout 로그의 LastWriteTime** — `~/.codex/sessions/<yyyy>/<MM>/<dd>/rollout-<ts>-<threadId>.jsonl`. 이게 90초+ 무변동이어야 턴 종료.
2. **codex PID 생존** — `Get-Process codex`. 디스패치 시각과 `StartTime` 이 맞는 PID 가 살아 있으면 아직 진행 중. (🚫 kill 금지 → [[feedback_codex_kill_shares_mcp_vendor]])
3. 대기는 Bash `run_in_background` + `until [ $(( $(date +%s) - $(stat -c %Y "$f") )) -ge 90 ]` 로 **단발 통지**(Monitor 는 다건용).

💡 **abort 로 잃은 threadId·최종보고는 rollout 로그에서 회수된다** — 파일명에 **threadId 가 박혀 있고**(`rollout-…-<threadId>.jsonl`) assistant 메시지 전문이 들어 있다. 회수 후 **`mcp__codex__codex-reply`(threadId)** 로 같은 세션을 이어받아 정식 보고를 받으면 된다(재디스패치 불필요).
⚠️ 이 jsonl 은 **UTF-8** 인데 Windows PowerShell 5.1 `Get-Content` 기본 인코딩이 ANSI 라 **한글이 mojibake** 로 나온다 → `[System.IO.File]::ReadAllLines($f, [System.Text.Encoding]::UTF8)` 로 읽되 **codex 가 쓰는 중이면 파일 잠금**이라 실패하니 종료 후 읽을 것.
