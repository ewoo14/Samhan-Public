---
name: 모든 PR 표준 리뷰 워크플로우 — 에이전트 리뷰 → TM 승인 → CI → PM 최종 승인 → 머지 요청
description: 모든 PR (chore 포함) 에 5-team agent (BE/FE/Designer/QA/DevOps) 리뷰 댓글 필수. TM 이 리뷰 종합 + 승인 댓글. CI green + PM 최종 승인 댓글 후 개발책임자 머지.
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
모든 PR 은 다음 5-단계 표준 워크플로우 통과 후만 머지 요청 가능. PR 새로 발행 X — 기존 PR 에 리뷰만 추가.

## 워크플로우 단계 (순서 의무)

### 1. 5-team agent 리뷰 (PR comment)

PR 발행 직후 PM 이 5-team agent 병렬 디스패치 → 각 agent 가 PR comment 로 리뷰:
- **backend-engineer**: 백엔드 코드 / 도메인 / DB 영향 검토
- **frontend-engineer**: 프론트 영향 / API contract / TypeScript 타입 검토
- **designer**: UI/UX / 인쇄 양식 / design-system 일관성 검토
- **qa-tester**: 시나리오 영향 / 회귀 가능성 / 도메인 정합성 검토
- **devops-engineer**: 인프라 / CI / .env / docker-compose 영향 검토

각 리뷰 댓글 형식:
```markdown
## [agent 이름] 리뷰
**결과**: ✅ 승인 / ⚠️ 조건부 / ❌ 거절

**검토 사항**:
- (검토 결과 상세)

**제안**:
- (있으면)
```

### 2. TM (Tech Manager) 종합 + 승인

TM agent 가 5-team 리뷰 종합 후 PR comment 로 승인:
```markdown
## TM 종합 + 승인

| Agent | 결과 |
|---|---|
| backend-engineer | ✅ |
| frontend-engineer | ✅ |
| ...
| **TM 종합** | ✅ 승인 / ⚠️ 추가 fix 필요 |
```

추가 fix 필요 시 TM 이 통합 fix commit 발행 → 1단계 재실행.

### 3. CI 검증

`gh pr checks <PR> --watch` background → 모든 check green 후 진행.

### 4. PM 최종 승인 (PR comment)

PM agent 가 PR comment 로 최종 승인:
```markdown
## ✅ PM 최종 승인

- 5-team 리뷰: ✅ 모두 승인
- TM 종합: ✅ 승인
- CI: ✅ 모든 check green

**개발책임자 머지 요청 드립니다.**
```

### 5. 개발책임자 머지

사용자 (개발책임자) 본인이 GitHub UI 에서 직접 머지. PM/Claude 의 admin 강행 머지 금지 (가드: feedback_user_merge_authority).

## 가드 (절대 위반 금지)

- **PR 새로 발행 금지** (이미 있는 PR 에 리뷰 추가만)
- **5-team 리뷰 누락 금지** (단순 chore PR 도 5-team 모두 리뷰 — 형식적이라도)
- **TM 승인 없이 PM 최종 승인 금지**
- **CI fail 상태에서 PM 최종 승인 금지**
- **PM 최종 승인 없이 머지 요청 금지**
- **PM/Claude 의 머지 금지** (사용자 본인만)

## 단순 PR (config / docs only) 도 적용

코드 변경 0 인 PR 도 5-team + TM + PM 리뷰 의무 — 형식적 리뷰라도 트리거. 단순 PR 의 5-team 리뷰는 짧게 ("DevOps 영향 only, BE/FE/Designer 무관" 형태).
