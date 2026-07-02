---
name: feedback_live_qa_every_round_screenshots
description: 라이브 QA(Docker 실서버+실 GUI 스크린샷)는 매 리뷰 라운드마다 필수 — 끝 1회/텍스트 대체 금지
metadata: 
  node_type: memory
  type: feedback
  originSessionId: b6595c58-1401-4b50-805f-e460138d686c
---

2026-07-02 개발책임자 강력 지적(E2 #699 위반). 캐논 워크플로우 QA 차원 = **"Docker 라이브QA + 단계별 스크린샷"을 매 리뷰 라운드마다**(Opus 5-agent 라운드 AND Codex 5-agent 라운드 각각) 수행. 위반 2종 금지:

1. **끝에 1회로 미루기 금지** — 라이브 QA 를 마지막 Task/PM 단계로 deferral 하지 말 것. **각 리뷰 라운드의 QA agent 가 실제 Docker 라이브 QA 를 수행**하고 스샷을 그 라운드 게시에 인라인.
2. **텍스트/API/SSE 캡처로 GUI 스샷 대체 금지** — 실사용자 데스크톱/브라우저 **화면 스크린샷**이 증거. curl/SSE round-trip 텍스트는 보조일 뿐, [[feedback_real_server_check_screenshot]] 기준(실 UI 화면 캡처) 필수. 단계별 여러 장(한 장 금지, [[feedback_canonical_workflow]]).

**Why**: E2 #699 에서 라이브 QA 를 매 라운드가 아닌 종료 직전 1회, 그것도 SSE 텍스트 캡처(스샷 없음)로 처리 → 개발책임자 "라이브 QA를 리뷰 라운드마다 요청했었음" 지적. QA 차원이 정적 코드리뷰로 형해화되면 실 UI 결함(깜빡임·stale·충돌)을 놓침.

**How to apply**: 매 Opus·Codex 리뷰 라운드에서 QA agent(또는 PM)가 (a) Docker 실서버 재빌드·기동(코드 반영, launch 후 `docker compose up -d --build <svc>` [[project_local_stack_qa_gotchas]]) (b) 실 게이트웨이 :8080·dev_master(`dev_p05_pass!` DEV-SEED)·mock OFF 로그인 (c) 해당 기능 실 화면 **단계별 스크린샷** 캡처(2세션 라이브 반영 등) docs/qa/<slice>/ (d) 라운드 게시에 인라인. Docker/GUI 불가 요소만 정직 명시(P2)+대안. [[feedback_qa_docker_real_test]] [[feedback_overnight_live_capture]] [[feedback_no_fake_data_ever]]
