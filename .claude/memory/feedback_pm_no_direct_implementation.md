---
name: feedback_pm_no_direct_implementation
description: "PM 직접 구현 금지 — 구현은 Codex, PM은 기획·리뷰·commit 대행만"
metadata: 
  node_type: memory
  type: feedback
  originSessionId: b6595c58-1401-4b50-805f-e460138d686c
---

2026-07-02 개발책임자 지시. **PM(Claude) 직접 구현 금지.** 구현(소스 작성)은 **Codex**가 담당. PM 은 기획·spec·plan·리뷰(Opus 5-agent)·commit 대행·PM 종합·머지만.

**Why**: 표준 워크플로우([[feedback_canonical_workflow]])가 "Opus 기획+PR → Codex 개발 → 듀얼리뷰"인데, infra 제약(분류기/Codex MCP 오류) 시 PM 직접 구현으로 우회하려 하자 개발책임자가 명시 금지. Codex 구현이 듀얼리뷰 cross-check 의 전제(PM 이 구현하면 리뷰 독립성 훼손).

**How to apply**: 구현 착수 시 항상 Codex 디스패치(`mcp__codex__codex` danger-full-access 또는 codex exec). Codex 불가(MCP 세션한계/config 손상) 시 새 세션·codex exec Bash 우회·회복 대기 — PM 직접 구현으로 대체 금지. Codex=파일만 수정, Claude commit 대행([[feedback_codex_sandbox_git]]). [[feedback_canonical_workflow]]
