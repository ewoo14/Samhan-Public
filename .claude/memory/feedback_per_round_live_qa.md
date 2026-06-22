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

## 🔁 반복 이탈 재발 방지 (2026-06-22 개발책임자 2차 지적 "메모리 박제까지 했는데 왜 개선 안 되냐")

**실패 모드(이름 박제)**: "라이브 QA는 무거우니(Docker 스택 기동) 코드 리뷰부터 돌리고 QA는 마지막 게이트에서" 라는 **합리화**. 지식 부족 아님 — 규율 이탈. 메모리만으론 안 고쳐짐 → 아래 구조 규칙으로 강제.

1. **스택 먼저 기동(리뷰 R1 착수 전)**: 첫 리뷰 라운드를 디스패치하기 **전에** Docker 스택을 띄운다(`docker compose -f docker-compose.yml -f docker-compose.local-all.yml -f docker-compose.no-host-ports.yml up -d` — influxd 8086/8088 충돌은 no-host-ports overlay `!reset []`). 변경 BE 서비스만 이미지 재빌드(`docker compose ... build <svc>`, 나머지 기존 이미지 재사용). FE=standalone 렌더러(`vite --config vite.renderer.dev.config.ts` :5175, `VITE_API_BASE_URL=:8080`, mock off) + real-qa playwright(`playwright.real-qa.config.ts`, `*-real-qa.spec.ts`, AUDIT_BASE_URL=:5175). "리뷰 시작=스택 기동"을 한 묶음으로.
2. **캡처 없는 라운드=미완**: 라운드 코멘트를 게시하거나 다음 라운드/머지로 넘어가기 전, "이 라운드에 실 라이브 캡처가 인라인됐나?" 아니면 **진행 정지하고 캡처부터**. 머지 직전 자가 차단.
3. **데이터 함정 선확인**: 시드 전표 actor가 비-UUID 사용자명이면 이름 resolve 안 됨(UUID actor 전환 필요 — API 로 accept/inspect). product-service 미시드면 INBOUND complete 404(PROCESSING 정지). OUTBOUND accept 는 A2-2 출고 결재 enforcement 로 비결재자 403. → 캡처용 전표는 **dev_master lifecycle 전환**으로 UUID actor 확보.
4. **🚨 라이브 결과 해석도 코드로 검증(과장 금지)**: 2026-06-22 슬5 회고. 라이브에서 이상(convert 가 /my 누락)을 보고 **코드 확인 전에 "RBAC 차단 버그"로 단정** → `getMyPermissions` 읽으니 MASTER=allPageActions(enum)/비-MASTER=bulkLoad(DB,enum무관)이라 **접근 차단 아닌 저심각도 카탈로그 정합**이었음(2회 정정). before/after 도 구 도커 이미지 stale 로 confounded. **라이브 QA 는 필수이되 그 결과 해석/심각도 단정은 반드시 실제 코드 경로로 검증**한 뒤 보고. 합리화(스킵)도 과장(허위 버그)도 둘 다 금지.

관련: [[temp-multimodel-workflow]] · [[rereview-converge-after-fix]] · [[no-fake-data-ever]] · [[overnight-live-capture]] · [[qa-docker-real-test]] · [[real-server-check-screenshot]]
