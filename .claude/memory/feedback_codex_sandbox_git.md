# Codex MCP 디스패치 — 커밋은 Claude 대행 + approval-policy never

`mcp__codex__codex` 를 `sandbox: "workspace-write"` 로 호출하면 Codex 가 코드는 정상 수정하지만 **`.git/objects` 쓰기가 막혀 `git add`/commit 이 실패**한다 (`insufficient permission for adding an object to repository database .git/objects`). `approval-policy` 가 `on-failure`/`untrusted` 이면 이 실패 시 Codex 가 **"MCP server codex requests your input" 승인 팝업**을 띄운다 (사용자가 싫어함).

**Why:** workspace-write 샌드박스는 `.git` 을 보호 영역으로 취급해 객체 쓰기를 거부. 반면 Claude 의 Bash 도구는 정상 권한(`.git/objects` 는 user `ewoo2` 소유 `drwxr-xr-x`)이라 commit 가능.

**How to apply (Codex 디스패치 표준):**
1. **`approval-policy: "never"`** 로 호출 → Codex 가 승인 팝업을 절대 띄우지 않음.
2. Codex 프롬프트에 **"git add/commit/branch 등 git 명령 실행 금지 — 파일 수정만 하고 commit 은 Claude 가 대행"** 명시.
3. Codex 완료 후 Claude 가 `git status` 로 변경 확인 → targeted compile/test 로 검증([[verification-before-completion]]) → plan task 기준 logical commit 으로 Claude 가 commit ([[korean-commits]], Co-Authored-By Codex 명시).
4. model: **스테이지별 명시 의무** (2026-07-15 워크플로우 전면 개편 [[feedback_canonical_workflow]]) — 기획검수/적대리뷰 = **`gpt-5.6-sol`**, 구현 = **`gpt-5.6-luna`** (2026-07-15 집PC codex CLI 실측 OK) + `config:{model_reasoning_effort:"high"}` (보안/migration 은 `"xhigh"`). 주의: `*-codex` 접미 모델은 본 ChatGPT 계정 미지원(400 error) — 접미 없는 모델만.

**⚠️ codex exec 집PC(Windows) 샌드박스 실측 (2026-07-03, codex-cli 0.130.0)**:
- `--sandbox read-only`: 파일 읽기/`git show·log·diff·status` 는 통과하나 **`git rev-parse` 실행이 정책 차단**("rejected: blocked by policy" 0ms) — HEAD 검증은 `git show -s --format=%H HEAD` 또는 `.git` ref 파일 직접 판독으로 지시할 것(rev-parse 지시 시 엄격한 에이전트는 중단 보고).
- `--sandbox workspace-write`: 집PC 에선 **파일 쓰기도 차단**("patch rejected: writing is blocked by read-only sandbox") — 사실상 read-only 강등. **fix 디스패치는 `danger-full-access`**(집PC 검증 모드) + git 금지 프롬프트 + 종료 후 `git status/diff` 대조로 규율 유지.
- 리뷰 디스패치 표준: read-only + 상기 HEAD 검증 지시 + "종료 시 git status 무변경 검증" 명시(에이전트가 스스로 확인·보고).

[[codex-implements-claude-reviews]] 의 "workspace-write + Claude commit 대행 폴백" 을 구체화. [[codex-plugin-setup]] [[codex-mcp-session-limit]] 참조.
