---
name: feedback_parallel_agent_gradle_shared_tree_contention
description: 병렬 리뷰/QA 서브에이전트가 공유 워킹트리에서 gradle 테스트 동시 실행 시 build 디렉터리 경합→transient false-failure. 권위 신호=CI on exact SHA
metadata:
  type: feedback
---

여러 리뷰/QA 서브에이전트를 **동시**에 디스패치해 각자 같은 워킹트리(`C:\dev\Samhan-Public`)에서 `./gradlew :service:test`를 실행하면, `build/test-results`·`build/` 디렉터리를 **동시 쓰기 경합**해 test-results XML이 통째로 사라지거나 무관한 `No qualifying bean`/DI 오류가 transient하게 발생한다. **PR 코드 결함이 아니며 CI에서 재현 안 됨.**

**Why:** SONNET 대체 모드의 5-agent 리뷰(BE/QA 등이 각자 genuine 재실행)에서 반복 관찰(#688 R2). 단독 재실행하면 사라지는 경합성 실패라 "회귀"로 오인하기 쉽다.

**How to apply:** 병렬 에이전트가 보고한 로컬 gradle 실패는 **exact HEAD SHA의 CI로 교차검증**(`gh api repos/OWNER/REPO/commits/<sha>/check-runs` 또는 `gh pr checks --json`)이 권위 신호. CI green이면 로컬 transient는 공유트리 오염으로 결론(투명 보고·은폐 아님). genuine 로컬 BE 검증이 꼭 필요하면 gradle 실행 에이전트를 **한 번에 하나만**(직렬) 돌리거나 격리 worktree(FE R2 에이전트가 `/c/swt/a`로 실증한 패턴)를 쓴다. [[feedback_gradle_test_cache_false_green]] [[feedback_changed_module_full_test_before_push]]

**🚨 더 심각한 변종 — 병렬 에이전트 git checkout/worktree 경합 (2026-07-12 #796·#797 반복 관찰):** build 디렉터리 경합을 넘어, 한 에이전트가 공유 워킹트리에서 `git checkout main`(또는 `git worktree remove`가 유발한 브랜치 전환)을 실행하면 **다른 에이전트/PM이 보던 브랜치 자체가 바뀐다**. 실제 피해: QA 에이전트가 feature 브랜치 기준으로 `bootJar`를 빌드하려는데 다른 프로세스의 checkout으로 main으로 전환돼 **PR 미반영 jar가 배포**됨(javap 역디컴파일로 신규 메서드 부재 발견 후 재빌드로 복구). gradle 경합(transient·자가복구)과 달리 **잘못된 SHA로 산출물이 만들어져 QA 결론을 오염**시킬 수 있어 더 위험. **How:** ① 병렬 QA/리뷰 에이전트에 "메인 워킹트리에서 `git checkout`/`git switch`/`git worktree remove` 금지, 필요 시 `git worktree add <별도경로>`로 격리하고 제거는 PM이" 명시. ② 라이브 QA(빌드+기동) 에이전트는 **직렬**로. ③ 산출물 빌드 전후 `git rev-parse HEAD`+`git branch --show-current`로 SHA/브랜치 자가 검증. ④ QA가 브랜치 전환 사고를 보고하면 그 라운드 산출물(jar/캡처)의 SHA 정합을 재확인.
