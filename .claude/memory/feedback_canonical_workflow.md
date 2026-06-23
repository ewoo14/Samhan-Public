---
name: feedback_canonical_workflow
description: 🚨 슬라이스/PR 표준 워크플로우 단일 진실원 — Opus 기획→Codex 개발→(Opus 5-agent+QA라이브→Codex 5-agent+QA라이브) 0수렴까지 반복→PM 머지. 과거 변동내역 폐기, 본 파일만 따른다.
metadata:
  type: feedback
---

🚨 2026-06-23 개발책임자 확정. **본 파일이 슬라이스/PR 워크플로우의 유일한 진실원.** 과거 워크플로우 변동내역(다모델/듀얼5에이전트/TM주도/조기PR/리뷰게시/5팀 등 개별 파일)은 폐기·본 파일로 통합. 워크플로우가 반복적으로 어겨져 단일화함 — **이 순서를 토씨까지 따른다.**

## 표준 순서 (슬라이스/PR 1건)

1. **Claude(Opus) 기획 + PR 개설** — 스펙/플랜 수립, 브랜치, 조기 PR 개설(구현 누적 전 PR 먼저).
2. **Codex 개발 + 개발사항 리뷰 게시** — Codex 가 구현(Claude 직접 코드 작성 금지, 예외=memory/docs/spec/plan/git/gh CLI). Codex 가 자기 개발사항 리뷰를 PR 에 게시.
3. **Claude 5-agent 리뷰 + fix → TM 통합리뷰 게시(스크린샷 인라인)** — 아래 [5 agents] 5인 리뷰. QA agent 는 **Docker 라이브 QA 진행 + 스크린샷 캡처**. fix 반영 후 **TM 통합리뷰**를 PR 에 게시(라이브 QA **스크린샷 인라인 포함**).
4. **Codex 5-agent 리뷰 + fix → TM 통합리뷰 게시(스크린샷 인라인)** — 동일 구조. Codex 측 5인 리뷰 + QA agent Docker 라이브 QA + 스크린샷. fix 후 TM 통합리뷰 게시(스크린샷 인라인).
5. **반복** — 3↔4 같은 사이클을, **리뷰의 error / skip / backlog 등 잔여가 0 으로 수렴할 때까지** 계속. (test.skip·false-green·미실행·백로그 이월 = 통과 아님. 백로그는 0수렴 불가 결함 한정, 단순 fix 우선.)
6. **PM 최종 확인 + CI 모니터링** — PM 이 종합 확인, `gh pr checks --watch` 로 CI green 확인.
7. **PM 머지** — 0수렴 + CI green + 라이브 QA 완료 시 PM 이 머지(개발책임자 머지요청 불요, 자율). 멈춤 = 신규 업무규칙/정책 결정만 개발책임자 확인.

## 5 agents 정의
- **FE / BE / Design / DevOps / QA** 5인.
- **QA agent 는 FE/BE/Design/DevOps 4인 리뷰 + fix 이후** 진행(순차) — 실 산출물에 대해 **Docker 라이브 QA**(실 게이트웨이:8080 / 실 서비스 / 실 시드, mock OFF) + **실사용자 화면 스크린샷 캡처**.

## 절대 규칙
- 🚫 **듀얼리뷰 병렬 절대 금지** — Claude(Opus) 라운드 **완료·게시** 후에야 Codex 라운드 진입(순차). 서로 cross-check 가능하게. 한 PR 의 Opus·Codex 동시 실행 금지.
- 🚫 **단축 금지** — 트리비얼/기계적/sweep/1줄 PR 도 동일 워크플로우. Codex 단독·단일모델 머지 금지. 모든 PR 에 Opus·Codex 양측 리뷰 + TM 통합 + 라이브 QA 스크린샷.
- 🚫 **리뷰 = 실 QA 동반 필수** — 모든 리뷰 라운드는 Docker 라이브 실 QA + 스크린샷을 그 라운드 코멘트에 인라인 게시. code-read PASS·가짜 캡처(PIL 합성/mock 화면) 금지. 실연동 불가 시 "사유" 정직 보고.
- 🚫 **제때 게시** — 각 라운드 완료 즉시 게시(머지까지 batch 보류 금지).
- ✅ **무중단 자율** — 슬라이스 끝마다 묻지 말고 PM 연속 진행(다음 슬라이스 자동 착수). 한국어 커밋/PR(prefix·trailer 예외), `[FEAT]`/`[FIX]` 대괄호 prefix, Role 풀네임, 개발책임자 결정은 진행 중 PR 에 누적 기록.

## 기술 참조(별도 유지)
- Codex 호출 = `mcp__codex__codex`(approval-policy:never, review=sandbox read-only / fix=workspace-write 또는 danger-full-access, config:{model_reasoning_effort}). Claude 가 commit 대행(Codex git 금지). → [[feedback_codex_plugin_setup]] [[feedback_codex_sandbox_git]]
- 라이브 QA 실행법(렌더러 mock off·standalone 부팅·캡처) → [[feedback_qa_docker_real_test]] [[realqa-run-and-false-red]]
- Codex MCP 세션 한계 시 새 세션/codex exec 우회 → [[feedback_codex_mcp_session_limit]]
