---
name: pm-10min-status-report
description: PM은 작업 진행 중 10분에 1회 개발책임자에게 진행상황 보고 의무 (2026-06-07 지시)
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 66bf5482-6c8e-4d40-8915-cbe33b1c607d
---

2026-06-07 개발책임자 지시: "10분에 한 번씩 PM이 나한테 진행상황 보고해."

**Why:** Codex 동기 호출·CI watch·suite 실행 등 긴 침묵 구간에서 진행 여부가 보이지 않아 사용자가 반복 중단/문의 — 투명성 요구.

**How to apply:** 슬라이스 진행 중 /loop 10m 상태 보고 가동 (단계표: 현재 단계 / 직전 완료 / 다음 예정 / 블로커). 짧게 — 표 1개 수준. 세션 시작 시 작업 개시와 함께 가동, 슬라이스 머지/세션 종료 시 해제. [[pm-codex-progress-verification]] 과 연동.
