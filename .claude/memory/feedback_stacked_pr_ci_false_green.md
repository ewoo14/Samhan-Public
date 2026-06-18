---
name: feedback-stacked-pr-ci-false-green
description: "stacked PR(base=feat/...)은 ci.yml branches:[main]이라 BE 빌드/JUnit 미트리거 false-green. base 머지 후 base=main 재생성"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: b9ce13e8-ed39-4a45-89a5-ff3c53f85914
---

stacked PR(base=다른 feature 브랜치)은 `.github/workflows/ci.yml`의 `pull_request: branches: [main]` 조건 때문에 BE 빌드/JUnit 등 main-타깃 잡이 **미트리거** → FE/모바일/GitGuardian 5체크만 green = false-green(BE 미검증 머지 위험).

**Why**: 수식 빌더 F6 #500이 base=feat/formula-builder-f1(F1 위 stacked)이라 BE 빌드 24체크 누락. DevOps 리뷰 단독 적발.

**How to apply**:
- base PR 먼저 머지 → stacked PR을 base=main으로 전환 후 full CI 재실행.
- **base 브랜치 삭제(--delete-branch) 시 stacked PR은 base 소실로 auto-CLOSED** → base=main으로 **PR 재생성**(gh pr create). 주의: `gh pr edit --base`는 token read:org scope 부족으로 실패·`gh pr reopen`은 base 삭제로 불가.
- base=main 재생성하면 F1 머지커밋이 공통조상이라 diff=신규 커밋만(중복 없음), mergeable.

[[feedback_ci_test_filter_false_green]] [[feedback_changed_module_full_test_before_push]] [[project_formula_builder_epic]]
