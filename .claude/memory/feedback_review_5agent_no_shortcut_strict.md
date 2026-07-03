---
name: feedback_review_5agent_no_shortcut_strict
description: 매 리뷰 라운드 5-agent 필수(Design 포함)·단축 절대금지·순차(병렬금지)·매 단계 워크플로우 재확인
metadata: 
  node_type: memory
  type: feedback
  originSessionId: b6595c58-1401-4b50-805f-e460138d686c
---

2026-07-02 개발책임자 반복 강력 지시(E2 PR #699 다수 위반 회고). 절대 준수:

1. **매 리뷰 라운드 = 5-agent 필수** — FE/BE/Design/DevOps/QA **5개 전부**. 3-agent 로 줄이거나 Design 을 "N/A / PM 정직 disposition" 으로 대체 **금지**. 수렴/재검 라운드도 예외 없이 full 5-agent. focused 축소 금지.
2. **워크플로우 단축 절대 금지** — 트리비얼·소형 fix·수렴 확인도 전 단계 준수. fix 후 반드시 full 5-agent 재리뷰(Opus)→순차 Codex→0수렴.
3. **순차(병렬 리뷰 금지)** — Opus 라운드가 **완료+PR 게시**된 뒤에만 Codex 라운드. Opus↔Codex 동시 실행 금지. (5-agent 는 한 라운드 안에서 5개 차원이며, 라운드 단위로 순차.)
4. **실행 라운드 = PR 게시 1:1** — Codex 개발/각 리뷰 라운드는 실행 즉시 PR 게시. fix 로 건너뛰기 금지(PREFLIGHT #6).
5. **매 단계 워크플로우 재확인** — 각 단계 착수 전 캐논 순서 재점검하며 진행. 매 단계 ScheduleWakeup 자각.
6. **컨텍스트에 항상 유지** — 본 규칙을 세션 내내 상기.

**Why**: PM 직접구현·focused 재검·병렬 리뷰·PM 종합 조기선언·게시 누락 등 반복 위반이 개발책임자에 연속 적발됨. 순차 5-agent 듀얼리뷰가 실 blocking(#699서 11건) 적발의 핵심. [[feedback_canonical_workflow]] [[feedback_pm_no_direct_implementation]] [[feedback_pr_open_not_draft]]

**2026-07-03 #710 위반 4종 박제(개발책임자 "리뷰 게시 위반. 워크플로우 다시 학습" 지적 → 소급 보완)**: ①**Opus 라운드 fix 를 Codex 위임 금지 절대** — 개발책임자의 "Codex 가 실제 fix 하는지 확인" 같은 *검증 질문*을 규칙 변경으로 추측 해석 금지(규칙 변경=명시 지시만) ②**게시 트리거 = 리뷰 수합 완료 직후, fix 착수보다 먼저** — "리뷰+fix 후 통합 게시" 아님. 리뷰 게시 → fix → fix+QA 게시 순 ③**모든 라운드 코멘트에 라이브 QA 스샷 단계별 인라인**(Codex 라운드 텍스트-only = 위반) ④**마지막 fix(1줄이라도) 후에도 full 5-agent 순차 재검** — PM 단독 diff 검증으로 0수렴 선언 금지.
