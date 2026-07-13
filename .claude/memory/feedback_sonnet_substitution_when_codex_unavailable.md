# Codex 부재 시 Sonnet 5 서브에이전트 대체 모드

Codex 사용 불가(사용량 한도 등) + 토큰 절약 필요 시, 표준 캐논([[feedback_canonical_workflow]])의 "Codex 구현/리뷰" 역할을 **Sonnet 5 서브에이전트**로 대체하고 Opus 는 PM + STEP4 만 수행한다. (2026-07-08 개발책임자 지시 — Codex Jul11 한도.)

## 역할 분담
- **Sonnet 5 서브에이전트**(`model: sonnet` · 최대추론, Agent 도구는 effort 노브 없어 세션 effort 상속): 정찰 · **구현(코드 작성)** · 5-agent 리뷰(FE/BE/Design/DevOps/QA 전 차원) · 라이브 QA · 검증.
- **Opus (=PM)**: 기획·판단 · **STEP4 독립 적대검증**(= Codex 라운드 + 개발책임자 승인 대체) · **Sonnet 산출물 점검**(중형모델이라 필수) · commit 대행 · PR/이슈 관리 · 머지.

## 규율 (대체모드에서도 캐논 엄수)
- 구현 코드는 Sonnet 만 작성([[feedback_pm_no_direct_implementation]]). Opus 는 diff STEP4 검토 + genuine 테스트 재실행(캐시 false-green 방지 [[feedback_gradle_test_cache_false_green]])로 점검 후 commit 대행.
- 매 라운드: Sonnet 5-agent 리뷰/구현 → Opus 점검·전지적 disposition → genuine 건만 Sonnet fix(그 라운드 진행모델) → **Opus STEP4 0수렴**. 리뷰=실 라이브 QA 동반·단축금지([[feedback_review_5agent_no_shortcut_strict]]).
- **STEP4·검증은 변경모듈 전체 스위트 실행**([[feedback_changed_module_full_test_before_push]]) — slice-IT 만 돌리면 P0 누락. (2026-07-08 #774: PageCode enum P0·FE permissionsApi parity 를 전체 auth/desktop 스위트 미실행으로 놓쳐 CI 가 포착.)
- Codex 복구(Jul11) 후 표준 Opus + Codex 듀얼리뷰로 복귀.

## 🚨 대체 발동 조건 = Codex 진짜 부재만 (2026-07-13 #813 위반 박제)
- 본 대체는 Codex 가 **진짜 사용 불가**(한도·MCP hang·복구 불가)일 때만. **"codex exec 가 다중파일 편집서 크래시할 것"이라는 미검증 단정으로 구현을 서브에이전트에 넘기고 캐논 2단계(Codex 개발+개발사항 리뷰)를 건너뛴 것 = 워크플로우 위반**(2026-07-13 #813 개발책임자 지적 "코덱스 개발 리뷰 없이 OPUS 리뷰로 건너뛰었잖아").
- codex exec `-s danger-full-access` **쓰기는 이 집PC서 반복 실증**(S5 R2 fix·#813 구현 fix·IT fix 전부 exit 0·genuine 산출). 크래시는 **파일탐색/rg PowerShell 스폰 한정**이라 **프롬프트에 인라인 스펙/컨텍스트 제공(파일읽기 불요) 시 쓰기·리뷰 정상**. → 대체 발동 전 **codex exec 실제 시도부터**(미검증 단정 금지).
- #813 실증: 소급 Codex 개발 리뷰가 **Opus 5-agent 가 놓친 HIGH blank-token parity 결함 + MED 응답 completeness 를 포착** → 캐논 2단계(Codex 개발 리뷰)가 결함 방지의 핵심. 순서(2단계→3단계 Opus→4단계 Codex 적대)도 엄수. [[feedback_review_5agent_no_shortcut_strict]] [[feedback_codex_rescue_unreliable_use_mcp]]

## 실증 (2026-07-08 · #729·#771·#17 S4a 3-PR 캐논 완주)
Sonnet 5-agent 리뷰가 Opus STEP4 가 놓친 실결함 3건(accounting `MultipleBagFetchException`×5 · 역분개 backfill orphan 회귀 · PageCode enum 미등록 P0)을 포착 — **대체모드에서도 5-agent 리뷰 규율 유지가 결함 방지의 핵심**.
