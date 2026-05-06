# Phase 8 3차 — 마무리 + Phase 9 진입 plan

본 보고서는 Phase 8 (AWS 호환성 가드) 3차 작업 결과를 정리한다.

- 1차 = AWS 호환성 가드 plan (PR #88) — docs only
- 2차 = ServiceDiscoveryClient + 환경변수 통일 + Secrets spec (PR #89)
- **3차 = 본 PR — AWS 마이그레이션 dry-run + 회고 + Phase 9 진입 plan**

---

## 1. Phase 8 마무리 산출물 (본 PR)

### 1-1. 신규 docs

| 파일 | 내용 |
|---|---|
| `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` | Phase 10 cutover 전 dry-run plan (14 section) |
| `docs/migration/phase9/M-PHASE-9-readiness.md` | Phase 9 진입 plan + 4 service skeleton + 5주 roadmap |
| `docs/dev-reports/phase8-retrospective.md` | Phase 8 회고 보고서 |
| `docs/dev-reports/phase8-step-3-completion-phase-9-readiness.md` | 본 보고서 |

### 1-2. 갱신 docs

| 파일 | 내용 |
|---|---|
| `ROADMAP.md` | Phase 8 = "진입 준비" → "완료" / Phase 9 = "잔여 도메인" → "진입 준비 완료" / Phase 10 = dry-run plan 위치 명시 |
| `migration/decisions/DECISIONS.md` | Phase 8 3차 결정 (D-P8-10 / D-P8-11) + Phase 9 진입 결정 (D-P9-01 / D-P9-02) |

### 1-3. 코드 변경 0 (docs only)

본 PR 시점 = 코드 변경 0 file. Phase 8 코드 산출물은 PR #89 시점에 모두 머지 완료
(`shared/discovery-abstraction/` 모듈 + 10 yml chained-default + 12 env-template).

---

## 2. AWS 마이그레이션 dry-run plan 요약

`docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md` 의 14 section 구성:

| Section | 주제 |
|---|---|
| 1 | 개요 — Phase 10 cutover 전 dry-run 의무 |
| 2 | RDS Postgres 호환 dry-run (Flyway V1~V8 staging RDS 적용) |
| 3 | S3 SDK endpoint override dry-run (MinIO → S3 cutover) |
| 4 | Eureka cluster 자체 EC2 운영 (Phase 8 2차 결정 — wrapper 불필요) |
| 5 | ALB / WAF design (api-gateway 8080 → ALB 443 + Target Group) |
| 6 | CloudWatch alert 매트릭스 (5xx > 1% / 응답 > 500ms / RDS CPU > 80% / DB connection > 80% / Disk > 85%) |
| 7 | Route 53 DNS cutover 절차 (`*.samhan-air.com` 8 subdomain) |
| 8 | Secrets Manager rotation 활성 (Phase 8 2차 spec → 실 lambda 배포) |
| 9 | ServiceDiscoveryClient AWS Cloud Map impl 활성 (Phase 8 2차 placeholder → 실 구현) |
| 10 | 14 service production 부트스트랩 순서 (Eureka → DB → backing → service) |
| 11 | 점진 cutover (blue-green / canary 10→50→100%) |
| 12 | roll-back 절차 |
| 13 | dry-run 시나리오 (3단계 — staging dry-run → canary 10% → full cutover) |
| 14 | timeline (Phase 10 5주 timeline) |

### 2-1. 핵심 결정

- Eureka 자체 EC2 운영 (multi-AZ 2 노드) → AWS Cloud Map wrapper 활성 보류 (placeholder 그대로)
- canary 10% → 50% → 100% 점진 cutover + DNS TTL 60s 사전 단축
- roll-back 트리거 = 5xx > 5% (10분) 또는 p99 > 1s

---

## 3. Phase 8 회고 요약

`docs/dev-reports/phase8-retrospective.md` 핵심:

### 3-1. Phase 8 머지된 PR (3건)

| PR | 차수 | 산출물 |
|---|---|---|
| #88 | 1차 | docs only (5 file), 12-factor 12/12 OK, RDS 호환 22 file 검증 |
| #89 | 2차 | shared:discovery-abstraction 신규 + 10 yml chained-default + Secrets spec |
| 본 PR | 3차 | docs only (4 file), Phase 8 마무리 + Phase 9 진입 가능 선언 |

### 3-2. 학습 사항 4종

- vendor 추상화 layer 신규 + impl placeholder 패턴 (`UnsupportedOperationException("Phase 10 cutover 시점 구현")`)
- chained-default 환경변수 (`${SAMHAN_NEW:${LEGACY:default}}`) 무중단 호환
- GitGuardian 회고 — 신규 env template `CHANGE_ME_LOCAL_ONLY` placeholder 의무
- Eureka 자체 EC2 운영 = AWS Cloud Map wrapper 회피 결정

### 3-3. 미결 (Phase 10 위임 9건 / Phase 9 위임 5건)

- Phase 10: BE 2 (DiscoveryConfiguration / EurekaClient null-safety) + QA 3 (chained-default WARN / Cloud Map IT / Secrets rotation rollback) + DevOps 4 (graceful shutdown / RDS·EC2·S3·Route53 / Secrets Manager / WAF·Prometheus·Grafana)
- Phase 9: FE 3 (frontend prefix 표준 / `.env.example` / mobile·desktop AWS 매핑) + BE 2 (4 service skeleton / 신규 service ServiceDiscoveryClient 도입)

---

## 4. Phase 9 진입 plan 요약

`docs/migration/phase9/M-PHASE-9-readiness.md` 핵심:

### 4-1. 4 신규 service

| Service | 포트 | DB | 핵심 |
|---|---|---|---|
| partner-service | 8095 | partner_db | 거래처 마스터 + 신용한도 + 거래내역 |
| groupware-service | 8092 | groupware_db | 결재선 + 메신저 + 일정 |
| notification-service | 8093 | notification_db | 푸시/이메일/SMS 통합 라우터 |
| dashboard-service | 8094 | dashboard_db | KPI + 실시간 재고 + 매출 |

### 4-2. 5주 roadmap

- W1: partner-service skeleton
- W2: groupware-service skeleton (결재선 + 메신저)
- W3: notification-service skeleton (push/email/sms adapter)
- W4: dashboard-service skeleton (실시간 KPI + materialized view)
- W5: Phase 9 회고 + Phase 10 진입 plan

### 4-3. 각 service 신규 시 가드

- BaseEntity 7 audit + Soft Delete only
- IT mockbean 외부 client 격리
- 한국어 commit + Javadoc + dev-reports
- ServiceDiscoveryClient 도입 (Phase 10 활성 대비)
- 환경변수 표준 + env-template 의무

---

## 5. Phase 10 dry-run plan 위치

- `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`
- 14 section + 5주 timeline
- Phase 9 4 service 완료 + AWS account 발급 + IAM baseline 후 진입 가능

---

## 6. ROADMAP / DECISIONS 갱신 요약

### 6-1. ROADMAP

- Phase 8 상태 = "진입 준비" → "완료 (PR #88 #89 본 PR)"
- Phase 9 상태 = "대기" → "진입 준비 완료"
- Phase 10 = "AWS 마이그레이션 + Migration Service + 운영 안정화 (AWS cutover 본격)" — dry-run plan 위치 명시
- 디렉토리 매트릭스에 `shared/discovery-abstraction` 추가
- 머지 PR 매트릭스에 #88 / #89 / 본 PR 추가

### 6-2. DECISIONS Phase 8 3차 결정

- D-P8-10: Phase 8 3차 = AWS 마이그레이션 dry-run + 회고 + Phase 9 진입 plan
- D-P8-11: AWS 마이그레이션 dry-run 위치 = `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`
- D-P9-01: Phase 9 4 신규 service 포트 확정 (partner=8095 / groupware=8092 / notification=8093 / dashboard=8094)
- D-P9-02: Phase 9 진입 = Phase 8 완료 + 호환성 가드 검증

---

## 7. 참조

- Phase 8 회고: `docs/dev-reports/phase8-retrospective.md`
- Phase 9 진입 plan: `docs/migration/phase9/M-PHASE-9-readiness.md`
- Phase 10 dry-run plan: `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`
- Phase 8 1차 dev report: `docs/dev-reports/phase8-step-1-aws-readiness.md`
- Phase 8 2차 dev report: `docs/dev-reports/phase8-step-2-discovery-secrets.md`
- 누적 결정: `migration/decisions/DECISIONS.md`
- ROADMAP: `ROADMAP.md`
