---
name: desktop-typecheck-command
description: clients/desktop 검증 = npm run typecheck(tsconfig.node+web) + npm run lint + build 셋 다 — CI "Frontend Desktop (typecheck+lint+build)" 와 동일. typecheck/vitest green ≠ CI green (PR #386 TS2367, A2-1b#554 lint FAIL 회고)
metadata:
  type: feedback
---

# clients/desktop 타입검증 = `npm run typecheck` (raw tsc 금지)

clients/desktop 변경 검증 시 **반드시 `cd clients/desktop && npm run typecheck`** (= `tsc -p tsconfig.node.json --noEmit && tsc -p tsconfig.web.json --noEmit`)를 실행한다. 루트 기본 `npx tsc --noEmit` 은 더 느슨해 일부 에러를 놓친다.

**Why:** PR #386 에서 mock.ts 에 `role === 'MASTER'` 비교(계정 role 타입 = `'MANAGER'|'SALES'|'DISPATCH'`, overlap 없음 → TS2367)가 들어갔는데 로컬 `npx tsc --noEmit` 통과·CI `npm run typecheck`(tsconfig.web strict) fail. "Frontend Desktop (typecheck + lint + build)" 잡 1건 fail 로 머지 1라운드 지연.

**A2-1b #554 재발(2026-06-21)**: `// eslint-disable-next-line jsx-a11y/no-autofocus`(미등록 룰 참조)가 lint 하드에러(`Definition for rule ... was not found`)인데 **typecheck + vitest 만 돌려 green 으로 PR 주장 → CI Frontend Desktop FAIL**. `npm run lint` 누락이 false-green. 머지 전 lint 필수.

**How to apply:** desktop fix/리뷰 후 = `npm run typecheck` (+ `npm run lint` 0 error, `npm run build` 성공). subagent 디스패치 prompt 에도 raw tsc 대신 **`npm run typecheck` 명시**. CI 잡 "Frontend Desktop (typecheck + lint + build)" 와 동일 명령으로 사전 재현. 관련 [[feedback_korean_path_jdk]], [[feedback_preauth_migration_lessons]].
