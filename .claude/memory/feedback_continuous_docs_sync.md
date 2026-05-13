---
name: 문서 동기화 의무 (작업 PR 에 포함, 별도 docs PR 금지)
description: 매 작업 PR (Phase / 통합 / fix) 에 README + ROADMAP + DECISIONS + 각 client/service README + dev-reports 갱신 의무 포함. 별도 docs PR 발행 금지 (PR #80/#85 패턴 폐기)
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
# 규칙

매 작업 PR (Phase 1차/2차/3차 + 통합 PR + fix PR) 에 다음 docs 동기화 의무 포함:

1. **루트 README.md** — Phase 진행 / 머지 PR 매핑 / 디렉토리 구조 / 신규 service 추가 시
2. **ROADMAP.md** — Phase 상태 (진행 중 / 완료) / 머지 PR 매트릭스 / 미결 결정
3. **DECISIONS.md** — 신규 결정 D-P{N}-{NN} append (작업 단위마다)
4. **각 client/service README** — 신규 기능 / 신규 endpoint / 신규 환경변수 추가 시
5. **dev-reports** — 작업별 dev-report 1건 의무

**별도 docs PR 발행 금지** — 코드 작업 + docs 갱신 = 동일 PR.

# Why

사용자 명시 (2026-05-05): "모든 문서가 계속 갱신이 안되고 있음 / 문서 갱신은 작업하면서 꾸준히 요청 / 따로 PR을 만들지 말고 현재 진행 중인 PR에 포함"

회고 사례:
- PR #80 (Phase 6 마무리) + PR #85 (문서 + ROADMAP) — docs 위주 별도 PR. 작업 PR 과 분리되어 있음
- 결과: 작업 PR 머지 후 docs 가 main 에 반영 안 된 상태에서 다음 작업 진행 → docs 와 코드 비동기 → ROADMAP 의 Phase 상태가 항상 1~2 PR 뒤처짐

# How to apply

## 1. 매 통합 TM prompt 에 docs 동기화 항목 의무 추가

```
## docs 동기화 (의무 — 본 PR 에 포함)
- ROADMAP.md — Phase {N} {차수} 진행/완료 표시 + 머지 PR 매트릭스에 본 PR 추가
- DECISIONS.md — 신규 결정 D-P{N}-{NN} append (본 작업의 결정 사항)
- 루트 README.md — 신규 service / 신규 client / Phase 진행 갱신 (필요 시)
- 각 영향 받는 client/service README — 신규 endpoint / 환경변수 / 의존성 갱신
- dev-report — `docs/dev-reports/{slice-name}.md` 신규 1건 의무
```

## 2. 매 reviewer prompt 에 docs 검증 항목 추가

```
# Review scope
... (기존 항목)
- docs 동기화 검증 — ROADMAP / DECISIONS / 영향 받는 README / dev-report 갱신 누락 X
```

## 3. 종합 TM 이 docs 누락 발견 시 즉시 추가 commit

reviewer 가 docs 누락 지적 시 = 종합 TM 의 fix 단계에서 추가 commit (별도 PR X).

## 4. 별도 docs PR 패턴 폐기

기존 패턴 (PR #80 / PR #85) = 작업 PR + 별도 docs PR 분리 → 폐기. 향후 모든 docs 갱신은 작업 PR 에 포함.

# 예외

- legacy 비즈니스 로직 변형 PR 의 docs 영향 0 시 = dev-report 1건만
- 긴급 hotfix (보안 / 데이터 손실) = docs 갱신 후속 PR 가능 (단 hotfix PR 본문에 후속 명시)

# 관련 가드

- `feedback_integrated_pr_pattern.md` — 통합 PR 패턴 (docs 도 통합에 포함)
- `feedback_function_documentation.md` — 함수 단위 문서화 3-layer
- `feedback_no_dev_director_mention.md` — docs 멘트 가드
- `feedback_tm_led_agent_discussion.md` — reviewer 토론 (docs 검증 포함)
