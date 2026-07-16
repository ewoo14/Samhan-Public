---
name: feedback_css_var_token_not_fallback
description: CSS var(--token, #fallback)는 토큰 정의 시 fallback 무시하고 토큰값 렌더 — "토큰화=값 불변"은 토큰 실제값==fallback일 때만 참, 대비 재계산 필수
metadata:
  type: feedback
---

인라인 raw hex를 `var(--token, #fallback)`로 "토큰화"할 때, **CSS `var(name, fallback)`은 name이 정의되어 있으면 fallback을 절대 쓰지 않고 토큰의 실제 정의값을 렌더**한다. 따라서 "raw hex를 토큰+동일 fallback으로 치환 = 값 불변"은 **토큰 실제값 == fallback일 때만** 참.

**Why:** #17 S4b(#776)에서 조회전용 안내 `#b45309`를 `var(--color-warning-700, #b45309)`로 치환했으나 `--color-warning-700`의 실제값은 `#B47A1F`(≠ #b45309) → 렌더 색상이 바뀌고 WCAG 대비 5.02:1→3.66:1로 **AA 회귀**. R1 리뷰가 "형제 파일도 이 관용구 씀·값 불변"으로 잘못 승인했고 R2 재검이 tokens.css 실제값 대조+대비 재계산으로 포착(내 R1 fix 유발 회귀).

**How to apply:** 색 토큰화 fix는 (1) `design-system/src/tokens/tokens.css`에서 **토큰 실제값 확인**, (2) fallback과 다르면 렌더 색이 바뀜을 인지, (3) WCAG 대비 재계산으로 AA(일반텍스트 4.5:1) 확인 후에만 "값 불변/개선" 주장. 실제값==fallback인 경우만 안전(예 `--color-neutral-600`=#4D5562). 안전 최소위험 = raw hex 유지 or 실제 AA 통과 토큰 선택. full 재수렴 재검([[feedback_canonical_workflow]])이 회귀 포착한 사례.
