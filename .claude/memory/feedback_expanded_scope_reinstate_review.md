---
name: feedback_expanded_scope_reinstate_review
description: 슬라이스 범위가 mechanical fix→substantive(BE/마이그)로 점증하면 정식 듀얼 리뷰+게시 사이클을 재가동 — 자체 검증(grep/probe/CI)으로 갈음 금지
metadata:
  node_type: memory
  type: feedback
---

PR 진행 중 범위가 mechanical fix(mock 데이터 등)를 넘어 **substantive 변경(BE 로직·Flyway 마이그·다서비스)** 으로 커지면, **반드시 정식 Opus 5-agent + Codex 순차 리뷰 라운드를 재가동하고 PR에 게시**한다. 자체 검증으로 리뷰를 갈음하지 않는다.

**Why:** #727 에서 입금보고서 목록 slipNo mock 형식 fix가 → 전 메뉴 전표번호 sweep → **수금계획/감사 2개 서비스 BE 채번 변경 + 프로덕션 Flyway 마이그(V20/V55)** 로 점증했는데, 정식 듀얼 리뷰+게시 없이 자체 검증(grep·마이그 probe·CI 30/30)으로 갈음하고 머지 → 개발책임자 "리뷰 게시 없이 왜 진행했는가?" 지적. 캐논([[feedback_canonical_workflow]])은 **substantive 변경마다** 5-agent(FE/BE/Design/DevOps/QA)+Codex 리뷰 + **실행=게시 1:1**. 검증(probe/CI)은 리뷰의 대체가 아니라 보완이다. BE/마이그는 특히 동시성·checksum·전환 안전성 등 리뷰 관점이 자체 grep으로 안 잡힌다.

**How to apply:**
- 범위가 커질 때마다 "이건 mechanical인가 substantive인가" 자문 → BE 로직·마이그·계약·다서비스면 즉시 5-agent+Codex 리뷰 라운드 재가동 + PR 게시.
- Codex 구현 디스패치 ≠ 리뷰. 구현 후 별도 리뷰 라운드 필수.
- 미준수 발각 시 **소급 리뷰**(머지 후라도 FABLE5+CODEX SOL 5.6 재실행·PR 게시·결함 시 후속 PR). → [[feedback_canonical_workflow]] · [[feedback_post_devlead_decisions_to_pr]] · [[feedback_fix_in_current_pr_no_split]]
