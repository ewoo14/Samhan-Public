---
name: feedback_gradle_test_cache_false_green
description: gradle test 가 UP-TO-DATE/FROM-CACHE 면 실제 미실행 — 검증 신호로 신뢰 금지, --rerun-tasks --no-build-cache 로 genuine 실행 강제
metadata:
  node_type: memory
  type: feedback
---

gradle `:svc:test` 가 `Task ...:test UP-TO-DATE`(증분 스킵) 또는 `FROM-CACHE`(빌드 캐시 복원)로 끝나면 **테스트가 실제로 실행되지 않은 것**이다(`BUILD SUCCESSFUL in Ns` + `N actionable tasks: N up-to-date/from cache`). 이전 실행(예: Codex dev 중, 또는 직전 CI) 결과가 캐시된 것일 뿐 — 이를 내 검증 신호로 신뢰하면 false-green.

**Why:** uncommitted 산출물을 검증할 때 소스가 이미 컴파일·테스트된 상태면 gradle 이 캐시로 즉시 반환한다. `cleanTest` 는 로컬 test 산출물만 지우고 **빌드 캐시(build-cache-1)는 별개**라 동일 입력 해시로 `FROM-CACHE` 다시 복원된다(#726 재개 세션 2회 연속 실측: UP-TO-DATE → cleanTest 후 FROM-CACHE).

**How to apply:** 검증 게이트(커밋·push·머지 전)에서는 `./gradlew :svc:test --rerun-tasks --no-build-cache --console=plain` 로 강제 실행하고, 로그에서 `N actionable tasks: N executed`(up-to-date/cache 아님) + JUnit XML `tests=.. failures=0 errors=0` + **신규 테스트 클래스/메서드 실행**을 실물 확인한다. Testcontainers 실행 시간(수분)이 캐시(수초)와 대비되는지도 대조. → [[feedback_changed_module_full_test_before_push]] · [[feedback_ci_test_filter_false_green]] · verification-before-completion (2026-07-04 #726)

**🚨 XML 집계 시 PowerShell `-Raw` 필수 (2026-07-16 #809 R8 실측 — 하마터면 1333 오집계 보고):** JUnit XML 을 `[xml]$d = Get-Content $x.FullName` 로 파싱하면 **`Get-Content` 가 라인 배열을 반환**해 `[xml]` 캐스트가 *"unexpected token / Name cannot begin with…"* 로 **일부 파일에서 조용히 실패**한다(전체가 아니라 일부만 터져 카운트가 낮게 나옴 = false-under-count). #809 에서 189클래스 중 19개가 파싱 실패해 **1333** 으로 집계됐고 실제는 **1343**(10건 누락). **반드시 `[xml]$d = Get-Content $x.FullName -Raw -Encoding UTF8`** (`-Raw` = 단일 문자열, `-Encoding UTF8` = 한글 클래스명). 집계 후 **파싱실패 카운트($bad)를 0으로 확인**하고, 클래스 수 = XML 파일 수와 일치하는지 대조. gradle `exit 0` 자체는 통과 신호로 신뢰하되(권위), **테스트 총계는 XML 을 직접 세지 말고 gradle 요약 또는 -Raw 집계로**. [[feedback_powershell_utf8_writes]]
