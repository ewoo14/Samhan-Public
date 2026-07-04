---
name: feedback_qa_screenshots_inline_to_user
description: QA/결과 스크린샷은 항상 사용자에게 인라인 첨부(SendUserFile display=render) — PR 코멘트 인라인 게시와 별개로 채팅에도 매번
metadata:
  node_type: memory
  type: feedback
---

라이브 QA 캡처 등 스크린샷을 산출하면 **항상 사용자(개발책임자)에게 SendUserFile 로 인라인 첨부**한다(display='render'). PR 코멘트의 SHA-pinned 인라인 게시와 **별개**이며 병행한다.

**Why:** 개발책임자가 채팅에서 즉시 시각 확인하기 위함 — 세션 중 2회 명시("스크린샷 첨부요망" 2026-07-04, "스크린샷은 항상 인라인 첨부 요망"). PR 코멘트만 게시하면 채팅에서 안 보임.

**How to apply:** 캡처 생성 직후 `SendUserFile(files=[...], display='render', caption=각 컷 요지)`. PR 게시(SHA-pinned raw URL)와 사용자 인라인 전송을 둘 다 수행. → [[feedback_pr_screenshot_sha_pinned_urls]] · [[feedback_real_server_check_screenshot]]
