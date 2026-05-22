---
name: cycle-n2-mandatory
description: 옵션 C (2026-05-21 사용자 결정) — 1f Claude fix 후 양쪽 reviewer 재실행 (Claude 5-agent + Codex 5-section) 사이클 N=2 의무. 가장 견고한 cross-check.
metadata:
  type: feedback
---

# 옵션 C — 사이클 N=2 의무 (2026-05-21 사용자 결정)

> 사용자 명시 (2026-05-21, MIG-14 머지 직후): "옵션 C 로 적용"
>
> **🚨 사용자 재지적 (2026-05-22, PR #293 사이클 1d 종료 시점)**: "코덱스 리뷰가 끝나면 원래 클로드가 한 번 더 점검을 하는 워크플로우였는데 또다시 위반" → **단계 9 Claude verify skip 절대 금지**. Codex 1e fix 후 CI green 만으로 사이클 종료한 회귀. 매 사이클 단계 9 (Claude verify) 의무.
>
> **🚨 사용자 명시 (2026-05-22, 동일 시점)**: "머지는 그 후 PM이 판단하에 자동으로 진행 및 다음으로 자동 진입 (사용자 문의X)" → 사이클 종료 + 양쪽 APPROVE + CI green 충족 시 **PM 자동 머지 + 다음 Sprint 자동 진입** (사용자 머지 요청 대기 금지). [[user-merge-authority]] 강화.

옵션 A ([feedback_codex_fix_claude_verify]) 의 약점 (1f fix 후 Codex 가 1f 변경을 다시 review 안 함, CI 만 의존) 보완. **1f Claude fix 발생 시 사이클 N=2 의무 진입** — Claude 5-agent + Codex 5-section 양쪽 전체 재실행.

**Why:** MIG-14 사례 — 옵션 A 단계 9 Claude verify 가 spec 정합만 검증했고 mock 환경 AOP 작동은 CI 가 잡음. 만약 CI 도 못 잡는 결함이면 1f 가 새 결함 도입한 채 머지됨. 옵션 C 는 양쪽 cross-check 완전성 보장.

## 워크플로우 (옵션 C, 12 + 9 단계 = 21단계)

### 사이클 1 (1~11)
1. Claude 5-agent 병렬 review
2. **TM Claude 통합 PR comment (즉시, head SHA 명시)** — skip 금지 의무
3. Claude fix
4. commit + push (head 갱신)
5. Codex 5-section review
6. **🚨 TM Codex 통합 PR comment (즉시, head SHA 명시)** — **2026-05-21 사용자 명시 강화** ("MIG-20 코덱스 리뷰 게시 없이 머지" 지적) → **skip 절대 금지 의무**. JSON 결과만 받고 PR comment 게시 안 하면 사이클 위반 → 사후 보완 의무.
7. Codex fix
8. commit + push (head 갱신)
9. Claude verify (BE + QA spot-check, fix diff 만)
10. **1f Claude fix (CI fail 또는 MAJOR/P0 발견 시)** — 발동 시 12~ 사이클 N=2 의무
11. commit + push (head 갱신)

### 사이클 N=2 (1f fix 발동 시 의무, 옵션 C 신규)
12. **Claude 5-agent 재실행 (2a)** — 1f 변경 영향 전체 영역 cross-check
13. TM Claude 통합 (2b)
14. Claude fix (2c, 결함 0 시 skip)
15. **Codex 5-section 재실행 (2d)** — 양쪽 완전 cross-check
16. TM Codex 통합 (2e)
17. Codex fix (2f, 결함 0 시 skip)
18. Claude verify (2g, 1f 패턴 미러)
19. CI 재검증

### 사이클 종료 조건 (옵션 C)
20. **사이클 N (1 또는 2) 종료 조건**:
    - 잔존 결함 0 (양쪽 reviewer 모두 APPROVE)
    - CI watch PASS
    - 1f fix 발동 안 함 (사이클 1 안 종료) OR 사이클 2 양쪽 APPROVE + CI green
21. PM 마지막 종합 + 자동 머지 / 미충족 시 사이클 N+1 (최대 N=3)

## How to apply

- **1f fix 발동 트리거**:
  - CI fail (MIG-14 사례 — mock AOP)
  - Claude verify (단계 9) 가 MAJOR/P0 발견
  - 사용자 명시 stop 또는 critical 발견
- **1f 발동 시 단계 12~19 의무** — skip 금지
- **CI green 만으로 머지 불가** — 양쪽 reviewer 모두 APPROVE 필수
- **사이클 N=3 안 완료 의무 유지** ([feedback_dual_5agent_review]) — 2c/2f fix 후에도 결함 있으면 사이클 3 진입 가능

## 옵션 비교 표

| 옵션 | 효율 | 안정성 | 적용 시점 |
|---|---|---|---|
| 옵션 A (이전) | 높음 (사이클 단축 가능) | 중간 (CI 의존) | 2026-05-21 ~ MIG-14 |
| **옵션 C (2026-05-21~)** | 중간 (사이클 2 의무) | **최고** (양쪽 완전 cross-check) | **MIG-15+ 의무** |

## MIG-14 사례 회고 (옵션 A → 옵션 C 교훈)

- 옵션 A 사이클 1f Claude fix 가 CI fail 만 해결 (mock AOP) — Codex 가 1f 변경 review 안 함
- 만약 1f 가 새 결함 도입 (예: ACCOUNTANT 권한 매트릭스 회귀) 시 CI 가 못 잡으면 운영 회귀
- **옵션 C 강화**: 1f 후 Codex 5-section 재실행 → 1f 가 새 결함 도입 안 함 확인 후 머지

## 관련 메모리

- [[dual-5agent-review]] — Claude + Codex 양쪽 5-agent review (사이클 N=1~3 의무)
- [[codex-fix-claude-verify]] — 옵션 A (이전, 2026-05-21 폐기 후 옵션 C 로 갱신)
- [[user-merge-authority]] — PM 자동 머지 (양쪽 0 결함 + CI green)
- [[qa-docker-real-test]] — QA Docker 실서버 검증 의무
- [[pm-auto-continuous]] — PM 자율 연속 진행
