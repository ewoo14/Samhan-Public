---
name: codex-config-nul-corruption
description: codex MCP/exec 둘 다 실패 시 ~/.codex/config.toml NUL 손상 의심 — 비정상 종료로 전체가 0x00 덮임. 백업 후 최소 config 재작성 + model="gpt-5.5" 명시(기본 gpt-5.3-codex=ChatGPT 계정 미지원 400). auth.json 정상이면 재로그인 불요.
metadata:
  type: feedback
---

2026-06-20 세션 회고. codex MCP `-32000 Connection closed` + `codex exec` 도 `config.toml:1:N key with no value` 에러로 둘 다 실패.

## 🪤 근본원인
`C:\Users\user\.codex\config.toml` 이 **전체 바이트가 NUL(0x00)** 로 손상(파일시스템 NUL-fill — 비정상 종료 시 발생). codex CLI 바이너리는 정상이나 config 파싱 실패로 **MCP 서버 기동·exec 둘 다 깨짐**. [[codex-mcp-session-limit]] 의 "MCP closed = 세션 한정" 으로 오인하기 쉬우나, **exec 도 동시에 깨졌으면 config 손상**을 먼저 의심.

## 진단 / 복구
1. 진단: `[System.IO.File]::ReadAllBytes("$HOME/.codex/config.toml")` 의 distinct codepoint 가 `0` 하나뿐 = NUL 손상. `auth.json` 비-NULL 바이트 존재하면 **인증 정상(재로그인 불요)** — config 만 복구.
2. 복구(전역 설정 수정 — 사용자 승인): 손상본 백업(`config.toml.corrupted-YYYYMMDD`) 후 최소 유효 config 재작성. **`model = "gpt-5.5"` 명시 필수** — 미지정 시 CLI 기본값이 `gpt-5.3-codex` 로 잡혀 `The 'gpt-5.3-codex' model is not supported when using Codex with a ChatGPT account` 400. ChatGPT 계정은 `*-codex` 접미 모델 전부 미지원 → 접미 없는 `gpt-5.5`/`gpt-5.4`. 모델/sandbox/approval 은 호출별 플래그(`-c model_reasoning_effort=high`, `--sandbox`)로 override.
3. 검증: `codex exec --sandbox read-only -c model_reasoning_effort=low "..." </dev/null` (반드시 `</dev/null` [[codex-exec-stdin-hang]]).
4. 세션 내 MCP(`mcp__codex__codex`) 는 시작 시 깨진 config 로 물려 여전히 Failed → `/mcp` 재연결 또는 새 세션. 단 **codex exec 는 config 복구 즉시 작동**하므로 리뷰/구현은 exec 로 진행 가능.

## How to apply
codex 양쪽(MCP+exec) 동시 실패 → "세션 한정" 단정 말고 config.toml NUL/파싱 먼저 점검. 관련: [[codex-mcp-session-limit]] [[codex-plugin-setup]] [[codex-sandbox-git]] [[codex-model-auto-switch]] [[temp-multimodel-workflow]].
