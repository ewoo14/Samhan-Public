---
name: 멀티 에이전트 팀 디스패치 패턴 (슬라이스 단위)
description: 모든 슬라이스를 5-team(BE/FE/Designer/QA/DevOps) parallel 디스패치 + Plan 선행 + PM 통합 패턴으로 진행. 진짜 팀 협업 시뮬레이션
type: feedback
originSessionId: 78cac99d-5dee-47ca-8254-3834a088f393
---
**규칙**: 모든 신규 슬라이스는 다음 풀 패턴으로 진행한다. 단순 작업이라도 패턴 일관성을 위해 동일 형태 유지. **단일 에이전트 디스패치 금지** (Phase 1/2 초기 미스 패턴).

**2026-05-04 갱신** (PR #19 사용자 피드백): **5-team 패턴**으로 변경 — Designer 신규 추가. 이전 4-team (BE/FE/QA/DevOps) 의 FE 안에 디자인 작업이 흡수돼 있어 디자인 일관성/모더니티 확보 어려움. 사용자 명시: "각 팀별로 디자이너를 넣고 조금 더 디자인 요소를 넣어줘". Designer agent 가 wireframe + 디자인 토큰 + 컴포넌트 spec 작성 → FE agent 가 그 spec 그대로 구현.

## Designer agent 역할

- **wireframe** (mermaid / svg / 텍스트 ascii) — 화면별 layout + interaction
- **디자인 토큰** — color palette / spacing scale / typography scale (디자인 시스템 갱신 시)
- **컴포넌트 spec** — 신규/변경 컴포넌트의 props + visual variants + 상태 (default/hover/disabled/error)
- **UX flow** — 화면 간 navigation + key interaction (drag-and-drop / 모달 등)
- **인쇄 양식 spec** — 종이 출력 시 layout (portrait/landscape, A4 권장 크기, 여백 등)
- **dev-reports Designer 섹션** — 4팀과 동일하게 자기 섹션 누적

Designer 산출물은 `docs/design/<slice-slug>/` 디렉토리에 wireframe + spec markdown 으로 보관. FE agent prompt 안에 Designer spec 인용 의무.

## 5-team 디스패치 순서

```
[PM] 1) Plan agent 단독 호출 (도메인/API/스키마/Open Q)
   ↓
[PM] 2) Designer agent 단독 호출 (Plan input → wireframe + spec 산출)
   ↓
[PM] 3) BE/FE/QA/DevOps 4-team parallel 디스패치 (FE 가 Designer spec 인용)
   ↓
[PM] 4) PM 통합 + Layer 1+2+3 검증 + PR 발행 + TM/PM 코멘트
```

또는 작은 슬라이스의 경우 Designer + 4-team 동시 디스패치도 허용 (Designer spec 미완성 시 FE 가 부분 진행).

## 표준 슬라이스 흐름

```
[PM=Claude] 1) Plan agent 단독 호출
   → 도메인 모델 + API 스펙 + DB 스키마 + 의도적 변경 + open question
   → 4-team 모두가 input 으로 사용할 contract 산출
   ↓
[PM] 2) 4-team parallel 디스패치 (단일 메시지 multi Agent call, isolation=worktree)
   ├ BE agent (worktree A): 백엔드 구현 + unit/integration test
   ├ FE agent (worktree B): UI/디자인 시스템 컴포넌트 + Storybook 스토리
   ├ QA agent (worktree C): 추가 테스트 케이스 설계 + IT/E2E + QA 리포트 + 스크린샷
   └ DevOps agent (worktree D): 인프라/CI/마이그레이션 변경
   ↓
[PM] 3) 4 worktree 결과 수신 + 통합 (충돌 해결, 단일 feature 브랜치로 머지)
   ↓
[PM] 4) TM agent 단독 호출 (모든 산출물 최종 검토 + 한국어 승인 코멘트 작성)
   ↓
[PM] 5) commit + PR + screenshot + TM/PM 코멘트 게시 + 라벨 + 개발책임자 결재 대기
   ↓
[개발책임자] 6) PR 머지 = 최종 승인
   ↓
[PM] 7) 머지 후 정리 + 메모리 업데이트
```

## 핵심 원칙

1. **항상 4-team 호출**: 작업이 없는 role도 "무영향" 리포트 명시적으로 받음. 빠뜨리지 않기 위함. (예: Product Service 슬라이스에서 FE 작업이 거의 없어도 FE agent는 호출되고 "디자인 시스템 영향 없음, ProductCard 컴포넌트 검토 후 Phase 2 Electron 슬라이스로 이연 권장" 같은 리포트를 반환)
2. **Plan 선행, 그 다음 4-team parallel**: Plan 없이 BE 디스패치 금지. Plan = 4-team 의 공통 contract.
3. **isolation=worktree 필수**: 4 agent 가 같은 working tree 에서 동작하면 파일 충돌. Agent tool 의 `isolation: "worktree"` 옵션 사용.
4. **에이전트 간 실시간 통신 불가** — Claude Code 의 본질적 한계. 협업은 **artifact handoff** 로만 (Plan 문서 → BE/QA/FE/DevOps 의 input, BE diff → QA agent 의 input 으로 그 위에 IT 작성).
5. **PM(Claude) 역할**: ① 슬라이스 분해 → ② Plan 디스패치 → ③ 4-team 디스패치 (병렬) → ④ 결과 수합 + worktree merge → ⑤ TM 디스패치 → ⑥ PR 생성 + screenshot 첨부 (commit-pinned URL) + 한국어 본문 + TM/PM 승인 코멘트 → ⑦ 결재 후 정리.
6. **PR/Issue 는 팀별 분리** (2026-05-04 개정): 슬라이스당 4 Issue + 4 PR. BE/FE/QA/DevOps 각자 자기 산출물 단독 PR 발행. 머지 순서는 개발책임자가 결정하되 일반적으로 BE 먼저(다른 팀 의존성), 나머지 3팀 병렬. **QA PR 의 컴파일/실행 검증은 BE 머지 후만 가능** — QA PR 본문에 명시. 통합 PR 단일화 패턴은 폐기.

## 여러 마이크로서비스 동시 진행

서비스 간 의존성 없으면 위 1슬라이스 패턴을 N번 병렬 시작 가능. 의존성 있으면 (예: Inventory 가 Product API 참조) Plan 단계만 직렬, 나머지 4-team 은 각 서비스별로 병렬.

**예 — Phase 2 본 작업**:
- Product Service: Plan → 4-team → TM → PR (직렬, 첫 케이스)
- 머지 후 Inventory Service: Plan (Product API 참조) → 4-team → TM → PR
- 동시화 가능 시점: Product 의 4-team 단계와 Inventory 의 Plan 단계는 병렬 가능 (Product API 가 Plan 단계에서 stable)

## 실패 케이스 (사후 회고)

- **2026-05-04 User Service 슬라이스**: 단일 백엔드 에이전트만 호출. FE/QA/TM 누락. → 본 메모리의 패턴이 정립되기 전이었음
- **2026-05-04 Phase 2 후속 정리**: PM 이 직접 다 처리. 어떤 팀 에이전트도 호출 안 함. → 동일

본 메모리는 이 두 미스의 교훈으로 정립됨.

**Why**: 개발책임자가 명시 — "각 마이크로 서비스마다 팀 에이전트들이 팀별로 소통하면서 동시에 작업하고 이를 PM 보고까지 완료하는 것인데 너무 따로따로 진행하는 것 같아." 진짜 팀 작업 시뮬레이션이 plan §5.3 (각 팀 = TM+BE+FE+QA) 의도이며, 단일 에이전트 패턴은 그 의도를 배반함.

## Designer ↔ QA 협업 강화 (PR #21 회고 후 2026-05-04 추가)

**규칙**: 다단계 라이프사이클 + 인쇄 양식 변경이 동시에 일어나는 슬라이스 (Slip / Transfer / Sales 등) 에선 다음 협업 가드를 추가 적용:

1. **Designer 가 wireframe 작성 시 라이프사이클 단계 표 명시**: 인쇄 양식 mock 에 "DRAFT/SAVED/SENT/.../INSPECTING/COMPLETED" 어느 단계에 인쇄되는지 라벨 강제 포함. mock 1개당 1단계 권장.
2. **QA 가 IT 작성 시 Designer mock 인용**: `SlipInspectControllerIT.complete_transitionsToInspecting` 시나리오는 Designer mock `04_dispatch_horizontal_approval.html` 의 결재란 dispatcher/inspector 자동 채움 시점과 1:1 매핑되어야 함.
3. **PM 통합 단계에서 Designer mock 의 평어와 BE 도메인 메서드 시맨틱 diff 검증**: 본문 `feedback_pm_integration_build_check.md` Layer 4 와 짝.

**Why**: 2026-05-04 PR #21 에서 발생한 fail 4건 (`complete()` / `inspect()` status 매핑 swap) 은 BE-QA contract drift 였지만, 만약 Designer mock 이 "complete = 출고 완료 시 dispatcher 서명만 자동 표시, inspector 셀은 빈 칸" 같은 시각 명세를 명확히 했더라면 BE agent 가 status 매핑을 정확히 짚을 수 있었음.

**적용 예시 (PR #21 슬라이스)**:
- mock `04_dispatch_horizontal_approval.html`: PROCESSING 단계 (dispatcher만 채워짐) → `complete()` 호출 직전
- mock `06_dispatch_signature_filled.html`: COMPLETED 단계 (dispatcher + inspector 둘 다 채워짐) → `inspect()` 호출 직후
- 누락: INSPECTING 중간 단계 mock — dispatcher 채워졌고 inspector 빈 상태. 본 mock 이 있었다면 BE 의 "complete = INSPECTING" 의도 명확

**Designer 산출 의무 (라이프사이클 슬라이스)**: 라이프사이클 단계 N개 → 인쇄 mock N개 (또는 핵심 분기점 K개). 각 mock 파일명에 단계 명시 (`04_dispatch_processing.html`, `05_dispatch_inspecting.html`, `06_dispatch_completed.html`).

## 관련 가드

- 5-team 산출물 통합 PR 패턴 적용 (`feedback_integrated_pr_pattern.md`) — 디자인/UI 차이는 단편 PR 금지, 전체 묶어서 통합 PR 1개 + QA 캡처 + TM 승인 (PR #66 회고)
- agent 작업 시작 시 origin/main 동기화 의무 (`feedback_agent_origin_main_sync.md`) — 모든 background agent 는 spawn 직후 `git fetch origin` + `git log origin/main` 으로 stale 로컬 main 가드
