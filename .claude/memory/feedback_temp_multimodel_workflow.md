---
name: 다모델 리뷰 워크플로우 (현행 단일)
description: 슬라이스 사이클 = Opus 4.8 계획/PR → Codex 개발 → Opus 4.8 5-agent → Codex 5-agent → PM. Opus 4.8↔Codex 2모델만 번갈아(Fable5 영구 제외). 각 라운드 5-agent에 QA agent 포함 + 스크린샷을 그 라운드 리뷰에 게시. 구 워크플로우 전부 대체
metadata:
  type: feedback
---
2026-06-11 개발책임자 지시 / 2026-06-12 재확인("내가 준 워크플로우대로... 이전 워크플로우는 모두 삭제하고 새 워크플로우대로") / **2026-06-13 Fable5 영구 제외 확정**. **본 워크플로우가 유일 — 구 dual/N-cycle 워크플로우 메모리(dual_5agent_review·cycle_n2_mandatory·cycle_pm_judgment_gate·pr_review_workflow·codex_fix_claude_verify)는 삭제·대체됨.**

> 🚫 **[2026-06-13 개발책임자] Fable5 영구 제외** — 엔트로픽이 Fable5 사용 중지. 워크플로우는 **Opus 4.8 + Codex 2가지만 번갈아** 진행. (구 step 5 'Fable5 5-agent' 라운드 영구 삭제. 리뷰 라운드는 3=Opus 4.8, 4=Codex 두 개만.)

> 🔁 **[2026-06-13 재확정] 사이클 규칙**: 기획 → Codex 개발 → **순차 5-agent 리뷰(라운드마다 PR 게시 + 실서버 QA 데스크톱 실화면 스크린샷 인라인 필수, 텍스트만 금지)** → **다음 리뷰어가 에러 0건 발견할 때까지 Opus 4.8↔Codex 계속 사이클**(라운드 수 무제한 — 0 도달이 종료조건) → 에러 0이면 **PM 최종 점검 + 머지**. (PM 이 반복 위반한 지점 = ①라운드 리뷰 미게시 ②실서버 스크린샷 누락 — 매 라운드 필수.)

> ⏱️ **[2026-06-21 개발책임자 정정] 리뷰는 Opus 먼저(완결) → 그 다음 Codex. 병렬 금지.** PM이 속도 위해 Opus·Codex 리뷰를 동시 디스패치한 것을 "OPUS가 먼저 리뷰하고 그리고 Codex가 리뷰해야지"로 정정. Codex는 **Opus findings까지 교차검증**(독립 + cross-check). 수렴 라운드·통합 테스트도 동일(Opus→Codex 순차).
> 🇰🇷 **[2026-06-21 개발책임자] Codex 리뷰/보고서는 한국어로 게시.** Codex 기본 영어 출력 → 디스패치 프롬프트에 "보고서를 한국어로 작성하라" 명시(또는 게시 전 번역). [[korean-commits]] 확장. (영어로 게시된 C1a/C1b Codex 리뷰를 사후 한국어로 PATCH 교체함.)
> 🛠️ **[2026-06-21] 환경/검증 함정**: ①이 Bash 도구에서 `cmd /c "gradlew.bat"`는 미작동(cmd 배너만·gradle 미실행) → **`./gradlew` 사용**. ②pessimistic-lock + re-fetch JPA 함정 — 락 전 같은 엔티티 no-lock 적재 시 `FOR UPDATE` 재조회가 영속성 stale 캐시 반환(동시성 깨짐) → projection으로 키만 얻고 엔티티는 락 단계 fresh 로드. ③모든 fix는 커밋 전 `./gradlew :services:<svc>:test`로 검증(broken commit 0). 정적 듀얼리뷰가 못 잡는 결함을 실 테스트가 단독 적발.

> 🔒 **2026-06-12 개발책임자 "영구 워크플로우" 확정 — temp 아님(슬러그만 legacy).**
> ⚠️ **정정(2026-06-12): "코덱스 구현 완료되면 PR 에 리뷰 게시" 의 뜻 = Codex 개발 직후 [개발사항](무엇을 개발했는지 요약)을 PR 코멘트로 게시.** 5-agent 리뷰 findings 를 (더구나 미완 1/2 로) 게시하라는 뜻이 아니었음 — 본 PM 2회 오해.
> **규칙: ① Codex 개발 끝나면 즉시 '개발사항' PR 게시(step 2.5) ② 모든 게시는 완결 산출만 — 부분/미완 리뷰 게시 금지 ③ 5-agent 리뷰 라운드도 완결 후 PR 게시**([[review-posting-and-zero-skip]]).

