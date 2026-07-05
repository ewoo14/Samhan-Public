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

**🚨 2026-07-05 개발책임자 재지적 #2 — "Codex 지적됐는데 왜 재수렴·PM 리뷰 없이 머지?"(#741 위반)**: Codex 5-agent 라운드가 FE [높음] 제기 → PM이 **독단 false-positive 판정 후 재수렴 리뷰·PM 종합 생략하고 즉시 머지**함(위반). **판정이 사후 검증상 옳았더라도(slip.period-lock 실 dead·예외 정당) 절차 생략 자체가 위반.** 규율: **Codex(또는 Opus) 라운드가 1건이라도 지적하면 — false-positive 의심이어도 — (a) 명시 disposition(fix or 검증된 무결 근거) (b) full 재수렴 리뷰(0확인) (c) PM 종합 (d) 머지 순 엄수. PM 독단 dismissal+즉시 머지 절대 금지.** 병렬 다중 PR이어도 매 슬라이스 이 순서 이탈 없이. PM은 매 머지 직전 "이 PR에 미해소 지적/미실시 재수렴/미실시 PM종합 없나" 자가점검 의무.

**🚨 2026-07-05 개발책임자 재지적 #3 — "병렬이라도 워크플로우 엄격 준수·PM이 매번 전 슬라이스 점검"**: 병렬 다중 PR 진행 시에도 각 슬라이스가 단축·누락 없이 캐논(조기PR→개발게시→Opus 5-agent+fix+게시→Codex 5-agent+fix+게시→0수렴→PM종합→CI→머지) 전 단계를 밟는지 **PM이 매번 점검**. 한 슬라이스라도 단계 건너뛰면 위반.

**🚨 2026-07-05 개발책임자 재지적 — "Codex 역시 5-agent 진행"**: Codex 순차 라운드도 **5-agent**(FE/BE/Design/DevOps/QA 5차원·QA=Docker 라이브+스샷)로 Opus 라운드와 **동일 구조**. **1개 종합 Codex 디스패치(mcp__codex__codex 단발)로 단축 금지**(내 상습 실수). 실행법: **codex-rescue 에이전트 5개 병렬**(각 차원 read-only Codex 리뷰·QA=danger-full-access 라이브) 또는 mcp Codex 5회, 5차원 취합 표(모든 라운드=표) 게시 후 0수렴 판정. [[feedback_codex_plugin_setup]](5 agents 병렬)·[[feedback_canonical_workflow]](Codex 5-agent+QA라이브). 모든 라운드(Opus AND Codex)=표+라이브 스샷 2곳(SendUserFile+PR SHA-pinned).
