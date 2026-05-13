---
name: TM 주도 + agent discussion 패턴 (PR/Issue comment)
description: TM 1명이 통합 PR 발행 후 다른 sub-agent 들이 PR comment 로 코드 리뷰 + 개선 제안 + 회고 토론. TM 이 토론 결과 종합하여 최종 결정 + 추가 commit
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
# 규칙

TM (통합 PR 발행 sub-agent) 이 주도하고, **다른 sub-agent 들 (BE/FE/Designer/QA/DevOps reviewer 역할)** 이 PR comment / Issue / GitHub Discussion 으로 **활발히 토론** 한다. 토론 결과를 TM 이 종합하여 최종 commit 추가 + PM 보고.

# Why

사용자 명시 (2026-05-05): "TM 주도 하 에이전트끼리 깃 discussion을 통한 활발한 토론 요구"

장점:
- TM 단독 결정 → 분산 검토 (다중 reviewer 시각)
- agent 별 전문성 활용 (BE / FE / Designer / QA / DevOps 각자 영역 review)
- 토론 history 가 PR 에 영구 보존 → 추후 회고 + 학습 자료
- 단순 push + merge 가 아니라 검토 + 개선 사이클

# How to apply

## 1. TM (통합 발행) → reviewer agent 들 spawn

TM 이 PR 발행 + CI green 확인 후, **reviewer 5 agent** 동시 spawn:

| Reviewer | 역할 |
|---|---|
| **BE Reviewer** | 코드 품질 (Java/Spring/Postgres) + 성능 + IT 충분성 |
| **FE Reviewer** | TypeScript / React / RN 패턴 + 접근성 + 모바일 호환 |
| **Designer Reviewer** | UI/UX 일관성 + 디자인 시스템 + legacy 보존 |
| **QA Reviewer** | 시나리오 충분성 + edge case + 회귀 위험 |
| **DevOps Reviewer** | 인프라 / 시크릿 / 배포 / 모니터링 / 보안 |

## 2. 각 reviewer agent 작업

```sh
$env:PATH = [System.Environment]::GetEnvironmentVariable("PATH","Machine") + ";" + [System.Environment]::GetEnvironmentVariable("PATH","User")
gh pr diff <PR_NUMBER> --repo ewoo14/SamhanLogis  # diff 분석
gh pr comment <PR_NUMBER> --repo ewoo14/SamhanLogis --body "..."  # 토론 의견
```

각 reviewer = **1~3 의견 comment** 작성:
- 칭찬 (legacy 보존 / 코드 명확성 / 테스트 충분 등)
- 우려 (성능 / 보안 / edge case / 누락)
- 개선 제안 (구체적 코드 / line 번호 명시)

## 3. TM 종합 + 추가 commit

TM 이 모든 reviewer comment 읽고:
- 채택 의견 = 추가 commit + PR 갱신
- 보류 의견 = PR comment 로 답변 + 후속 PR 위임
- 불일치 의견 = TM 자체 판단 + 사유 명시

## 4. PM 보고

TM 이 토론 종합 결과 + CI 재검증 결과 PM 보고. PM = 사용자에게 최종 보고.

# 토론 채널 우선순위

1. **PR comment** (default) — `gh pr comment <PR>` — 가장 자연스러움, PR history 에 보존
2. **Issue comment** — Issue 발행 + 토론 (PR 와 별개 주제 시)
3. **GitHub Discussion** — repo Settings → Discussions 활성화 필요. 자유 주제 / Q&A / 회고

# 토론 의견 작성 가드

- 한국어
- 변경 사실 + 기술적 이유만 (책임 귀속 표현 X — `feedback_no_dev_director_mention.md`)
- 구체적 line 번호 / 함수명 명시
- 추측 X, 코드 grep / IT 결과 기반 의견
- 짧고 명확 (200자 이내 권장)

# PR 본문 마지막 승인 섹션 (의무)

토론 종합 + CI green 후 PR 본문 마지막에 다음 섹션 의무 (`feedback_no_dev_director_mention.md` 의 "승인 의무" 일관):

```markdown
## 승인

- **TM 승인**: ✅ <TM agent ID> — 토론 종합 결과 + 추가 commit 적용
- **PM 승인**: ✅ CI <X/Y PASS> — 머지 진행 가능
```

# 적용 사례

- 첫 적용: Phase 7 1차 통합 PR (#아직 미정) — 사용자 명시 시점부터 spawn 시 의무

# 관련 메모리

- `feedback_integrated_pr_pattern.md` — TM 종합 PR 패턴 (본 토론 패턴 의 전제)
- `feedback_multi_agent_team_pattern.md` — 5-team 병렬 (reviewer 도 동일 5 카테고리)
- `feedback_no_dev_director_mention.md` — comment 본문 멘트 가드
- `feedback_pr_ci_monitoring.md` — CI watch (TM 의무)
- `feedback_github_pr_workflow.md` — TL → PM → 머지 흐름
