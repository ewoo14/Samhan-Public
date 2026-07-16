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

## 범위 외 결함 = PM 자율 이슈 등록 (2026-07-15 개발책임자 확정)

리뷰 라운드에서 **현재 슬라이스 범위 밖** 결함을 발견하면 **PM 이 개발책임자 확인 없이 즉시 `gh issue create` 후 보고**한다. 캐논이 PR 게시·커밋·머지만 명시하고 Issue 개설 규정이 없었던 공백을 메움.

**Why:** 은폐 방지가 목적. 범위 밖이라 안 고치는 건 맞지만, 발견을 채팅/리포트에만 남기면 세션 종료와 함께 사라진다. 이슈는 발견을 살려두는 최소 비용 장치.

**How to apply:**
- **등록 대상** = 범위 밖 + 실측 근거 있는 결함. 추측·"~일 수 있음"은 등록하지 말 것(노이즈).
- 이슈 본문에 **① 실측 증거**(로그/run URL/쿼리 결과) **② 근본원인** **③ 착수 전 확정 필요 항목** **④ 발견 경위 + 왜 그 PR 범위 밖인지**를 적는다. 그대로 다음 슬라이스의 기획 입력이 되게.
- 등록 후 **개발책임자에게 보고**하고, 처분(유지/닫기/현 PR 에서 같이 fix)은 개발책임자가 정한다.
- ⚠️ **"같이 fix" 결정이 나오면 범위 점증** → [[feedback-expanded-scope-reinstate-review]] 발동(자체 검증 갈음 금지·정식 라운드가 점증분까지 재검).
- 선례(2026-07-15 #820 R4): nightly `slip-it-public` 잡이 **존재한 적 없는 패키지 필터**로 3연속 실패(`No tests found`) → DevOps 차원은 "무해한 dead-weight"로 오판했으나 **문서 차원이 nightly run 로그 실측으로 반박** → 이슈 #821 등록 → 개발책임자가 "유지 + #820 에서 같이 fix" 결정.

관련: [[feedback-canonical-workflow]] [[feedback-expanded-scope-reinstate-review]] [[feedback-pm-permission-autonomy]]
