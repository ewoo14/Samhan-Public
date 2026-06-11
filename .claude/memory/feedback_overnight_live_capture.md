---
name: 야간 위임 = 라이브 Docker 실QA 캡처 미루지 말 것
description: 개발책임자가 야간 위임 시 Docker 실QA 라이브 캡처를 원함. 서비스 재빌드 필요해도 진행, "스택 안전" 명목 deferral 금지
metadata:
  type: feedback
---
개발책임자 강한 지적(2026-06-12, #463 야간): "밤새 그거 진행하라니깐 뭐했어 빨리 진행해" — 라이브 Docker 실QA 캡처를 야간에 했어야 하는데 PM 이 **"스택 안전" 명목으로 아침으로 deferral** 한 것이 잘못.

**Why**: 개발책임자는 [[qa-docker-real-test]]·[[no-fake-data-ever]]·[[real-server-check-screenshot]] 대로 **라이브 실서버 캡처를 머지 게이트로 요구**. 야간 위임("나 잘테니까 모두 적용")은 **리뷰/CI/mock 뿐 아니라 라이브 실QA 캡처까지 포함**. 실행 중 컨테이너가 stale jar 라 신규 엔드포인트가 없으면 **서비스 재빌드(`docker compose up -d --build <svc>`)까지 해서 라이브 캡처를 만드는 것**이 위임 범위. CI Testcontainers IT 가 있어도 그것으로 라이브 캡처를 대체하지 말 것.

**How to apply**:
- 신규 BE 엔드포인트/마이그 동반 기능은 머지 전(또는 야간 위임 시) **slip-service 등 해당 서비스 재빌드 → 실 API 동작 → 실 화면 캡처**. jar 빌드(`gradlew :services:X:bootJar`) → `docker compose -f infrastructure/docker-compose.yml -f ...local-all.yml -f ...slip-port-override.yml up -d --build X` → health(:808x/actuator/health UP) + Flyway 적용 로그 확인 → Playwright 라이브 캡처(override config: testIgnore 없음·webServer 없음·AUDIT_BASE_URL=:5178·API_BASE=:8080).
- 라이브 캡처 스펙은 `*-real-qa.spec.ts` 접미(mock CI 게이트 testIgnore 대상)로. 실 API 등록 + UI 렌더 단언 + 스크린샷.
- "스택 안전(개발책임자 자는 중)" 으로 deferral 금지 — 재빌드는 additive 마이그면 저위험이고, 개발책임자는 캡처를 원함. 실패 시에만 "캡처 불가+사유" 정직 보고.
- 관련: [[qa-docker-real-test]] [[real-server-check-screenshot]] [[no-fake-data-ever]] [[local-stack-qa-gotchas]] [[standalone-boot-real-qa]].