## 슬라이스 사이클 (Opus 4.8 ↔ Codex 2모델만 번갈아)
1. **Opus 4.8** — 계획 + PR 개설(조기)
2. **Codex(GPT5.5)** — **초기 개발** (이 단계만 Codex 구현 의무 [[codex-implements-claude-reviews]]; 토큰 회복 시)
2.5. **개발사항 PR 게시 (의무)** — Codex 개발 직후 **무엇을 개발했는지**(BE/FE/test/migration 변경 요약 + 컴파일·IT 검증 결과)를 PR 코멘트로 즉시 게시. ← 개발책임자 "리뷰 게시" = 이것.
3. **Opus 4.8 5-agent TM** — 리뷰 + **fix(Opus 4.8 가 직접)** + 게시 (완결 후)
4. **Codex 5-agent TM** — 리뷰 + **fix(Codex 가 직접)** + 게시 (토큰 회복 시)
5. **PM 종합** — 검토 → 머지 또는 다음 사이클 + 게시. **에러 0 도달까지 3↔4(Opus 4.8↔Codex) 라운드 무제한 반복.**

> 🚨 **각 리뷰 라운드의 fix 는 그 라운드를 리뷰한 모델이 직접 수행** (개발책임자 2026-06-12 정정 "OPUS fix 인데 왜 코덱스에 디스패치?"). Opus 4.8 라운드=Opus 4.8 fix, Codex 라운드=Codex fix. **fix 를 일괄 Codex 에 디스패치 금지** — 각 모델이 자기 관점으로 찾은 결함을 자기가 고쳐야 다모델 다양성 유지. **[[codex-implements-claude-reviews]] 의 "Codex 구현 의무·Claude 직접 구현 금지"는 step 2(초기 개발) 한정** — 리뷰-라운드 fix 에는 미적용(Opus 4.8 가 직접 코드 수정).

## 🚨 각 리뷰어 라운드(3·4)에 QA agent + 스크린샷 의무 (자주 위반 — 2026-06-12 재지적)
- 각 라운드 5-agent는 코드축(BE/FE/data/sec)**만** 돌리면 위반. **QA agent 가 Docker 실서버 QA(서비스 재빌드 포함)를 수행하고 그 스크린샷을 해당 라운드 리뷰 코멘트에 인라인 게시**.
- **코드만 리뷰하고 실 QA·스크린샷을 마지막 단일 단계나 별도 전달(SendUserFile/PR본문만)로 미루는 것은 위반.** 라운드별 리뷰 게시에 스크린샷이 함께 있어야 함.
- 라이브 캡처는 미루지 말 것([[overnight-live-capture]]) — 서비스 재빌드해서라도 라운드 안에서.

## 머지 게이트
- **Opus 4.8만 돌리고 머지 물어보기 금지.** 3·4 라운드(Opus 4.8·Codex) + 각 fix + PM 종합까지 완주 후 머지.
- 머지 = **리뷰 error 0 · skip 0** + CI 모두 green + Docker 실 QA(라운드별 스크린샷) 후. PM 종합 게시 → 머지.
- 종료 = 개발책임자 stop.

## 🔁 세션 중단 후 재개 = 마지막 리뷰 라운드 수렴 검증 (2026-06-16 PR #494)
원격 세션이 step5 PM 종합("머지 게이트 충족") 게시 후 머지 직전 끊기면, **Codex step4 fix 뒤 Opus 수렴 재리뷰가 누락된 채 PM 종합만 남을 수 있음**(게시된 PM 종합 = 실수렴 보장 아님). **재개 시 "마지막 fix 라운드 모델 == 마지막 리뷰 라운드 모델?" 확인 의무** — 다르면(예: 마지막이 Codex fix 인데 뒤이은 Opus 재리뷰 없음) 미수렴 → 누락 라운드(Opus 5-agent, **실빌드 동반**)를 보강한 뒤 머지. + "error 없냐" 질문엔 자기보고 인용 금지, **CI 가 실제 HEAD 커밋 기준 green 인지·마이그 V번호 충돌 없는지 독립 재확인** 후 증거로 답. PR #494 = 개발책임자 "코덱스 fix 했으면 Opus 재리뷰" 지적이 누락 단독 적발.

## How to apply
각 라운드 review→fix→PR 코멘트 게시(QA 스크린샷 포함). Codex 라운드는 토큰 회복 후. **Fable5 영구 제외 — Opus 4.8↔Codex 2모델만.** 진행 중 슬라이스는 완료 단계 다음부터 진입. 관련 원칙: [[codex-implements-claude-reviews]] [[review-posting-and-zero-skip]] [[pr-qa-screenshots]] [[qa-docker-real-test]] [[overnight-live-capture]] [[codex-model-auto-switch]].
