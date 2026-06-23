---
name: feedback_autonomous_loop_schedulewakeup
description: 야간/장시간 자율 진행 요청 시 ScheduleWakeup으로 매 워크플로우 단계마다 재자각+연속 진행. 긴 세션 워크플로우 드리프트 방지.
metadata:
  type: feedback
---

🚨 2026-06-24 개발책임자 지시. **사용자가 야간/장시간 자율 진행을 요청하면(예: "취침 예정이니 계속 자율 진행", "아침 7시까지 진행"), `ScheduleWakeup`으로 매 워크플로우 단계마다 재자각하며 끊김 없이 연속 진행한다.**

**Why**: 세션이 길어지면 워크플로우 위반(라이브 QA 누락·라운드 게시 누락·재수렴 생략 등)이 반복 발생. 사용자 부재 중 PM이 턴을 yield 하면 루프가 죽음. ScheduleWakeup이 (1) 턴을 넘겨 루프를 살리고 (2) 매 wake마다 워크플로우를 강제 재자각시킴.

**How to apply**:
- 각 워크플로우 단계(또는 1~2단계 묶음) 완료 후, 다음 단계를 `ScheduleWakeup`으로 예약하고 턴 종료. `delaySeconds`는 연속작업이면 짧게(60s, 캐시 <5분 유지). 외부 장시간 대기(빌드/CI)면 그에 맞게.
- **wake prompt(=재자각 프롬프트)에 반드시 포함**: ① 표준 워크플로우 요약([[feedback_canonical_workflow]]) + 절대규칙(라운드마다 라이브QA+스샷 인라인·각 라운드 독립 게시·fix후 0수렴 재리뷰·듀얼리뷰 순차·fix주체) ② 슬라이스 큐 + 현재 슬라이스/PR/단계 상태 ③ 다음 단계 구체 지시(중점·실행법) ④ 자율 권한 범위(막히면 사유 정직 기록 후 진행, 신규 업무규칙/정책만 사용자 확인 대기).
- 각 단계 진입 시 "🧭 워크플로우 자각" 한 줄로 현재 단계+요구사항 명시 후 진행(단계마다 자각 — 개발책임자 명시).
- 상태가 매 단계 변하므로 wake마다 prompt를 **갱신**해 재예약(동적 루프). 진행은 PR 코멘트에 라운드별 누적 게시로 추적 가능하게.
- 멈춤 = 신규 업무규칙/정책 결정 필요 시만. 그 외(트리비얼 결정·라이브QA 실연동 불가 등)는 자율 판단/정직 기록 후 계속.

관련: [[feedback_canonical_workflow]] [[feedback_post_each_review_round_distinctly]] [[feedback_rereview_converge_after_fix]] [[feedback_no_fake_data_ever]]
