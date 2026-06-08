---
name: changed-module-full-test-before-push
description: BE 서비스 코드 변경 시 신규 테스트 타깃 실행만으로 push 금지 — 변경 모듈 전체 :test 로컬 완주 의무 (PR
metadata: 
  node_type: memory
  type: feedback
  originSessionId: ca45b1aa-4753-4946-bf50-8f7d1bf4188a
---

BE 서비스 코드 변경 후 `--tests "*신규IT*"` 타깃 실행 green 만 보고 push 하면, 같은 모듈의 **기존 mock 단위 테스트가 구 호출 패턴을 스텁/verify** 하고 있을 때 CI 에서만 깨진다 (PR #424: fix5 채번 lock 전환 → EstimateNumberServiceTest/DispatchTaskServiceTest/StockTransferServiceTest/TaxInvoice IT 7건 CI 적발).

**Why:** 구현 변경은 신규 테스트가 아니라 기존 테스트의 가정을 깨는 쪽이 대부분이다. 로컬 런타임 ERROR 0 (실서버 로그) ≠ 테스트 스위트 green — 레이어가 다르다. [[ci-test-filter-false-green]] 의 역방향 사례.

**How to apply:** push 전 `gradlew :services:<변경서비스>:test` 를 변경 모듈 전부에 대해 완주 (타깃 필터 없이). 시간이 들어도 CI 왕복 1회(~10분+리뷰 신뢰 손상)보다 싸다. 구현 변경 Codex 디스패치 프롬프트에 "기존 단위 테스트 스텁 동기화 포함" 을 명시.
