---
name: codex-detached-write-settle
description: detached(nohup &)·백그라운드 codex exec 직후 git status 가 비면 "Codex 미수행" 으로 단정 말 것 — 아직 쓰는 중일 수 있다. 작업트리 변경이 안정될 때까지 폴링 후 판단
metadata:
  type: feedback
---
2026-06-12 PR #471(#2) 회고. `nohup codex exec ... &` 로 detached 실행한 직후 `git status` 가 코드 변경 0(메모리만) 으로 보여 "Codex 가 서술만 하고 실제 edit 안 함" 으로 오판 → surgical 재디스패치 프롬프트(v3)까지 작성했으나, 실제로는 **Codex 가 그 시점에 아직 파일을 쓰는 중**이었다. 수십 초 뒤 13개 파일(BE 8 + FE 5)이 모두 적용됨(+343/-61). 재디스패치는 불필요했고 취소함.

- detached/백그라운드 codex 는 **출력 파일(`-o`) 이 비거나, git status 가 부분적이어도 "미수행" 단정 금지**. nohup 으로 분리되면 자동 task-notification 도 안 온다.
- 판단 전 **작업트리 변경 안정화 폴링**: `git status --short -- <paths>` 의 md5 가 N회(예 3회×10s) 연속 동일할 때 "완료"로 간주. 또는 `tasklist | grep codex.exe` 가 0 이 될 때까지.
- Codex 출력이 "기존 구현 서술"처럼 보여도, **실제 diff 로 검증**(`git --no-pager diff --stat`)이 진실. 출력 텍스트 ≠ 실제 변경.
- 교훈 보강: 가능하면 codex exec 를 **harness 추적 백그라운드(run_in_background, nohup·`&` 없이)** 로 띄워 자동 완료 알림을 받게 하라. `&`/nohup 분리는 추적을 끊어 폴링을 강제한다.

**Why:** 조기 단정은 멀쩡한 Codex 산출을 버리고 중복 재디스패치(토큰·시간 낭비, 충돌 위험)를 부른다.
**How to apply:** detached codex 후 (1) 변경 안정화 폴링 → (2) `git diff --stat` 로 실제 변경 확인 → (3) 그 다음에 검토/재디스패치 판단. 관련: [[pm-codex-progress-verification]] [[codex-exec-stdin-hang]] [[codex-implements-claude-reviews]].
