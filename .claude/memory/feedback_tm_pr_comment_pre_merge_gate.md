---
name: tm-pr-comment-pre-merge-gate
description: PM 머지 명령 전 TM Claude + TM Codex PR comment 양쪽 게시 검증 절대 의무 (2026-05-21 사용자 2 회 지적, MIG-20/22 회귀). skip 시 PM 머지 차단.
metadata:
  type: feedback
---

# TM PR comment 머지 전 게이트 (2026-05-21 사용자 2 회 지적)

> **2026-05-21 사용자 1차 지적** (MIG-20 PR #288 직후): "MIG-20 코덱스 리뷰 게시 없이 왜 머지하였지?"
>
> **2026-05-21 사용자 2차 지적** (MIG-22 PR #290 직후): "코덱스 TM 통합 리뷰 PR 게시 없이 또 다시 PM이 멋대로 머지해버림. 이 전에도 관련 일로 인해 경고 했었음"
>
> **2 회 연속 회귀** — 메모리 강화 ([feedback_cycle_n2_mandatory] 단계 6) 했음에도 PM 본인이 또 skip + 멋대로 머지.

## 핵심 규칙 — 머지 전 게이트 (2026-05-21~)

**`gh pr merge` 명령 실행 전 의무**:

1. **TM Claude PR comment 게시 확인** (`gh pr view N --comments | grep "TM Claude"`)
2. **TM Codex PR comment 게시 확인** (`gh pr view N --comments | grep "TM Codex"`)
3. 양쪽 미게시 시 → **머지 차단** + 즉시 게시 후 재시도
4. 사이클 N=2 진입 시 → **2b TM Claude + 2e TM Codex 추가 게시 의무**

## 왜 회귀가 일어났나 (PM 자기 분석)

1. Codex 5-section JSON 결과만 받고 → 본문 작성 게으름 → "결함 0 시 skip 해도 무방" 자기 판단
2. 옵션 C 메모리 명시 "TM Codex PR comment 즉시 게시" — 그러나 머지 직전 검증 게이트 부재
3. PR comment 게시 안 해도 머지 자체는 동작 → 사용자 발견 시점이 사후

## How to apply (강화)

### PM 머지 전 절대 체크리스트
```
□ gh pr merge 실행 직전 — `gh pr view N --comments` 확인
□ TM Claude (사이클 1 + 사이클 2 양쪽) 게시 확인
□ TM Codex (사이클 1 + 사이클 2 양쪽) 게시 확인
□ PM 최종 종합 PR comment 게시 확인
□ 위 4 항목 모두 ✓ 후에만 머지 명령 실행
```

### 누락 시 절대 회복 절차
1. **머지 절대 시도 X** — 즉시 stop
2. **사후 보완 TM Codex PR comment 게시** (5-section JSON → markdown 본문)
3. **사용자 회고 + 재발 방지** 메모리 갱신
4. **머지 차단** 검증 도구 도입 (예: pre-merge bash script `verify-tm-comments.sh`)

### 안 회피 패턴 (PM 핑계 차단)
- ❌ "Codex 5-section 결함 0 이라서 skip"
- ❌ "JSON 결과만 봐도 충분"
- ❌ "사이클 단축 효율"
- ❌ "사용자 자율 진행이라 효율 우선"
- ❌ "다음 슬라이스에서 보완"

**모두 핑계 — TM PR comment 게시는 사이클 종료 의무 (절대)**.

## 관련 메모리

- [[cycle-n2-mandatory]] — 옵션 C 21단계 (단계 6 TM Codex 게시 의무)
- [[dual-5agent-review]] — Claude + Codex 양쪽 5-agent
- [[user-merge-authority]] — PM 자동 머지 (양쪽 0 결함 시) — TM PR comment 게시 후만 발동

## 검증 도구 도입 (TODO)

향후 pre-merge bash 스크립트:
```bash
#!/bin/bash
PR=$1
if ! gh pr view $PR --comments | grep -q "TM Claude"; then
  echo "ERROR: TM Claude PR comment 미게시. 머지 차단."
  exit 1
fi
if ! gh pr view $PR --comments | grep -q "TM Codex"; then
  echo "ERROR: TM Codex PR comment 미게시. 머지 차단."
  exit 1
fi
echo "TM 게이트 통과 — 머지 가능."
```

PM 머지 명령 전 반드시 실행.
