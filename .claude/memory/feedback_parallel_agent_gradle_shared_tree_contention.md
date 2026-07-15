---
name: feedback_parallel_agent_gradle_shared_tree_contention
description: 병렬 리뷰/QA 서브에이전트가 공유 워킹트리에서 gradle 테스트 동시 실행 시 build 디렉터리 경합→transient false-failure. 권위 신호=CI on exact SHA
metadata:
  type: feedback
---

여러 리뷰/QA 서브에이전트를 **동시**에 디스패치해 각자 같은 워킹트리(`C:\dev\Samhan-Public`)에서 `./gradlew :service:test`를 실행하면, `build/test-results`·`build/` 디렉터리를 **동시 쓰기 경합**해 test-results XML이 통째로 사라지거나 무관한 `No qualifying bean`/DI 오류가 transient하게 발생한다. **PR 코드 결함이 아니며 CI에서 재현 안 됨.**

**Why:** SONNET 대체 모드의 5-agent 리뷰(BE/QA 등이 각자 genuine 재실행)에서 반복 관찰(#688 R2). 단독 재실행하면 사라지는 경합성 실패라 "회귀"로 오인하기 쉽다.

**How to apply:** 병렬 에이전트가 보고한 로컬 gradle 실패는 **exact HEAD SHA의 CI로 교차검증**(`gh api repos/OWNER/REPO/commits/<sha>/check-runs` 또는 `gh pr checks --json`)이 권위 신호. CI green이면 로컬 transient는 공유트리 오염으로 결론(투명 보고·은폐 아님). genuine 로컬 BE 검증이 꼭 필요하면 gradle 실행 에이전트를 **한 번에 하나만**(직렬) 돌리거나 격리 worktree(FE R2 에이전트가 `/c/swt/a`로 실증한 패턴)를 쓴다. [[feedback_gradle_test_cache_false_green]] [[feedback_changed_module_full_test_before_push]]

**🚨 세 번째 변종 — 병렬 에이전트 공유 라이브 DB 쓰기 경합 (2026-07-16 #809 R6 실증):** gradle/worktree 를 넘어 **공유 dev Docker 스택의 DB** 도 같은 함정이다. R6 에서 PM 이 5차원(BE/FE/Design/DevOps/QA)을 **병렬** 디스패치했는데, BE 차원이 결함 확증용으로 **라이브 PUT 프로브**를 돌리는 동안 QA 차원이 같은 스택에서 라이브 QA 스위트를 실행 → **BE 의 PUT(03:01:02)이 만든 오염행을 QA 의 시나리오 12a(03:01:04)가 자기 것으로 오인**해 `Expected 0 / Received 1` **false-RED**. QA 가 **격리 재실행 PASS** 로 교차 오염을 스스로 확정했다(그 과정에서 오염 출처가 BE 프로브임을 타임스탬프로 특정 — 부수적으로 BE finding 의 독립 증거가 되기도 했다).

**Why:** 라이브 QA 스펙의 단언이 **거래처 전역 카운트**(예: "이 거래처의 구성품 기억행 = 0")처럼 넓으면, 같은 거래처를 건드리는 **어떤 외부 트래픽에도 오염**된다. gradle 경합(transient·자가복구)과 달리 **결론이 뒤집히고**, "회귀 발견"으로 오인하면 없는 결함을 쫓게 된다. 반대로 진짜 결함을 "동시 실행 탓" 으로 무마할 위험도 대칭으로 존재한다.

**How to apply:** ① **라이브 스택에 쓰는 차원은 직렬화** — 리뷰 라운드에서 "라이브 프로브 허용" 을 여러 차원에 동시에 주지 말 것. 읽기(psql SELECT·curl GET)만 병렬 허용. ② 부득이 병렬이면 **차원별 전용 거래처/품목** 을 배정해 대상 쌍을 겹치지 않게. ③ 라이브 QA 스펙의 단언은 **자기 저장 창구간 diff** 또는 **자기 시나리오 전용 쌍**으로 좁혀 외부 트래픽에 면역이 되게 설계(전역 카운트 금지). 단 **자기 쌍에 대해서는 정확히 단언**(0/1 을 "≥0" 으로 무르게 하는 건 약화). ④ 라이브 FAIL 은 **단독 재실행으로 재현 확인 후에만** 결함으로 확정. ⑤ 프로브가 남긴 데이터는 **대상 쌍만 `WHERE` 로** 정리(테이블 전체 DELETE 금지 — 권한 분류기도 차단한다).

**🚨 더 심각한 변종 — 병렬 에이전트 git checkout/worktree 경합 (2026-07-12 #796·#797 반복 관찰):** build 디렉터리 경합을 넘어, 한 에이전트가 공유 워킹트리에서 `git checkout main`(또는 `git worktree remove`가 유발한 브랜치 전환)을 실행하면 **다른 에이전트/PM이 보던 브랜치 자체가 바뀐다**. 실제 피해: QA 에이전트가 feature 브랜치 기준으로 `bootJar`를 빌드하려는데 다른 프로세스의 checkout으로 main으로 전환돼 **PR 미반영 jar가 배포**됨(javap 역디컴파일로 신규 메서드 부재 발견 후 재빌드로 복구). gradle 경합(transient·자가복구)과 달리 **잘못된 SHA로 산출물이 만들어져 QA 결론을 오염**시킬 수 있어 더 위험. **How:** ① 병렬 QA/리뷰 에이전트에 "메인 워킹트리에서 `git checkout`/`git switch`/`git worktree remove` 금지, 필요 시 `git worktree add <별도경로>`로 격리하고 제거는 PM이" 명시. ② 라이브 QA(빌드+기동) 에이전트는 **직렬**로. ③ 산출물 빌드 전후 `git rev-parse HEAD`+`git branch --show-current`로 SHA/브랜치 자가 검증. ④ QA가 브랜치 전환 사고를 보고하면 그 라운드 산출물(jar/캡처)의 SHA 정합을 재확인.
