---
name: feedback_live_qa_penetrates_it_masking
description: 라이브/통합 QA가 IT·단위테스트가 마스킹하는 프로드 결함을 관통한다. ①IT 클래스 @Transactional이 OSIV-off lazy-init 500을 가림(NOT_SUPPORTED 테스트/라이브가 잡음) ②BE 검증은 crafted 요청 아닌 실 FE payload로 검증(계약 render→submit→assert) ③saveAndFlush mock이 DB NOT NULL 위반 false-green. 2026-07-19 #823.
metadata:
  type: feedback
---

**사건(2026-07-19 #823 매출·매입 배분 거래처 검증)**: R1(OPUS 4-agent)+CI 34/34 green이었으나, PM 라이브QA와 R2(CODEX SOL) 2-모델이 IT/단위테스트가 마스킹한 프로드 결함 3종을 관통 포착:

1. **IT `@Transactional`이 OSIV-off lazy-init 프로드 500을 마스킹** — `SlipInternalController.getSlipLine`/`getSlipLines`가 slip-service OSIV off(`open-in-view: false`)에서 `line.getSlip()`/`slip.getLines()` lazy 접근 → `LazyInitializationException` 500. **accounting 배분이 getSlipLine 사용 → 프로드 배분 전면 500**(dev CONFIRMED 원천 부재로 잠복). `SlipInternalControllerIT`의 **클래스 `@Transactional`이 세션을 열어둬 이 프로드 경로를 가림**(그래서 그간 green). **라이브 QA(실 :8086)만이 실제 500을 관통**. fix=fetch-join(`JOIN FETCH`) + **`@Transactional(propagation=NOT_SUPPORTED)` 테스트**(테스트 tx 차단→프로드 OSIV-off 재현).

2. **BE 검증을 crafted 요청으로만 검증→실 FE payload 미반영 false-green** — BE 배분 거래처 검증(헤더 partnerId==원천 partnerId)은 BE-correct였으나, **실 FE 폼이 헤더 partnerId에 `fallbackUuid('sales-partner')` 가짜 placeholder 전송**(원천 partnerId 폐기) → 내 검증이 **실 UI 배분을 항상 422로 전면 차단**. 내 라이브QA(crafted 매칭 요청)와 억지-정렬 BE IT(스냅샷==헤더 동일 UUID)는 실 FE를 대표 못 해 이를 놓침. → **FE↔BE payload 계약 테스트**(폼 render→원천 배분→submit body 캡처→`header.partnerId==source.partnerId` 단언)가 필수 게이트. (더 깊은 root: 매출전표 partner가 placeholder였음.)

3. **`saveAndFlush` mock이 DB NOT NULL 위반 false-green** — code/name 검증 완화가 DB `partner_code/name NOT NULL`(V18/V19)과 충돌해 저장 시 500이나, 단위테스트가 `saveAndFlush`를 mock해 **영속 전 객체만 검사→제약 위반 미검출**. → **실 DB IT**(Testcontainers·mock 아님)가 clean 422 vs DB-예외 500을 실제 구분.

**Why**: 셋 다 **단위/IT green + CI green이었으나 실 프로드/실 FE에서 깨짐**. IT의 tx/mock 편의가 프로드 런타임(OSIV·DB제약·실 payload)을 가린다. [[feedback_verify_playwright_gate_before_adversarial]](CI green≠정확)·[[feedback_react_query_freshness_route_param_reset]](presence=false-green)의 BE/통합판.

**How to apply**:
1. **lazy 연관 접근 컨트롤러/서비스** = OSIV 설정 확인. IT `@Transactional`은 프로드 tx 경계를 마스킹하므로, **비-tx 경로(NOT_SUPPORTED 테스트 또는 라이브)**로 lazy-init 재현 검증. fetch-join/`@Transactional(readOnly)` 명시.
2. **BE 검증/계약** = crafted 요청 아닌 **실 FE가 실제 보내는 payload**로 검증(폼 render→submit body 캡처 계약 테스트). "BE-correct"가 "실 UI 작동"을 보장 안 함.
3. **DB 제약(NOT NULL/unique/CHECK) 관여 경로** = `saveAndFlush` mock 금지·**실 DB IT**(Testcontainers)로 제약 위반(500) vs clean 4xx 구분.
4. **라이브 QA는 BE 슬라이스도 필수** — 실 스택 크로스서비스 라운드트립이 IT가 못 잡는 통합/프로드 결함을 관통.

→ [[feedback_qa_docker_real_test]]·[[feedback_restclient_contract_test_false_green]]·[[feedback_it_mockbean_external_clients]]·[[feedback_self_invocation_transactional_bypass]].
