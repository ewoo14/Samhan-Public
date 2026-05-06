# M-PHASE-9-readiness — Phase 8 → Phase 9 전환 준비 plan

본 문서는 Phase 9 (잔여 도메인 — partner / groupware / notification / dashboard) 진입을 위한 전제 조건, 작업 분해, 5주 roadmap, 가드를 정리한다.

---

## 1. 진입 조건

| 전제 조건 | 산출 | 상태 |
|---|---|---|
| Phase 8 완료 (PR #88 #89 #본 PR 머지) | Phase 8 회고 보고서 + DECISIONS Phase 8 항목 | 본 PR 머지 시 충족 |
| 호환성 가드 검증 완료 (AWS 마이그레이션 가능성 보존) | 12-factor 12/12 OK + 22 file Flyway RDS 호환 | PR #88 충족 |
| 14 service 환경변수 통일 (chained-default) | 10 yml + 12 env-template + Java 코드 변경 0 | PR #89 충족 |
| ServiceDiscoveryClient wrapper 보유 (Phase 10 활성 대기) | shared:discovery-abstraction 신규 모듈 + 단위 테스트 13 case | PR #89 충족 |

---

## 2. Phase 9 작업 분해 (4 신규 service)

### 2-1. service 매트릭스

| Service | 포트 | DB | 핵심 |
|---|---|---|---|
| partner-service | 8095 | partner_db | 거래처 마스터 + 신용한도 + 거래내역 (M5 의 partnerCode → partnerId lookup 의존성 해소) |
| groupware-service | 8092 | groupware_db | 결재선 + 메신저 + 일정 |
| notification-service | 8093 | notification_db | 푸시/이메일/SMS 통합 라우터 |
| dashboard-service | 8094 | dashboard_db | KPI + 실시간 재고 + 매출 |

### 2-2. 기존 14 service 포트 cross-check

- 8080 api-gateway / 8081 auth / 8082 logging / 8083 user / 8084 product / 8085 inventory
- 8086 slip / 8087 accounting / 8088 partner-order / 8089 dc-config / 8091 partner-auth / 8761 eureka
- Phase 9 신규: **8092 groupware / 8093 notification / 8094 dashboard / 8095 partner**
- Phase 10 신규: 8096 migration-service

### 2-3. partner-service 의존성 해소 효과

- 현재 M5 slip-service `/from-*` endpoint 가 partnerCode 를 lookup 의존
- partner-service 도입 후 partnerCode → partnerId 정규화
- partner-order-service (8088) 와 별개 도메인 (master vs 주문)

---

## 3. 5주 roadmap

| 주차 | 산출물 | 상태 |
|---|---|---|
| W1 | partner-service skeleton + 환경변수 + IT | **완료 (PR #91 — 2 entity + 2 controller + 2 service + 4 dto + 4 config + 1 exception handler + IT 2 + 단위 테스트 1)** |
| W2 | groupware-service skeleton (결재선 도메인 모델 + 메신저 entity) | **완료 (본 PR — 5 entity + 2 controller + 3 service + 9 dto + 5 config + 1 exception handler + 1 client (UserClient) + IT 2 + 단위 테스트 3)** |
| W3 | notification-service skeleton (push/email/sms adapter) | **완료 (본 PR — 2 entity + 3 enum + 3 channel adapter (인터페이스+운영+mock) + 2 controller + 1 service + N dto + N config + UserClient bulk verify (BE backlog #4 채택) + IT 2 + 단위 테스트 3)** |
| W4 | dashboard-service skeleton (실시간 KPI 집계 + materialized view) | 예정 |
| W5 | Phase 9 회고 + Phase 10 진입 plan | 예정 |

### 3-1. W1 — partner-service

- 도메인: Partner (거래처 마스터) / CreditLimit (신용한도) / TransactionLedger (거래내역)
- Flyway V1: partner + credit_limit + transaction_ledger 테이블 (BaseEntity 7 audit + Soft Delete)
- API: CRUD + lookup by-code (사용자 노출 식별자)
- **Backend IT** (`services/partner-service/src/test/java/.../PartnerServiceIT.java`): Internal API token guard + Eureka 등록 + by-code lookup PASS + 외부 client `@MockBean` 격리 (현재 직접 의존 없음 — self-contained)
- **e2e 위치 (Playwright)**: `qa/playwright/tests/partner/` — master CRUD / lookup-by-code / credit-limit (각 happy + edge)
- ServiceDiscoveryClient 도입 (Phase 10 활성 대비)
- env-template 보유 (`SAMHAN_PARTNER_SERVICE_URL`)

### 3-2. W2 — groupware-service

- 도메인: ApprovalLine (결재선) / Message (메신저) / Schedule (일정)
- Flyway V1: 3 테이블
- API: 결재선 생성/승인 + 메신저 send/receive + 일정 CRUD
- **Backend IT** (`services/groupware-service/src/test/java/.../GroupwareServiceIT.java`): Internal API + 결재선 흐름 PASS + UserClient `@MockBean` 격리 (직원 정보 lookup)
- **e2e 위치 (Playwright)**: `qa/playwright/tests/groupware/` — 결재선 생성/승인 / 메신저 / 일정 (각 happy + edge)
- ServiceDiscoveryClient 도입

### 3-3. W3 — notification-service

- 도메인: NotificationRequest (요청) / NotificationLog (발송 이력)
- adapter: push (FCM) / email (SES placeholder) / sms (Aligo)
- Flyway V1: 2 테이블
- API: send / status / retry
- 기존 SMS Aligo 마이그레이션 (Phase 5) 흡수
- **Backend IT** (`services/notification-service/src/test/java/.../NotificationServiceIT.java`): 3 channel adapter PASS (mock gateway) + UserClient `@MockBean` 격리 (수신자 정보 lookup)
- **e2e 위치 (Playwright)**: `qa/playwright/tests/notification/` — 채널별 발송 / 재시도 / status 조회
- **e2e 위치 (Detox)**: `qa/detox/e2e/mobile-v4/notification-push.test.ts` (Android + FCM mock) + `qa/detox/e2e/mobile-staff/notification-push.test.ts` (iOS + APNs mock + permission grant flow)
- ServiceDiscoveryClient 도입

### 3-4. W4 — dashboard-service

- 도메인: KpiSnapshot (KPI 스냅샷) / RealTimeStock / SalesAggregate
- materialized view 활용 (PostgreSQL standard)
- 다른 service (inventory / accounting / partner-order) 의 데이터 집계
- Flyway V1: 3 테이블 + materialized view
- API: KPI 조회 + 실시간 재고 + 매출 집계
- **Backend IT** (`services/dashboard-service/src/test/java/.../DashboardServiceIT.java`): 외부 service `@MockBean` 격리 (InventoryClient / AccountingClient / PartnerOrderClient / PartnerClient) + 집계 PASS + materialized view refresh PASS
- **e2e 위치 (Playwright)**: `qa/playwright/tests/dashboard/` — KPI 조회 / 실시간 재고 / 매출 집계 + **visual baseline 신규 작성 의무** (Phase 7 4차 dark-mode 패턴 1:1 적용 — Designer 협업)
- ServiceDiscoveryClient 도입

### 3-5. W5 — Phase 9 회고 + Phase 10 진입

- Phase 9 회고 보고서 (`docs/dev-reports/phase9-retrospective.md`)
- Phase 10 진입 plan (`docs/migration/phase10/M-PHASE-10-readiness.md`)
- DECISIONS D-P9 시리즈 추가 (4 service 도입 결정 + Phase 10 cutover 시점)

---

## 4. 각 service 신규 시 가드 (Phase 0/6/8 학습 일관 적용)

| 가드 | 항목 |
|---|---|
| BaseEntity 7 audit | id / created_at / updated_at / created_by / updated_by / is_deleted / deleted_at |
| Soft Delete only | `@SQLRestriction("is_deleted = false")` 의무 |
| DB 컬럼 타입 | `VARCHAR(N)` 만, `CHAR(N)` 금지 |
| IT mockbean 외부 client 격리 | `@MockBean` + lenient setup (Phase 6 학습) |
| 한국어 commit + Javadoc + dev-reports | 함수 단위 문서화 3-layer (1) Javadoc (2) springdoc-openapi (3) dev-reports |
| ServiceDiscoveryClient 도입 | shared:discovery-abstraction 의존성 + provider toggle (Phase 10 활성 대비) |
| 환경변수 표준 | `SAMHAN_<SERVICE>_<KEY>` prefix + chained-default fallback |
| env-template 의무 | `infrastructure/env-templates/<service>.env` 보유 |
| Flyway baseline | V1 부터 시작, out-of-order 비활성 |
| gradlew 실행 권한 | `git update-index --chmod=+x gradlew` 의무 |

---

## 5. 통합 PR 패턴 (Phase 6/7/8 일관)

| 패턴 | 적용 |
|---|---|
| TM 종합 dev report + reviewer 5 평행 토론 (BE / FE / Designer / QA / DevOps) | Phase 9 1차 ~ 4차 일관 |
| 단편 PR / 단독 PR 회피 = 1 통합 PR | TM 자체 발행 |
| Blocker 우선 fix → nit 후속 분리 | Phase 7 학습 |
| historic commit GitGuardian 검사 = sub 별 단일 commit (`git merge --squash` x N) | Phase 6 PR #76 회고 |

---

## 6. 의존성 매트릭스 (Phase 9 4 service ↔ 기존 14)

| 신규 service | 의존 service | 호출 패턴 |
|---|---|---|
| partner-service | (없음) | self-contained |
| groupware-service | user-service (직원 정보) | Internal API + ServiceDiscoveryClient |
| notification-service | user-service (수신자 정보) | Internal API |
| dashboard-service | inventory / accounting / partner-order / partner | Internal API + materialized view 집계 |

---

## 7. 참조

- Phase 8 회고: `docs/dev-reports/phase8-retrospective.md`
- Phase 10 dry-run plan: `docs/migration/phase10/M-AWS-MIGRATION-DRY-RUN.md`
- Phase 8 환경변수 표준: `docs/migration/phase8/M-ENV-STANDARDIZATION.md`
- Phase 8 ServiceDiscoveryClient: `shared/discovery-abstraction/`
- 누적 결정: `migration/decisions/DECISIONS.md` (D-P9-01 ~ D-P9-08)
- W1 완료 dev-report: `docs/dev-reports/phase9-step-1-partner-service.md`
- W1 service README: `services/partner-service/README.md`
- W2 완료 dev-report: `docs/dev-reports/phase9-step-2-groupware-service.md`
- W2 service README: `services/groupware-service/README.md`
- W3 완료 dev-report: `docs/dev-reports/phase9-step-3-notification-service.md`
- W3 service README: `services/notification-service/README.md`
