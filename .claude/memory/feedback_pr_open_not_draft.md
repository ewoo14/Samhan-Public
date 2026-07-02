---
name: feedback_pr_open_not_draft
description: PR은 DRAFT 아닌 OPEN 상태로 개설 (조기 PR 포함)
metadata: 
  node_type: memory
  type: feedback
  originSessionId: b6595c58-1401-4b50-805f-e460138d686c
---

2026-07-02 개발책임자 지시. **PR 은 DRAFT 가 아닌 OPEN 상태로** 개설/유지. 조기 PR(캐논 워크플로우 1단계) 도 draft 로 만들지 말고 처음부터 OPEN.

**Why**: draft 는 리뷰/CI 가시성·트래킹이 약함. 개발책임자가 진행 상황을 OPEN PR 로 바로 보기를 원함. E2 PR #699 를 draft 로 개설했다가 OPEN 전환 지시받음.

**How to apply**: `gh pr create` 시 `--draft` 쓰지 말 것. 이미 draft 면 `gh pr ready <N>`. [[feedback_canonical_workflow]] (조기 PR = OPEN).
