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
