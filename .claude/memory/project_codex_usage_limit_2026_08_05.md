---
name: project-codex-usage-limit-2026-08-05
description: Codex 계정 사용 한도 초과 — 2026-08-05 14:52 까지 sol·luna 전부 불가, 캐논 5단계 중 3개 결손
metadata:
  type: project
---

**2026-07-30 (집PC 세션) 실측** — `mcp__codex__codex` 호출이 모델 무관으로 즉시 거절됩니다.

```text
You've hit your usage limit. Visit https://chatgpt.com/codex/settings/usage
to purchase more credits or try again at Aug 5th, 2026 2:52 PM.
```

- `gpt-5.6-luna`(구현·라운드 fix) · `gpt-5.6-sol`(2차 적대검증) **둘 다** 같은 에러 — **계정 레벨 한도**이지 모델 용량 문제가 아닙니다.
- 🔑 **`Selected model is at capacity` 와 다릅니다** — capacity 는 재시도/폴백(terra)으로 우회되지만([[feedback_model_substitution_delegated_to_pm]]), usage limit 은 **모델을 바꿔도 동일**합니다. 두 모델로 각 1회 확인했습니다.
- 리셋 시각 **2026-08-05 14:52**. 그때까지 캐논 5단계 중 **LUNA 구현 · LUNA 라운드 fix · SOL 적대검증** 3개가 결손입니다.

**Why:** Codex 는 PM 세션 토큰과 별개 풀이라는 전제로 위임 대상이었는데([[feedback_pm_delegate_to_codex_conserve_tokens]]), 그 전제가 6일간 사라집니다. 트랙을 띄우기 전에 확인하지 않으면 브리핑을 다 작성한 뒤 거절당합니다.

**How to apply:**
- 세션 시작 시 Codex 트랙을 계획하기 전에 **한도부터 확인** — 작은 프롬프트 1회로 즉시 판별됩니다.
- 한도 상태에서 남아 있는 캐논 경로 = **OPUS 기획 · OPUS 5-agents 적대리뷰 · SONNET5 라운드 fix · PM 라이브QA/종합/머지**. SONNET5 fix 는 캐논의 정식 역할이므로 클로드 대체가 아닙니다.
- 결손된 **2차 검증(SOL) 은 PR 에 명시 기록**할 것 — 안 돌린 스테이지를 "결함 0" 으로 세면 [[feedback_unverified_scope_is_not_zero_defects]] 그대로 재현됩니다.
- 크레딧 충전은 **개발책임자 결제** 사항 — PM 이 대신 결정할 수 없습니다.
