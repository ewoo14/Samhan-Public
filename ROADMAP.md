# SamhanLogis ROADMAP

(주)삼한공조시스템 자체 물류·회계·견적·주문 통합 플랫폼의 단계별 로드맵.
본 문서는 origin/main 머지 사실 기준이며, PR 진행 상황과 1:1 동기화된다.

> 갱신 기준 commit: 본 PR 머지 시점 (Phase 7 완료 PR #87 + Phase 8 완료 PR #88 / #89 / 본 PR)

---

## Phase 개요

| Phase | 기간 (목표) | 목표                                                                 | 상태       |
| ----- | ----------- | -------------------------------------------------------------------- | ---------- |
| 0     | -           | 저장소 / Gradle multi-project / 가드 정립                            | 완료       |
| 1     | 1 ~ 3 주차  | infrastructure + auth-service + eureka + api-gateway + logging       | 완료       |
| 2     | 4 ~ 8 주차  | user / product / inventory + Electron Desktop 첫 슬라이스            | 완료       |
| 3     | 9 ~ 13 주차 | slip-service (출고/입고 10단계 라이프사이클 + 모바일 전자서명)       | 완료       |
| 4     | 14 ~ 17 주차| accounting-service (한국 일반기업회계기준 65 row 시드 + 시산표)      | 완료       |
| 5     | 18 ~ 21 주차| Solapi SMS 알림 + signature-slice + sales-form polish                | 완료       |
| 6     | 22 ~ 27 주차| legacy 마이그레이션 본격 구현 (M1a / M2 / M3 / M4 / M5 + 5 client)   | 완료       |
| 7     | 28 ~ 31 주차| 호스팅 인프라 + e2e QA + 운영 가드 + UI 통합                         | 완료 (PR #87) |
| 8     | 32 주차 ~   | AWS 호환성 가드 (테스트 단계 유지) — 직접 cutover 보류              | **완료 (PR #88 / #89 / 본 PR)** |
| 9     | -           | 잔여 도메인 (partner-service / groupware / notification / dashboard) | **2차 진행 (W1 partner #91 + W2 groupware skeleton 본 PR)** |
| 10    | -           | AWS 마이그레이션 + Migration Service + 운영 안정화 (AWS cutover 본격) — dry-run plan: `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` | 대기       |

---

## Phase 0 — 저장소·가드 정립

### 산출물
- Gradle multi-project (`shared/common` + `services/*` + `clients/*`)
- BaseEntity 7 audit 컬럼 + Soft-delete 전용 (`@SQLRestriction("is_deleted = false")`) 가드
- DB 컬럼 타입 가드 (`VARCHAR(N)` 만 허용, `CHAR(N)` 금지)
- gradlew 실행 권한 가드 (`git update-index --chmod=+x gradlew`)
- 한국어 commit / PR / Issue 의무

### 완료 조건
- 모든 후속 슬라이스가 위 가드 적용 (CI assemble PASS).

---

## Phase 1 — Infrastructure + Auth + Eureka + Logging

### 산출물
- `infrastructure/docker-compose.yml` — PostgreSQL 10 DB / Redis / RabbitMQ / Elasticsearch / MinIO / Prometheus / Grafana
- `services/eureka-server` (8761), `services/api-gateway` (8080, reactive)
- `services/auth-service` (8081, JWT HS256 + internal API)
- `services/logging-service` (8082, RabbitMQ consumer + Elasticsearch)
- gateway HeaderAuthenticationFilter 패턴 정립

### 머지 PR
- #2 auth + user-service 첫 슬라이스
- #3 team/auth (auth-service 정상화)
- #5 devops post-phase2-cleanup

### 완료 조건
- gateway 8080 → 각 서비스 routing OK, JWT 검증 OK, RabbitMQ → Elasticsearch 흐름 OK.

---

## Phase 2 — User + Product + Inventory + Electron 첫 슬라이스

### 산출물
- `services/user-service` (8083, 16명 시드 + AuthClient 패턴)
- `services/product-service` (8084, jsonb 태그 + GIN 인덱스 + Internal API)
- `services/inventory-service` (8085, FIFO + 4-tier 창고 + 22 endpoint, X-Internal-Token gateway 우회)
- `clients/desktop` Electron 첫 슬라이스 (electron-vite + React + 16 컴포넌트 디자인 시스템 적용)
- `clients/web/design-system` 16 + 1 (SignaturePad/Viewer 후속) 컴포넌트 + Storybook

### 머지 PR
- #7 product BE 도메인 + API
- #9 product FE 디자인 시스템 컴포넌트
- #11 product DevOps gateway routing
- #13 product QA 테스트 + 리포트
- #15 product hotfix (currency bpchar)
- #16 inventory 첫 슬라이스 (4-tier)
- #17 slip 첫 슬라이스 (Phase 3 진입)
- #18 desktop electron skeleton

### 완료 조건
- 4-tier 창고 + FIFO + 이동전표 22 endpoint 가 IT 통과, Electron desktop 4 화면 동작.

---

## Phase 3 — Slip Service (출고/입고 10단계 + 전자서명)

### 산출물
- `services/slip-service` (8086, 10단계 라이프사이클, dispatcher/inspector 자동 서명, 라인 specification, DeliveryBatch)
- 전자서명 (Canvas + SHA-256, 인수자/기사 양측 캡처, DispatchView 인쇄 통합)

### 머지 PR
- #19 sales output-format
- #20 sales form-polish
- #21 sales polish-2 (인쇄 양식)
- #22 notification-slice-B (Solapi SMS)
- #23 signature-slice-C (Canvas + SHA-256)
- #26 signature-mobile-ux

### 완료 조건
- slip-service 30 endpoint, 모바일 전자서명 2-step (기사 → 인수자) UX 통과.

---

## Phase 4 — Accounting Service

### 산출물
- `services/accounting-service` (8087, 한국 일반기업회계기준 65 row 시드, ChartOfAccount + Journal + JournalLine + 시산표, 7 endpoint, audit-safe reverse 분개)

### 머지 PR
- #28 accounting-slice-A

### 완료 조건
- 시드 65 row + 시산표 endpoint + reverse 분개 IT 통과.

---

## Phase 5 — SMS Aligo 마이그레이션

### 산출물
- Solapi → 알리고 SMS 게이트웨이 마이그레이션 + Mock 게이트웨이

### 머지 PR
- #30 sms-aligo-migration

### 완료 조건
- 발송번호 사전등록 + Mock 활성 (test/local 프로파일) 검증.

---

## Phase 6 — Legacy Migration 본격 구현

### 산출물 (backend 5 슬라이스)
- M1a — `services/product-service` 시드 + Google Sheets cron 동기화 + by-code endpoint (Phase 7 3차 추가)
- M2 — `services/partner-auth-service` (8091, 거래처 자체 인증 7 endpoint, password_history 5 FIFO)
- M3 — `services/dc-config-service` (8089, Partner master owner + DC 노출 5겹 가드)
- M4 — `services/partner-order-service` (confirm 흐름 + outbox + 16종 bootstrap)
- M5 — `services/slip-service` `/from-*` endpoint + idempotency 3중 격리

### 산출물 (client 5종)
- `clients/web/order-app` v4 — Vite + React + legacy `partner-order/index.html` 9427 라인 임베드 (PWA 보존)
- `clients/web/estimate-app` v2 — Node.js + Express + EJS + legacy estimate 18614 라인 1:1 변환 (B2 옵션)
- `clients/desktop` v4 — Electron + electron-vite + 16 + signature 라우트
- `clients/mobile` v4 — Expo + RN WebView + order-app v4 임베드 (dev URL `http://localhost:5185/`)
- `clients/mobile-staff` v3 — Expo + RN WebView + estimate-app v2 임베드 (dev URL `http://localhost:5183/`)

### 머지 PR
- #38 M1a product-service 시드
- #50 / #53 web order-app v4 (Vite SPA + PWA)
- #51 / #54 desktop v4
- #52 mobile v4 (RN WebView)
- #58 estimate-app v2 (Node.js + Express + EJS, B2 옵션)
- #61 mobile DC notice 삭제 (UUID 노출 회피)
- #67 / #70 legacy-v2 import + revert (별 프로젝트 분리)
- #68 / #75 product-service google sheets cron + 정정
- #69 RN client 통합 (Mobile v4 + mobile-staff v3)
- #71 (close) M3 단독 → #76 통합
- #72 M2 partner-auth-service
- #73 estimate-app google sheets 직접 연동
- #74 (close) M4 단독 → #76 통합
- #76 Phase 6 backend 통합 (M2 GG fix + M3 + M4 fix + M5)
- #77 DEVOPS — Cloudflare Pages deploy workflow (order-app 활성)
- #78 QA — Playwright + Detox 셋업 + CI workflow
- #79 client mock 일괄 제거 (`USE_MOCK_FALLBACK` 폐기)
- #80 Phase 6 마무리 (회고 + DECISIONS + dev URL 검증 + estimate-app 호스팅 + Phase 7 readiness)

### 완료 조건
- backend 5 슬라이스 + client 5종 모두 origin/main 머지, 회고 보고서 + DECISIONS 등록.

### 회고
- `docs/dev-reports/phase6-retrospective.md` 참조
- 통합 PR 패턴 정착 (PR #66 / #71 / #74 / #77 / #78 / #79 close 후 통합 재구성)
- GitGuardian 패턴 정리 (placeholder + fixture 키 이름 + Testcontainers default)

---

## Phase 7 — 호스팅 인프라 + e2e QA + 운영 가드 + UI 통합 (완료)

### 산출물 (1차 — PR #81)
- `infrastructure/cafe24/test-ssh-connection.sh` — SSH dry-run script (배포 X)
- `infrastructure/render/render.yaml` + `deploy-checklist.md` — Render Blueprint (estimate-app 활성, order-app autoDeploy false)
- `qa/playwright/tests/` — 60+ cell 시나리오 (5 project × 15 spec × happy/edge)

### 산출물 (2차 — PR #82)
- QA edge — `api-5xx-fallback` / `stock-reserve-deduct-race` / `dc-snapshot-strict`
- Designer — visual regression `dark-mode-toggle.visual.spec.ts`
- FE — schema/selector 정밀화
- DevOps — CSP script-src 보안, alert rotation, Slack 비동기
- Detox 6 시나리오 (mobile-staff 3 + mobile v4 3, iOS/Android)

### 산출물 (3차 — PR #83)
- BE — `services/product-service` `GET /api/products/by-code/{code}` (사용자 노출 식별자 modelCode → productId)
- QA — tautology / race delta / immutable 정정 (3 spec)
- FE — selector 정밀 + testMatch 직교 (2 항목)
- DevOps — render.yaml mirror 헤더 6 + order-app vitest 도입
- Designer — dark-mode body[data-theme] assertion 보강

### 산출물 (4차 — PR #84)
- design-system tokens.css 의 light/dark 정식 토큰 10종 + body 바인딩 + toggleTheme + visual baseline 6 spec
- WCAG AA 대비비 4.5:1 충족 (dark text-tertiary #888 → #9a9a9a)
- FOUC 방지 — `html[data-theme="dark"], body[data-theme="dark"]` selector

### 산출물 (5차 docs — PR #85)
- README.md 신규 + ROADMAP.md 신규
- 각 client / service README 갱신
- DECISIONS.md Phase 7 항목 (D-P7-01 ~ D-P7-05)

### 산출물 (4차 잔여 — PR #86)
- 통일 alias 토큰 (폰트 family/size/weight/line-height + spacing + radius + shadow)
- Pretendard web font (jsdelivr 1차)
- mobile / mobile-staff RN graceful 폰트 hook

### 산출물 (5/6차 — 본 PR)
- DevOps self-host font (jsdelivr SPOF 회피) — `scripts/download-pretendard-fonts.sh` + `public/fonts/` + `design-system/src/styles/fonts.css`
- DevOps helmet + CSP 정식 도입 (estimate-app v2)
- DevOps desktop CSP 갱신 (font-src / connect-src / img-src 보강)
- QA visual baseline `document.fonts.ready` 가드 5 spec 일관 적용
- Phase 7 회고 보고서 + Phase 8 진입 plan + DECISIONS Phase 7 마무리 + Phase 8 진입 항목

### 머지 PR
- #81 Phase 7 1차 (env 이름 정정 + OOM 가드 + autoDeploy 비활성 + action SHA pin)
- #82 Phase 7 2차 (CSP / getStock schema / Slack 비동기 / visual selector)
- #83 Phase 7 3차 (product by-code + QA tautology fix + FE selector + DevOps render+vitest + Designer dark-mode)
- #84 Phase 7 4차 (DS 토큰 + body 바인딩 + toggleTheme + visual baseline)
- #85 Phase 7 5차 docs (README + ROADMAP 신규 + DECISIONS Phase 7)
- #86 Phase 7 4차 잔여 (통일 토큰 + Pretendard + RN graceful 폰트 hook)
- 본 PR Phase 7 5/6차 (self-host font + helmet+CSP + desktop CSP + QA fonts.ready + 회고 + Phase 8 plan)

### 완료 조건
- Phase 7 4~6차 산출물 모두 머지, 60+ cell e2e 시나리오 staging stack 검증, UI 통합 (다크모드 + Pretendard 통일) 정착, Phase 8 진입 plan 정립.
- Render production cutover 자체는 D9 답변 후 Phase 8 위임.

---

## Phase 8 — AWS 호환성 가드 (테스트 단계 유지) (완료)

목표 = AWS (EC2 + RDS) 마이그레이션 가능성을 열어두는 호환성 가드 + 운영 가드 (현재 인프라 = 카페24 + Cloudflare + Render 그대로 유지). 직접 cutover 는 Phase 10 (모든 개발 완료 후).

상세 plan:
- `docs/migration/phase8/M-PHASE-8-readiness.md` (당초 8 작업 plan, 일부 항목 Phase 10 위임)
- `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md` (12-factor / 환경변수 / standard SQL / AWS 서비스 매핑)
- `docs/migration/phase8/M-ENV-STANDARDIZATION.md` (환경변수 표준화)

### 산출물 (1차 — PR #88)
- AWS 호환성 가드 plan (12-factor 12/12 OK, RDS 호환 22 file 검증, AWS 서비스 매핑 표 17건)
- 환경변수 표준화 plan (12 service 환경변수 grep + secrets/config 분리 + AWS Secrets Manager 마이그레이션 plan)
- ROADMAP 재정의 (Phase 8 = 호환성 가드, Phase 10 = AWS cutover)
- DECISIONS D-P8-03 ~ D-P8-06 추가
- dev-report `phase8-step-1-aws-readiness.md`

### 산출물 (2차 — 본 PR)
- ServiceDiscoveryClient interface + Eureka wrapper + AWS Cloud Map placeholder (`shared:discovery-abstraction` 신규 모듈, 단위 테스트 13 case PASS)
- 환경변수 표준 적용 (`SAMHAN_INTERNAL_TOKEN` / `SAMHAN_JWT_SECRET` / `SAMHAN_<SERVICE>_SERVICE_URL`) — chained-default fallback 패턴 (legacy 호환 100%)
- 12 service `infrastructure/env-templates/<service>.env` 보유 (10 신규 + 2 갱신)
- AWS Secrets Manager rotation lambda spec (`docs/migration/phase8/M-SECRETS-ROTATION-spec.md`) — Phase 10 cutover 시점 활성
- DECISIONS D-P8-07 ~ D-P8-09 추가
- dev-report `phase8-step-2-discovery-secrets.md`

### 산출물 (3차 — 본 PR)
- AWS 마이그레이션 dry-run plan (`docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`, 14 section)
- Phase 8 회고 보고서 (`docs/dev-reports/phase8-retrospective.md`)
- Phase 9 진입 plan (`docs/migration/phase9/M-PHASE-9-readiness.md`, 4 service skeleton + 5주 roadmap)
- ROADMAP / DECISIONS Phase 8 마무리 + Phase 9 진입 항목
- DECISIONS D-P8-10 / D-P8-11 + D-P9-01 / D-P9-02 추가
- dev-report `phase8-step-3-completion-phase-9-readiness.md` + `phase8-retrospective.md`

### Phase 8 위임 (Phase 10) — Resilience4j prod / API Gateway production / monitoring alert 등은 Phase 10 dry-run 산출물 (section 5/6/11) 에 흡수 위임

### 진입 조건
- Phase 7 완료 (PR #87 머지) → 충족
- D9 답변 = X3 AWS 옵션 확정 (D-P8-03) → 충족 (Phase 10 cutover 시점)
- D6/D7/D8 = AWS 채택으로 카페24 SSH 활성 X (현재 테스트 단계만 유지)

### 보류 항목 (Phase 10 위임)
- 14 MSA production cutover (DNS + traffic 전환)
- RDS / EC2 / S3 / Route 53 리소스 생성
- Secrets Manager / Parameter Store 도입
- AWS WAF / Managed Prometheus / Managed Grafana

---

## Phase 9 — 잔여 도메인 (2차 진행)

### 예정 산출물
- `services/partner-service` (8095, 거래처 마스터 + 신용한도 + 거래내역) — 8088 (partner-order-service) 충돌 회피 — **완료 (PR #91)**
- `services/groupware-service` (8092, 결재선 + 메신저 + 일정 + UserClient) — **완료 (본 PR)**
- `services/notification-service` (8093, 푸시/이메일/SMS 통합 라우터) — W3 예정
- `services/dashboard-service` (8094, KPI / 실시간 재고 / 매출) — W4 예정

**기존 14 service 포트 매핑 (Cross-check)**:
- 8080 api-gateway / 8081 auth / 8082 logging / 8083 user / 8084 product / 8085 inventory
- 8086 slip / 8087 accounting / 8088 partner-order / 8089 dc-config / 8091 partner-auth / 8761 eureka
- 신규 추가: 8092 groupware / 8093 notification / 8094 dashboard / **8095 partner**

### 산출물 (1차 — PR #91)
- `services/partner-service` (8095) skeleton — 2 entity (Partner / PartnerCreditHistory) + 2 enum (PartnerStatus / CreditEventType) + 2 repository + 2 service + 2 controller (Internal lookup / Admin CRUD) + 4 dto + 4 config + 1 exception handler
- Flyway V1 (`partners` + `partner_credit_history`, BaseEntity 7 audit + Soft Delete + partial unique index)
- IT 2 (`PartnerInternalControllerIT` + `PartnerAdminControllerIT`) + 단위 테스트 1 (`PartnerServiceTest` 8 case)
- M5 의존성 해소 endpoint = `GET /internal/partners/{partnerCode}` (X-Internal-Token, slip-service 측 client 구현은 W5 또는 Phase 10 위임)
- ServiceDiscoveryClient 도입 (`shared:discovery-abstraction` 의존성 + `samhan.discovery.provider=eureka` default)
- 환경변수 표준 (`SAMHAN_PARTNER_DB_*` chained-default + `SAMHAN_INTERNAL_TOKEN` + `SAMHAN_PARTNER_SERVICE_URL` + `SAMHAN_DISCOVERY_PROVIDER`)
- `infrastructure/env-templates/partner-service.env` 신규
- `services/partner-service/README.md` + `docs/dev-reports/phase9-step-1-partner-service.md` 신규
- DECISIONS D-P9-03 / D-P9-04 / D-P9-05 추가
- ROADMAP / DECISIONS / M-PHASE-9-readiness 갱신

### 산출물 (2차 — 본 PR)
- `services/groupware-service` (8092) skeleton — 5 entity (ApprovalLine / ApprovalStep / Message / Schedule / ScheduleParticipant) + 3 enum (ApprovalStatus / MessageStatus / ScheduleStatus) + ApprovalStepStatus enum + 3 repository + 3 service + 2 controller (Internal lookup / Admin) + 9 dto + 5 config + 1 client (UserClient) + 1 exception handler
- Flyway V1 (`approval_lines` + `approval_steps` + `messages` + `schedules` + `schedule_participants`, BaseEntity 7 audit + Soft Delete + partial unique index 2종)
- IT 2 (`GroupwareInternalControllerIT` 4 case + `GroupwareAdminControllerIT` 6 case, UserClient @MockBean) + 단위 테스트 3 (`ApprovalLineServiceTest` 8 case + `MessageServiceTest` 4 case + `ScheduleServiceTest` 4 case = 16 case)
- ServiceDiscoveryClient **두 번째 소비자** (W1 partner-service 첫 소비자) — `shared:discovery-abstraction` 의존성 + `samhan.discovery.provider=eureka` default
- UserClient — user-service `/internal/users/{userId}` lookup, fail-open 정책 (Phase 10 시점 fail-fast 강화)
- 환경변수 표준 (`SAMHAN_GROUPWARE_DB_*` chained-default + `SAMHAN_USER_SERVICE_URL` + `SAMHAN_INTERNAL_TOKEN` + `SAMHAN_GROUPWARE_SERVICE_URL` + `SAMHAN_DISCOVERY_PROVIDER`)
- `infrastructure/env-templates/groupware-service.env` 신규
- `services/groupware-service/README.md` + `docs/dev-reports/phase9-step-2-groupware-service.md` 신규
- DECISIONS D-P9-06 / D-P9-07 / D-P9-08 추가
- ROADMAP / DECISIONS / M-PHASE-9-readiness 갱신

### 진입 조건
- Phase 8 호환성 가드 + 운영 가드 정착 (PR #88 / #89 / #90 머지 시 충족)

### 가드
- Phase 8 환경변수 표준 적용 (`SAMHAN_<SERVICE>_<KEY>` prefix, `<NAME>_SERVICE_URL` 패턴, `.env.example` 의무)
- 12-factor 준수 + standard SQL + AWS 호환성 가드 일관 적용
- 신규 service 모두 `shared:discovery-abstraction` 의존성 도입 (Phase 10 cutover 시점 활성 대비)

### plan 위치
- `docs/migration/phase9/M-PHASE-9-readiness.md` (4 service skeleton + 5주 roadmap)

---

## Phase 10 — AWS 마이그레이션 + Migration Service + 운영 안정화 (대기)

### 예정 산출물
- AWS 인프라 cutover — RDS PostgreSQL 16 + EC2/ECS Fargate + ElastiCache + AWS MQ + S3 + Route 53 + ACM
- Secrets Manager rotation lambda + Parameter Store
- `services/migration-service` (8096, ECount 일괄 데이터 이관) — Phase 9 partner-service (8095) 충돌 회피
- 장기미수 마이그레이션 일괄 처리
- 운영 안정화 (장애 복구 / 백업 / DR)
- 환경변수 통일 정정 (`INTERNAL_TOKEN` → `INTERNAL_AUTH_TOKEN`, `<NAME>_HOST` → `<NAME>_SERVICE_URL`)

### 진입 조건
- Phase 9 도메인 완료
- AWS account 발급 + IAM baseline 정의

### dry-run plan 위치
- `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` (14 section + 5주 timeline)

---

## 미결 결정 항목 (D-시리즈)

| ID  | 주제                                       | 상태       | 결정 시점          |
| --- | ------------------------------------------ | ---------- | ------------------ |
| D6  | 카페24 SSH 배포 대상 앱                    | 보류       | AWS cutover 시점에 무관 (현재 인프라 그대로) |
| D7  | 카페24 호스트 내 배포 디렉토리             | 보류       | D6 답변 후         |
| D8  | 카페24 pm2 process 명명 규약               | 보류       | D6 / D7 답변 후    |
| D9  | 14 backend MSA 운영 호스팅 옵션 (X1 ~ X4) | **확정 (X3 AWS, Phase 10 cutover)** | D-P8-03 |

---

## 머지 PR ↔ Phase 매트릭스

| PR | Phase | 설명                                              |
| -- | ----- | ------------------------------------------------- |
| #2 | 1     | auth + user-service 첫 슬라이스                   |
| #3 | 1     | team/auth                                         |
| #5 | 1     | devops post-phase2-cleanup                        |
| #7 | 2     | product BE 도메인 + API                           |
| #9 | 2     | product FE 디자인 시스템                          |
| #11| 2     | product DevOps gateway routing                    |
| #13| 2     | product QA                                        |
| #15| 2     | product hotfix (currency bpchar)                  |
| #16| 2     | inventory 첫 슬라이스                             |
| #17| 2/3   | slip 첫 슬라이스                                  |
| #18| 2     | desktop electron skeleton                         |
| #19| 3     | sales output-format                               |
| #20| 3     | sales form-polish                                 |
| #21| 3     | sales polish-2 (인쇄 양식)                        |
| #22| 3     | notification-slice-B (SMS)                        |
| #23| 3     | signature-slice-C                                 |
| #26| 3     | signature-mobile-ux                               |
| #28| 4     | accounting-slice-A                                |
| #30| 5     | sms-aligo-migration                               |
| #34| 2     | DS extension                                      |
| #36| 2     | CI frontend jobs                                  |
| #38| 6     | M1a product-service 시드                          |
| #50| 6     | order-app v4 (Vite SPA + PWA)                     |
| #51| 6     | desktop v4                                        |
| #52| 6     | mobile v4 (RN WebView)                            |
| #53| 6     | order-app v4 정정                                 |
| #54| 6     | desktop v4 정정                                   |
| #58| 6     | estimate-app v2 (Express + EJS)                   |
| #61| 6     | mobile DC notice 삭제                             |
| #67| 6     | legacy-v2 import (revert 됨)                     |
| #68| 6     | product google sheets cron 1차                   |
| #69| 6     | RN client 통합                                    |
| #70| 6     | #67 revert                                        |
| #72| 6     | M2 partner-auth-service                           |
| #73| 6     | estimate-app google sheets 직접                  |
| #75| 6     | #68 정정                                          |
| #76| 6     | Phase 6 backend 통합 (M2/M3/M4/M5)                |
| #77| 6     | DEVOPS Cloudflare Pages workflow                  |
| #78| 6     | QA Playwright + Detox 셋업                        |
| #79| 6     | client mock 일괄 제거                             |
| #80| 6     | Phase 6 마무리 + Phase 7 readiness                |
| #81| 7     | Phase 7 1차 (카페24 SSH + Render Blueprint + QA)  |
| #82| 7     | Phase 7 2차 (CSP + visual + Slack 비동기)         |
| #83| 7     | Phase 7 3차 (by-code + tautology + render mirror) |
| #84| 7     | Phase 7 4차 (DS 토큰 + body 바인딩 + visual baseline) |
| #85| 7     | Phase 7 5차 docs (README + ROADMAP + DECISIONS Phase 7) |
| #86| 7     | Phase 7 4차 잔여 (통일 토큰 + Pretendard + RN graceful) |
| #87| 7     | Phase 7 마무리 (self-host font + helmet+CSP + desktop CSP + QA fonts.ready + 회고 + Phase 8 plan) |
| #88| 8     | Phase 8 1차 (AWS 호환성 가드 + 12-factor 검증 + 환경변수 표준 + ROADMAP/DECISIONS 갱신) |
| #89| 8     | Phase 8 2차 (ServiceDiscoveryClient interface + Eureka wrapper + AWS placeholder + 환경변수 통일 chained-default + Secrets Manager spec) |
| #90| 8     | Phase 8 3차 (AWS 마이그레이션 dry-run + Phase 8 회고 + Phase 9 진입 plan + ROADMAP/DECISIONS 갱신) |
| #91 | 9 | Phase 9 1차 W1 (partner-service skeleton — port 8095, M5 partnerId lookup endpoint + 2 entity + Admin CRUD + ServiceDiscoveryClient 도입) |
| 본 PR | 9 | Phase 9 2차 W2 (groupware-service skeleton — port 8092, 결재선 chain + 메신저 + 일정 + UserClient + ServiceDiscoveryClient 두 번째 소비자) |

---

## 디렉토리 ↔ Phase 매트릭스

| 디렉토리                             | Phase 도입 | 현재 상태         |
| ------------------------------------ | ---------- | ----------------- |
| `services/eureka-server`             | 1          | 운영              |
| `services/api-gateway`               | 1          | 운영              |
| `services/auth-service`              | 1          | 운영              |
| `services/logging-service`           | 1          | 운영              |
| `services/user-service`              | 2          | 운영              |
| `services/product-service`           | 2 / 6      | by-code endpoint 추가 (Phase 7 3차) |
| `services/inventory-service`         | 2          | 운영              |
| `services/slip-service`              | 2 / 3 / 6  | M5 `/from-*` endpoint 추가 |
| `services/accounting-service`        | 4          | 운영              |
| `services/partner-auth-service`      | 6          | M2 운영           |
| `services/dc-config-service`         | 6          | M3 운영           |
| `services/partner-order-service`     | 6          | M4 운영           |
| `services/partner-service`           | 9          | W1 skeleton (8095, 거래처 마스터 + M5 partnerCode lookup endpoint, ServiceDiscoveryClient 도입) |
| `services/groupware-service`         | 9          | W2 skeleton (8092, 결재선 chain + 메신저 + 일정 + UserClient, ServiceDiscoveryClient 두 번째 소비자) |
| `clients/desktop`                    | 2 / 6      | v4                |
| `clients/web/design-system`          | 2          | 21 컴포넌트       |
| `clients/web/order-app`              | 6          | v4 (Vite + 임베드)|
| `clients/web/estimate-app`           | 6          | v2 (Express + EJS)|
| `clients/mobile`                     | 6          | v4 (WebView)      |
| `clients/mobile-staff`               | 6          | v3 (WebView)      |
| `qa/playwright`                      | 7          | 60+ cell          |
| `qa/detox`                           | 7          | 6 시나리오        |
| `infrastructure/cafe24`              | 7          | SSH 테스트만      |
| `infrastructure/render`              | 7          | Blueprint 정의 (1차 estimate-app, autoDeploy false) |
| `shared/discovery-abstraction`       | 8          | ServiceDiscoveryClient wrapper (Eureka default + AWS Cloud Map placeholder), Phase 10 활성 대기 |
| `infrastructure/env-templates`       | 8          | 12/12 service env-template 보유 (10 신규 + 2 갱신, chained-default fallback) |

---

## 참조 문서

- 누적 결정: `migration/decisions/DECISIONS.md`
- Phase 6 회고: `docs/dev-reports/phase6-retrospective.md`
- Phase 7 진입 평가: `docs/migration/phase7/M-PHASE-7-readiness.md`
- Phase 7 회고: `docs/dev-reports/phase7-retrospective.md`
- estimate-app 호스팅 결정: `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md`
- Phase 8 readiness plan: `docs/migration/phase8/M-PHASE-8-readiness.md`
- AWS 호환성 가드: `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`
- 환경변수 표준: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- Phase 8 1차 dev report: `docs/dev-reports/phase8-step-1-aws-readiness.md`
- Phase 8 2차 dev report: `docs/dev-reports/phase8-step-2-discovery-secrets.md`
- Phase 8 3차 dev report: `docs/dev-reports/phase8-step-3-completion-phase-9-readiness.md`
- Phase 8 회고: `docs/dev-reports/phase8-retrospective.md`
- Phase 9 진입 plan: `docs/migration/phase9/M-PHASE-9-readiness.md`
- Phase 9 1차 dev report: `docs/dev-reports/phase9-step-1-partner-service.md`
- Phase 9 2차 dev report: `docs/dev-reports/phase9-step-2-groupware-service.md`
- Phase 10 dry-run plan: `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`
- 본 문서 갱신 보고: `docs/dev-reports/docs-roadmap-update.md`
