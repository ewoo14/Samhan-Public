---
name: feedback_parallel_agent_gradle_shared_tree_contention
description: 병렬 리뷰/QA 서브에이전트가 공유 워킹트리에서 gradle 테스트 동시 실행 시 build 디렉터리 경합→transient false-failure. 권위 신호=CI on exact SHA
metadata:
  type: feedback
---

여러 리뷰/QA 서브에이전트를 **동시**에 디스패치해 각자 같은 워킹트리(`C:\dev\Samhan-Public`)에서 `./gradlew :service:test`를 실행하면, `build/test-results`·`build/` 디렉터리를 **동시 쓰기 경합**해 test-results XML이 통째로 사라지거나 무관한 `No qualifying bean`/DI 오류가 transient하게 발생한다. **PR 코드 결함이 아니며 CI에서 재현 안 됨.**

**Why:** SONNET 대체 모드의 5-agent 리뷰(BE/QA 등이 각자 genuine 재실행)에서 반복 관찰(#688 R2). 단독 재실행하면 사라지는 경합성 실패라 "회귀"로 오인하기 쉽다.

**How to apply:** 병렬 에이전트가 보고한 로컬 gradle 실패는 **exact HEAD SHA의 CI로 교차검증**(`gh api repos/OWNER/REPO/commits/<sha>/check-runs` 또는 `gh pr checks --json`)이 권위 신호. CI green이면 로컬 transient는 공유트리 오염으로 결론(투명 보고·은폐 아님). genuine 로컬 BE 검증이 꼭 필요하면 gradle 실행 에이전트를 **한 번에 하나만**(직렬) 돌리거나 격리 worktree(FE R2 에이전트가 `/c/swt/a`로 실증한 패턴)를 쓴다. [[feedback_gradle_test_cache_false_green]] [[feedback_changed_module_full_test_before_push]]
