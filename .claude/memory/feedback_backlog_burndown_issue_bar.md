---
name: feedback_backlog_burndown_issue_bar
description: 슬라이스 파생 chore 이슈 순증 방지 — 배치 번다운으로 순감·이슈 바 상향·워크플로우 임의 단축 금지
metadata:
  type: feedback
---

**2026-07-19 심야 개발책임자 지시** (B1 배치 착수 중): "자꾸 issue가 늘어가는데 이러면 잔여 해결 issue가 많아질뿐" + "issue 먼저 해결하고 다음 슬라이스 진행" + "워크플로우 임의 단축은 금지하며 반드시 엄수".

**Why:** 슬라이스마다 적대검증이 범위 외 pre-existing 결함을 발견 → [[feedback_fix_in_current_pr_no_split]] 대로 매번 새 이슈 등록 → **닫는 것보다 여는 게 빨라 백로그 순증**. "잔여 해결"이 무한후퇴.

**How to apply:**
1. **이슈 바 상향**: 범위 외 결함은 (a) 저비용이면 **현 PR 내 in-round fix**, (b) marginal·재현불가는 **dev-report/PR 노트**로만, (c) **실질·재현 가능·물질적 영향** 결함만 이슈 등록. marginal(1회 관측·CI green·단독 통과 류) 이슈는 등록 말 것(등록됐으면 close 재검토).
2. **배치 번다운**: 파생 chore 다수를 **성격별 그룹 배치 PR**로 묶어 한 캐논 사이클로 다수 close(오버헤드 amortize·순감 전환). 예: DS/a11y 클러스터·회계 데이터 클러스터·실버그.
3. **feature보다 백로그 우선**: 개발책임자가 "이슈 먼저" 지시 시 잔여 이슈를 순감시킨 뒤 다음 feature 슬라이스.
4. **워크플로우 임의 단축 절대 금지**: chore·배치도 풀 캐논 엄수([[feedback_infra_chore_not_canon_exempt]]·[[feedback_canonical_workflow]]). 배칭 자체가 효율이지 워크플로우 축약이 효율 아님.

**실증(2026-07-20)**: 활성 21→13(#856 close·#830 Phase11 defer)→B1-A(#834/837/840) 배치 머지로 10. 배치 = B1-A/B1-B(DS a11y)·B2(회계)·B3(실버그).
