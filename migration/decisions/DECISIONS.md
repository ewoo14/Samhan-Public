# SamhanLogis Migration Decisions

본 문서는 legacy → SamhanLogis MSA 마이그레이션 과정에서 내려진 누적 결정 (decision log) 을 시간순으로 기록한다. 각 항목은 결정의 사실, 근거, 영향 범위만 기재한다.

---

## Phase 6 마무리 결정 (2026-05-05)

### D-P6-01. Phase 6 backend 4 슬라이스 + product-service google sheets sync 완료

- M2 partner-auth-service (PR #72 + GG fix `97ca8da` 합류)
- M3 dc-config-service (PR #71 close → 통합 PR #76 합류)
- M4 partner-order-service (PR #74 close + CI fail fix → 통합 PR #76 합류)
- M5 slip-service `/from-*` endpoint (통합 PR #76 첫 발행)
- product-service google sheets sync (PR #68 + #75 정정)

영향: backend 슬라이스 4건 + product-service 동기화가 origin/main 에 반영. 14 backend MSA 중 5개 슬라이스가 실제 코드 단계 진입.

### D-P6-02. client mock fallback 일괄 제거 (PR #79)

- `USE_MOCK_FALLBACK` 환경변수 폐기 (estimate-app v2)
- `samhanApi.ts` / `code.js` / `slip-bridge.js` 의 silent fallback 분기 제거
- 영구 보존 항목: dev-only `desktop/src/renderer/api/mock.ts` (`VITE_MOCK_MODE=1` 빌드 시점 분기), audit logger silent `.catch`, jest 테스트 stub

근거: silent fallback 은 endpoint 회귀 시점을 가려 잘못된 데이터로 흐름이 진행되는 위험이 있음. A 옵션 (완전 폐기) 채택.

영향: client → 실 backend 호출 전환. backend 미가동 환경에서는 RPC 5xx/네트워크 오류로 명확하게 실패.

### D-P6-03. PR 발행 정책 — 통합 발행 채택

- 단편 PR 발행 회피 (PR #66 close 후속 결정)
- 단독 발행 회피 (PR #71 / #74 / #77 / #78 / #79 의 단독 발행 후 통합 재구성 발생)
- 통합 PR 의 historic commit 도 GitGuardian 검사 대상 → `git merge --squash` x N (sub 별 단일 commit) 권장 (PR #76 1차 발행 후속 결정)

영향: 후속 슬라이스부터 단일 통합 PR 으로 발행. 단독 발행 시 close + 통합 재구성.

### D-P6-04. 카페24 SSH 배포 보류 (Phase 6 범위에서 제외)

- `.github/workflows/deploy-cafe24-ssh.yml.template` 활성 X (PR #77)
- D6/D7/D8 (배포 대상 / 디렉토리 / pm2 명명) 답변 Phase 7 위임

영향: Phase 6 동안 카페24 환경은 테스트만 진행, 실 배포는 Phase 7 호스팅 결정 후 활성.

### D-P6-05. estimate-app v2 호스팅 결정 Phase 7 위임

- estimate-app v2 (Express SSR + EJS) 는 Cloudflare Pages 정적 호스팅 기술적 불가
- 3안 비교 (A Cloudflare Workers / B Render.com / C 카페24 SSH) → `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md` 에 정리
- Phase 7 진입 전 호스팅 옵션 1건 확정 필요

영향: Phase 6 종료 시점 estimate-app v2 production URL (`estimate.samhan-air.com`) 미가동.

### D-P6-06. legacy-v2 (이카운트/노션 살린 버전) 분리

- PR #67 머지 후 PR #70 revert
- legacy-v2 변종은 SamhanLogis 범위에서 제외, 별도 프로젝트로 이전

영향: SamhanLogis 의 client 5개 (order-app v4 / Desktop v4 / Mobile v4 / mobile-staff v3 / estimate-app v2) 는 모두 SamhanLogis 자체 stack (Vite + React 또는 Express + EJS) 으로 통일.

---

## Phase 7 진행 결정 (2026-05-06)

### D-P7-01. PR 발행 가드 — 통합 PR 의무

- TM 종합 dev report + reviewer 5 토론 (BE / FE / Designer / QA / DevOps) + TM/PM 승인 의무
- 단편 PR 발행 회피 (Phase 6 PR #66 / #71 / #74 / #77 / #78 / #79 close 회고 후속)
- 단독 PR 발행 회피 — TM 자체 1 통합 PR 으로 발행
- 통합 PR 의 historic commit 도 GitGuardian 검사 대상 → `git merge --squash` x N (sub 별 단일 commit) 권장

영향: Phase 7 1차 ~ 3차 모두 단일 통합 PR 으로 발행 (PR #81 / #82 / #83). 본 docs 통합 PR 도 동일 패턴.

### D-P7-02. legacy-v2 폐기 확정

- D-P6-06 (legacy-v2 분리) 의 보강
- legacy-v2 (이카운트 / 노션 살린 변종) 는 SamhanLogis 범위에서 영구 제외
- 별 프로젝트로 이전, SamhanLogis 저장소 / docs 에서 후속 언급 X

영향: legacy-v2 관련 코드 / 문서 / branch 가 SamhanLogis 에 잔존하지 않는다.

### D-P7-03. 카페24 SSH 배포 보류 — 테스트만 진행

- `infrastructure/cafe24/test-ssh-connection.sh` (SSH 인증 + 자원 + 도구 dry-run) 만 사용
- `.github/workflows/deploy-cafe24-ssh.yml.template` 의 `.template` suffix 보존 (workflow 비활성)
- D6 (배포 대상) / D7 (디렉토리) / D8 (pm2 명명) 답변 + 활성화 결정 후 활성

영향: Phase 7 동안 카페24 환경은 SSH 연결 검증만 수행, 실 배포는 D6/D7/D8 답변 후속에 위임.

### D-P7-04. estimate-app v2 호스팅 = Render Starter

- `docs/migration/phase7/M-ESTIMATE-APP-hosting-decision.md` 의 3안 비교 (A Cloudflare Workers / B Render / C 카페24 SSH) → **B 옵션 채택**
- Render Starter $7/mo (always-on, 512MB RAM)
- Blueprint: `infrastructure/render/render.yaml` (estimate-app 활성, order-app autoDeploy false 미러)
- 절차: `infrastructure/render/deploy-checklist.md`
- DNS: 카페24 또는 Cloudflare DNS → CNAME `quote.samhan-air.com` → `samhan-estimate-app.onrender.com`

영향: estimate-app v2 production cutover 가 Render dashboard "Manual Deploy" 또는 GitHub Actions workflow_dispatch 로 진행 가능. 1차 estimate-app 만 활성, order-app 은 Cloudflare Pages 가 owner.

### D-P7-05. 14 backend MSA Phase 8 별도 호스팅 결정 위임

- `docs/migration/phase7/M-PHASE-7-readiness.md` § 4 의 X1 ~ X4 옵션 (D9 미결)
- Phase 7 동안 backend 는 staging stack (로컬 Docker Compose) 만 가동
- production cutover 는 Phase 8 진입 + D9 답변 후 진행
- Render 의 `SAMHAN_API_BASE_URL` 실 값은 D9 답변 후 확정

영향: Phase 7 6차 (Render production cutover) 시점에는 estimate-app 이 정적 + Google Sheets 직접 연동만 동작. backend 호출 endpoint 는 D9 답변 후 추가.

---

## Phase 7 완료 + Phase 8 진입 결정 (2026-05-05)

### D-P7-06. Phase 7 6차 production cutover 보류

- estimate-app v2 의 Render production cutover 는 D9 (14 backend MSA 호스팅 옵션) 답변에 의존
- D9 답변 X 시 estimate-app 만 단독 cutover 시 backend 호출 endpoint 가 미가동 → 정적 + Google Sheets 직접 연동만 동작
- Phase 8 진입 후 D9 답변과 함께 일관 cutover

영향: Phase 7 6차 production cutover = Phase 8 4주차 (DNS cutover) 작업으로 위임.

### D-P7-07. 후속 PR 4건 본 PR 통합 발행

- DevOps 후속 3건 (self-host font + helmet+CSP + desktop CSP) + QA 후속 1건 (visual baseline `document.fonts.ready` 가드)
- 단편 PR 4건 발행 회피 (D-P7-01 가드 일관 적용)
- 본 PR = Phase 7 회고 + Phase 8 진입 plan + DECISIONS Phase 7 마무리 + Phase 8 진입 항목까지 통합

영향: Phase 7 마무리 작업 = 1 통합 PR 으로 일관. Phase 8 진입 plan 도 동일 PR 에 첨부.

### D-P8-01. Phase 8 진입 조건

- 필수 — D9 답변 (14 backend MSA 호스팅 옵션 X1 ~ X4 중 1택)
- (X1 옵션 시) 추가 — D6/D7/D8 답변 (카페24 SSH 활성)
- 선택 — 카페24 plan 업그레이드 X 가정 시 X2 (Hetzner) / X3 (AWS) / X4 (하이브리드) 중 1택으로 진행 가능

영향: D9 답변만으로 Phase 8 진입 가능. D6/D7/D8 은 X1 옵션 채택 시에 한해 필수.

### D-P8-02. Phase 8 plan 위치

- `docs/migration/phase8/M-PHASE-8-readiness.md`
- W1 ~ W5 5주 plan + 8 작업 분해 + 호스팅 옵션 비교 + DNS cutover 8 서브도메인 매핑

영향: Phase 8 작업 시작 시 본 plan 을 reference 로 사용. 8 작업 모두 Phase 8 슬라이스의 input.

---

## Phase 8 진입 결정 (2026-05-05)

### D-P8-03. 호스팅 = AWS (EC2 + RDS) 향후 예정 (Phase 10 cutover 시점)

- 14 backend MSA 운영 호스팅 = AWS (EC2 + RDS) 채택
- D9 미결 항목 (X1 카페24 / X2 Hetzner / X3 AWS / X4 하이브리드) 중 X3 AWS 옵션 확정
- cutover 시점 = Phase 10 (모든 개발 완료 후)
- 현재 시점 = AWS 리소스 생성 X, account 발급 X, terraform 코드 생성 X

영향: Phase 8 ~ 9 동안 AWS 호환성 유지가 의무. Phase 10 진입 시 RDS / EC2 / S3 / Route 53 일괄 cutover 진행.

### D-P8-04. 현재 = 테스트 단계, 카페24 + Cloudflare + Render 그대로 유지

- 모든 개발 진행 동안 (Phase 8 ~ 9) 현재 인프라 그대로
- 카페24 SSH (D6/D7/D8 답변 후 활성), Cloudflare Pages (order-app), Render (estimate-app) 보존
- production cutover X = AWS 마이그레이션 시점에 일괄 진행

영향: 현재 단계의 호스팅 결정 (Phase 7 D-P7-04 Render 채택 등) 그대로 유지. AWS 마이그레이션은 코드 변경 X, infra 변경만으로 진행.

### D-P8-05. AWS 마이그레이션 가능성을 열어두는 호환성 가드 검증 의무

- 12-factor app 준수 (모든 service)
- 환경변수 추상화 (`${ENV:default}` 패턴 의무)
- PostgreSQL standard SQL (RDS PostgreSQL 16 호환, RDS 미지원 extension 부재)
- AWS 서비스 매핑 표 보유 (`docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md`)
- vendor lock-in 회피 (Cloudflare Workers / Render-specific feature 의존 X, S3 SDK 사용 시 endpoint override 패턴)

영향: 모든 후속 슬라이스 (Phase 8 2차 ~ Phase 9) 에서 본 가드 일관 적용. 위반 시 PR 단계 reviewer 가드.

### D-P8-06. Phase 8 1차 = AWS 호환성 가드 plan + 검증 (본 PR)

- 산출물 5건 = AWS 호환성 가드 plan + 환경변수 표준 + ROADMAP 갱신 + DECISIONS 갱신 + dev-report
- 코드 변경 0 file (docs only)
- 12-factor 검증 결과 = 12/12 OK (IX 만 Phase 10 개선 항목 1건 = `server.shutdown=graceful`)
- standard SQL 검증 결과 = 22 file Flyway migration 모두 RDS 호환
- 환경변수 추상화 검증 결과 = 12 service 모두 OK, 통일 권장 3건 (`INTERNAL_TOKEN` / `<NAME>_HOST` / `.env.example`) 은 Phase 9 위임

영향: Phase 8 1차 머지 후 2차 (Eureka cluster prod) 진입 가능. AWS 마이그레이션 dry-run plan 은 Phase 8 3차 또는 Phase 10 진입 시점에 작성.

---

## Phase 8 2차 결정 (2026-05-06)

### D-P8-07. ServiceDiscoveryClient interface 도입 (Eureka default + AWS Cloud Map placeholder)

- 신규 모듈 `shared:discovery-abstraction` (Java library, Spring Boot 미적용)
- 인터페이스 = `ServiceDiscoveryClient` (4 operation: register / deregister / lookup / healthcheck)
- impl = `EurekaServiceDiscoveryClient` (현재 운영 Eureka, `EurekaClient` wrapper) + `AwsCloudMapServiceDiscoveryClient` (placeholder, `UnsupportedOperationException("Phase 10 cutover 시점 구현")`)
- impl 토글 = `@ConditionalOnProperty(name = "samhan.discovery.provider", havingValue = "eureka", matchIfMissing = true)`
- Eureka bean = `@ConditionalOnClass(EurekaClient)` 로 소비자가 명시 의존성 추가 시점에만 활성
- 14 service 의존성 추가는 Phase 10 cutover 시점 위임 (본 PR = wrapper 신규 + 단위 테스트만)

근거: Phase 8 1차 doc 의 "Eureka 자체 EC2 운영 권장 → wrapper 불필요" 결정과 별개로,
호환성 가드 차원에서 vendor 추상화 layer 를 미리 보유. 14 service 의존성 추가 시점은
Phase 10 cutover 결정에 따름.

영향: 신규 모듈 1개 (`shared:discovery-abstraction`), settings.gradle / build.gradle
leafProjects 에 등록. 기존 14 service 의 build.gradle / yml / Java 코드 모두 변경 X
(Phase 10 cutover 시점에 service 별 의존성 추가 + provider 토글로 활성).

### D-P8-08. 환경변수 표준 `SAMHAN_<SERVICE>_<KEY>` 적용 (chained-default fallback 패턴 = legacy 호환 100%)

- Phase 8 1차 doc 검출 불일치 3건 처리 — `INTERNAL_AUTH_TOKEN` (6) vs `INTERNAL_TOKEN` (1) / `<NAME>_HOST` vs `<NAME>_URL` / `.env.example` 부재
- 표준 = `SAMHAN_INTERNAL_TOKEN` / `SAMHAN_JWT_SECRET` / `SAMHAN_<SERVICE>_SERVICE_URL` (full URL)
- yml 패턴 = chained-default `${SAMHAN_NEW:${LEGACY:default}}` — 신규 표준 우선, legacy fallback 보존
- 영향 yml = 10 file (10/12 service. eureka-server / logging-service 는 적용 대상 변수 부재)
- Java 코드 변경 X — yml level 표준화만, `@ConfigurationProperties` 바인딩 / `InternalTokenGuard` / `InternalAuthProperties` 모두 그대로
- `infrastructure/env-templates/<service>.env` 12/12 service 보유 의무 적용 (10 신규 + 2 갱신)

근거: Phase 8 1차 doc 의 "Phase 9 또는 별도 슬라이스 위임" 표지를 본 슬라이스에서 처리.
chained-default 패턴 = 기존 배포 환경 (`INTERNAL_AUTH_TOKEN` 등 설정된 .env) 호환 100%
보존하면서 신규 표준 도입.

영향: Phase 9 신규 service (partner / groupware / notification / dashboard) 부터 본 표준
의무 적용. Phase 10 cutover 시점에 `spring.config.import: aws-secretsmanager:samhan/<env>/...`
추가로 Secrets Manager 자동 fetch 활성. legacy fallback 폐기 = Phase 11 시점.

### D-P8-09. Secrets Manager rotation = Phase 10 cutover 시점 활성 (본 PR = spec only)

- 신규 doc `docs/migration/phase8/M-SECRETS-ROTATION-spec.md`
- 대상 secrets 7건 (`SAMHAN_DB_PASSWORD` 30일 / `SAMHAN_INTERNAL_TOKEN` 90일 / `SAMHAN_JWT_SECRET` 90일 / `SAMHAN_GOOGLE_SERVICE_ACCOUNT_KEY` manual / `ALIGO_API_KEY` manual / `SAMHAN_SLACK_WEBHOOK_URL` manual / `RABBIT_PASSWORD` 90일)
- lambda 구조 = Python 3.12, IAM `secretsmanager:RotateSecret` + `rds:ModifyDBInstance` + `mq:UpdateUser`
- 4 단계 (createSecret / setSecret / testSecret / finishSecret) Python sample 코드 포함
- service 측 fetch 패턴 = `spring-cloud-aws-starter-secrets-manager` (Phase 10 적용)
- monitoring + alert = CloudWatch alarm (`RotationFailed` / `Errors` / `Throttles` / `Duration`) + Slack webhook
- Phase 10 cutover 6 단계 절차 명시

근거: Phase 8 1차 doc 의 "AWS Secrets Manager 마이그레이션 가능성 (Phase 10)" 표지를
본 슬라이스에서 spec 으로 정착. 실 lambda 코드 + AWS 리소스 생성은 Phase 10 위임.

영향: Phase 10 진입 시 본 spec 따라 lambda 발행 → Secrets Manager rotation 활성. 본
PR 시점은 D-P8-08 의 환경변수 표준 (SAMHAN_*) 만 보유, lambda 코드 X, AWS 리소스 X.

---

## Phase 8 3차 결정 (2026-05-05)

### D-P8-10. Phase 8 3차 = AWS 마이그레이션 dry-run + 회고 + Phase 9 진입 plan (본 PR)

- 산출물 4건 = AWS 마이그레이션 dry-run plan + Phase 8 회고 + Phase 9 진입 plan + dev-report
- 코드 변경 0 file (docs only)
- ROADMAP 갱신 = Phase 8 "진입 준비" → "완료" / Phase 9 "대기" → "진입 준비 완료" / Phase 10 dry-run plan 위치 명시
- DECISIONS 갱신 = D-P8-10 / D-P8-11 + D-P9-01 / D-P9-02

영향: Phase 8 3차 머지 후 Phase 9 진입 가능. Phase 9 1차 (partner-service skeleton) 시점부터
4 신규 service 슬라이스 진행. Phase 10 cutover 는 Phase 9 완료 + AWS account 발급 후.

### D-P8-11. AWS 마이그레이션 dry-run 위치 = `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`

- phase10/ 디렉토리 신규 생성 (Phase 10 cutover 산출물 위치)
- 14 section 구성 = 개요 / RDS Postgres / S3 endpoint override / Eureka cluster / ALB+WAF / CloudWatch alert / Route 53 / Secrets rotation / ServiceDiscoveryClient 활성 / 부트스트랩 순서 / 점진 cutover / roll-back / dry-run 시나리오 / timeline
- Section 4 = Eureka 자체 EC2 운영 (multi-AZ 2 노드) → AWS Cloud Map wrapper 활성 보류 (Phase 8 2차 결정 보강)
- Section 11 = canary 10% → 50% → 100% 점진 cutover + DNS TTL 60s 사전 단축
- Section 12 = roll-back 트리거 = 5xx > 5% (10분) 또는 p99 > 1s

영향: Phase 10 진입 시 본 dry-run plan 을 reference 로 사용. 14 section 모두 staging
dry-run → canary 10% → full cutover 3단계로 진행. 신규 결정 (예: AWS Cloud Map 활성 시점)
은 Phase 11 또는 운영 부담 임계 도달 시점 결정.

---

## Phase 9 진입 결정 (2026-05-05)

### D-P9-01. Phase 9 4 신규 service 포트 확정 (partner=8095 / groupware=8092 / notification=8093 / dashboard=8094)

- partner-service = 8095 (8088 partner-order-service 와 충돌 회피)
- groupware-service = 8092 (결재선 + 메신저 + 일정)
- notification-service = 8093 (push/email/sms 통합 라우터, Phase 5 SMS Aligo 흡수)
- dashboard-service = 8094 (KPI + 실시간 재고 + 매출 + materialized view)

기존 14 service 포트 cross-check:
- 8080 api-gateway / 8081 auth / 8082 logging / 8083 user / 8084 product / 8085 inventory
- 8086 slip / 8087 accounting / 8088 partner-order / 8089 dc-config / 8091 partner-auth / 8761 eureka

Phase 10 신규: 8096 migration-service (ECount 일괄 이관)

영향: Phase 9 W1 ~ W4 슬라이스 진행 시 본 포트 매핑 일관 적용. 환경변수 표준 = `SAMHAN_PARTNER_SERVICE_URL` / `SAMHAN_GROUPWARE_SERVICE_URL` / `SAMHAN_NOTIFICATION_SERVICE_URL` / `SAMHAN_DASHBOARD_SERVICE_URL`.

### D-P9-02. Phase 9 진입 = Phase 8 완료 + 호환성 가드 검증

- 진입 조건 = Phase 8 (PR #88 / #89 / 본 PR) 머지 + 호환성 가드 12-factor 12/12 OK + 14 service 환경변수 통일
- 진입 plan = `docs/migration/phase9/M-PHASE-9-readiness.md`
- 5주 roadmap = W1 partner / W2 groupware / W3 notification / W4 dashboard / W5 회고 + Phase 10 진입 plan
- 각 service 신규 시 가드 = BaseEntity 7 audit + Soft Delete + IT mockbean 외부 client 격리 + 환경변수 표준 + ServiceDiscoveryClient 도입 (Phase 10 활성 대비) + 한국어 commit + Javadoc + dev-reports

후속 결정 가능 항목 (D-P9 시리즈 추가 가능):
- 4 service 도메인 모델 확정 (W1 ~ W4 진행 시점)
- materialized view 구조 (W4 dashboard-service)
- notification adapter 추상화 (W3)

영향: Phase 9 1차 (partner-service skeleton) 부터 본 가드 일관 적용. Phase 10 cutover 시점에 14 + 4 = 18 service 모두 AWS 마이그 대상.

---

## Phase 9 1차 결정 (2026-05-06)

### D-P9-03. Phase 9 1차 = W1 partner-service skeleton (본 PR)

- 신규 service `services/partner-service` (port 8095, DB `partner_db`) 추가
- 2 entity = `Partner` (거래처 마스터, partnerCode UK + bizNo UK + 신용한도 + 미수금) + `PartnerCreditHistory` (append-only 이력)
- 2 enum = `PartnerStatus` (ACTIVE / SUSPENDED / TERMINATED) + `CreditEventType` (SLIP_ISSUED / PAYMENT / CREDIT_LIMIT_CHANGE)
- 2 controller = `PartnerInternalController` (X-Internal-Token, M5 lookup) + `PartnerAdminController` (X-User-* + @PreAuthorize, CRUD + history)
- 2 service = `PartnerService` (마스터 라이프사이클) + `PartnerCreditService` (한도/잔액 갱신 + history append, 동일 transaction)
- Flyway V1 = `partners` + `partner_credit_history` (BaseEntity 7 audit + Soft Delete + partial unique index `WHERE is_deleted=false`)
- 단위 테스트 1 (`PartnerServiceTest` 8 case) + IT 2 (`PartnerInternalControllerIT` 4 case + `PartnerAdminControllerIT` 5 case)
- self-contained = 외부 client 의존 없음 (M-PHASE-9-readiness §6 의존성 매트릭스 일관)
- 환경변수 표준 = `SAMHAN_PARTNER_DB_*` chained-default (LEGACY_DB_* fallback) + `SAMHAN_INTERNAL_TOKEN` + `SAMHAN_PARTNER_SERVICE_URL` + `SAMHAN_DISCOVERY_PROVIDER`
- `infrastructure/env-templates/partner-service.env` 신규 (CHANGE_ME_LOCAL_ONLY placeholder)
- `services/partner-service/README.md` + `docs/dev-reports/phase9-step-1-partner-service.md` 신규

근거: M-PHASE-9-readiness §3-1 (W1 partner-service) 일정 일관 진행. partner-service 가 self-contained 이므로 외부 service 의존성 가드 (IT @MockBean) 불요 — 신규 service 중 가장 단순한 진입점.

영향: 본 PR 머지 후 Phase 9 W2 (groupware-service) 진입 가능. 14 + 1 = 15 service. settings.gradle / build.gradle leafProjects 양쪽 갱신.

### D-P9-04. M5 slip-service partnerCode → partnerId lookup client 구현 = Phase 9 W5 또는 Phase 10 cutover 시점

- 본 PR scope = partner-service `/internal/partners/{partnerCode}` endpoint 신규만
- slip-service 측 `PartnerClient` 구현 (service URL = `SAMHAN_PARTNER_SERVICE_URL`, X-Internal-Token 헤더 자동 첨부) 은 별도 PR
- slip-service `/from-*` endpoint 의 partnerCode → partnerId 정규화 흐름 통합도 별도 PR
- 시점 = (1) Phase 9 W5 마무리 + 회고 시점 또는 (2) Phase 10 cutover 사전 정합 시점

근거: 본 PR scope 를 partner-service 신규 서비스 한정으로 제한 (단편 PR 회피). slip-service 측 변경은 IT M5 idempotency 3중 격리 회귀 테스트 동반 의무 — 별도 충분한 시간 확보 필요.

영향: 본 PR 머지 직후 시점 = slip-service 의 partnerId 처리는 Phase 6 M5 상태 그대로. partner-service 의 internal endpoint 는 운영 활성이지만 호출자 0. 호출자 활성 = W5 또는 Phase 10 시점.

### D-P9-05. ServiceDiscoveryClient `samhan.discovery.provider=eureka` default — Phase 10 cutover 시점 aws-cloud-map 토글

- 본 PR partner-service 의 application.yml 에 `samhan.discovery.provider: ${SAMHAN_DISCOVERY_PROVIDER:eureka}` 추가
- partner-service 의 build.gradle 에 `implementation project(':shared:discovery-abstraction')` 의존성 추가
- 본 시점 = `EurekaServiceDiscoveryClient` 자동 활성 (Eureka 자체 EC2 운영 결정 D-P8-11 일관). `AwsCloudMapServiceDiscoveryClient` 는 placeholder 유지
- Phase 10 cutover 시점에 `SAMHAN_DISCOVERY_PROVIDER=aws-cloud-map` 으로 환경변수 토글하면 코드 변경 없이 vendor 전환 (build.gradle 의존성은 그대로)

근거: D-P8-07 (ServiceDiscoveryClient interface 도입) 일관. Phase 9 신규 service 부터 본 의존성 표준 적용 — 14 기존 service 의 build.gradle 의존성 추가 부담을 Phase 10 cutover 일괄 시점으로 미루지만, 신규 service 는 최초 작성 시점부터 도입.

영향: 본 PR 머지 후 시점 = partner-service 가 첫 번째 ServiceDiscoveryClient 소비자. provider=eureka default 동작은 기존 `@EnableDiscoveryClient` Eureka client 와 동일 (functional 동일성 보장). Phase 10 cutover 시 partner-service 가 가장 먼저 aws-cloud-map 으로 전환 가능한 service.

---

## Phase 9 2차 결정 (2026-05-06)

### D-P9-06. Phase 9 2차 = W2 groupware-service skeleton (본 PR)

- 신규 service `services/groupware-service` (port 8092, DB `groupware_db`) 추가
- 5 entity = `ApprovalLine` (결재선 종합 + chain) + `ApprovalStep` (chain 단일 단계, sequence ASC) + `Message` (1:1 메신저) + `Schedule` (일정) + `ScheduleParticipant` (참여자 1:N)
- 4 enum = `ApprovalStatus` (5상태) + `ApprovalStepStatus` (3상태) + `MessageStatus` (UNREAD/READ) + `ScheduleStatus` (DRAFT/CONFIRMED/CANCELLED)
- 2 controller = `GroupwareInternalController` (X-Internal-Token, 결재 lookup + 미열람 카운트) + `GroupwareAdminController` (결재 3 + 메신저 2 + 일정 4 endpoint)
- 3 service = `ApprovalLineService` + `MessageService` + `ScheduleService`
- 1 client = `UserClient` (user-service `/internal/users/{userId}` lookup) — fail-open 정책 (Phase 10 시점 fail-fast 강화)
- Flyway V1 = 5 테이블 + BaseEntity 7 audit + Soft Delete + partial unique index 2종 (`schedule_participants` schedule+participant / `approval_steps` line+sequence)
- 단위 테스트 16 case (ApprovalLineServiceTest 8 + MessageServiceTest 4 + ScheduleServiceTest 4) + IT 10 case (Internal 4 + Admin 6, UserClient @MockBean)
- M-PHASE-9-readiness §6 의존성 매트릭스 일관 — user-service (직원 정보) 단일 외부 의존
- 환경변수 표준 = `SAMHAN_GROUPWARE_DB_*` chained-default + `SAMHAN_USER_SERVICE_URL` + `SAMHAN_INTERNAL_TOKEN` + `SAMHAN_GROUPWARE_SERVICE_URL` + `SAMHAN_DISCOVERY_PROVIDER`
- `infrastructure/env-templates/groupware-service.env` 신규 (CHANGE_ME_LOCAL_ONLY placeholder)
- `services/groupware-service/README.md` + `docs/dev-reports/phase9-step-2-groupware-service.md` 신규

근거: M-PHASE-9-readiness §3-2 (W2 groupware-service) 일정 일관 진행. 결재선 + 메신저 + 일정 3 도메인은 사용 흐름이 인접하므로 단일 service 보유 결정.

영향: 본 PR 머지 후 Phase 9 W3 (notification-service) 진입 가능. 14 + 2 = 16 service. settings.gradle / build.gradle leafProjects 양쪽 갱신.

### D-P9-07. 결재선 chain 모델 = ApprovalLine + ApprovalStep 분리, ApprovalStatus 5상태

- 결재선 chain 은 별도 entity (`ApprovalStep`) 로 분리, `@OneToMany` + `@OrderBy("sequence ASC")` 보관 (1 line : N step)
- chain 단계는 0-base sequence 자동 할당, partial unique index `(approval_line_id, sequence)` 활성 행 한정으로 중복 방지
- `ApprovalStatus` 5상태 = `PENDING` / `IN_PROGRESS` / `APPROVED` / `REJECTED` / `WITHDRAWN`
  - PENDING = 발의 직후 (1번째 결재자 처리 대기)
  - IN_PROGRESS = chain 일부 승인 + 후속 대기
  - APPROVED = 모든 step 승인 완료
  - REJECTED = chain 중 1명이라도 반려 (즉시 종료)
  - WITHDRAWN = 요청자 본인 회수
- 종료 상태 (APPROVED/REJECTED/WITHDRAWN) 는 추가 승인/반려 호출 거부 (`ensureMutable` 가드)
- chain 순서 강제 — `currentStep()` PENDING 중 sequence 최소 step 만 처리 가능, 다른 결재자 호출 거부
- 본인 결재자 차단 — `appendStep` 가드로 요청자 ≠ approver 강제

근거: 결재선의 비즈니스 흐름은 chain (sequence) 이 본질이므로 별도 entity 분리가 자연스럽다. 5상태 enum 은 `WITHDRAWN` 까지 포함하여 회수 흐름을 status 로 표현 (별도 boolean 컬럼 회피, 종료 상태 단일 가드 일관). 본인 결재자 차단 / chain 순서 강제는 도메인 단위 가드로 service / controller 우회 불가.

영향: chain 의 sequence ASC orderly approval 흐름이 도메인 invariant. 결재 도메인 후속 확장 시 (예: 병렬 결재 / 전결 / 위임) 본 가드를 어떻게 완화할지 별도 결정 필요 (W5 회고 시점 검토).

### D-P9-08. ServiceDiscoveryClient 두 번째 소비자 = groupware-service

- W1 partner-service 가 첫 소비자 (D-P9-05). 본 PR groupware-service = 두 번째 소비자
- `build.gradle`: `implementation project(':shared:discovery-abstraction')` 의존성 추가 (W1 패턴 1:1 복제)
- `application.yml`: `samhan.discovery.provider: ${SAMHAN_DISCOVERY_PROVIDER:eureka}` (W1 패턴 1:1 복제)
- 본 PR 시점 = `EurekaServiceDiscoveryClient` 자동 활성. UserClient 가 본 wrapper 를 보유 (현재 미사용, Phase 10 활성 시 경로별 호출 라우팅에 사용 예정)
- W3 notification-service / W4 dashboard-service 도 동일 패턴 적용 의무 (Phase 9 신규 service 표준)

근거: D-P9-05 (W1 도입) 일관. 신규 service 가 최초 작성 시점부터 의존성 도입하여 14 기존 service 의 의존성 추가 부담을 Phase 10 cutover 일괄 시점으로 미룬다. groupware-service 는 UserClient 보유 service 로서 향후 service-to-service 호출 라우팅의 첫 비-self-contained 소비자.

영향: Phase 10 cutover 시점에 `SAMHAN_DISCOVERY_PROVIDER=aws-cloud-map` 토글로 partner-service + groupware-service 2개 신규 service 가 동시 vendor 전환 가능. UserClient 의 `getDiscoveryClient()` 는 현재 unused — Phase 10 시점에 base URL 대신 service-name 기반 lookup 으로 전환 (별도 PR scope).

---

## Phase 9 3차 결정 (2026-05-07)

### D-P9-09. Phase 9 3차 = W3 notification-service skeleton (본 PR)

- 신규 service `services/notification-service` (port 8093, DB `notification_db`) 추가
- 2 entity = `NotificationRequest` (발송 요청 종합 + payload JSONB) + `NotificationLog` (발송 이력 1 request : N attempt)
- 3 enum = `NotificationChannel` (PUSH/EMAIL/SMS) + `NotificationStatus` (PENDING/SENT/FAILED/RETRYING) + `RecipientType` (USER/PARTNER/EXTERNAL_PHONE)
- 2 controller = `NotificationInternalController` (X-Internal-Token, send + status) + `NotificationAdminController` (send / list / single / retry, MASTER+MANAGER)
- 1 service = `NotificationService` (생성 / 게이트웨이 호출 / 재시도 / 페이지)
- 3 channel adapter (인터페이스 + 운영 + mock) = PushAdapter (`FcmPushAdapter` + `MockPushAdapter`) / EmailAdapter (`SesEmailAdapter` + `MockEmailAdapter`) / SmsAdapter (`AligoSmsAdapter` + `MockSmsAdapter`)
- 1 client = `UserClient` (user-service `/internal/users/{userId}` 단건 + `/internal/users/verify-bulk` bulk)
- Flyway V1 = 2 테이블 + BaseEntity 7 audit + Soft Delete + JSONB payload + partial unique index (`notification_logs.request_id+attempt_no` 활성 행 한정)
- 단위 테스트 12 case (NotificationGatewayTest 3 + NotificationServiceTest 6 + UserClientBulkVerifyTest 3) + IT 9 case (Internal 4 + Admin 5, UserClient @MockBean)
- 환경변수 표준 = `SAMHAN_NOTIFICATION_DB_*` chained-default + `SAMHAN_INTERNAL_TOKEN` + `SAMHAN_USER_SERVICE_URL` + `SAMHAN_DISCOVERY_PROVIDER` + `SAMHAN_ALIGO_*` + `SAMHAN_FCM_*` + `SAMHAN_USER_CACHE_*`
- `infrastructure/env-templates/notification-service.env` 신규 (CHANGE_ME_LOCAL_ONLY placeholder)
- `infrastructure/postgres/init/01-create-databases.sql` `notification_db` 추가
- `infrastructure/prometheus/prometheus.yml` `notification-service:8093` + `groupware-service:8092` scrape target 추가 (DevOps Follow-up #11/#12 W3 시점 흡수)
- `services/notification-service/README.md` + `docs/dev-reports/phase9-step-3-notification-service.md` 신규

근거: M-PHASE-9-readiness §3-3 (W3 notification-service) 일정 일관 진행. 푸시/이메일/SMS 라우터는 단일 service 가 모든 channel 어댑터를 strategy pattern 으로 보유하는 것이 운영 / 추적 / 재시도 흐름 단순화에 유리.

영향: 본 PR 머지 후 Phase 9 W4 (dashboard-service) 진입 가능. 14 + 3 = 17 service. settings.gradle / build.gradle leafProjects 양쪽 갱신.

### D-P9-10. 3 channel adapter strategy + Phase 5 Aligo 흡수

- `NotificationGateway` 공통 인터페이스 + `NotificationGatewayConfig` 가 Spring 발견 bean 을 channel enum 키 EnumMap 으로 라우팅
- service 레이어 (`NotificationService`) 는 channel → adapter 1회 lookup → send 호출 → result 적재 (재시도 정책 분리)
- `MockPushAdapter` / `MockEmailAdapter` / `MockSmsAdapter` 는 단위 테스트 전용 (Spring bean 미등록)
- `FcmPushAdapter` — credentials placeholder 인 경우 stub-success (외부 호출 X). Phase 10 cutover 시 FCM Admin SDK 통합
- `SesEmailAdapter` — placeholder, Phase 10 cutover 시 AWS SES SDK 통합
- `AligoSmsAdapter` — Phase 5 `slip-service.delivery.sms.AligoSmsGateway` 의 form-urlencoded 호출 모델 흡수 (key/user_id/sender/receiver/msg/testmode_yn). 응답 `result_code == 1` 만 success
- credentials placeholder (CHANGE_ME_LOCAL_ONLY) 시 외부 호출 skip + stub-success — local dev / dev-default 호환

근거: 채널별 어댑터 분리는 mock injection / test 격리 / Phase 10 SDK 통합 시점 분리 측면 모두 유리. EnumMap 라우팅은 channel 추가 시 어댑터 bean 등록만으로 자동 통합 (config 코드 수정 불요). Aligo 는 단순 form 인증으로 Solapi (HMAC-SHA256) 대비 통합 비용 낮음 + Phase 5 시점 검증 완료된 호출 모델이라 흡수가 안전.

영향: Phase 5 의 `services/slip-service/.../sms/AligoSmsGateway.java` 는 본 PR 시점에 그대로 보존 (W3 운영 단편화 회피). Phase 10 cutover 또는 후속 정리 슬라이스 시점에 slip-service 가 notification-service `/internal/notifications/send` 호출로 전환 + 본인 SMS 모듈 제거.

### D-P9-11. UserClient bulk verify + Caffeine TTL 60s — BE backlog #4 채택

- PR #92 BE Reviewer 후속 backlog #4 (groupware ApprovalLine N 결재자 fan-out 직렬 RPC 비용) 를 W3 시점에 통합 채택
- `UserClient.verifyBulk(List<UUID>)` — 한 번의 RPC 로 N user 검증 + Caffeine cache (TTL 60s, max 10000 entries)
- user-service 신규 endpoint `POST /internal/users/verify-bulk` (Repository.findAllByIdIn 활용, 1 query)
- groupware-service `ApprovalLineService.create` 도 직렬 N+1 → bulk 1회 호출로 전환 (본 PR 통합 적용)
- 영향 file 5 = notification-service UserClient + UserCacheProperties + groupware UserClient + groupware ApprovalLineService + user-service InternalUserController + 2 dto + IT mock setup
- user-service 측 InternalTokenFilter / SecurityConfig 갱신 (Phase 9 W3 신규 — Phase 9 W1/W2 의 UserClient 가 호출하는 단건 lookup endpoint 의 실 보호 추가)

근거: W3 시점에 적용해 두면 W4 dashboard-service / W5 시점에 다중 client (InventoryClient / AccountingClient / PartnerClient / UserClient) 통합 패턴이 일관 정착. 별도 PR 분리 시 W4 까지 fan-out 부하 누적 + 후속 PR 의존성 발생. 통합 PR 1건 시 5 file 추가 변경으로 후속 슬라이스 정착 비용 0.

영향: groupware-service IT 의 mock setup 확장 (`verifyBulk(anyList())` lenient 추가). dashboard-service / 후속 service 의 UserClient 신규 작성 시 본 패턴 (verifyBulk + Caffeine) 의무 표준화.

---

## Phase 9 4차 결정 (2026-05-07)

### D-P9-12. Caffeine 일관 유지 + Redis 토글 약속 (W3 DevOps backlog #4 채택)

- W3 reviewer 토론에서 DevOps 가 제기한 "Caffeine in-process vs Redis 공유 캐시" 트레이드오프를 W4 통합 PR 에서 정식 결정
- 단계별:
  - W3 (notification) — Caffeine in-process (UserClient TTL 60s)
  - W4 (dashboard, 본 PR) — Caffeine 일관 유지 (KPI 응답 60s TTL, max 5000 entries)
  - Phase 10 — multi-instance scaling 시점에 Redis 전환 검토
- 토글 = `samhan.cache.provider=caffeine|redis` 환경변수 표준 — 코드 변경 없이 전환 가능하도록 `DashboardCacheProperties` + `CacheConfig` 보유
- 본 PR 시점 = Caffeine impl 만 활성. Redis impl 은 Phase 10 별도 PR scope

근거: W4 dashboard-service single-instance 가동 + 5분 간격 materialized view REFRESH 가 데이터 일관성의 1차 갱신 메커니즘. 60초 KPI cache TTL 은 REFRESH 주기보다 짧아 stale 위험 없음. multi-instance 전환 시점 (Phase 10) 에 Redis 공유 캐시 + ttl 길이 재검토.

영향: W4 시점 추가 의존성 0 (Redis 미도입). Phase 10 cutover 시점에 Redis driver + Lettuce client + connection pool 추가 후 `samhan.cache.provider=redis` 토글로 전환 — 본 결정으로 후속 PR scope 분리.

### D-P9-13. Materialized view CONCURRENTLY refresh + 5분 간격 scheduled

- `mv_realtime_stock_summary` (창고별 SKU 수 + 총수량) + `mv_sales_daily_summary` (일별 거래처 수 + 총금액 + 총항목수) 2 view 도입
- CONCURRENTLY 모드 — unique index 의무 (V1 SQL 보유)
- `samhan.dashboard.refresh.interval-minutes` (default 5) 주기로 scheduled REFRESH (`MaterializedViewRefreshConfig`)
- `POST /admin/dashboard/refresh` 수동 트리거 endpoint + KPI cache invalidate 동시 호출
- fail-soft — REFRESH 실패 시 silent skip + warn log (다음 주기 재시도, 예외 미전파)

근거: 창고별 / 일별 집계 query 가 dashboard 의 핵심 read 패턴. row level 데이터를 매 호출마다 GROUP BY 하면 N row 부하 누적. materialized view 를 CONCURRENTLY refresh 하면 read 부하를 view scan 으로 일정화 + 5분 stale 허용 (운영 dashboard 특성상 분 단위 stale 충분).

영향: H2 PG MODE (test local 프로파일) 는 MATERIALIZED VIEW 미지원 → IT 는 Postgres Testcontainer 기반 + local 프로파일은 flyway 비활성. CI Linux runner 에서 실 Postgres 16 + view CONCURRENTLY refresh 검증.

### D-P9-14. 4 외부 client + ServiceDiscoveryClient 네 번째 소비자

- W1 partner / W2 groupware / W3 notification 에 이은 ServiceDiscoveryClient 네 번째 소비자
- 4 외부 client = `InventoryClient` (8085) + `AccountingClient` (8087) + `PartnerOrderClient` (8088) + `PartnerClient` (8095, W1)
- 본 슬라이스 = skeleton fail-soft 정책 (네트워크 실패 / 404 시 empty/ZERO/0). Phase 10 cutover 시점에 endpoint 정착 후 응답 파싱 + DTO 매핑
- `PartnerClient` 만 W1 의 `/internal/partners/{partnerCode}` endpoint 활용 (운영 가능 상태)
- IT 4 client 모두 `@MockBean` 격리 의무 (memory feedback_it_mockbean_external_clients) + lenient setup

근거: dashboard-service 는 데이터 집계 책임상 4 service 의존이 본질. 본 PR 시점에 client + fail-soft 정책 + IT mock pattern 일관 정착하여 Phase 10 cutover 시점 추가 비용을 endpoint 응답 파싱 한 가지로 한정.

영향: ServiceDiscoveryClient 의 4 service 동시 진입 패턴 표준화. Phase 10 시점 `aws-cloud-map` 토글로 4 service 동시 vendor 전환 가능. 향후 신규 service 도입 시 본 패턴 (skeleton fail-soft + ServiceDiscoveryClient 의존성) 일관 적용.

### D-P9-15. shared:user-client-abstraction 통합 (W3 BE backlog #1 채택)

- W3 reviewer 토론에서 BE 가 제기한 "notification / groupware UserClient 중복 구현 + groupware Caffeine 누락" 통합
- 신규 모듈 `shared/user-client-abstraction/` = `UserVerifier` interface + `DefaultUserVerifier` impl + `UserVerifierProperties` + 6 case 단위 테스트
- 표준 = RestClient + Caffeine TTL 60s + max 10000 entries + fail-soft / fail-fast 토글 (`failFast` boolean)
- notification-service / groupware-service 의 기존 `UserClient` 클래스를 본 abstraction 의 thin delegate 로 변환 (회귀 0 — `@MockBean UserClient` 패턴 유지)
- dashboard-service 도 본 모듈 의존성 등록 (실 사용은 후속 — Phase 10 시점 user lookup 통합)

근거: 동일 책임 (user verify) 의 2 service 중복 코드 + groupware 의 Caffeine 누락은 abstraction 부재의 명백한 비용. W4 시점에 abstraction 으로 통합하면 Phase 10 시점 fail-fast 토글 활성 (BE backlog #2) + Phase 11 시점 잠재적 GraphQL 통합 등 후속 변경의 단일 진입점 확보.

영향: 회귀 검증 — notification 12 + groupware 16 단위 + 각 IT 9 + 11 = 21 case 모두 PASS 유지. 향후 신규 service 의 user lookup 도입 시 본 abstraction 1 줄 의존성 추가 + UserVerifier 주입만으로 정착.

---

## Phase 9 W5 결정 (2026-05-07)

### D-P9-16. partner-service `POST /internal/partners/find-by-codes` bulk endpoint + dashboard PartnerCodeResolver bulk 전환 (W4 BE 의견 3 채택)

- partner-service 신규 `POST /internal/partners/find-by-codes` — partnerCode N건 동시 조회 batch endpoint (X-Internal-Token + ROLE_MASTER)
- `PartnerService.findByCodes(Collection<String>)` — distinct 정규화 + 빈 입력 short-circuit + IN 절 1회 query
- `PartnerRepository.findAllByPartnerCodeIn(Collection<String>)` — Spring Data JPA 자동 query (Soft Delete `@SQLRestriction` 가드 자동 적용)
- IT 4건 신규 (정상 / 빈 / 일부 미존재 누락 / 토큰 누락 403)
- dashboard-service 측 `PartnerClient.findByCodes(List<String>)` — partner-service POST 호출 + skeleton-mode 토글 일관 + fail-soft 빈 리스트 반환
- `PartnerCodeResolver.resolveAll(List<String>)` — cache hit/miss 분리 + miss 만 1회 bulk RPC + cache 적재 (단건 resolve 와 cache name `dashboard-partner-resolve` 공유)
- `PartnerCodeResolverTest` 단위 4건 신규 (빈 / 전체 miss / hit+miss 분리 / 일부 미존재)

근거: PR #94 dev-report § Phase 10 cutover 약속 (BE 의견 3) — `DashboardAdminController.salesAggregate` 의 partner 정보 lookup fan-out 시 N 회 직렬 RPC 회피용 backing endpoint. W4 시점 사용자 가드 (`feedback_integrated_pr_pattern.md` § fix 후속 PR/Phase 위임 금지) 명시 후 11건은 본 PR 채택, 1건 (BE 의견 3) 만 W5 위임 → 본 W5 PR 채택으로 잔존 backlog 0 으로 정리.

영향: 향후 매출 집계 / KPI 화면이 partnerCode N건 동시 노출 시 fan-out 직렬 RPC → 1회 batch 호출. partner-service 자체 IT 4 + dashboard-service 단위 4 추가 (회귀 0 — 기존 12 + 16 + 17 단위 + 9 IT 모두 PASS 유지).

### D-P9-17. slip-service 시간 의존 design fix (LocalDate.now()) — main 도 영향 받았을 회귀 사전 예방

- PR #94 후속 fix `cde6db9` — slip-service 24 case IT/단위 fail
- 원인 — 6 file × `LocalDate.of(2026, 5, 5)` 하드코딩 + DeliveryBatch 토큰 만료 비교 (`tokenExpiresAt = 2026-05-06 23:59:59`) 가 2026-05-07 시점 만료 영향으로 fail
- fix — 6 file 모두 `LocalDate.now()` 동적 값으로 정정 (DeliveryBatchTest / DeliveryBatchServiceTest / SlipServiceSignatureTest / PublicSignatureControllerIT / PublicSlipControllerIT / SlipSignatureAdminIT)

근거: 본 PR 변경 영향이 아닌 시간 흐름 (날짜 변경) 회귀이지만, main 도 동일 영향 받았을 패턴이며 사용자 가드 적용 (Phase 10/W5 위임 X 정공법 fix). W5 시점 grep 가드로 다른 service 의 단순 fixture 데이터 (`LocalDate.of(2026,1,1)` user 입사일 등) 는 회귀 영향 없음 추가 검증.

영향: CI 7/7 PASS 회복. 회귀 0 — dashboard / notification / groupware / partner / user 모두 PASS 유지.

### D-P9-18. 사용자 가드 적용 — `feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지"

- W4 PR #94 시점 사용자 명시 — reviewer 식별 fix 12건 매트릭스 중 11건 본 PR 채택 + 1건 (BE 의견 3) W5 위임
- W5 본 PR 시점 잔존 1건도 채택 — backlog 누적 0 으로 종료

근거: 단편 fix 후속 PR / Phase 위임 시 backlog 누적 → 후속 슬라이스 부담 + 가드 위반 (단편 PR 회피). 본 가드 적용 후 W4 + W5 모두 reviewer 식별 fix 본 PR 일괄 채택 패턴 정착. memory `feedback_integrated_pr_pattern.md` 갱신 후속 진행.

영향: Phase 9 W4 → W5 backlog 위임 패턴 1건 (BE 의견 3) 만 잔존 → 본 PR 채택. Phase 10 진입 시점 backlog 누적 0.

### D-P9-19. Phase 10 진입 준비 완료 — AWS migration cutover plan 채택

- `docs/migration/phase10/M-PHASE-10-readiness.md` 신규 — 6 섹션 (진입 조건 / 작업 분해 / 가드 / 일정 / roll-back / 참조)
- 작업 분해 — P10-1 (Secrets + Cache) / P10-2 (Discovery + Resilience) / P10-3 (RDS + Cutover) 3 슬라이스
- Phase 10 dry-run plan (`M-AWS-MIGRATION-DRY-RUN.md`, Phase 8 도입) 14 section 과 짝
- AWS 4 큰 변화 (Secrets Manager / aws-cloud-map / Redis / Aurora PostgreSQL) 모두 Phase 8/9 추상화로 사전 흡수 (코드 변경 1줄 ~ 1 모듈 수준)

근거: Phase 9 회고 (`phase9-retrospective.md` § 6) 기준 — 14 service skeleton + 4 추상화 모듈 + 12-factor + chained-default + ShedLock 가드 모두 OK. AWS account + IAM + Aurora + ALB + Route 53 인프라 준비 시점에 P10-1 진입 가능.

영향: Phase 10 cutover 회귀 위험 최소화 + roll-back 단위 명확. 사용자 결정 (`AWS account 발급 시점` + `cutover 슬라이스 분할 합의`) 후 P10-1 진입.

### D-P9-20. Phase 9 회고 종합 + Phase 10 시점 결정

- `docs/dev-reports/phase9-retrospective.md` 신규 (10 섹션) — Phase 9 5 슬라이스 (W1~W5) 종합
- 산출 통계 매트릭스 — 4 service + 1 shared module + 2 materialized view + 4 외부 client + 19 결정 + 25 backlog 채택
- 핵심 회고 7 success + 6 학습 — 사용자 가드 정착 / shared abstraction 통합 / slip-service 시간 의존 사전 예방 / W2 Lazy fix / W3 raw URL pin / W4 backlog 누적 → W5 압박 / 임시 브랜치 회피
- 누적 backlog 채택 결과 — Phase 10 위임 N건 (W3 BE backlog #2/#3, W3 DevOps #6/#7/#10, W3 QA #11/#12/#13)

근거: Phase 9 = "잔여 도메인" phase 의 마무리. 14 service skeleton 완료 + Phase 10 진입 준비 완료 시점 명시.

영향: Phase 10 진입 시점 = 본 PR 머지 직후. AWS account 준비 시점에 P10-1 슬라이스 시작.

---

## post-W5 backlog cleanup 결정 (2026-05-07)

### D-P9-11 보강. UserVerifierProperties fail-mode (OPEN/STRICT) alias 토글 (Q-W3-3 채택)

- 본 보강은 D-P9-11 의 `failFast` 부울 토글에 대한 의미 명시 alias 도입이며, 동작 변경 없음 (회귀 안전)
- `UserVerifierProperties.FailMode` enum 신설 — `OPEN` (fail-soft, default) / `STRICT` (fail-fast, Phase 10 cutover 시점 활성)
- `setFailMode` / `setFailFast` 양방향 alias setter — 한 쪽 변경 시 다른 쪽 자동 동기화 (legacy `failFast` 호출자 / 신규 `failMode` 호출자 모두 호환)
- 환경변수 `SAMHAN_USER_CLIENT_FAIL_MODE=OPEN` 표준 — `notification-service.env` + `groupware-service.env` 신규 추가
- Phase 10 cutover 시점 = `SAMHAN_USER_CLIENT_FAIL_MODE=STRICT` 전환 약속 명시 (P10-1 슬라이스 산출물)
- 회귀 검증 — `DefaultUserVerifierTest` 8 case (기존 6 + IT 2 신규 OPEN/STRICT alias) 모두 PASS

근거: Phase 10 cutover 시점에 fail-mode 의미 명시 토글 필요. 부울 `failFast` 만 보유한 상태에서는 환경변수 명/문서/코드 일관성이 약화 (`fail-fast=true` vs `fail-mode=STRICT` 의미 동일하지만 리뷰어 인지 비용). post-W5 backlog cleanup 시점에 의미 명시 alias 추가하여 향후 Phase 10 P10-1 슬라이스 진입 시 환경변수 단일 표준 (`SAMHAN_USER_CLIENT_FAIL_MODE=OPEN|STRICT`) 만 보유.

영향: 기존 `failFast` 호출자 (4 service `UserClient` + IT) 변경 없이 호환. 신규 `failMode` setter 호출자 (Phase 10 P10-1 시점 cutover) 만 신설.

---

### D-P9-21. post-W5 backlog cleanup — Phase 10 위임 backlog 중 즉시 처리 가능 7건 본 PR 채택

- 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지") 일관 적용 — Phase 10 위임 backlog 중 환경 의존성이 없는 7건 본 PR 채택
- 채택 매트릭스:
  | # | 영역 | 출처 | 산출 |
  |---|---|---|---|
  | 1 | design-system PR template | Designer D-W4-3 보강 | QA HTML mobile responsive table wrapper (`.qa-table-wrapper` + `@media max-width 768px`) |
  | 2 | design-system tokens | Designer D-W5-2 채택 | slice accent 3색 토큰 (`--color-slice-{success,pending,deferred}` Google Material Green/Yellow/Gray) + utility class |
  | 3 | notification-service | QA Q-W3-1 채택 | retry max-attempts property (`samhan.notification.retry.max-attempts` default 5) + `requeueForRetry_exceedsMaxAttempts_marksFailedPermanent` IT |
  | 4 | notification-service | QA Q-W3-2 채택 | `NotificationSendRequest.payload` `@Size(max=4000)` (Postgres TOAST 임계 회피) + `send_payloadOver4000Bytes_returns400` IT |
  | 5 | shared:user-client-abstraction | QA Q-W3-3 채택 | `UserVerifierProperties.FailMode` enum (OPEN/STRICT) alias + 양방향 자동 동기화 + IT 2건 |
  | 6 | notification-service | DevOps backlog 채택 | `NotificationGatewayMetrics` 신규 (3 channel × 2 result = 6 Micrometer counter) — `notification_gateway_send_total{channel,result}` actuator/prometheus 노출 + IT 2건 |
  | 7 | user-service | DevOps backlog 채택 | `Employee.DEFAULT_HIRE_DATE = 2026-01-01` 의도 주석 + 한국어 Javadoc — W4 slip-service 시간 의존 회귀 학습 적용 (코드 동작 변경 0) |
- IT 신규 5건 합계 — `requeueForRetry_exceedsMaxAttempts_marksFailedPermanent` (NotificationServiceTest) + `send_payloadOver4000Bytes_returns400` (NotificationAdminControllerIT) + `verify_strictMode_failFast_returnsFalseOnGatewayError` + `verify_openMode_failSoft_returnsTrueOnGatewayError` (DefaultUserVerifierTest) + `NotificationGatewayMetricsTest` 2 case
- 회귀 검증 5 영역 — `:shared:user-client-abstraction:test` + `:services:notification-service:test` + `:services:user-service:test` + `:services:groupware-service:test` + `:services:dashboard-service:test` 모두 PASS
- 잔존 Phase 10 위임 backlog (환경 의존 항목만) — Designer #1 ChannelBadge 일관성 (Phase 10 W1) / QA Q-P10-1 skeleton-mode IT sweep / DevOps `partner_client_fail_total` Micrometer counter (Phase 10 W2 Resilience4j 통합 시점) / Phase 10 P10-1 ~ P10-3 슬라이스 본격 작업

근거: Phase 9 W5 머지 직후 (PR #95) 시점에 Phase 10 위임 backlog 매트릭스 재검토 결과, 7건은 환경 의존 (AWS account / Redis / Aurora) 없이 main 직접 작업 가능. 단편 PR 분리 시 backlog 누적 + 가드 위반 (사용자 명시 가드). 통합 PR 1건 시 9+ docs 영역 동기화 + QA 캡처 3종 + CI 7/7 검증 패턴으로 Phase 10 진입 시점 backlog 0 보장.

영향: Phase 9 = 완료 + post-W5 cleanup 완료 상태로 종료. Phase 10 진입 시점 = 본 PR 머지 직후. notification-service 의 retry max-attempts / payload @Size / Micrometer counter 3건은 production 진입 직전 보강 (운영 안정성 향상). user-client-abstraction 의 fail-mode alias 는 Phase 10 P10-1 slice cutover 진입 시점 단일 환경변수 표준 (`SAMHAN_USER_CLIENT_FAIL_MODE`) 활용 가능. design-system slice accent + PR template mobile wrapper 는 W6+ 전 PR 일관 적용 의무.

종합 TM fix 8건 (사용자 가드 일관 적용, 5 reviewer 토론 종합):
- **FE-1** slice-accent CSS variable 일관 (`--badge-radius` / `--badge-channel-font-size` `b-channel-*` 와 동등 token)
- **FE-2** `--qa-table-min-width-{sm,md,lg}` 3단계 변수 + PR-template-color-reference.md § 5.2 컬럼 수별 가이드 (4 이하 sm 600px / 5~6 md 800px / 7 이상 lg 1000px)
- **BE-1** `NotificationSendRequest.payload` `@AssertTrue` byte 검증 (UTF-8 byte length ≤ 4000 — multi-byte 문자 정합)
- **BE-2** `NotificationService.retry()` DEAD_LETTER 분기 `gatewayMetrics.recordFailure()` 호출 (Grafana dead-letter 가시성)
- **BE-3** `OrgChartSeeder.DEFAULT_HIRE_DATE` 중복 상수 제거 + `Employee.DEFAULT_HIRE_DATE` 인용 (DRY 정합)
- **QA-1** IT 4001 byte oversize fixture 1줄 압축 (`"a".repeat(4001)` — ASCII 1 byte/char)
- **QA-2** `UserVerifierProperties.connectTimeoutMs` / `readTimeoutMs` 추가 + `DefaultUserVerifier.buildClient()` 적용 + 테스트 100ms/200ms 명시 (가용 X 포트 호출 시 OS 기본 timeout 회귀 회피, WireMock 의존 추가 대안보다 가벼움)
- **QA-3** 문서 정합 — slip-service "만료 비교 패턴 부재" → "fixture 회귀 패턴 0 + 도메인 의도 비교 {`Slip.java:713` + `DeliveryBatch.java:195`} 2건 정상" 정정 (production 만료 검증 + 동적 테스트 fixture 패턴 명시)

---

## Phase 10 결정 (arologis-service 배차 마이크로서비스, 2026-05-07 ~)

### D-P10-01. arologis-service 도입 결정 (배차 마이크로서비스 신규)

- 신규 service `services/arologis-service/` (port 8097, DB `arologis_db`) — 카톡 메시지 파싱 → 차량/정차/기사 매칭 → 전자서명 → GPS 추적 통합
- 5 entity (Dispatch / Vehicle / VehicleStop / Driver / Signature) + DriverLocation GPS 추적
- 7 enum (DispatchType / VehicleTonnage / VehicleStatus / StopStatus / DriverSource / MatchSource / SignatureSource)
- W10-1 (본 PR) = skeleton (parser + matcher 추상화 + 4 client + 3 controller + 31 case)
- W10-2 ~ W10-5 = vendor 통합 / 모바일 / slip 통합 / 회고

근거: 기존 14 service 와 별도 도메인 (배차 = 외부 vendor + 모바일 어플 + GPS) — 단일 service-per-DB 격리 + 향후 외부 vendor 교체 가능 (DriverMatcher 추상화) 의도. 사용자 결정 2026-05-07.

영향: 14 service → 15 service. Phase 11 cutover 시점 RDS arologis_db 추가 + Prometheus scrape target 1건 추가.

### D-P10-02. port 8097 + arologis_db 표준 채택

- 포트 = 8097 (기존 14 service 8081~8095 + 8096 migration 예약 다음)
- DB = `arologis_db` (service-per-DB 표준 일관)
- 환경변수 = `SAMHAN_AROLOGIS_*` (chained-default 패턴 D-P8-08 일관)

근거: 기존 service 포트 인벤토리 일관 + service-per-DB 격리 + 환경변수 표준.

영향: `infrastructure/postgres/init/01-create-databases.sql` `arologis_db` 추가. `infrastructure/prometheus/prometheus.yml` `arologis-service:8097` scrape 추가.

### D-P10-03. DriverMatcher 추상화 + Mock + Insung Quick 토글

- `DriverMatcher` interface + `DriverMatchResult` record
- W10-1 default = `MockDriverMatcher` (`samhan.arologis.matcher.provider=mock`) — MOCK-001 / 010-0000-0000 driver 매칭 (DB 자동 upsert)
- W10-2 prod = `InsungQuickDriverMatcher` (`provider=insung-quick`) — 본 PR 은 placeholder (UnsupportedOperationException), W10-2 시점 실 vendor API 통합
- 외부 vendor 5만 프리랜서 풀 (인성데이타 퀵프로그램, 사용자 결정 2026-05-07)
- 향후 SMS / Kakao 추가 vendor 시 `MatchSource` enum 확장만으로 통합 가능

근거: vendor lock-in 회피 + vendor 교체 가능 design + dev/test 환경 mock 일관. Phase 8 ServiceDiscoveryClient 추상화 패턴 일관.

영향: W10-2 인성데이타 통합 시점에 InsungQuickDriverMatcher 만 변경 — DispatchService / Controller 등 호출 코드 영향 0.

### D-P10-04. 모바일 어플 stack = RN Expo (`clients/mobile-staff` 패턴 일관)

- W10-3 시점 RN Expo 어플 도입 — 기존 `clients/mobile-staff` 패턴 일관 (`clients/mobile-staff` 내부 driver tab 추가 vs 신규 `clients/mobile-driver` — W10-3 진입 시점 결정)
- Driver-app endpoint = `/driver-app/arologis/**` (인증 = X-User-Id + X-User-Role=DRIVER)
- 본 어플 사용 driver = INTERNAL Driver (`source=INTERNAL`, `appUserId=user-service userId`, `appInstalled=true`)
- 외부 vendor 매칭 driver = LINK 기반 카톡/SMS 서명 (어플 미설치, `source=EXTERNAL_*`)

근거: 사용자 결정 2026-05-07 — 신규 native stack 도입보다 기존 RN Expo 일관성 + cross-platform 운영 부담 최소화.

영향: W10-3 시점 `clients/mobile-staff` 또는 `clients/mobile-driver` 신규 폴더 + RN Expo 패키지 (사용자 결정 시점).

### D-P10-05. Phase 10/11 renumber — arologis = Phase 10 / AWS migration cutover = Phase 11

- 사용자 결정 2026-05-07 — 기존 Phase 10 (AWS migration cutover) → **Phase 11 으로 이동**
- 신규 **Phase 10 = arologis-service** (배차 마이크로서비스, 5 슬라이스 W10-1 ~ W10-5)
- docs 동기화:
  - `docs/migration/phase10/M-PHASE-10-readiness.md` **재작성** (arologis 5 슬라이스 plan)
  - `docs/migration/phase11/M-PHASE-11-readiness.md` **신규** — 기존 phase10 readiness 의 AWS migration cutover plan 이동
  - `docs/migration/phase11/M-AWS-MIGRATION-DRY-RUN.md` 이동 (기존 phase10 → phase11)
  - 루트 `README.md` + `ROADMAP.md` Phase 매트릭스 갱신
  - 모든 service `README.md` 의 "Phase 10 cutover" 인용 → "Phase 11 cutover" 정정
- DECISIONS 의 "Phase 10 cutover" 인용은 향후 D-P11-* 신규 결정 시점에 정정 (본 결정만 phase10/11 boundary 명시)

근거: 사용자 우선순위 변경 — arologis 가 즉시 사업 가치 (실 카톡 배차 자동화 + 5만 프리랜서 매칭 + 어플 GPS 추적) 산출. AWS migration 은 Phase 11 으로 미뤄 안정성 검증 후 cutover.

영향: 기존 Phase 10 인용 (DECISIONS 본문 / service README / env-template 코멘트) 은 향후 PR 시점에 점진 정정. 본 PR 은 readiness / ROADMAP / README 핵심 docs 만 정정 (모든 코드 코멘트 즉시 정정 시 본 PR 부담 과다 — 사용자 가드 일관 후속 PR 미루지 않고 본 PR 채택 가능 영역만 일괄).

### D-P10-06. 알림 분담 정책 (2026-05-07)

- 배차 단계 알림 = **인성 알림톡** (W10-2 시점 인성 vendor 직접 호출, notification-service 우회)
- 본 시스템 알림 (어플 설치 invite / 일반 사용자 push) = **notification-service Aligo**
- W10-1 시점: notification-service skeleton-mode 토글 (`samhan.arologis.client.skeleton-mode=true`) 로 호출 차단
- W10-2 진입 시점: 인성 알림톡 직접 호출 + notification-service 호출 = 어플 설치 invite 만 (분리 정책)

근거: 사용자 결정 2026-05-07 — vendor 가 자체 알림톡 채널 보유, notification-service 의존 회피로 vendor 통합 시점에 통신 단순화. 본 시스템 알림은 자체 운영 통제 일관 (Aligo, D-W3 표준).

영향: W10-2 진입 시점 InsungQuickDriverMatcher 가 매칭 직후 인성 알림톡 직접 호출 (notification-service 호출 X). 본 PR (W10-1) 은 docs 명시만.

### D-P10-07. 모바일 어플 driver tab = mobile-staff 내부 채택 + GPS 권한 정책 (2026-05-07)

- 모바일 어플 옵션 = **`clients/mobile-staff` 내부 driver tab** 채택 (별도 `mobile-driver` 신규 X)
- 진입 흐름 = `AppRootNavigator` 의 `mode='estimate' | 'driver'` 분기 — 기존 v2/v3 EstimateWebViewScreen 100% 보존
- GPS 권한 정책:
  - foreground 권한 = **의무** (배송 도중 위치 추적)
  - background 권한 = 선택 (운영 시점 결정)
  - 거부 fallback = **어플 사용 불가** (`GpsBlockedScreen` 노출, driver tab 차단)
- W10-3 진입 조건 = W10-1 완료 (W10-2 의존 X) — 본 어플 GPS only 활성, 인성 LBS 통합은 W10-2 시점
- W10-3 GPS source = `APP_GPS_ACTIVE` (foreground 권한 O), `APP_GPS_BACKGROUND` (선택, 운영 시점 활성)

근거: 사용자 결정 2026-05-07 — FE-1 + Designer-2 채택. 별도 mobile-driver client 신규 시 5 client 통합 부담 + 영업직원/배송기사 같은 사람 가능성 (사용자 명시) → 동일 어플 안 mode 분기로 단순화.

영향: 본 PR (W10-3) `clients/mobile-staff/src/screens/driver/` 5 화면 (Dashboard / LocationTracking / Signature / GpsBlocked / TabNavigator) + `AppRootNavigator` 신규. 기존 EstimateWebViewScreen 변경 0.

### D-P10-08. Pretendard self-host 정식 도입 (2026-05-07)

- mobile-staff Pretendard 폰트 = **self-host 정식** (jsdelivr CDN 회피, Phase 7 4차 통일 폰트 패턴 일관)
- `clients/mobile-staff/assets/fonts/Pretendard-*.otf` 4~9 weight 배치 (본 PR 진입 시점 = graceful guard, 후속 fix 정식 배치)
- `app.json` `plugins.expo-font` 정식 등록 — `Regular / Medium / SemiBold / Bold` 4 weight
- `usePretendardFontGuarded()` = useFonts hook 정식 활성 + try/catch graceful (asset 미배치 환경 RN UI 미차단)

근거: 사용자 결정 2026-05-07 — Designer-2 채택. jsdelivr CDN 의존성 회피 (오프라인 환경 / 한국 망 latency / vendor 차단 위험) + Phase 7 4차 통일 폰트 패턴 일관 (5 client 동등).

영향: 본 PR (W10-3) `clients/mobile-staff/src/theme/usePretendardFontGuarded.ts` 정식 활성. driver tab RN native UI 의 `fontFamily.sans = 'Pretendard'` 적용. WebView 안 legacy estimate 는 자체 web font (변경 0).

### D-P10-09. mobile theme 토큰 = web/design-system 1:1 복제 (2026-05-07)

- `clients/mobile-staff/src/theme/tokens.ts` 신규 — `clients/web/design-system/src/tokens/tokens.css` 의 RGB 값을 1:1 복제
- 복제 대상 (W3+W4+W5+post-W5+W10-1):
  - post-W5 sales-form-polish-slice — surface / ink / line / action / state
  - W3 dashboard — Google Material method (GET/POST/PUT/DELETE) + status badge (ok/warn/info/new)
  - W4 notification — 3 channel badge (push/email/sms)
  - post-W5 D-W5-2 — slice accent (success/pending/deferred)
  - W10-1 — unparsed peach (b-unparsed)
- `badgeStyle(kind)` 헬퍼 = RN inline style 객체 반환 (CSS class `b-channel-push` / `slice-accent-success` 1:1 매핑)
- spacing (4-base) / radii (badge 4 / card 8 / button 4 / modal 8) / typography (Pretendard family + 8 size + 4 weight + 3 line-height) 동등 export

근거: 사용자 결정 2026-05-07 — Designer-2 채택. 5 client (estimate / order / desktop / mobile / mobile-staff) 디자인 통일성 + 신규 driver tab UI 가 web/design-system 과 동등 시각 인상 의무.

영향: 본 PR (W10-3) `theme/tokens.ts` + 5 화면 (Dashboard / LocationTracking / Signature / GpsBlocked / TabNavigator) 모두 본 토큰 인용. web `tokens.css` 변경 시 본 파일도 동기화 의무 (후속 슬라이스 가드 추가 권장).

### D-P10-10. Pretendard 9 weight 운영 배치 약속 (2026-05-07)

본 PR (W10-3) 시점 = 4 weight (Regular / Medium / SemiBold / Bold) 의무 + graceful guard 보호 (`usePretendardFontGuarded` `useState(true)` 기본값).

EAS Build 진입 시점 (W10-5 또는 운영 진입) 의무:

- `clients/mobile-staff/assets/fonts/Pretendard-{Thin,ExtraLight,Light,Regular,Medium,SemiBold,Bold,ExtraBold,Black}.otf` 9 weight 정식 배치
- `app.json` `plugins.expo-font` 의 9 weight asset 등록
- `usePretendardFontGuarded` 기본값 정정 — `useState(false)` + `useFonts` complete 후 `setReady(true)` 패턴
- splash screen guard 도입 — OTF load 완료 전 RN UI 렌더 차단 회피

근거: 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지") 일관 적용. W10-3 종합 TM 5 reviewer 채택 fix 7건 중 Designer-2 / FE-2 / B-DEVOPS-1 통합 — Pretendard OTF 4 weight 본 PR 의무 + 9 weight 운영 진입 시점 의무 + `useState(false)` 정정은 OTF 정식 배치 시점 동시 처리.

영향: 본 PR (W10-3) 시점 = 4 weight 자산 누락 시 graceful guard 가 RN UI 미차단. EAS Build 진입 시점 = 본 결정에 따라 9 weight 배치 + `useState(false)` 정정 + splash guard 도입 의무. ROADMAP `W10-5` 또는 `Phase 10 운영 진입` task 로 추적.

### D-P10-11. signature_source 컬럼 추가 + LINK/APP 통합 (2026-05-07)

slip-service Phase 10 W10-4 (PR #99) 시점에 `signatures` 관련 컬럼군에 `signature_source` 컬럼 3개 추가:
- `slips.signature_source` VARCHAR(20) NOT NULL DEFAULT 'LINK' (인수자 서명)
- `slips.driver_signature_source` VARCHAR(20) NOT NULL DEFAULT 'LINK' (기사 서명)
- `slip_signature_audit.signature_source` VARCHAR(20) NULL (audit 행, INVALIDATE 시 NULL)

근거:
- arologis-service 의 driver-app 직접 캡처 (source=APP) 가 W10-3 부터 활성, slip-service 에 전파 시 source 식별 의무
- 기존 SMS/Aligo 공개 모바일 endpoint 발급 (LINK) 데이터는 backfill DEFAULT 'LINK' 로 호환 보존
- 전자서명법 시행령 §17 무결성 입증 — audit 테이블에도 source 보존 의무
- `SignatureSource` enum (LINK/APP) 은 기존 `SignatureChannel` enum (MOBILE_CANVAS/PAPER_SCAN) 과 직교 (입력 매체 vs 발급 경로)

영향:
- 기존 `Slip.recordSignature` / `recordDriverSignature` 4-arg / 3-arg 시그니처 보존 + source overload 추가 (LINK 자동 위임)
- `SlipSignatureAudit.record` / `recordDriver` 도 source overload — RECORD/RECORD_DRIVER 행에 LINK/APP 보존
- 기존 데이터 / 호출자 영향 0 (DEFAULT 'LINK' backfill + 시그니처 호환)
- 본 PR 신규 endpoint `POST /internal/slips/{slipId}/signatures` 는 APP source 만 허용 (LINK 는 기존 공개 모바일 endpoint 전용 — 400 가드)
- Phase 11 cutover 시점 — APP source 슬립의 imageRef 가 S3 placeholder 에서 실 S3 업로드로 전환 (현 PR 은 placeholder bytes + hash 보존)

### D-P10-12. ApiResponse wrapper IT 의무화 (W10-3 F-3 채택, 2026-05-07)

W10-3 PR #98 backlog F-3 (ApiResponse wrapper IT 검증) 을 W10-4 (PR #99) 시점에 정식 채택.

근거:
- W10-3 회고에서 mobile-staff 가 `response.data.data.*` 처럼 wrapper 안 안 데이터를 직접 접근하는 패턴이 정착
- BE 측 IT 가 wrapper schema 를 명시적으로 검증하지 않으면, controller 응답 형식 회귀 (예: 직접 `Map` 반환) 시 mobile-staff 가 런타임 깨짐
- W10-4 신규 endpoint 2종 (slip-service `/internal/slips/{slipId}/signatures` + `/internal/slips/by-partner/{partnerId}/recent`) + arologis sign 응답 schema 확장 모두 mobile-staff 호출 경로 → IT schema 검증 의무
- PR #92 raw URL 회고 가드 일관 — schema mismatch fail-fast 패턴

영향:
- `SlipInternalControllerIT` (slip-service 신규 9 case) — 모든 200 OK 응답에 `success`/`data.*` schema 검증 의무
- `SignatureIntegrationIT` (arologis 신규 3 case) — 동일 schema 검증 의무
- 향후 모든 신규 IT 도 ApiResponse wrapper schema 검증 의무 (Phase 11 cutover 진입 시 운영 가드 일관 보존)
- 기존 IT 는 점진 보강 (회귀 영향 없는 변경)


### D-P10-13. SlipResolver 실 활성 + slip-service /internal/slips/by-partner-code/{code}/recent endpoint (2026-05-07)

W10-4 (PR #99) 5 reviewer 토론 종합 시점에 BE-1 채택. SlipResolver.resolveByPartnerCode 가 항상 empty 반환하던 fallback 을 실 활성으로 전환 — slipBridged=true 운영 0건 갭 해소.

근거:
- W10-4 초기 구현은 partnerCode → partnerId UUID 매핑 부재로 SlipClient 호출 자체가 막힘 (slipBridged 항상 false)
- 운영 시점에 양쪽 저장 패턴이 동작하지 않으면 W10-4 통합 의미 상실 (driver-app 캡처가 slip 인수자/기사 서명에 반영 X)
- partner-service 의 기존 `GET /internal/partners/{partnerCode}` 응답 (PartnerInternalResponse) 이 partnerId UUID 를 포함 — 추가 API 변경 0
- slip-service 가 자체 PartnerInternalClient 로 partnerCode → partnerId resolve 후 slips 테이블 lookup → graceful 200 + data=null 패턴 (404 미반환)

영향:
- slip-service 신규 `PartnerInternalClient` (timeout DV-1 일관 적용)
- slip-service `SlipInternalController` 신규 `GET /internal/slips/by-partner-code/{partnerCode}/recent` endpoint
- slip-service `SlipSignatureService.findRecentByPartnerCode(String)` Optional 반환 메서드
- arologis `SlipResolver.resolveByPartnerCode` 실 호출로 전환 (PartnerClient 의존 제거 — slip-service 가 흡수)
- arologis `SlipClient.findRecentSlipIdByPartnerCode(String)` 신규
- IT 보강: SlipInternalControllerIT 3 case 신규 (BE-1 검증) + SignatureIntegrationIT happy-path case 1 신규 (QA-2 검증)


### D-P10-14. SlipClient connect/read timeout 설정 (2026-05-07)

W10-4 (PR #99) 5 reviewer 토론 종합 시점에 DV-1 채택. arologis SlipClient + slip-service PartnerInternalClient 모두 connect 2s / read 3s timeout 명시.

근거:
- driver-app sign endpoint 가 동기 호출 — slip-service hang 시 driver UX 차단 (앱 응답 없음)
- 양쪽 저장 패턴은 graceful fallback 보장 의무 (자체 INSERT 보존, slip 호출 실패 시 false 반환)
- Spring Boot 3.4 표준 `ClientHttpRequestFactories` + `ClientHttpRequestFactorySettings` 사용
- Phase 11 운영 진입 시 RDS Aurora SLA 정합 — read timeout 3s 가 SLA 95% (요청당 1.5s) 의 2배 안전 마진

영향:
- arologis `SlipClient.buildClient()` helper — connect 2s / read 3s 적용
- slip-service `PartnerInternalClient` 생성자 — 동일 timeout 적용 (cross-service 일관)
- 운영 모니터링 backlog 추가 — Grafana 에서 SlipClient timeout 빈도 추적 (Phase 11 cutover 시점)

### D-P10-16. step-8 9 슬라이스 통합 PR — Flyway V 번호 sequence + 단일 PR 채택 + inventory 차이 분개 코드 (2026-05-09)

PR #114 (`feature/integrated-phase-10-step-8-ui-9-slice`) — 매뉴얼 안내 미구현 UI 9 슬라이스 통합. 5-team (BE/FE/Designer/QA/DevOps) 병렬 + TM 종합 fix.

근거:
- 9 슬라이스 = 모두 Phase 10 step 8 범위 — 9 개 PR 분리 시 cross-slice 회귀 검증 비용 폭증, 단일 통합 PR 채택 (`feedback_integrated_pr_pattern.md`)
- accounting Flyway V 번호 sequence — V1 (init+seed) + V2 (tax_invoice) + V3 (accounting_period) + V4 (재고감모 seed) — V4 = inventory AccountingClient 호환 시드 (150 재고자산 / 919 재고감모손실, 한국 일반기업회계기준)
- inventory 차이 자동 분개 — 차이 (+) 차변 150 / 대변 919, 차이 (-) 차변 919 / 대변 150 (한국 일반기업회계기준 표준 대로 영업외비용 919 환입)
- service-layer 마감 가드 — `JournalService.create` 안에서 `MonthEndCloseService.findClosedPeriodCovering` 호출 (interceptor `AccountingPeriodGuard` + filter `CachedBodyFilter` 의 MockMvc 비호환 회피, IT 안전성 우선)
- inventory `findByFilters` 쿼리 — PostgreSQL JDBC 의 `(? IS NULL OR ...)` 패턴은 SQLState 42P18 → boolean flag + non-null sentinel 패턴으로 우회

영향:
- `services/accounting-service/src/main/resources/db/migration/V4__seed_inventory_audit_accounts.sql` 신규
- `services/accounting-service/src/main/java/.../service/JournalService.java` — MonthEndCloseService 의존 추가 + `create` 가드 호출
- `services/inventory-service/src/main/java/.../repository/InventoryAuditRepository.java` — boolean flag 시그너처 변경
- `services/inventory-service/src/main/java/.../service/InventoryAuditService.java` — sentinel 부여 + boolean flag 전달
- `services/accounting-service/src/test/java/.../service/JournalServiceTest.java` — MonthEndCloseService mock 추가 + 기본 stub
- `docs/qa/integration-pr-9-slice/scenarios.md` — testid 명명 정합 (실 FE 표준), 1.2.6 본인 변경 case 신규 (총 161 case)
- `tools/manual-capture/data-testid-required.md` — slice 1/4/6 정정 + slice 10 (매출 마감) + slice 11 (재고 실사) 신규 명세
- `ROADMAP.md` — Phase 10 W10-step-8 row 추가
- `docs/dev-reports/integration-phase-10-step-8-ui-9-slice.md` 신규

### D-P10-17. step-9 시트 흐름 보강 + 노션 4 CSV 이식 + partner_code 매핑 정정 (2026-05-10)

PR (`feature/integrated-phase-10-step-9-sheet-notion-import`) — PR #114 머지 후 사용자 우려 (시트 비동기 회귀) + 노션 운영 4 CSV (REGION/DC/CHAT/BLOCK) 의 Samhan Public native 이식.

근거:
- 시트 흐름 보강 (Part 1) — `partner-order-service` + `product-service` 가 Phase 10 W10-step-8 머지 후 시트 동기화 누락 회귀 — 본 슬라이스 PR-D Part A (사용자 옵션 C 의도 완성) 으로 5분 cron 재활성
- 노션 4 CSV 이식 (Part 2) — REGION (가배차 지역별 분류) / DC (거래처 할인 정보) / CHAT (단톡방리스트) / BLOCK (발송금지리스트) — Notion DB export → arologis V3 / dc-config V2 / notification V2 / partner V4 Flyway + 서비스 레이어 import 로 native 이식 (Notion 의존성 제거)
- **partner_code 매핑 정정 (TM Part 3)** — 사용자 명시 (2026-05-10): "단톡방리스트와 발송금지리스트의 경우 추후 거래처명이 아니라 거래처코드로 매핑할 수 있도록". import 시 모호한 LIKE 매칭 회피 + source-of-truth 일관성 확보:
  - `PartnerLookupClient.verifyPartnerCode(String)` 신규 (notification-service)
  - `PartnerService.findByCodeForLookup(String)` 신규 (partner-service)
  - `ChatRoomImportService` + `PartnerBlockImportService` 양쪽에서 거래처코드 컬럼 (`거래처코드` 또는 `partner_code`) 우선, 없으면 사업자명 fallback
  - 사업자명 미공급 시 snapshot 은 `[partnerCode]` placeholder (entity invariant 보호 + admin UI 후속 보완 경로)
- **R2 backlog 보존** — KakaoDispatchParser 의 "-214" 카톡 식별자 vs partner-service 의 partner_code (예: "P-2026-0001") 명칭 충돌은 본 PR 범위 외 (별도 PR 위임 — 사용자 명시 격리)
- ManualDispatchRequest 의 `Long partnerCode` (= 카톡 슬립번호) 는 본 PR 미변경 — R2 별도 PR 시 String partner_code 분리 + entity 마이그레이션 동시 진행

영향:
- `services/notification-service/src/main/java/.../client/PartnerLookupClient.java` — `verifyPartnerCode` 메서드 추가
- `services/notification-service/src/main/java/.../client/NoopPartnerLookupClient.java` — Lambda → Anonymous class 변환 (2 메서드 구현)
- `services/notification-service/src/main/java/.../service/ChatRoomImportService.java` — 거래처코드 컬럼 우선 매핑 분기 추가
- `services/partner-service/src/main/java/.../service/PartnerService.java` — `findByCodeForLookup` Optional 형 추가
- `services/partner-service/src/main/java/.../service/PartnerBlockImportService.java` — 거래처코드 컬럼 우선 매핑 분기 추가
- `services/notification-service/src/test/java/.../service/ChatRoomImportServiceTest.java` — 코드 우선 / fallback / placeholder / 영문 헤더 4 case 추가
- `services/partner-service/src/test/java/.../service/PartnerBlockImportServiceTest.java` — 코드 우선 / fallback / placeholder / 모두 miss 4 case 추가
- `services/notification-service/src/test/java/.../it/ChatRoomMappingAdminControllerIT.java` — `verifyPartnerCode` lenient mock 추가
- `services/notification-service/build.gradle` — OpenCSV + commons-io 의존성 추가 (BE-D commit 누락 보강)
- `.gitignore` — `tools/legacy-gas/` + `.tmp-*` 추가
- `ROADMAP.md` — Phase 10 W10-step-9 row 추가
- `docs/dev-reports/integration-phase-10-step-9-sheet-notion-import.md` 신규

후속 (별도 PR 위임):
- R2 — KakaoDispatchParser 의 카톡 슬립번호 vs partner-service partner_code 명칭 충돌 정리 (entity 컬럼 rename + 마이그레이션 동시 진행)
- BE-E — partner-service 의 실 RestClient `PartnerLookupClient` 구현체 등록 (현재 NoopPartnerLookupClient placeholder)

#### TM 종합 fix — PR #115 5-team 리뷰 + CI fail (2026-05-10)

5-team 리뷰 결과 — Designer ✅ / DevOps ✅ / FE ✅(3 minor) / QA ✅(2 권고) / BE Critical (CI fail 1건). TM 단일 commit 종합 fix:

1. **BE Critical** — notification-service IT 15건 `BeanDefinitionOverrideException` 회귀. `NoopPartnerLookupClient` 의 `@Configuration` + `@Bean` + `@ConditionalOnMissingBean` 가 `@MockBean` 보다 늦게 평가되어 noop bean + mock bean 동시 등록 시도. `@Component` + `PartnerLookupClient` 직접 구현 + class-level `@ConditionalOnMissingBean(PartnerLookupClient.class)` 로 재설계 — component scan 단계에서 안정적 평가. (memory `feedback_it_mockbean_external_clients` 일관)
2. **FE C/F/I minor** — `BlockedPartnersPage` testid `${b.id}` → `${b.partnerCode}` (UUID 비공개), invalidate 위치 `onClose` → `onUpload` resolve (타 3 admin CSV 페이지 패턴 일관). `SalesPartnerDcConfigPage` testid prefix `dc-config-*` → `admin-dcconfig-*` (admin 페이지 일관성).
3. **QA 권고** — `RegionClassifier` 광역 prefix 가중치 알고리즘 추가 ("중구" 4 그룹 모호 키워드 회귀 회피). 1차 광역 prefix 매칭 → 2차 sort_order keywords → 3차 group_name fallback. 회귀 테스트 case 6/7 추가 (7 PASS).
4. **AdminLayout DC 설정 entry** — `/sales/partner-dc-config` link, MASTER 가드 (sales 라우트지만 CSV 일괄 업로드 MASTER 전용 → admin 사이드바에도 진입 편의 노출).

검증:
- `./gradlew :services:notification-service:assemble` → BUILD SUCCESSFUL
- `./gradlew :services:arologis-service:test --tests "*RegionClassifierTest"` → 7 PASS
- `./gradlew assemble -x test` → BUILD SUCCESSFUL (95 actionable)
- `clients/desktop` typecheck → 무에러
- notification IT 15건 — Windows 로컬 Docker 미가용 → Testcontainers skip (정상). CI Linux runner 에서 BeanDefinition 충돌 해소 후 실 IT 동작 확인 (CI 재실행 자동).

### D-P10-18. PR-E 진입 전 선행 — R2 parsedPartnerCode rename + BE-E PartnerLookupClient 실 구현 (2026-05-10)

PR (`feature/integrated-pre-rename-partnerlookup`) — D-P10-17 후속 backlog 2건 (R2 + BE-E) 을 PR-E1 (slip+arologis+inventory 7건) 진입 전 선행 단일 PR 로 정리. Critical Path = arologis 의 partnerCode 명칭 충돌 해소 + notification 의 partner-service 실 호출 활성.

근거:
- **R2** — KakaoDispatchParser 의 `parsed_partner_code` (Long, 카톡 슬립번호 "(에스엠하나공조-214)" 의 214) 와 partner-service 의 `partner_code` (String, "P-2026-0001" 비즈니스 식별자) 가 동일 명칭으로 PR-E1 의 RegionClassifier + PartnerLookupClient 통합 시점에 의미 혼동 위험. PR-E1 진입 전 entity / DTO / service 명칭을 분리해야 lookup 결과 컬럼 신설 시 충돌 0.
- **BE-E** — D-P10-17 시점 NoopPartnerLookupClient (placeholder) 가 production 에서 활성되어 ChatRoom/BlockedPartner CSV import 가 noop empty 반환 → 모든 row reject 회귀. PR-E1 의 import 운영 활성 전에 partner-service 실 호출 RestClient 구현체 등록 의무.
- **단일 PR 통합** — 두 작업 모두 PR-E1 의 선행 의존성이며, 동일 도메인 (arologis ↔ notification ↔ partner-service) 의 partner_code 명칭 정합 작업이라 통합 PR 회귀 비용이 분리 PR 보다 낮음.

영향:
- `services/arologis-service/src/main/resources/db/migration/V4__rename_parsed_partner_code.sql` 신규 — `parsed_partner_code` (BIGINT) → `parsed_kakao_seq` rename + 신규 `parsed_partner_code` (VARCHAR(50)) 컬럼 + 인덱스 rename + 신규 partial index
- `services/arologis-service/src/main/java/.../domain/VehicleStop.java` — `parsedKakaoSeq` (Long) + `parsedPartnerCode` (String) 분리, 9-인자 factory 추가, `updateParsedPartnerCode` setter (PR-E1 lookup 후속 갱신용)
- `services/arologis-service/src/main/java/.../parser/ParsedDispatch.java` (record) — `parsedPartnerCode` → `parsedKakaoSeq` (Long) rename, 7-인자 호환 생성자 보존
- `services/arologis-service/src/main/java/.../parser/KakaoDispatchParser.java` — `parsePartnerCode` → `parseKakaoSeq` 메서드 rename + Javadoc 정정
- `services/arologis-service/src/main/java/.../service/SlipResolver.java` — `resolveByPartnerCode(Long)` → `resolveByKakaoSeq(Long)` rename (의미 동일, naming 만)
- `services/arologis-service/src/main/java/.../controller/ArologisDriverAppController.java` — SlipResolver 호출 이름 정합
- `services/arologis-service/src/main/java/.../dto/{ManualDispatchRequest, ManualDispatchPreviewResponse, DispatchDetailResponse, ParsedDispatchResponse}.java` — Long 카톡 식별자 필드 `partnerCode`/`parsedPartnerCode` → `kakaoSeq`/`parsedKakaoSeq` rename. DispatchDetailResponse.StopDetail 은 `parsedPartnerCode` (String) 추가 (PR-E1 lookup 결과 응답)
- `services/arologis-service/src/main/java/.../service/{DispatchService, DispatchManualService}.java` — VehicleStop 저장 시 `kakaoSeq` 전달
- `services/arologis-service/src/test/java/.../parser/KakaoDispatchParserTest.java` — case 3/8 정정 (`parsedKakaoSeq()`)
- `services/arologis-service/src/test/java/.../it/SignatureIntegrationIT.java` — 코멘트 정정 (`resolveByKakaoSeq`)
- `services/notification-service/src/main/java/.../client/RestClientPartnerLookupClient.java` 신규 — partner-service `GET /internal/partners/{partnerCode}` + `GET /internal/partners/by-name?name=` 호출, X-Internal-Token 인증, 404/409/5xx fail-soft
- `services/notification-service/src/main/resources/application.yml` — `samhan.partner-service.url` (default `http://localhost:8095`) + `samhan.notification.partner-lookup.enabled` (default true) 토글 신규
- `services/notification-service/src/test/java/.../client/RestClientPartnerLookupClientTest.java` 신규 — MockRestServiceServer 5 case (200 정상 / 404 / 409 / 한글 query encode / token 미설정)
- `ROADMAP.md` — Phase 10 PR-E 진입 전 선행 row 추가
- `docs/dev-reports/integration-pre-pr-rename-partnerlookup.md` 신규

후속 (PR-E1):
- arologis V4 의 신규 String 컬럼 `parsed_partner_code` 를 RegionClassifier + PartnerLookupClient 결과로 채우는 batch / parser 통합
- slip-service 의 `/internal/slips/by-partner-code/{code}/recent` endpoint 의 path variable 명칭 정합 (kakaoSeq vs partnerCode 의미 분리) — 본 PR scope 외, slip 측 PR 별도 진행

### D-P10-19. step-10 (PR-E1) GAS B 11건 이식 — 이카운트 엑셀 → 출고전표 자동 조회 + DPS 엑셀 업로드 보존 + REGION 활용 + SMS 2-step (2026-05-10)

PR (`feature/integrated-phase-10-step-10-gas-b-ecount-auto`) — Samhan Public 운영 GAS 11 도구 (사용자 분류 B) 중 7건 (DPS비교 / 가배차 / 미배차 / 지방가배차 / 내일자전표 / 전표정리 / 배차안내 SMS) 을 단일 통합 PR 로 native 이식. 잔여 4건 (원장 / 거래명세서 / 계산서 / 일마감) = accounting-service 도메인 PR-E2 위임.

근거:
- **출고전표 자동 조회 (이카운트 의존 0)** — slip-service `slips` 테이블이 PR #99 (W10-4 전자서명 통합) + PR #115 (W10-step-9 시트 흐름 보강) 시점 partner_code / driver_phone / region 컬럼 구비. PR #116 (R2 + BE-E) 시점 명칭 정합 + PartnerLookupClient 실 호출 활성. step-10 PR-E1 시점 = GAS 의 이카운트 엑셀 업로드 가공 패턴을 자체 자동 조회로 전면 격상 가능.
- **DPS 입고 비교 만 사용자 명시 보존** — 창고 측 표준 운영 절차 (DPS 시스템에서 받은 엑셀 → 자체 슬립 비교) 가 이미 정착되어 있어 자동 조회 격상보다 엑셀 업로드 + 매칭 알고리즘 native 이식이 적합. `DpsExcelParser` + `DpsCompareService` (SLIP/ITEM 단위 매칭) + `RowMismatch` 분류 (QUANTITY=주황 / PARTNER=빨강 / NOT_FOUND=회색).
- **REGION / CHAT / BLOCK 활용 (PR #115 산출)** — 가배차 = `RegionClassifier` 광역 prefix 17 시도 + 권역 그룹핑. 내일자 전표 이미지 = 단톡방별 섹션 + 발송금지 자동 제외 (5 way join: slips × chat × block × region × partner). 배차안내 SMS = 단톡방 매핑 + blocked 가드.
- **SMS preview/send 2-step** — 배차안내는 운영 사고 영향 큼 (잘못 발송 시 거래처 다수 동시 영향). preview 단계에서 단톡방 그룹핑 + 발송금지 가드 검증 후 send 단계 별 도 trigger. dryRun 패턴으로 single-call 사고 회피.
- **Phase B FE 6 ↔ Phase A BE 4 + Designer 1 1:1 매핑** — 11 commits (실 10 + FE-1 두 분할 1) 단일 PR 로 통합. 다중 FE agent race 결과 d163caa commit 메시지가 "FE-1 DPS" 표기이지만 실제 변경 = FE-1+2+6 통합 (사이드바/라우트/가배차/SMS) — rebase 정정 회피, PR body 명시 보완 (`feedback_integrated_pr_pattern` 의 fix 후속 PR 금지 일관).

영향:
- `services/slip-service/src/main/resources/db/migration/V15__add_slip_partner_code_region.sql` 신규 — slips.partner_code (VARCHAR(50)) + classified_region_group (VARCHAR(50)) + 인덱스 3종 (partner_code/region/driver_phone × slip_date partial active)
- `services/slip-service` — `Slip` entity 2 필드 추가 + `SlipRepository extends JpaSpecificationExecutor` + `SlipService.list` 7-arg overload + `SlipController` 5 query param + `NextDaySlipImageService` (5 way join) + `SlipCleanupService` (정합성 flag 4종) + `NotificationChatRoomClient` + `PartnerBlockClient` (Feign + graceful fallback)
- `services/inventory-service` — `DpsCompareController` (multipart + template) + `DpsCompareService` (매칭 알고리즘) + `DpsExcelParser` + `SlipServiceClient` (Feign) + `DpsCompareResponse` / `RowMismatch` DTO
- `services/notification-service` — `DispatchBatchAdminController` (preview + send) + `DispatchBatchPreview/Send/MessageTemplateService` + `SlipServiceClient` / `BlockedPartnerLookupClient` interface + Noop placeholder + 4 DTO (Preview/Send Request/Response)
- `services/arologis-service` — `ArologisAdminController` 3 endpoint (`/dispatches/pre-classify`, `/unassigned`, `/regional`) + `PreClassify/Regional/UnassignedService` + `SlipServiceClient` (skeleton-mode 토글) + `VehicleStopRepository` 활성 dispatch 조회 + 3 DTO + IT 4 파일 SlipServiceClient `@MockBean` 격리 추가
- `clients/desktop` Phase B 6 page — `arologisDispatchApi` / `dispatchSmsApi` / `dpsCompareApi` / `nextDaySlipApi` / `slipCleanupApi` + `ArologisPreClassifyPage` / `ArologisUnassignedPage` / `DispatchSmsPage` / `InventoryDpsComparePage` / `NextDaySlipPage` / `SlipCleanupPage` + `AppLayout` 사이드바 entry 6건 + `routes/index.tsx` 라우트 + `ArologisManualDispatchPage` query 자동 채움
- `clients/desktop/src/renderer/print/NextDaySlipView.tsx` (+CSS Module) Designer 1차 mock — 단톡방별 섹션 + 거래처/슬립 표 + @media print A4 세로 + page-break-after 옵션 (Malgun Gothic, 사용자 Edge 캡처 검토 후 2~5차 iteration)
- 단위 테스트 56 case 신규 (slip 16 + inventory 14 + notification 12 + arologis 14)
- `ROADMAP.md` Phase 10 step-10 row 추가
- `docs/dev-reports/integration-phase-10-step-10-gas-b-ecount-auto.md` 신규

후속 (PR-E2):
- accounting-service 4 도메인 (ledger / statement / tax invoice / daily close) = GAS B 8~11번 native 이식
- NextDaySlipView 인쇄 양식 2~5차 iteration (사용자 Edge 캡처 → CSS-only 미세 조정, `feedback_print_design_iteration`)

### D-P10-20. step-11 (PR-E2) GAS B accounting 4건 이식 — 원장/거래명세서/계산서/일마감 + 자체 분개/세금계산서 자동 조회 (2026-05-10)

PR (`feature/integrated-phase-10-step-11-gas-b-accounting`) — PR #117 (PR-E1, GAS B 11건 중 7건) 머지 후 사용자 명시 GAS B 잔여 4건 (원장 / 거래명세서 / 계산서 / 일마감) 을 accounting-service native 이식. 본 PR 머지 시점 GAS B 11건 매핑 100% 완성, 후속 PR-F (GAS C/D 6건) 진입 가능.

근거:
- **자체 분개 + 세금계산서 자동 조회 (이카운트 의존 0)** — accounting-service `journal_entries / journal_lines / tax_invoices` 테이블이 Phase 4 (PR #28 accounting-slice-A) + Phase 6 (M2/M3/M4/M5 backend 통합 PR #76) + Phase 9 (W4 dashboard 보강) + W10-step-8 (V3/V4 seed 150/919 추가) 시점 한국 일반기업회계기준 65 row 시드 + 401/110/255 코드 + ISSUED 상태 머신 구비. step-11 시점 = GAS 의 이카운트 매출/세금계산서 export 패턴을 자체 자동 조회로 전면 격상 가능.
- **외부 client 3종 도입 (ProductClient + PartnerLookupClient + ChatRoomMappingClient)** — Ledger/StatementBatch 응답에 partner snapshot (사업자번호/대표/주소) + 단톡방 매핑 (운영자 가시성) + product 명칭 (라인 snapshot) 동반. accounting-service 자체 보유 0 → product-service / partner-service / notification-service Feign 호출 의무. 모두 fail-soft (404/5xx 시 응답 partial null) + IT @MockBean 격리 (memory `feedback_it_mockbean_external_clients`).
- **POI 5.2.5 도입 (Apache License 2.0)** — 홈택스 일괄 양식 xlsx 100건 sheet 분할 표준 라이브러리. 내장 Java SXSSF (streaming) 회피 — 100건 단위 sheet 분할은 일반 XSSFWorkbook 의 명시적 batch 분할 패턴이 사용자 운영자 (회계사) 검토 흐름과 정합. memory `project_korean_accounting` 의 한국 일반기업회계기준 표준 정합.
- **단일 통합 PR (5+1 = 6 commits)** — Phase A (BE 1 통합 5 task + Designer 2 view) + Phase B (FE 4) + multi-agent collision 복구 1 = 6 commits 단일 통합 PR. 별도 docs PR 회피 (memory `feedback_continuous_docs_sync` + `feedback_integrated_pr_pattern` 일관).
- **multi-agent collision 복구 패턴** — FE-10 의 `git reset --soft` 가 FE-8 (commit `eb473b4`) + FE-9 (commits `6cf9646` / `8f62b57`) 를 destroy → working tree unstaged 산출 단일 복구 commit `55ebad5` 으로 일괄 stage + commit, destroy 된 SHA 3건 commit body 명시. PR-E1 의 d163caa (FE-1+2+6 통합) 와 동일 패턴 — rebase 정정 회피 + PR body 명시 보완 (`feedback_integrated_pr_pattern` 의 fix 후속 PR 금지 일관). 후속 PR-F 진입 시점 sequential commit 강제 또는 task 별 worktree 분리 검토.

영향:
- `services/accounting-service/build.gradle` — Apache POI 5.2.5 (`poi` + `poi-ooxml`) 의존성 추가
- `services/accounting-service/src/main/java/.../web/AccountingReportController.java` 신규 — 5 endpoint 통합 (`/accounting/sales/aggregate` BE-A8, `/accounting/journals/ledger-data` BE-A9, `/accounting/statements/batch-data` BE-A10, `/accounting/tax-invoice/hometax-export` BE-A11 binary xlsx, `/accounting/closings/daily` BE-A12), 모두 `ACCOUNTANT/MASTER` `@PreAuthorize` 가드, ApiResponse 래핑 (xlsx 제외)
- `services/accounting-service/src/main/java/.../service/SalesAggregateService.java` 신규 — 401 (제품매출) + 110 (외상매출금) 코드 합계 (기간 + partnerCode 옵션)
- `services/accounting-service/src/main/java/.../service/LedgerImageService.java` 신규 — 거래처 snapshot + 단톡방 매핑 + 분개 line 시간순 + 누적 잔액
- `services/accounting-service/src/main/java/.../service/StatementBatchService.java` 신규 — 기간 ISSUED 세금계산서 → 거래처별 그룹핑 + 라인 snapshot
- `services/accounting-service/src/main/java/.../service/HometaxExportService.java` 신규 — POI 100건 sheet 분할 + 한국어 파일명 + 표준 컬럼 (구분/공급자사업자번호/공급가액/세액 등)
- `services/accounting-service/src/main/java/.../service/MonthEndCloseService.java` — `getDailyDetail` 신규 메서드 (read-only, 마감 OPEN/CLOSED 무관)
- `services/accounting-service/src/main/java/.../client/ProductClient.java` + `ProductSummary.java` 신규 — product-service `/internal/products/by-id` Feign + X-Internal-Token + fail-soft
- `services/accounting-service/src/main/java/.../client/PartnerLookupClient.java` + `PartnerSummary.java` 신규 — partner-service `/internal/partners/{partnerCode}` Feign + X-Internal-Token + fail-soft
- `services/accounting-service/src/main/java/.../client/ChatRoomMappingClient.java` 신규 — notification-service `/internal/chat-rooms/by-partner-code` Feign + X-Internal-Token + fail-soft
- `services/accounting-service/src/main/java/.../repository/JournalLineRepository.java` 신규 — Specification 기반 read-only
- `services/accounting-service/src/main/java/.../repository/TaxInvoiceRepository.java` 신규 — 기간 ISSUED 조회
- `services/accounting-service/src/main/java/.../web/dto/{LedgerImageResponse, StatementBatchRow, SalesAggregateRow, DailyClosingDetailResponse}.java` 신규 — 4 DTO, 모두 partnerCode + partnerName + slipNo / taxInvoiceNo / journalNo 만 노출 (UUID 비공개)
- `services/accounting-service/src/test/java/.../service/{SalesAggregateServiceTest, LedgerImageServiceTest, StatementBatchServiceTest, HometaxExportServiceTest, DailyClosingDetailServiceTest}.java` 신규 — 단위 20 case 신규 (4+4+3+5+4) 전부 PASS
- `clients/desktop/src/renderer/api/{partnerLedgerApi, statementBatchApi, hometaxExportApi, closingApi}.ts` 신규 — 4 API client (`getDailyClosingDetail` 신규 포함)
- `clients/desktop/src/renderer/routes/{PartnerLedgerPage, StatementBatchPage, HometaxExportPage}.tsx` 신규 + `MonthEndClosingPage.tsx` 일별 detail 보강 (productName/discount/supply/vat/total + 일별 CSV)
- `clients/desktop/src/renderer/print/{PartnerLedgerView, StatementBatchView}.tsx` (+CSS Module) 신규 — Designer 1차 mock (사용자 Edge 캡처 후 2~5차 iteration `feedback_print_design_iteration`)
- `clients/desktop/src/renderer/components/AppLayout.tsx` 회계 그룹 entry 4건 신규 ("거래처 원장" / "거래명세서 일괄" / "홈택스 일괄 양식" / 일마감 detail) + `clients/desktop/src/renderer/routes/index.tsx` 라우트 5종 (`/accounting/partner-ledger`, `/accounting/statement-batch`, `/accounting/hometax-export`, `/print/partner-ledger`, `/print/statement-batch`)
- `ROADMAP.md` Phase 10 step-11 row 추가
- `docs/dev-reports/integration-phase-10-step-11-gas-b-accounting.md` 신규

GAS B 11건 매핑 (PR-E1 + PR-E2 = 100% 완성):
- PR-E1 (#117) 7건 — DPS비교 (inventory) / 가배차 / 미배차 / 지방가배차 (arologis) / 내일자전표 / 전표정리 (slip) / 배차안내 SMS (notification)
- PR-E2 (본 PR) 4건 — 원장 / 거래명세서 / 계산서 (홈택스 xlsx) / 일마감 (모두 accounting-service)

후속 (PR-F 이후):
- **PR-F** — GAS C/D 6건 진입 (사용자 분류 C/D 도구) 별도 슬라이스
- **인쇄 양식 iteration** — PartnerLedgerView + StatementBatchView 2~5차 (`feedback_print_design_iteration`)
- **POI 5.2.5 운영 진입** — Hometax v2026 표준 회귀 테스트 1건 추가 권장
- **외부 client cache** — Ledger/StatementBatch 의 PartnerLookup/ChatRoom 호출 운영 부하 진입 시점 short-TTL Caffeine cache 검토
- **CI fail 시뮬레이션** — 후속 별도 슬라이스 (사용자 명시)

### D-P10-21. step-12 (PR-F1) GAS C/D 일부 이식 — 알리고 sync (mock) + 운송사 reconcile + Tesseract OCR 결정 (PR-F2 의존, 2026-05-10)

PR (`feature/integrated-phase-10-step-12-gas-cd-vendor`) — PR #117 (PR-E1) + #118 (PR-E2) 머지로 GAS B 11건 native 이식 100% 완성 후, 사용자 분류 GAS C/D 6건 중 vendor 외부 의존 0 인 2건 (C 9번 알리고 자동 업로드 + D 11번 운송사 실배차 비교) 을 단일 통합 PR 로 native 이식. OCR 엔진 의존 2건 (D 10번 에어디자이너 운송장 OCR + D 14번 제이시스템 운송장 OCR) 은 PR-F2 별도 슬라이스 위임 (Tesseract 채택).

근거:
- **알리고 주소록 자동 동기화 (mock 안내)** — 실 알리고 API spec 사용자 입수 전 단계. 본 PR 시점 = `MockAligoAddressBookClient` (dryRun 응답: `added=N, http=200`) 활성, 실 RestClient 구현체 `RestClientAligoAddressBookClient` (TODO comment + skeleton 만) 선등록. 사용자 spec 입수 시점 `samhan.notification.aligo.address-book.dry-run=false` 토글 + RestClient 본문 채우면 즉시 운영 활성. CSV export 양식 (UTF-8 BOM + 헤더 4컬럼 + 비고 `[partnerCode]`) 은 알리고 콘솔 직접 import 호환 표준이라 mock 무관 검증 가능.
- **차단 거래처 자동 제외 + 휴대폰 정규화** — `BlockedPartner` 매칭 row skip + 휴대폰 prefix `010|011|016|017|018|019` 검증 + `+82-10-...` / `00821012345678` 등 8 변형 정규화. 알리고 발송 실패 (잘못된 prefix) + 차단 거래처 실수 발송 양쪽 회귀 차단. 사용자 명시 신용정보 / 전자소송 / 폐업의심 strikethrough filter 는 본 PR 시점 미적용 (status=ACTIVE 만 적용) — 향후 filter 추가 PR 시 BE-1 #5 test (`exportAligoCsv_userNotedStrikethroughFilters_areNotApplied`) 폐기 의무.
- **chunk 50 분할 + 429 backoff retry** — `AligoAddressBookSyncService` 가 chunk 50 (알리고 권장 상한) 으로 분할 + 429 응답 시 backoff 재시도 (`BACKOFF_MAX_RETRIES`) + 소진 시 failed 누적 (운영자 인지). partial fail (첫 chunk success + 둘째 chunk 500) 시 다른 chunk 결과 보장.
- **운송사 실배차 비교 (POI 다중 vendor 양식 매처)** — `VendorExcelParser` 가 4 vendor 양식 헤더 매처 (CJ대한통운 `접수일자/접수시간/업체명` + 롯데 `예약번호/발송일자/발송시간` + 한진 `송장번호/출고일/거래처명` + 2층 헤더 row0 그룹/row1 컬럼 패턴 GAS 11번 호환). 영문 양식 등 미인식 vendor 는 빈 list 반환 (예외 X) 으로 partial parse 보장 — 1개 vendor 양식 미지원이어도 다른 vendor 결과 노출 (전체 fail 회귀 차단).
- **left join TRUE / FALSE_LEFT / FALSE_RIGHT 분류** — `DispatchReconcileService` 가 우리 dispatch ↔ vendor 엑셀 양방향 mismatch 분류. FALSE_LEFT = vendor 누락 (영업 매출 손실 차단) + FALSE_RIGHT = 자체 dispatch 누락 (회계 자동 매출 분개 차단). status filter UI + CSV 다운로드 → 회계 외주 (ACCOUNTANT) 매출 마감 정합 검증 가능.
- **Tesseract OCR 채택 (PR-F2 의존)** — 사용자 결정. 에어디자이너 / 제이시스템 운송장 PDF/이미지 → 운송장번호 / 거래처명 / 일자 OCR 추출. 후보 비교: (1) Tesseract (Apache 2.0, 한국어 학습 모델 `kor.traineddata` 무료, 자체 호스팅, OCR 정확도 80~90%) — 채택, (2) Naver CLOVA OCR (월 ₩100 / 호출, vendor lock-in) — 보류, (3) Google Vision OCR (해외 cloud, 가격 변동) — 보류. PR-F2 시점 = `arologis-service` 또는 신규 `ocr-service` (8098, 미정) Tesseract 4.x JNI binding (`tess4j`) + 한국어 traineddata 동봉 (~10MB) + 후처리 정규화 (운송장번호 12자 hyphen 표준).
- **단일 통합 PR (5 commits) — 별도 docs PR 회피** — Phase A (Designer 1 + BE 2 = 3 commits) + Phase B (FE 1 + QA 1 = 2 commits) 단일 통합 PR. ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신 (memory `feedback_continuous_docs_sync` 일관). 별도 docs PR 폐기 패턴.

영향:
- `services/partner-service/src/main/java/.../partner/service/PartnerAligoExportService.java` 신규 — UTF-8 BOM CSV export + BlockedPartner skip + 휴대폰 정규화 (8 변형 + prefix 검증) + group1 fallback ("기본") + RFC 4180 escape, 단위 7 case PASS
- `services/partner-service/src/main/java/.../partner/controller/PartnerAdminController.java` 신규 — `GET /api/v1/partners/admin/aligo/csv` (MASTER 가드)
- `services/notification-service/src/main/java/.../notification/service/AligoAddressBookSyncService.java` 신규 — chunk 50 분할 + 429 backoff retry + partial fail 누적, 단위 6 case PASS
- `services/notification-service/src/main/java/.../notification/client/{AligoAddressBookClient,MockAligoAddressBookClient,AligoCsvSourceClient,NoopAligoCsvSourceClient,RestClientAligoCsvSourceClient}.java` 신규 — 알리고 client interface + mock dryRun + CsvSource interface (Noop / RestClient 분기)
- `services/notification-service/src/main/java/.../notification/controller/AligoAddressBookController.java` + `dto/AligoAddressBookSyncResponse.java` 신규 — `POST /api/v1/notify/aligo/address-book/sync` (MASTER 가드)
- `services/arologis-service/src/main/java/.../arologis/parser/{VendorExcelParser,VendorExcelRow}.java` 신규 — POI 4 vendor 헤더 매처 + 2층 헤더 패턴 + 영문 양식 빈 list partial parse, 단위 6 case PASS
- `services/arologis-service/src/main/java/.../arologis/service/DispatchReconcileService.java` 신규 — left join TRUE/FALSE_LEFT/FALSE_RIGHT + 다중 vendor + 인자 검증, 단위 9 case PASS
- `services/arologis-service/src/main/java/.../arologis/controller/DispatchReconcileController.java` + `dto/{DispatchReconcileResponse,MismatchedRow}.java` 신규 — `POST /api/v1/arologis/dispatch/reconcile` (DISPATCH/MANAGER/MASTER + multipart)
- `services/arologis-service/src/main/java/.../arologis/repository/DispatchRepository.java` 신규 — 기간 + status (`COMPLETED`) 조회
- `services/arologis-service/build.gradle` — POI 5.2.5 의존성 추가
- `clients/desktop/src/renderer/api/{aligoAddressBookApi,dispatchReconcileApi}.ts` 신규 — 2 API client (multipart 업로드 + binary CSV 다운로드)
- `clients/desktop/src/renderer/routes/admin/AligoAddressBookPage.tsx` 신규 — `/admin/aligo-address-book` (AdminLayout MASTER 가드, 거래처 미리보기 + 그룹 dropdown + "동기화 실행" + 결과 chip 4종)
- `clients/desktop/src/renderer/routes/ArologisDispatchReconcilePage.tsx` 신규 — `/arologis/dispatch-reconcile` (DISPATCH/MANAGER/MASTER, drag-drop 다중 업로드 + 시작/종료일 + 비교 실행 + status filter + CSV 다운로드)
- `clients/desktop/src/renderer/components/AdminLayout.tsx` "관리자 (MASTER 전용)" 그룹 entry 1건 신규 ("알리고 주소록 sync") + `routes/index.tsx` 라우트 2건
- `clients/desktop/src/renderer/api/mock.ts` `_resolveMockRole()` 신규 — `?mockRole=MASTER` dev-only override (capture 자동화용)
- `tools/manual-capture/capture-pr-f1.js` 신규 — Playwright headless 캡처 자동화 스크립트 (msedge channel → chromium fallback)
- `docs/qa/phase-10-step-12-gas-cd-vendor/scenarios.md` 신규 — 14 case (1.x 5 + 2.x 6 + 3.x 3 권한/UUID) + 단위 28 case 매핑 + 페르소나 5 + 회귀 위험 7건 + 후속 6건
- `docs/qa/phase-10-step-12-gas-cd-vendor/working-aligo-address-book.png` + `working-dispatch-reconcile.png` 신규 — Playwright 작동 캡처 (한국어 100% + UUID 비공개 통과)
- `ROADMAP.md` Phase 10 step-12 row 추가
- `docs/dev-reports/integration-phase-10-step-12-gas-cd-vendor.md` 신규

후속 (PR-F2 이후):
- **PR-F2** — GAS D 운송장 OCR 2건 (10번 에어디자이너 + 14번 제이시스템) — Tesseract 4.x + tess4j JNI + `kor.traineddata` 동반 + 운송장번호 / 거래처명 / 일자 추출 + 정규화 후처리. 신규 `services/ocr-service` (8098) 또는 `arologis-service` 흡수 미정 (PR-F2 진입 시점 결정).
- **알리고 실 RestClient 활성** — 사용자 알리고 API spec 입수 시점 `RestClientAligoAddressBookClient` 본문 채움 + `samhan.notification.aligo.address-book.dry-run=false` 토글 + 운영 진입 (X-API-Key + 단톡방 token).
- **운송사 vendor sample 다양화** — 본 PR 시점 = CJ대한통운 / 롯데 / 한진 / 2층 헤더 4 vendor 매처. 운영 진입 시점 추가 vendor (한진 / 우체국 / 로젠 등) 헤더 sample 입수 시 매처 keyword 확장.
- **인쇄 양식 iteration** — 운송사 reconcile 결과 CSV 외 PDF / 인쇄 양식 도입 권고 (사용자 Edge 캡처 → CSS-only 미세 조정 `feedback_print_design_iteration`).
- **동일 vendor 다중 파일 합산 정책** — `CJ_2026-05.xlsx` + `CJ_2026-06.xlsx` 동시 업로드 시 vendor 식별자 합산 vs 분리 정책 미정의 — 운영 도입 시 결정 후 case 추가.

### D-P10-22. step-13 (PR-F2) vendor 발주 OCR 이식 — 에어디자이너 + 제이시스템 (Tesseract) + 종합견적서 시트 단가 일원화 (2026-05-10)

PR (`feature/integrated-phase-10-step-13-vendor-ocr`) — PR #119 (PR-F1) 머지로 GAS C/D 6건 중 4건 native 이식 완성 후, 사용자 분류 잔여 OCR 의존 2건 (D 10번 에어디자이너 발주서 OCR + D 14번 제이시스템 발주서 OCR) 단일 통합 PR 이식. OCR 엔진 = Tesseract 4.x + tess4j 5.13 (D-P10-21 결정 재확인 + 본 PR 시점 production setup 완성). 흡수 위치 = `partner-order-service` (발주 도메인 일관성) — 신규 `services/ocr-service` 분리 보류.

근거:
- **Tesseract 흡수 위치 = `partner-order-service`** — D-P10-21 시점 미결 (`arologis-service` 또는 신규 `services/ocr-service` 8098). 본 PR-F2 진입 시점 결정 = `partner-order-service` 흡수. 사유: (1) vendor 발주서 OCR 결과는 즉시 `PartnerOrder` draft 등록으로 이어짐 — 발주 도메인 entity / repository / Controller / 권한 가드 (`SALES/MANAGER/MASTER`) 모두 동일 service 내부 호출 가능, (2) 별도 service 분리 시 OCR 결과 → PartnerOrder draft 의 transactional consistency 가 RestClient + 분산 트랜잭션 회피 패턴 (Saga / Outbox) 필요 → 운영 진입 비용 큼, (3) Tesseract 호출량 = 일 평균 vendor 발주서 ~10건 (현 사용량 기준) 으로 별도 service scaling 불요. 운영 호출량 폭증 시점 (일 100건 이상) 별도 service 분리 + 비동기 큐 도입 검토 (PR-F3 이후 backlog).
- **OcrEngine 추상화 + `@ConditionalOnProperty` 양분기** — `MockOcrEngine` (preset key 매처, dev/test/CI fallback, `samhan.ocr.engine=mock` default) ↔ `TesseractOcrEngine` (tess4j JNI binding, `samhan.ocr.engine=tesseract` 운영). Tesseract native 라이브러리 미설치 환경 (Windows dev / macOS dev / 한글 경로 JDK 17) 에서 ApplicationContext 부팅 실패 회귀 차단 의무 — `@ConditionalOnProperty(name="samhan.ocr.engine", havingValue="tesseract")` 가 미설치 시 자동 비활성 + 운영자 503 graceful 안내. PR-F1 회귀 가드 `*Bean` suffix 일관 (`mockOcrEngineBean` / `tesseractOcrEngineBean`).
- **vendor parser 분리 패턴 + 자동 detect** — `VendorOrderParser` interface + `AirDesignerOrderParser` (keyword "에어디자이너" + 라인 정규식 `^\d+\.\s*(.+)\s*\[(.+)\]\s*(\d+)개\s*([\d,]+)원`) + `JSystemOrderParser` (keyword "제이시스템" + 표 형식 row 매처). `VendorParserRegistry` 가 자동 detect (첫 5줄 keyword score) + `vendorHint` 명시 시 우회. 신규 vendor 양식 추가 시 `VendorOrderParser` interface + `register()` 만 구현하면 자동 detect 진입 — vendor 양식 다양화 운영 진입 비용 최소화.
- **단가 lookup 우선순위 = CATALOG (시트) > OCR > MANUAL** — `VendorOrderService` 가 OCR parser 결과 라인 → `ProductCatalogLookupClient` (모델 코드 lookup) → 시트 단가 (`source="CATALOG"`) → 시트 미존재 시 OCR 단가 (`source="OCR"`) → FE inline edit 시 (`source="MANUAL"`). DC 적용 = `DcConfigClient.findHomeDiscount(partnerCode)` (PR #115 산출 활용). 합계 불일치 (OCR 합계 vs 라인 합산) 시 `suggestions` 메시지 노출 — 운영자 인지 동선 확보.
- **종합견적서 시트 단가 일원화** — 본 PR 시점 `ProductCatalogLookupClient` 가 호출하는 시트 단가 source 가 운영상 다중 시트 (가정용 / 업소용 / 종합견적서) 분산 → **종합견적서 시트 단가로 일원화** 결정. 사유: 사용자 운영 표준 = 거래처 견적 / 계산서 / 발주 모두 종합견적서 단가 기준. PR-F2 시점 = client 인터페이스만 정착, 실제 시트 source 통합은 PR-G2 (예정) 또는 product-service `ProductCatalogClient` 의 시트 ID 환경변수 (`samhan.product.catalog-sheet-id`) 정정 시점 동시 진입.
- **단일 통합 PR (5 commits) — 별도 docs PR 회피** — Phase A (DevOps 1 + Designer 1 + BE 1 = 3 commits) + Phase B (FE 1 + QA 1 = 2 commits). ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신 (memory `feedback_continuous_docs_sync` 일관). 별도 docs PR 폐기 패턴.

영향:
- `services/partner-order-service/src/main/java/.../partnerorder/vendor/ocr/{OcrEngine,MockOcrEngine,TesseractOcrEngine,OcrEngineConfig,OcrProperties,OcrException}.java` 신규 — OCR engine 추상화 + Tesseract 4.x JNI binding (tess4j 5.13) + 503 graceful fallback
- `services/partner-order-service/src/main/java/.../partnerorder/vendor/parser/{VendorOrderParser,AirDesignerOrderParser,JSystemOrderParser,ParsedVendorOrder,VendorParserRegistry}.java` 신규 — vendor parser interface + 2 구현체 + 자동 detect registry
- `services/partner-order-service/src/main/java/.../partnerorder/vendor/service/VendorOrderService.java` 신규 — multipart → OCR → parser → catalog lookup → DC 적용 → response, 단위 7 case PASS
- `services/partner-order-service/src/main/java/.../partnerorder/vendor/client/{PartnerLookupClient,PartnerSummary,ProductCatalogLookupClient}.java` 신규 — RestClient + X-Internal-Token + fail-soft empty Map
- `services/partner-order-service/src/main/java/.../partnerorder/vendor/web/VendorOrderController.java` + `dto/{VendorOrderUploadResponse,VendorOrderConfirmRequest,VendorOrderConfirmResponse}.java` 신규 — `POST /api/v1/admin/partner-order/vendor/{upload,confirm}` (SALES/MANAGER/MASTER + multipart)
- `services/partner-order-service/build.gradle` — tess4j 5.13.0 의존성 추가
- `services/partner-order-service/src/main/resources/application.yml` — Tesseract 설정 4종 (`samhan.ocr.{engine,tessdata-path,languages,timeout-ms}`)
- `services/partner-order-service/src/test/java/.../it/{ApplicationContextLoadIT,VendorOrderControllerIT}.java` 신규 — Spring context 부팅 검증 + 외부 client `@MockBean` 격리 (PR-F1 회귀 가드 일관)
- `clients/desktop/src/renderer/api/vendorOrderApi.ts` 신규 — multipart 업로드 + 확정 등록 2 endpoint client
- `clients/desktop/src/renderer/routes/SalesVendorOrderUploadPage.tsx` + `.module.css` 신규 — 3-step wizard (`/sales/vendor-order/upload`, SALES/MANAGER/MASTER)
- `clients/desktop/src/renderer/components/AppLayout.tsx` "영업 (SALES)" 그룹 entry "발주서 OCR 업로드" 신규 + `routes/index.tsx` 라우트 1건
- `clients/desktop/src/renderer/api/mock.ts` vendor OCR fixture 4 preset 추가 (capture 자동화 의존)
- `tools/manual-capture/capture-pr-f2.js` 신규 — Playwright headless 캡처 자동화 (msedge → chromium fallback)
- `docs/qa/phase-10-step-13-vendor-ocr/scenarios.md` 신규 — 15 case (1.x 5 + 2.x 5 + 3.x 1 + 4.x 4) + 단위 30 case 매핑 + 페르소나 5 + 회귀 위험 + 후속 backlog
- `docs/qa/phase-10-step-13-vendor-ocr/working-vendor-order-step{1-upload,2-preview,3-confirm}.png` 신규 — Playwright 작동 캡처 3 PNG (한국어 100% + UUID 비공개 통과)
- `.github/workflows/ci.yml` — Linux runner Tesseract 설치 step 추가 (CI IT 가능)
- `docs/dev-environment/tesseract-setup.md` 신규 — Windows / macOS / Ubuntu / Docker / EC2 m5.xlarge 5 환경 설치 절차 + `kor.traineddata` 다운로드
- `.gitignore` — traineddata 대용량 binary 7건 무시
- `README.md` — Tesseract 설치 안내 link 추가
- `ROADMAP.md` Phase 10 step-13 row 추가
- `docs/dev-reports/integration-phase-10-step-13-vendor-ocr.md` 신규

후속 (PR-G1 이후):
- **PR-G1 — slip-service e-Count schema 보강 + API 제거** — 본 PR 머지 후 즉시 진입 (사용자 명시). 자체 분개 + 출고전표 자동 조회 + accounting-service native 이식 (PR #117 + #118) 완성 후 schema 정리 단계.
- **종합견적서 시트 단가 일원화 — `samhan.product.catalog-sheet-id` 환경변수 정정** — PR-G2 (예정) 또는 product-service `ProductCatalogClient` 정정 시점. 본 PR 시점 = client 인터페이스만 정착, 실제 시트 source 통합은 후속 PR 진입.
- **OCR 후처리 정규화 보강** — 운송장번호 12자 hyphen 표준 / 모델코드 대소문자 / 단가 천단위 콤마 정규화. 운영 진입 시 OCR fail rate 측정 후 보강.
- **신규 vendor 양식 추가 시 parser 등록 패턴 정착** — `VendorOrderParser` interface + `VendorParserRegistry.register()` 만 구현하면 자동 detect 진입 (등록 절차 dev-report § 4 명시).
- **`services/ocr-service` 분리 검토** — 운영 호출량 폭증 시점 (일 100건 이상) 별도 service (8098, 미정) 분리 + 비동기 큐 도입 검토.
- **인쇄 양식 — 발주서 확정 후 vendor 회신용 PDF / 인쇄 양식** — 사용자 Edge 캡처 → CSS-only 미세 조정 (`feedback_print_design_iteration`) iteration.

### D-P10-15. 사용자 강화 가드 (2026-05-08) — Phase 11 위임 0건 + 본 PR 잔존 backlog 모두 채택

W10-4 (PR #99) 종합 TM 시점 잔존 4 fix (DV-3 / DV-2 흡수 / Grafana JSON / 운영 진입 검증 plan) 모두 본 PR 채택 — Phase 11 위임 0건.

근거:
- 기존 사용자 가드 (`feedback_integrated_pr_pattern.md` § "fix 후속 PR/Phase 위임 금지", 2026-05-07) 강화 — 통합 PR 의 backlog 흩뿌리기 패턴 차단
- `shared/security` module 추출 (DV-3) 은 13 service 회귀 위험 큼 — 본 PR 의 InternalTokenFilter 신규 (slip-service) 와 동시 진입이 follow-up 분리보다 회귀 검증 비용 누적 측면 유리
- Flyway V11 CONCURRENTLY (DV-2) — V10 + V11 한 PR 동시 채택이 production cutover 시점 `executeInTransaction = false` 운영 가드 학습 비용 최소화
- Grafana JSON dashboard — Phase 11 진입 시점 즉시 사용 가능

영향:
- DV-3 — `shared/security` 신규 module + 13 service refactor (auth/user/product/inventory/slip/accounting/partner/partner-order/dc-config/dashboard/groupware/notification/arologis)
- DV-2 흡수 — `services/slip-service/src/main/resources/db/migration/V11__concurrently_signature_indexes.sql` 신규 (`-- ${flyway:executeInTransaction:false}` 명시)
- Grafana — `infrastructure/grafana/dashboards/arologis-slip-bridge.json` 신규 (4 panel + alert 1)
- dev-report § 11 — 운영 진입 검증 plan 5 case 명시 (signature_source 분류 / Grafana / Flyway lock 시뮬레이션 / SlipClient SLA / shared/security 회귀)

---

## Phase 12 결정 (실시간 협업 시리즈, 2026-05-09 ~)

### D-P12-01. 실시간 통신 = SSE (Spring `SseEmitter`) 표준 채택 + 단일 노드 in-memory broker + JWT 헤더 + `slip_comments` 신규 + Flyway V17 (PR-H1, 2026-05-10)

PR (`feature/integrated-phase-12-step-1-websocket-infra`) — PR #122 (운영 검증 인프라) 머지 후 사용자 결정 옵션 A (Phase 12 실시간 협업 시리즈, 총 ~13주) 진입. 시리즈 1/4 = SSE infra + slip 코멘트 smoke. **Samhan Public 핵심 가치 = "두 사람이 같은 전표 보고 한 명 코멘트 → 다른 사람에게 실시간 반영"** 의 최소 검증 단계.

근거:
- **실시간 통신 = SSE (Spring `SseEmitter`) 표준 채택** — 후보 비교: (A) WebSocket / STOMP, (B) SSE / `SseEmitter`, (C) 외부 SaaS (Pusher / Firebase Realtime / Ably). **B 채택**. 사유: (1) Samhan Public 통신 흐름 = 단방향 server → client push 가 99% (코멘트 broadcast / audit overlay / slip 라이프사이클 변경 / 권한 수락 알림 모두 server → client). 양방향이 필요한 시나리오 (PR-H3 권한 수락) 도 client → server 는 일반 REST POST 로 처리 가능 → WebSocket / STOMP 의 양방향 양식 비용 불요. (2) HTTP/1.1 keep-alive + 재연결 = 기존 nginx / AWS ALB / Cloudflare 인프라 그대로 사용 가능 (WebSocket upgrade 별도 라우팅 가드 불요). (3) Spring `SseEmitter` 표준 = JDK 표준 + Spring Web MVC 내장, 외부 라이브러리 의존 0. (4) 외부 SaaS 의존 0 (사용자 핵심 가치 = self-host 100%, 외부 SaaS 비용 / 데이터 주권 / 장애 의존도 회피). (5) 회귀 가드 단순 — `MockMvc` async dispatch + `SseEmitter` IT case 만으로 검증 가능 (WebSocket session manager mock 비용 회피).
- **단일 노드 in-memory `Map<UUID, CopyOnWriteArrayList<SseEmitter>>` broker** — `SlipRealtimeBroker` = `ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>>` 기반. 단일 노드 운영 가정 (현 시점 cafe24 단일 + Phase 11 AWS 단일 환경 = `project_phase11_aws.md` Seoul `m5.xlarge` 단일). 다중 노드 진입 시 PR-H4 시점 Redis Pub/Sub 분기 추가 (slip-service → Redis publish → 모든 노드 subscribe → 각 노드의 in-memory broker → SseEmitter 노드별 broadcast). 본 PR-H1 시점 = 단일 노드 in-memory 만 + 30s heartbeat (idle 연결 cleanup) + IOException / IllegalStateException 자동 cleanup.
- **JWT 헤더 인증 (`Authorization: Bearer <token>`)** — SSE 연결 시 EventSource 표준은 헤더 주입 불가 → 후보 비교: (A) 쿼리 파라미터 `?token=...`, (B) fetch+ReadableStream polyfill 로 헤더 주입, (C) 쿠키 기반 세션. **B 채택**. 사유: (1) 쿠키 세션은 JWT stateless 패턴 회귀, (2) 쿼리 파라미터는 access log / 캐시 / 브라우저 history 노출 위험. desktop = `fetch + ReadableStream polyfill` 로 헤더 주입, mobile-staff = `react-native-sse@^1.2.1` 가 헤더 주입 표준 지원. gateway `HeaderAuthenticationFilter` 패턴 일관 (SSE 라우트도 동일 인증 흐름).
- **`slip_comments` 신규 entity + Flyway V17** — slip 라이프사이클 10단계와 분리된 자유 코멘트 도메인. BaseEntity 7 audit 의무 + Soft Delete (`is_deleted = false` `@SQLRestriction`) + `slip_id` FK + `author_user_id` FK + `body TEXT` + 부분 인덱스 (`WHERE is_deleted = false ORDER BY created_at DESC`). 단위 9 case (Service 5 + Broker 4) + IT 5 case (SSE subscribe / POST 201 / GET 200 / broker cleanup / 403 권한 거부) PASS.
- **gateway `httpclient.response-timeout: 600s` (SSE keep-alive)** — Spring Cloud Gateway default 60s 가 SSE 장기 연결을 끊음. 600s (10분) 로 확장 + slip-service `samhan.realtime.heartbeat-seconds=30` (default) 로 30초마다 heartbeat event 발송 → 연결 keep + idle cleanup. nginx production 시점 = `proxy_read_timeout 600s` + `proxy_buffering off` + `gzip off` (운영 hint `docs/devops/realtime-sse-production.md` 명시).
- **Designer `userIdToColor` HSL deterministic hash util 시드** — PR-H2 (slip audit overlay + 실시간 sync) 진입 시 사용자별 색상 표시 의존. `clients/web/design-system/src/utils/userColorHash.ts` 신규 + Storybook 1 story (5 userId 색상 swatch + Determinism 검증). 본 PR-H1 시점 = util 만 시드, 실제 audit overlay UI = PR-H2 진입.
- **multi-context Playwright 작동 캡처 4 PNG (사용자 핵심 가치 시각 증거)** — `tools/manual-capture/capture-pr-h1.js` = `browser.newContext` 2회로 사용자 A / B 분리 + `addInitScript` 으로 mock comments seed 사전 주입 + `sharp` 로 좌-우 합성 (1280+1280=2560) + 한국어 라벨 헤더 60px. 4 PNG = (1) `working-comment-context-a-input.png` (사용자 A MASTER 영업 코멘트 입력 직전), (2) `working-comment-context-a-after-send.png` (사용자 A 전송 직후 optimistic 표시), (3) `working-comment-context-b-receives.png` (사용자 B SALES 창고 SSE 시뮬레이션 수신), (4) `working-multi-context-split.png` (좌-A 우-B 한 화면 합성). PR body inline raw URL + commit-pinned + HEAD 200 검증 의무 (memory `feedback_pr_qa_screenshots`).
- **단일 통합 PR (6 commits) — 별도 docs PR 회피** — Phase A (DevOps 1 + BE 1 + FE-1 desktop + FE-2 mobile-staff + Designer = 5 commits) + Phase B (QA 1 = 1 commit). ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신 (memory `feedback_continuous_docs_sync` 일관). 별도 docs PR 폐기 패턴.

영향:
- `services/slip-service/src/main/java/.../slip/realtime/{SlipRealtimeBroker,SlipRealtimeController}.java` 신규 — `SseEmitter` 표준 + in-memory broker + 30s heartbeat + IOException/IllegalStateException cleanup
- `services/slip-service/src/main/java/.../slip/comment/{domain/SlipComment,repository/SlipCommentRepository,service/SlipCommentService,web/SlipCommentController,web/dto/{AddSlipCommentRequest,SlipCommentResponse}}.java` 신규 — slip_comments 도메인 (BaseEntity 7 audit + Soft Delete + ROLE 가드 + ApiResponse wrapper)
- `services/slip-service/src/main/resources/db/migration/V17__add_slip_comments.sql` 신규 — `slip_comments` 신규 + 부분 인덱스 + BaseEntity 7 audit
- `services/slip-service/src/main/resources/application.yml` — `samhan.realtime.heartbeat-seconds` property + `@EnableScheduling` 활성
- `services/slip-service/src/test/java/.../slip/{comment/it/SlipRealtimeControllerIT,comment/service/SlipCommentServiceTest,realtime/SlipRealtimeBrokerTest,it/ApplicationContextLoadIT}.java` 신규 / 보강 — 단위 9 + IT 5 case PASS
- `services/api-gateway/src/main/resources/application.yml` — `httpclient.response-timeout: 600s` (SSE keep-alive)
- `infrastructure/env-templates/{api-gateway,slip-service}.env` — `SAMHAN_REALTIME_HEARTBEAT_SECONDS=30` + gateway response-timeout 600s
- `clients/desktop/src/renderer/realtime/SlipRealtimeClient.ts` 신규 — fetch+ReadableStream polyfill (JWT header + 5s reconnect backoff)
- `clients/desktop/src/renderer/api/slipComment.ts` 신규 — list + add 2 endpoint client
- `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` 보강 — 코멘트 Card (useQuery + useEffect SSE + optimistic add, data-testid 4종)
- `clients/desktop/src/renderer/api/mock.ts` 보강 — POST/GET `/comments` mock (`globalThis.__SAMHAN_MOCK_COMMENTS_SEED` 으로 capture 시점 seed 주입)
- `clients/mobile-staff/package.json` — `react-native-sse@^1.2.1` 의존 추가 (RN EventSource polyfill)
- `clients/mobile-staff/src/realtime/SlipRealtimeClient.ts` 신규 — `subscribeToSlip` + heartbeat watchdog 60s
- `clients/mobile-staff/src/api/slipComment.ts` 신규 — list/create/delete + ApiResponse wrapper assert
- `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` 신규 — slip 정보 + 코멘트 list/입력/전송 + SSE invalidate
- `clients/mobile-staff/src/screens/driver/{DriverDashboardScreen,DriverTabNavigator}.tsx` 보강 — slip card 에서 "전표 보기 / 코멘트" 진입 link + minimal stack push
- `clients/web/design-system/src/utils/{userColorHash.ts,userColorHash.stories.tsx}` 신규 — HSL deterministic hash util + Storybook 1 story (PR-H2 audit overlay 의존 시드)
- `clients/web/design-system/src/{index.ts,utils/index.ts}` — barrel export 보강
- `docs/devops/realtime-sse-production.md` 신규 — nginx config + AWS ALB / cafe24 운영 hint
- `docs/uiux/phase12/H1-comment-smoke.md` 신규 — wireframe + 한국어 라벨
- `docs/qa/phase-12-step-1-websocket-infra/scenarios.md` 신규 — 14 case (subscribe + broadcast 5 + 다중 client 5 + API contract 4) + 페르소나 5
- `docs/qa/phase-12-step-1-websocket-infra/working-{comment-context-a-input,comment-context-a-after-send,comment-context-b-receives,multi-context-split}.png` 신규 — multi-context Playwright 작동 캡처 4 PNG
- `tools/manual-capture/capture-pr-h1.js` 신규 — Playwright headless 자동화 (msedge → chromium fallback, browser.newContext 2회 분리, sharp 좌-우 합성)
- `ROADMAP.md` Phase 12 row + Phase 12 section 신규
- `docs/dev-reports/integration-phase-12-step-1-websocket-infra.md` 신규

후속 (PR-H1 머지 후):
- **PR-H2 (~3주) — slip audit overlay + 실시간 sync** — slip 라이프사이클 10단계 변경 시 모든 접속 client 에게 SSE broadcast (DRAFT→SAVED→DISPATCHED→...→COMPLETED) + 사용자별 색상 audit overlay (userColorHash 활용) + 변경 이력 timeline UI. 본 PR-H1 머지 후 즉시 진입.
- **PR-H3 (~1.5주) — 권한 / 수락 / 거절 워크플로우** — 영업 → 창고 → 기사 인계 시점 명시적 수락 + SSE 양방향 push.
- **PR-H4 (~7주) — 전 15 service 확장** — partner / inventory / accounting / arologis / dashboard 등 14 backend MSA 도메인 모두 SSE 채널 도입 + `shared/realtime` module 추출 + Redis Pub/Sub 분기 (다중 노드 진입 시 활성).

---

### D-P12-02. slip audit overlay (Flyway V18) + 실시간 sync (`slip:edit` SSE event) + TM 보완 3건 흡수 (multi-emitter 동시성 IT + ArgumentCaptor SSE payload + `RedisRealtimeBroker` config toggle) (PR-H2, 2026-05-10)

PR (`feature/integrated-phase-12-step-2-slip-audit-overlay`) — PR #123 (PR-H1 SSE infra + slip 코멘트 smoke) 머지 후 Phase 12 시리즈 2/4 진입. **사용자 핵심 요구 = "두 사람이 같은 전표 보면서 한 명이 메모를 수정하면 다른 사람 화면에 1초 안에 취소선 + 수정자 색상 + 수정자 이름 + 수정 시각 으로 audit overlay 가 표시"** 의 4 요소 시각 검증 단계. PR-H1 시드 `userIdToColor` HSL hash util 활용 + audit overlay 컴포넌트 도입 + TM 보완 3건 흡수 (사용자 명시 = "multi-emitter 동시성 / ArgumentCaptor SSE payload / Redis broker config toggle").

근거:
- **Flyway V18 (`slip_audit_logs` + `slips.revision_count`) 신규** — slip 본문 필드 변경 (memo / shippingAddress / contactPhone / partnerName / discountRate 등 11 필드 시범) 마다 1 row 누적 + revisionNo 그룹핑. BaseEntity 7 audit (id / created_at / created_by_user_id / updated_at / updated_by_user_id / is_deleted / version) + Soft Delete (`@SQLRestriction("is_deleted = false")`) + 부분 인덱스 (`WHERE is_deleted = false ORDER BY revision_no DESC`). `slips.revision_count BIGINT NOT NULL DEFAULT 0` 누적 카운터 → desktop / mobile-staff 수정 횟수 chip / 헤더 표시 의존.
- **`SlipAuditLogService` 4 책임 (record / recordBatch / listBySlip / revertToRevision)** — `recordOverlayPatch(slipId, fieldName, oldValue, newValue, actor)` 단일 필드 + `recordBatch(slipId, changes[], actor)` 다중 필드 (1 revision = N field rows) + `listBySlip(slipId, limit)` 최신순 + `revertToRevision(slipId, revisionNo, actor)` 신규 revision 으로 audit 영원 보존 (덮어쓰기 금지). actor = `{actorId, actorName, actorColor (userIdToColor hash)}` snapshot (UUID 비공개 가드 — 화면 노출은 actorName + actorColor 만).
- **`Slip.applyOverlayPatch/readOverlayField/incrementRevision` 11 필드 시범** — 도메인 entity 에 자체 reflection-free `switch` 패턴 (memo / shippingAddress / contactPhone / partnerName / discountRate 등). `applyOverlayPatch(name, value)` 마감 lock 가드 (`SlipService.applyOverlayPatch` wrapper) + `readOverlayField(name)` audit oldValue snapshot. 11 필드 시범 → PR-H3 / PR-H4 시점 전 60+ 필드 확장 plan.
- **신규 endpoint 3 (`GET /audit-logs` / `PATCH /audit/overlay` / `POST /audit/revert/{n}`)** — (1) `GET /slips/{id}/audit-logs` 인증 사용자 전체 (도메인 권한 0, 이력 조회 자유), (2) `PATCH /slips/{id}/audit/overlay` SALES / WAREHOUSE / MANAGER / MASTER (DRIVER 차단), (3) `POST /slips/{id}/audit/revert/{revisionNo}` MANAGER / MASTER 만 (영업 / 창고 / 기사 차단). ApiResponse wrapper 의무 (PR #98 D-P10-12 일관) + ROLE 풀네임 가드 (memory `feedback_role_naming_full`).
- **`SlipService.editHeader` memo diff → `SlipAuditLogService.recordBatch` + SSE `slip:edit` broadcast** — 기존 editHeader 호출 시 memo 변경 감지 → audit row 1 (혹은 다중) + `SlipRealtimeBroker.publish(slipId, "slip:edit", {revisionNo, actorId, actorName, actorColor, changes[]})` payload 5 키 일치 (ArgumentCaptor 검증 의무). 사용자 핵심 요구 "1초 안 sync" 측정 — multi-context Playwright `working-multi-context-edit-split.png` 시각 증거 1 PNG.
- **design-system `AuditOverlay` 컴포넌트 (취소선 + 색상 dot + 수정자명 + 시각) + Storybook 4 story** — `clients/web/design-system/src/components/AuditOverlay/AuditOverlay.tsx` 신규. props = `{currentValue, history[]}` history 항목 = `{revisionNo, oldValue, newValue, actorName, actorColor, occurredAt}`. CSS `text-decoration: line-through` (oldValue) + `<span class="dot" style="background:${actorColor}">` (사용자 색상) + 수정자명 (actorName) + 시각 (relative). Storybook 4 story = Single / Multiple / Empty / MultiUserShowcase. desktop = 직접 import, mobile-staff = RN 1:1 복제 (`clients/mobile-staff/src/components/AuditOverlay.tsx`, RN Text strikethrough + View dot).
- **TM 보완 #1 — `SlipRealtimeBrokerConcurrencyIT` (multi-emitter 동시성 3 case, 사용자 명시)** — broker `Map<UUID, CopyOnWriteArrayList<SseEmitter>>` race condition 회귀 가드: (1) 50 emitter 동시 subscribe → broker subscriber count = 50 정확, (2) cleanup race — 동시 publish + emitter close → no exception + count 정정, (3) 100 emitter / 1000 publish → 전체 emitter 1000 receive (lost 0). `CountDownLatch` + `Executors.newFixedThreadPool(N)` 패턴.
- **TM 보완 #2 — `SlipAuditPayloadCaptorTest` (ArgumentCaptor SSE payload schema 3 case, 사용자 명시)** — `SlipRealtimeBroker.publish` 호출 시 payload 구조 정합 검증: (1) `slip:edit` event = `{revisionNo, actorId, actorName, actorColor, changes[]}` 5 키 일치, (2) `slip:reverted` event = revert 시 신규 revision payload 동일 구조, (3) `changes[]` 다중 필드 = `[{fieldName, oldValue, newValue}, ...]` schema. Mockito `ArgumentCaptor<Map<String, Object>>` + JSON schema assert.
- **TM 보완 #3 — `RedisRealtimeBroker` + `RedisRealtimeConfigBean` + `RealtimePublishHook` (config toggle, 사용자 명시)** — `SAMHAN_REALTIME_BROKER` 환경변수 / `samhan.realtime.broker` property = `in-memory|redis` toggle. **default = `in-memory`** (단일 노드 cafe24 / Phase 11 AWS 단일 환경 일관) + `redis` 옵션 시 `RedisRealtimeBroker` 활성 (Lettuce Pub/Sub publisher / subscriber + 노드별 in-memory broker 로 fanout). `RedisRealtimeConfigBean` (`*Bean` suffix 가드 PR #119 회귀 가드 일관) — Redis 미연결 시 startup 정상 (graceful fallback). PR-H4 시점 다중 노드 진입 시 toggle 만으로 활성 (D-P12-01 시점 plan 한 분기 시드).
- **단위 24 + IT 9 case + multi-context Playwright 작동 캡처 4 PNG** — 단위 = `SlipAuditLogServiceTest` 6 + `SlipAuditLogServiceRevertTest` 4 + `SlipAuditPayloadCaptorTest` 3 + `SlipServiceAuditDiffTest` 5 + `RedisRealtimeBrokerTest` 3 + `Slip` overlay patch 단위 3. IT = `SlipRealtimeBrokerConcurrencyIT` 3 + `SlipAuditPayloadCaptorTest` SSE schema 3 (단위/IT 양쪽 카운트) + `ApplicationContextLoadIT` `SlipAuditLogService` 단일 등록 가드 + 기존 PR-H1 IT 5 회귀 PASS. 작동 캡처 = `working-audit-overlay-context-a-edit.png` (97KB) / `working-audit-overlay-context-b-receives.png` (90KB) / `working-audit-overlay-multi-revision.png` (102KB) / `working-multi-context-edit-split.png` (120KB, 핵심 시각 증거 = 좌-A 우-B 합성). PR body inline raw URL + commit-pinned + HEAD 200 검증 의무 (memory `feedback_pr_qa_screenshots`).
- **단일 통합 PR (5 commits) — 별도 docs PR 회피** — Phase A (DevOps 1 + BE 1 + FE-1 desktop+design-system 1 + FE-2 mobile-staff 1 = 4 commits) + Phase B (QA 1 = 1 commit). ROADMAP / DECISIONS / dev-report 본 PR 동시 갱신 (memory `feedback_continuous_docs_sync` 일관). 별도 docs PR 폐기 패턴 일관.

영향:
- `services/slip-service/src/main/java/.../slip/audit/{domain/SlipAuditLog,repository/SlipAuditLogRepository,service/SlipAuditLogService,web/SlipAuditLogController,web/dto/{OverlayPatchRequest,SlipAuditLogResponse}}.java` 신규 — audit overlay 도메인 (BaseEntity 7 audit + Soft Delete + ApiResponse wrapper + ROLE 풀네임 가드)
- `services/slip-service/src/main/java/.../slip/domain/Slip.java` — `applyOverlayPatch` / `readOverlayField` / `incrementRevision` 11 필드 시범 (memo / shippingAddress / contactPhone / partnerName / discountRate 등)
- `services/slip-service/src/main/java/.../slip/service/SlipService.java` — `applyOverlayPatch` wrapper (마감 lock 가드) + `editHeader` memo diff → `recordBatch` + SSE `slip:edit` broadcast
- `services/slip-service/src/main/java/.../slip/realtime/{RedisRealtimeBroker,RedisRealtimeConfigBean,RealtimePublishHook}.java` 신규 — Redis Pub/Sub config toggle (`SAMHAN_REALTIME_BROKER=in-memory|redis`, default in-memory, 미연결 startup 정상, `*Bean` suffix 가드)
- `services/slip-service/src/main/java/.../slip/realtime/SlipRealtimeBroker.java` — publishCount / publishFailureCount / heartbeatCount 통계 보강 (TM 보완 IT 의존)
- `services/slip-service/src/main/resources/db/migration/V18__add_slip_audit_logs.sql` 신규 — `slip_audit_logs` 신규 + `slips.revision_count BIGINT NOT NULL DEFAULT 0` + 부분 인덱스 + BaseEntity 7 audit
- `services/slip-service/src/main/resources/application.yml` — `samhan.realtime.broker` config toggle + `spring.data.redis` host/port
- `services/slip-service/build.gradle` — `spring-boot-starter-data-redis` 의존 추가 (config toggle redis 옵션 지원)
- `services/slip-service/src/test/java/.../slip/audit/service/{SlipAuditLogServiceTest,SlipAuditLogServiceRevertTest,SlipAuditPayloadCaptorTest}.java` 신규 — 단위 6+4+3 = 13 case
- `services/slip-service/src/test/java/.../slip/service/{SlipServiceAuditDiffTest,SlipServiceTest}.java` — memo diff 5 case + 회귀 3 case
- `services/slip-service/src/test/java/.../slip/realtime/{SlipRealtimeBrokerConcurrencyIT,RedisRealtimeBrokerTest}.java` 신규 — IT 3 + 단위 3 case
- `services/slip-service/src/test/java/.../slip/it/ApplicationContextLoadIT.java` — `SlipAuditLogService` 단일 등록 가드 보강
- `infrastructure/env-templates/slip-service.env` — `SAMHAN_REALTIME_BROKER=in-memory` (default) + `REDIS_HOST` / `REDIS_PORT` placeholder
- `clients/web/design-system/src/components/AuditOverlay/{AuditOverlay.tsx,AuditOverlay.module.css,AuditOverlay.stories.tsx,index.ts}` 신규 — 취소선 + 색상 dot + 수정자명 + 시각 + Storybook 4 story (Single / Multiple / Empty / MultiUserShowcase) + barrel export 보강
- `clients/web/design-system/src/index.ts` — AuditOverlay barrel export
- `clients/desktop/src/renderer/api/slipAudit.ts` 신규 — `listAuditLogs` + `revertToRevision`
- `clients/desktop/src/renderer/routes/SlipDetailPage.tsx` 보강 — `auditLogsQuery` + 수정 횟수 chip (`slip-detail-revision-count`) + AuditOverlay 적용 (memo / shippingAddress) + 복원 dropdown (`slip-detail-revert-select`) + SSE `slip:edit` cache invalidate
- `clients/desktop/src/renderer/api/mock.ts` — audit-logs / overlay PATCH / revert mock endpoint (capture 자동화 의존)
- `clients/mobile-staff/src/utils/userColorHash.ts` 신규 — design-system 1:1 RN 호환 복제
- `clients/mobile-staff/src/components/AuditOverlay.tsx` 신규 — RN Text 취소선 + View dot 색상 + 수정자명/role
- `clients/mobile-staff/src/screens/SlipDetailScreen.tsx` 보강 — 수정 횟수 헤더 + AuditOverlay 적용 (partnerName / status) + 복원 버튼 MASTER/MANAGER 만
- `clients/mobile-staff/src/api/slipAudit.ts` 신규 — list + revert + ApiResponse wrapper assert
- `clients/mobile-staff/src/realtime/SlipRealtimeClient.ts` — `slip.edit` event type 추가
- `clients/mobile-staff/src/screens/driver/DriverTabNavigator.tsx` — `currentUserRole='DRIVER'` 명시 (복원 버튼 비표시 검증 의존)
- `docs/devops/redis-realtime-broker.md` 신규 — in-memory vs Redis 가이드 + AWS ElastiCache cache.t3.micro ~₩30K/월 + cutover 절차 + Testcontainers Redis 권고
- `docs/uiux/phase12/H2-audit-overlay.md` 신규 — wireframe + 한국어 라벨 + Designer 매뉴얼
- `docs/manual/05-슬립공유-수정-처리.md` 신규 — 사용자 시나리오 (페르소나 5) + 권한 + 화면 캡처 stub
- `docs/qa/phase-12-step-2-slip-audit-overlay/scenarios.md` 신규 — 27 case (audit_log 자동 기록 5 + AuditOverlay UI 5 + 수정 횟수 카운트 3 + 복원 4 + 실시간 sync 5 + 동시 수정 충돌 3 + Redis broker fallback 2) + 페르소나 5
- `docs/qa/phase-12-step-2-slip-audit-overlay/working-{audit-overlay-context-a-edit,audit-overlay-context-b-receives,audit-overlay-multi-revision,multi-context-edit-split}.png` 신규 — multi-context Playwright 작동 캡처 4 PNG (취소선 + 색상 + 수정자명 + 1초 sync 4 요소 시각 증거)
- `tools/manual-capture/capture-pr-h2.js` 신규 — Playwright multi-context 자동화 (browser.newContext 2회 분리 + sharp 좌-우 합성 + 한국어 라벨)
- `ROADMAP.md` Phase 12 row + Phase 12 section + PR 매트릭스 갱신
- `docs/dev-reports/integration-phase-12-step-2-slip-audit-overlay.md` 신규

후속 (PR-H2 머지 후):
- **PR-H3 (~1.5주) — 권한 / 수락 / 거절 워크플로우** — 영업 → 창고 → 기사 인계 시점 명시적 수락 + SSE 양방향 push (영업 입력 시 창고 알림 / 창고 수락 시 영업 알림 / 기사 수락 시 양측 알림). 본 PR-H2 머지 후 즉시 진입.
- **PR-H4 (~7주) — 전 15 service 확장 + Redis Pub/Sub 활성** — partner / inventory / accounting / arologis / dashboard 등 14 backend MSA 도메인 모두 SSE 채널 도입 + `shared/realtime` module 추출 + 본 PR-H2 시드된 `RedisRealtimeBroker` config toggle 활성 (다중 노드 진입 시).
