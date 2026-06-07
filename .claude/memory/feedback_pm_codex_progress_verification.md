---
name: pm-codex-progress-verification
description: PM은 Codex 디스패치마다 산출물 즉시 검증 + 진행 상태를 주기 보고 — 중간 멈춤/침묵 금지 (2026-06-07 개발책임자 지시)
metadata: 
  node_type: memory
  type: feedback
  originSessionId: 66bf5482-6c8e-4d40-8915-cbe33b1c607d
---

2026-06-07 PR #420 진행 중 개발책임자 지시: "계속 중간에 멈추니까 PM이 주기적으로 codex랑 구현 제대로 되고 있는지 확인 좀 해봐."

**Why:** mcp__codex__codex 동기 호출은 수 분간 무응답이라 사용자에게 멈춘 것처럼 보임. 사용자가 2회 중단 후 상황 문의 — 진행 투명성 부족이 워크플로우 중단을 유발.

**How to apply:**
1. Codex 디스패치 **직전** 사용자에게 1줄 예고 (무엇을, 예상 소요).
2. Codex 응답 수신 **즉시** `git status/diff --stat` 로 산출물 실재 검증 (보고만 믿지 않기) + 결과 1줄 보고.
3. 단계 전환(리뷰→fix→QA→CI)마다 짧은 상태 표 갱신 보고 — 긴 침묵 구간 만들지 않기.
4. CI/suite 등 백그라운드 대기 중에도 다음 가능한 작업(문서/리뷰 게시)을 병행해 흐름 유지.
5. 사용자 승인 대기로 멈출 일은 묶어서 한 번에 — 개별 도구 거부로 인한 반복 중단 방지.

관련: [[codex-sandbox-git]] [[codex-exec-stdin-hang]] [[pm-auto-continuous]]
