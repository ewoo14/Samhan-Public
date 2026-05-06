# docs + ROADMAP 통합 갱신 — dev report

PR #83 (Phase 7 3차) 머지 후 origin/main 기준 모든 문서를 현재 시점에 맞게 갱신하고,
저장소 루트에 `ROADMAP.md` 를 신규 작성한다.

## 1. 갱신 대상 문서 매트릭스

| 분류 | 파일 | 동작 | 비고 |
|---|---|---|---|
| 루트 | `README.md` | 정정 | Phase 진행 상태 + 14 backend MSA + 5 client + Phase 6/7 PR 목록 + 가드 정렬 |
| 루트 | `ROADMAP.md` | **신규** | Phase 0 ~ 10 + PR 매트릭스 + 디렉토리 매트릭스 + 미결 결정 |
| client | `clients/web/order-app/README.md` | 정정 | Phase 6/7 backend 연계 + QA 시나리오 매핑 |
| client | `clients/web/estimate-app/README.md` | 정정 | Render 호스팅 결정 + Phase 7 QA 매핑 |
| client | `clients/desktop/README.md` | 정정 | v4 후속 + by-code endpoint 도입 + Phase 7 electron-desktop project |
| client | `clients/mobile/README.md` | 정정 | 가드 위반 표현 제거 (사용자 명시 / 회고 등) + Phase 7 QA 매핑 |
| client | `clients/mobile-staff/README.md` | 정정 | 가드 위반 표현 제거 + Phase 7 Detox 매핑 |
| service | `services/product-service/README.md` | 정정 | Google Sheets sync + by-code endpoint (Phase 7 3차) + 가드 위반 표현 제거 |
| service | `services/partner-auth-service/README.md` | 정정 | Phase 7 QA 매핑 추가 |
| service | `services/dc-config-service/README.md` | **신규** | M3 4 entity + 5겹 DC 노출 가드 + endpoint 매트릭스 |
| service | `services/partner-order-service/README.md` | **신규** | M4 8 entity + confirm 흐름 + 16종 bootstrap + Outbox |
| service | `services/slip-service/README.md` | **신규** | 기존 라이프사이클 + M5 통합 발행 endpoint + idempotency 3중 격리 |
| 결정 | `migration/decisions/DECISIONS.md` | 보강 | Phase 7 결정 5건 추가 (D-P7-01 ~ D-P7-05) |
| dev-report | `docs/dev-reports/docs-roadmap-update.md` | 신규 | 본 파일 |

비변경: `infrastructure/render/README.md`, `infrastructure/cafe24/README.md`, `qa/playwright/README.md`, `qa/detox/README.md` —
PR #80 / #81 / #82 / #83 시점에 이미 현재 사실과 정합 (재정정 불필요).

## 2. ROADMAP.md 구조 요약

| 섹션 | 내용 |
|---|---|
| Phase 개요 | Phase 0 ~ 10 단계 / 기간 / 상태 한눈 표 |
| Phase 0 ~ 10 | 단계별 산출물 / 머지 PR / 완료 조건. 진행 중 단계는 다음 단계 명시 |
| 미결 결정 | D6 ~ D9 (카페24 D6/D7/D8 + 14 backend 호스팅 D9) 표 |
| 머지 PR ↔ Phase 매트릭스 | #2 ~ #83 전체 매핑 |
| 디렉토리 ↔ Phase 매트릭스 | services / clients / qa / infrastructure 의 도입 Phase + 현재 상태 |
| 참조 문서 | DECISIONS / 회고 / readiness / dev-report 위치 |

## 3. 주요 변경 사실

- **루트 README**: 4-team 에이전트 / 33주 plan 같은 outdated 표현 제거. 14 backend MSA + 5 client 현재 사실로 정렬. 빠른 시작 명령 갱신 (모든 service `:bootRun` 포함).
- **ROADMAP.md 신규**: 단일 문서로 Phase 진행 추적 가능. Phase 7 진행 중 / Phase 8 ~ 10 대기 명시. PR #2 ~ #83 전체가 어느 Phase 에 속하는지 1:1 매핑.
- **service README 3건 신규**: dc-config / partner-order / slip-service 의 README 가 부재했음. domain / endpoint / Phase 7 QA 연계 매핑 포함.
- **client README 가드 정정**: mobile / mobile-staff README 의 "사용자 명시" / "회고" / "PR #65 close" 같은 표현은 가드 위반 → 변경 사실 + 기술적 이유만 남김.
- **DECISIONS Phase 7**: D-P7-01 통합 PR 가드 / D-P7-02 legacy-v2 폐기 / D-P7-03 카페24 보류 / D-P7-04 Render 채택 / D-P7-05 backend 호스팅 D9 위임.

## 4. 후속 작업

- Phase 7 4차 ~ 6차 진행 시 ROADMAP.md `Phase 7` 섹션 산출물 / 머지 PR 추가
- Phase 8 진입 시 ROADMAP.md `Phase 8` 섹션 본격 작성 (호스팅 옵션 채택 후)
- D9 답변 후 estimate-app `SAMHAN_API_BASE_URL` 등록 + Render production cutover 시점에 `infrastructure/render/README.md` 갱신
- Phase 9 / 10 의 잔여 도메인 (partner / groupware / notification / dashboard / migration) README 는 해당 슬라이스 도입 시 작성

## 5. 검증

- ROADMAP.md / README.md / DECISIONS.md / service README 3건 / client README 5건 markdown 렌더 테스트 (GitHub web preview)
- gradle assemble — 본 PR 은 docs only, 빌드 영향 X (전체 .md 변경)
- CI — pull request 시 lint + assemble PASS 확인
