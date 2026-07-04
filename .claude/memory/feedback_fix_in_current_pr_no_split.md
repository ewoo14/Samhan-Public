---
name: feedback-fix-in-current-pr-no-split
description: 리뷰에서 나온 fix는 별도 PR/후속 이슈로 분리하지 말고 현재 슬라이스(PR) 안에서 처리 — 분리는 타 서비스/타 슬라이스 범위만
metadata: 
  node_type: memory
  type: feedback
  originSessionId: c2ed01c8-fdc9-42e8-b0c2-4893fe025ab5
---

# 리뷰 fix = 현재 PR 내 처리 (분리 금지)

2026-07-04 개발책임자 지시(PR #724 진행 중): "fix의 경우 PR을 따로 열지 말고 현재 슬라이스(PR) 안에서 진행해."

**Why:** 리뷰가 적발한 결함을 후속 이슈/별도 PR로 미루면 0수렴의 의미가 약해지고 부채가 쌓임. 같은 서비스·같은 슬라이스 범위의 fix는 지금 PR에서 해소하는 것이 리뷰 체인의 목적.

**How to apply:**
- 리뷰 findings 중 현재 PR의 서비스/도메인 범위 안에 있는 것은 심각도 무관 현재 PR fix 라운드에서 해소(예: #724에서 IllegalState 승격+영문 메시지 한국어화를 accounting 3곳 전부 회수).
- 후속 이슈 분리가 허용되는 경우 = **타 서비스·타 슬라이스 범위**(예: #725를 slip/partner-order 잔여분 전용으로 축소), 또는 개발책임자가 명시적으로 미룬 것.
- 선례: #723(S1 부채 — 이미 머지된 타 슬라이스 유입분)은 분리 유지, #725 accounting 몫은 회수.

관련: [[feedback-canonical-workflow]] [[feedback-review-5agent-no-shortcut-strict]]
