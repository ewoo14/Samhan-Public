---
name: feedback_workflow_discipline_root_cause
description: 워크플로우 반복 위반의 근본원인 4종 + 머지전 9-게이트/디스패치전 체크리스트 (2026-07-05 개발책임자 "왜 계속 위반?")
metadata:
  node_type: memory
  type: feedback
---

2026-07-05 개발책임자 "워크플로우가 계속 위반되는 이유"에 대한 자기진단. 한 세션서 위반 다발(Codex 라운드 텍스트/스샷누락·조기PR 대신 dev먼저·#741 독단머지(재수렴·PM종합 생략)·Opus 라운드 fix를 Codex 위임·dev-report 스킵). 근본원인:

1. **처리량(병렬 속도) > 절차 충실도로 우선순위 역전** — "오늘 전 슬라이스 병렬 완료" 압박 하에 최속 경로(모든 fix=mcp Codex·판단으로 게이트 스킵)를 관성 선택. 병렬성과 절차 충돌 시 병렬성이 이김. **워크플로우 준수가 명시 최우선 지시이므로 병렬성보다 절대 우위**.
2. **워크플로우를 "결과 맞추는 가이드"로 오해** — "지적이 false-positive니 결과는 맞다→단계 생략 가능" 논리. 워크플로우 가치=**내 판단이 틀릴 때를 잡는 것**이라, 내 판단으로 단계 대체 시 자기무력화. 물음은 "결과가 맞나"가 아니라 "**모든 단계를 밟았나**".
3. **규칙이 메모리에 있어도 결정 시점 미대조** — 관성(mcp Codex=기본 fix 도구)으로 움직이고 행동 전 규칙 확인 안 함. 메모리가 수동 저장만·결정 순간 소비 안 됨.
4. **긴 세션+병렬 알림 폭주가 규율 침식** — 다PR×전체 워크플로우 동시추적 부하→최소저항 경로로 단계 탈락.

**강제 시정 메커니즘 (응답에 명시=감사 가능):**

- **머지 전 9-게이트**(매 PR·병렬 무관·하나라도 미충족=머지 금지): ①Opus 5-agent 완료+게시 ②전 지적 disposition(fix or 검증된 무결) ③**fix=그 라운드 진행 모델**(Opus라운드=Opus·Codex라운드=Codex) ④full 재수렴 0확인 ⑤Codex 5-agent 완료+게시 ⑥0수렴 ⑦PM 종합 ⑧dev-report ⑨CI green.
- **fix 디스패치 전 필수 자문**: "이 라운드는 Opus인가 Codex인가?" → 그 모델로만 fix.
- **병렬이어도 각 슬라이스 단계를 느리게·정확히** — 속도 < 충실도. 병렬은 PR 다중 in-flight로 얻되 각 PR은 단축 0.
- **PM은 매 머지 직전 9-게이트를 응답에 체크로 명시**(사용자 감사).

**How to apply**: 매 fix 착수 전 라운드 모델 확인. 매 머지 직전 9-게이트를 응답에 나열·각 ☑ 근거. 병렬 압박 시 "속도<충실도" 재확인. [[feedback_review_5agent_no_shortcut_strict]] [[feedback_canonical_workflow]] [[feedback_fix_in_current_pr_no_split]] [[feedback_emit_real_tool_calls]]
