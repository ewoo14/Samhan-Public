# 🚨 리뷰 라운드마다 Docker 실 라이브 QA 인라인 (슬라이스 1회 금지) — 반복 이탈 박제

> 2026-06-22 개발책임자 지적("리뷰 때마다 도커 실 라이브 테스트한거 맞아?"). 슬1~4b 회고 — Docker 실QA를 슬라이스당 1회만 하고 라운드별로 안 함.

**규칙**([[temp-multimodel-workflow]] 강화): 듀얼리뷰 **각 라운드마다**(🔵 Opus 5-agent 라운드 / 🟣 Codex 5-agent 라운드) **QA agent 포함 + Docker 실 라이브 QA 캡처를 그 라운드 리뷰 코멘트에 인라인 게시**한다. 슬라이스당 1회(PM 종합에만)로 끝내지 말 것.

**왜**: 각 라운드가 fix를 적용하면 그 fix를 **그 라운드에서 실 라이브로 검증**해야 한다(다음 라운드/PM 종합으로 미루면 fix가 라이브 미검증으로 통과). [[no-fake-data-ever]]·[[overnight-live-capture]]: 실 게이트웨이 :8080 + 실 로그인/응답. mock은 실 불가 시 한정 + "사유 명시"(슬4b config admin-게이트처럼).

**⚠️ 핵심(개발책임자 정정)**: **실 라이브 QA는 "리뷰"가 한다 — PM 종합이 하는 게 아니다.** 각 리뷰 라운드의 **QA agent**가 Docker 실 라이브 QA를 수행하고 그 라운드 코멘트에 캡처를 인라인. **PM 종합은 합성/수렴 확인만**(라운드 QA 결과를 종합할 뿐, QA를 거기서 수행 X).

**올바른 패턴** (라운드별):
1. 🔵 Opus 5-agent(BE/FE/QA/DevOps/완전성 — **QA agent가 Docker 실 라이브 QA 수행**) → fix → **그 라이브 캡처를 Opus 라운드 코멘트에 인라인**.
2. 🟣 Codex 5-agent(**QA agent가 Docker 실 라이브 QA 수행**, 특히 Codex 라운드 fix 반영분) → fix → **그 라이브 캡처를 Codex 라운드 코멘트에 인라인**.
3. 🟢 PM 종합 = **합성·수렴 확인만**([[rereview-converge-after-fix]]). 여기서 QA를 새로 하지 않는다(라운드 QA를 종합).

**자가 점검(라운드 게시 직전)**: "이 라운드 코멘트에 Docker 실 라이브 QA 캡처가 인라인으로 들어갔나? fix 있었으면 그 fix를 라이브로 찍었나?" — 아니면 캡처부터.

**효율 주의**: BE-only/무수정 라운드의 동일 화면 반복 캡처가 과해 보여도, fix가 있으면 반드시 그 라운드 라이브 캡처. 무수정 라운드는 핵심 라이브 1컷이라도 그 라운드에 첨부.

관련: [[temp-multimodel-workflow]] · [[rereview-converge-after-fix]] · [[no-fake-data-ever]] · [[overnight-live-capture]] · [[qa-docker-real-test]] · [[real-server-check-screenshot]]
