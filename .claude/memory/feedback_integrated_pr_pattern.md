---
name: 통합 PR 패턴 의무 (단편 hotfix 금지)
description: 디자인/UI 차이는 단편 fix PR 금지, 전체 차이 묶어서 통합 PR 1개 + QA 캡처 + TM 승인 흐름 의무 (PR #66 회고)
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: 디자인 / UI 차이를 발견했을 때 단일 항목 (예: 색상 1개, padding 1개, textColor 4행 등) 만 fix 하는 단편 PR 발행 **금지**. 발견된 차이를 모두 누적 → **통합 PR 1개**로 묶어서 발행하고, QA 캡처 + TM 승인 흐름을 거친다.

## Why

**PR #66 (Mobile v4 카테고리 textColor 4행 hotfix) 회고** — 2026-05-05 개발책임자 명시 피드백:

> "PR66의 경우 전체적으로 디자인이 모두 다름"
> "PR은 한 번에 통합해서 QA 확인 후 TM 승인하게 업로드 요청"

PR #66 은 카테고리 textColor 4행만 정정하는 단편 hotfix 였으나, 전수 조사 시 다음 문제 노출:

1. **PM 의 차이 평가가 부정확**: "95% 일치" 보고가 사용자 체감 ("전체적으로 모두 다름") 과 큰 괴리. 단일 항목 패치는 PM 이 차이 전수를 인지하지 못함을 시사
2. **PR 수가 누적되어 리뷰 부담 증가**: 단편 hotfix N개 = N개 PR description / N회 CI / N회 TM 승인. 통합 1개면 1회로 처리
3. **QA 검증 단위가 분산되어 회귀 위험 증가**: 화면 × viewport 매트릭스 캡처가 PR 마다 부분만 → 전체 시각적 일관성 검증 불가
4. **TM 승인 흐름 효율 저하**: 단편 PR 마다 TM 검토 부담, 차이 평가의 종합적 판단 어려움

→ PR #66 은 close 처리. 차이 항목 전수 누적 후 통합 PR 재발행 결정.

## How to apply

### 1. 차이 식별 시 행동 변경

디자인 / UI 차이 1건이라도 발견하면 **즉시 fix 하지 않는다**. 대신:

- **전수 조사** 우선 — 다음 8 차원으로 화면 × viewport 매트릭스 점검:
  1. **색상** (text / background / border / icon)
  2. **타이포그래피** (font-family / size / weight / line-height / letter-spacing)
  3. **레이아웃** (flex / grid / padding / margin / gap)
  4. **모서리** (border-radius / 모서리 처리)
  5. **그림자** (box-shadow / elevation)
  6. **상호작용** (hover / active / focus / disabled / transition)
  7. **아이콘** (크기 / 색상 / spacing / 정렬)
  8. **반응형** (mobile / tablet / desktop breakpoint 별 차이)

- 차이 항목을 **차이 매트릭스 표** (화면 × 차원) 로 누적 → 1차 보고

### 2. 통합 PR 발행

차이 누적 완료 후:

- 단일 feature 브랜치에 모든 fix 묶어서 commit
- PR 본문에 **차이 매트릭스** + **QA 캡처** (각 화면 × 각 viewport × before/after) 인라인 첨부
- 캡처는 commit-pinned raw URL 사용 (memory `feedback_pr_qa_screenshots.md`)

### 3. TM 승인 흐름 의무

통합 PR 은 반드시 다음 체인 통과:

- **TL 검토** → **PM 승인** → **개발책임자 (대표) 머지**
- memory `feedback_github_pr_workflow.md` 일관

### 4. 예외 (긴급 분리 허용)

다음 케이스는 통합 대기 X, 즉시 hotfix 분리 가능:

- **보안 취약점** (XSS / SQL injection / 인증 우회 등)
- **데이터 손실 위험** (DB 마이그레이션 결함, soft-delete 우회 등)
- **production crash** (런타임 에러, 빌드 fail, CI 차단 등)

이 외의 디자인 / UI / UX / 사소한 텍스트 정정은 **모두 통합 PR 대기 대상**.

## 강화 — TM 종합 패턴 (2026-05-05)

### 추가 규칙

**다중 agent 작업 시 단편 PR 생성 금지** — 각 agent 가 자체 PR 발행 X. 모든 agent 는 push only (PR 본문 파일만 작성). **TM 1명** 이 모든 agent 산출물을 종합하여 **1 통합 PR 발행**.

### Why

PR #71 / #74 / #77 / #78 / #79 = 각 agent 가 자체 PR 발행 → 같은 시점에 5~7개 PR 동시 open → 머지 부담 + CI 부하 + 리뷰 분산.

### How to apply

1. **각 agent (sub-team)**: `feature/<영역>-<작업>` branch 에 push only. PR 발행 X. PR 본문 파일 (`.pr-body-*.md`) 만 작성 + worktree 보존.
2. **TM 1명** (별도 spawn 또는 PM 직접): 모든 agent push 완료 알림 받은 후
   - 통합 worktree 생성 (`integrated-<phase>-<scope>`)
   - 각 sub-branch merge
   - 각 sub-PR 본문 통합 → 1 통합 PR 본문 작성
   - 1 통합 PR 발행 + CI watch
3. **TM 자체 검토** → **PM 보고** → **PM 이 CI 모두 완료 확인** → 개발책임자 머지 요청

### TM = sub-agent 또는 PM

- 사용자 명시 "통합 발행도 TM이 결정" → 통합 PR 발행도 별도 sub-agent (TM) 위임 권장
- PM = 결과 받아 보고 + 머지 요청

## 적용 사례

- **PR #66** (2026-05-05): Mobile v4 카테고리 textColor 4행 단편 hotfix → close → 본 가드 정립
- **PR #71 / #74 / #77 / #78 / #79** (2026-05-05): 각 agent 자체 PR 발행 패턴 → 강화 가드 정립 계기 (TM 종합 패턴 추가)
- 향후 통합 PR 적용 사례는 본 섹션에 누적

## 강화 — fix 후속 PR/Phase 위임 금지 (2026-05-07 PR #94 회고)

### 규칙 (강화)

reviewer 가 PR comment 에서 "**fix**" 또는 "**bug**" 또는 "**(Bug)**" 또는 "**fix 권장**" / "**채택 권장**" 식으로 식별한 항목은 **해당 PR 안에서 즉시 fix 처리 의무**.

후속 PR / Phase 10 cutover / W5 회고 / "후속 PR 위임" 으로 미루는 분류 **금지**.

### 적용 범위

| 카테고리 | 처리 |
|---|---|
| **(Bug) 명확한 결함 / config 일관성 / 회귀 위험** | **본 PR 즉시 fix 의무** |
| **운영 가드** (예: ShedLock multi-instance / fail-fast 토글 / Resilience4j) | **본 PR 즉시 fix 의무** (Phase 10 영향이라도 적용) |
| **컴포넌트화 / refactor / 디자인 보강** (예: storybook story / CSS Module 이동 / `<ChannelBadge>` 컴포넌트) | **본 PR 즉시 적용 의무** (별도 client 통합 PR 위임 X) |
| **edge case IT 추가** (예: payload @Size / typeMismatch 일관 / boundary case) | **본 PR 즉시 추가 의무** |
| **e2e 시나리오 매핑 (qa/playwright/qa/detox)** | 본 PR 채택 가능 시 의무. 단, 다른 PR scope 영향이면 W5 위임 명시 가능 |
| **회고 / plan 문서** (Phase 9 회고 보고서 / Phase 10 진입 plan) | W5 위임 가능 (본 PR scope 외) |

### 예외

- 본 PR scope 와 명백히 분리된 영역 (예: dashboard-service PR 인데 partner-service 의 별도 backlog 항목)
- 단순 plan 문서 (Phase 9 회고 보고서 / Phase 10 진입 plan)

### Why

사용자 명시 (2026-05-07): "fix는 phase10으로 넘기지 말고 해당 PR에서 다시 fix 처리 요망"

배경:
- W2 / W3 / W4 PR 마다 reviewer 가 식별한 fix 가 W5 / Phase 10 cutover 로 누적 위임됨 → 누적 backlog 28건 (W2 13 + W3 15 + W4 12 추정) 누적
- W5 시점에 fix 28건 일괄 처리 = 검토 부담 폭증 + 회귀 위험 누적
- Phase 10 cutover 시점 fix 미적용 = production 진입 직후 hotfix 폭주 위험
- 사용자 의도: fix 발견 즉시 처리 = 누적 부담 분산 + production 직진 안전성

### How to apply

#### 1. reviewer prompt 의무 추가

향후 모든 reviewer spawn prompt 에 다음 명시:

```
## fix 분류 의무 (본 PR 즉시 처리)
- 식별한 fix / bug / 운영 가드 / 컴포넌트화 / edge case 모두 "본 PR 채택 권장" 분류 의무
- "Phase 10 cutover" / "W5 위임" / "후속 PR" 분류 금지 (회고 plan 문서 제외)
- backlog 누적 형태로만 보고 X — 종합 TM 이 모두 본 PR 채택 의무
```

#### 2. 종합 TM prompt 의무 추가

```
## 채택 fix 의무 (본 PR commit)
- reviewer 식별 fix / bug / 운영 가드 / 컴포넌트화 모두 본 PR 채택 의무
- "후속 PR 위임" 분류 금지 (예외: 본 PR scope 외 / 회고 plan 문서)
- 채택 fix 의 회귀 위험 = CI 재검증 의무로 가드
- backlog 명시는 회고 / plan 문서 (`Phase 9 회고 보고서`) 에 한함
```

#### 3. 적용 사례 (2026-05-07)

- **PR #94** (Phase 9 4차 W4): 종합 TM 1차 = 채택 fix 1건 + dev-report § 후속 backlog 12건 → 사용자 가드 적용 = **fix TM 추가 spawn 으로 backlog 12건 중 fix 가능 모두 본 PR 적용** (W5 회고 plan 만 위임 유지)
- 향후 W5 + Phase 10 모든 PR = 본 가드 일관 적용

### 회고

| Before (W2/W3/W4 1차 종합 TM) | After (W4 가드 적용 + 향후) |
|---|---|
| reviewer 식별 fix → "Phase 10 cutover backlog" / "W5 회고 backlog" 누적 위임 | reviewer 식별 fix → 본 PR 즉시 채택 의무 |
| backlog 28+건 누적 → W5 시점 일괄 처리 부담 | 본 PR 마다 5~15건 fix 분산 처리 |
| Production 진입 직후 hotfix 폭주 위험 | Production 진입 시 fix 누적 적용 완료 상태 |

## 관련 메모리

- `feedback_pr_qa_screenshots.md` — QA 캡처 첨부 의무 (통합 PR 도 동일 적용)
- `feedback_print_design_iteration.md` — 인쇄 양식 iteration 후 통합 PR 1개로 발행
- `feedback_multi_agent_team_pattern.md` — 5-team 산출물 통합 PR 패턴
- `feedback_github_pr_workflow.md` — TL → PM → 대표 승인 체인
- `feedback_pr_ci_monitoring.md` — PR 발행 후 PM 자동 CI 모니터링
