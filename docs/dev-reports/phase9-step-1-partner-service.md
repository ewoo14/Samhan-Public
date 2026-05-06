# Phase 9 1차 — W1 partner-service skeleton

본 보고서는 Phase 9 (잔여 도메인) 1차 작업 결과를 정리한다.

- W1 = **본 PR — partner-service skeleton + M5 partnerCode lookup endpoint + ServiceDiscoveryClient 도입**
- W2 예정 = groupware-service (결재선 + 메신저 + 일정)
- W3 예정 = notification-service (push/email/sms 통합)
- W4 예정 = dashboard-service (KPI + 실시간 재고 + 매출)
- W5 예정 = Phase 9 회고 + Phase 10 진입 plan

---

## 1. 본 PR 산출물

### 1-1. 신규 service `services/partner-service/` (port 8095)

```text
services/partner-service/
├── build.gradle                                     (Spring Boot + Eureka client + Flyway + shared:common + shared:discovery-abstraction)
├── README.md                                        (도입 배경 + Domain + REST + 환경변수 + 테스트 + Phase 10 영향)
├── src/main/java/com/samhanair/logis/partner/
│   ├── PartnerServiceApplication.java               (@SpringBootApplication + @EnableDiscoveryClient)
│   ├── config/
│   │   ├── SecurityConfig.java                      (InternalToken + HeaderAuth filter chain)
│   │   ├── InternalAuthProperties.java              (@ConfigurationProperties("app.security.internal"))
│   │   ├── InternalTokenFilter.java                 (X-Internal-Token → ROLE_MASTER)
│   │   ├── HeaderAuthenticationFilter.java          (X-User-Id/Role → ROLE_<ROLE>)
│   │   └── InternalTokenGuard.java                  (prod 프로파일 + dev 기본 토큰 부팅 거부)
│   ├── controller/
│   │   ├── PartnerInternalController.java           (GET /internal/partners/{partnerCode})
│   │   └── PartnerAdminController.java              (POST/GET/PUT/DELETE /admin/partners + /credit-history)
│   ├── service/
│   │   ├── PartnerService.java                      (register / findByCode / updateProfile / delete / suspend / activate / terminate)
│   │   └── PartnerCreditService.java                (recordSlipIssued / recordPayment / changeCreditLimit / findHistory)
│   ├── domain/
│   │   ├── Partner.java                             (BaseEntity + @SQLRestriction("is_deleted = false"))
│   │   ├── PartnerCreditHistory.java                (append-only, balance/creditLimit 스냅샷)
│   │   ├── PartnerStatus.java                       (ACTIVE / SUSPENDED / TERMINATED)
│   │   └── CreditEventType.java                     (SLIP_ISSUED / PAYMENT / CREDIT_LIMIT_CHANGE)
│   ├── repository/
│   │   ├── PartnerRepository.java                   (findByPartnerCode / findByBizNo / findAllByStatus / findAllByNameContainingIgnoreCase)
│   │   └── PartnerCreditHistoryRepository.java      (findAllByPartnerIdOrderByOccurredAtDesc + 기간 + eventType 필터)
│   ├── dto/
│   │   ├── PartnerInternalResponse.java             (record — partnerId UUID + 마스터 + 신용 정보)
│   │   ├── PartnerAdminRequest.java                 (record + Bean Validation)
│   │   ├── PartnerAdminResponse.java                (record — partner UUID 미포함, partnerCode 만 노출)
│   │   └── CreditHistoryResponse.java               (record — 이벤트 + 금액 + 스냅샷 + reference)
│   └── exception/PartnerExceptionHandler.java       (BusinessException / Validation / IllegalState → ApiResponse 봉투)
├── src/main/resources/
│   ├── application.yml                              (port 8095 + chained-default DB + samhan.discovery.provider + local 프로파일)
│   └── db/migration/V1__init_partner.sql            (partners + partner_credit_history, BaseEntity 7 audit + Soft Delete + partial unique index)
└── src/test/java/com/samhanair/logis/partner/
    ├── service/PartnerServiceTest.java              (단위, 8 case)
    └── it/
        ├── AbstractPostgresIT.java                  (Testcontainers PostgreSQL + Docker 미가용 skip)
        ├── PartnerInternalControllerIT.java         (4 case — 토큰 누락(403)/불일치(401)/일치+lookup(200)/일치+미존재(404))
        └── PartnerAdminControllerIT.java            (5 case — 403 익명 / 403 SALES / 200 MANAGER / 409 중복 / DELETE soft)
```

### 1-2. 기존 파일 갱신

| 파일 | 변경 |
|---|---|
| `settings.gradle` | `include 'services:partner-service'` + `projectDir` 매핑 + Phase 9 services 주석 추가 |
| `build.gradle` (root) | `leafProjects` 에 `:services:partner-service` 추가 |
| `README.md` (root) | 진척률 / service 인벤토리 / 디렉토리 구조 / Phase 진행 상태 / bootRun 예시 갱신 |
| `ROADMAP.md` | Phase 9 = "진입 준비" → "1차 진행 (W1 partner-service skeleton)" / 산출물 섹션 신규 / 머지 PR 매트릭스 / 디렉토리 매트릭스 / 참조 문서 갱신 |
| `migration/decisions/DECISIONS.md` | D-P9-03 / D-P9-04 / D-P9-05 추가 |
| `infrastructure/env-templates/partner-service.env` | 신규 (chained-default + CHANGE_ME_LOCAL_ONLY placeholder) |
| `docs/migration/phase9/M-PHASE-9-readiness.md` | W1 산출물 표기 ("진입 준비 완료" → "본 PR 머지 시점 완료") |

---

## 2. M5 slip-service 의존성 해소

### 2-1. 현재 미해결 사항

slip-service 의 `/from-partner-order` / `/from-estimate` endpoint (M5, PR #76) 는 partnerCode 만 받아 자체 슬립 도메인에 저장한다. 거래처 마스터 (UUID, 신용한도, 미수금) 정보가 필요한 시점 (예: 한도 가드, 회계 분개 link) 에는 별도 lookup 이 필요.

### 2-2. 본 PR 의 해소 endpoint

| Method | Path | 인증 | 응답 |
|---|---|---|---|
| GET | `/internal/partners/{partnerCode}` | X-Internal-Token (ROLE_MASTER) | `PartnerInternalResponse` (partnerId UUID + partnerCode + name + creditLimit + outstandingBalance + status) |

slip-service 측에서 `PartnerClient` (RestClient 또는 Feign) 가 본 endpoint 호출 시 partnerCode 1회 호출로 전체 마스터 + 신용 정보 획득.

### 2-3. slip-service 측 client 구현 시점 (D-P9-04)

본 PR scope 외. (1) Phase 9 W5 마무리 시점에 partner-service 가 W1 ~ W4 모든 신규 service 의 의존성 통합 정합 작업과 함께 진행하거나, (2) Phase 10 cutover 사전 정합 시점에 진행. 결정은 W5 또는 Phase 10 진입 시점.

slip-service 측 변경은 다음 항목 동반 의무:
- `SlipServiceClient`-style `PartnerClient` 신규 작성 (X-Internal-Token 헤더 자동 첨부)
- `application.yml` 의 external base URL 패턴에 `partner-service: ${SAMHAN_PARTNER_SERVICE_URL:http://partner-service:8095}` 추가
- M5 idempotency 3중 격리 IT 회귀 테스트
- 한도 초과 시 응답 코드 / 사용자 표시 메시지 결정

---

## 3. ServiceDiscoveryClient 도입 검증

### 3-1. partner-service 측 적용

`build.gradle`:
```gradle
implementation project(':shared:discovery-abstraction')
```

`application.yml`:
```yaml
samhan:
  discovery:
    provider: ${SAMHAN_DISCOVERY_PROVIDER:eureka}
```

### 3-2. 동작 검증

- `samhan.discovery.provider=eureka` (default, matchIfMissing) → `EurekaServiceDiscoveryClient` 자동 활성 (`shared:discovery-abstraction` 의 `DiscoveryConfiguration` 가 `@ConditionalOnClass(EurekaClient)` + `@ConditionalOnProperty` 로 bean 등록)
- 본 시점 partner-service 는 `EurekaClient` 가 classpath 에 존재 (build.gradle 의 `spring-cloud-starter-netflix-eureka-client`) → bean 활성
- `EnableDiscoveryClient` 와 병존 — Eureka 자체 service registration / lookup 은 기존 Spring Cloud auto-config 그대로, `ServiceDiscoveryClient` 는 추가 wrapper

### 3-3. Phase 10 cutover 시점 (D-P9-05)

`SAMHAN_DISCOVERY_PROVIDER=aws-cloud-map` 환경변수 토글:
- `EurekaServiceDiscoveryClient` 비활성 (provider 미일치)
- `AwsCloudMapServiceDiscoveryClient` 활성 (현재 placeholder, Phase 10 시점 실 구현)
- partner-service 코드 변경 없음

---

## 4. 가드 일관 적용

| 가드 | 적용 |
|---|---|
| BaseEntity 7 audit | `Partner` / `PartnerCreditHistory` 모두 `extends BaseEntity` |
| Soft Delete only | `@SQLRestriction("is_deleted = false")` 두 entity 적용 |
| partial unique index | `partner_code` / `biz_no` 활성 행 unique (`WHERE is_deleted = FALSE`) |
| DB 컬럼 타입 | `VARCHAR(N)` / `NUMERIC(15,2)` / `INT` / `TEXT` (CHAR 0건) |
| UUID 비공개 | `PartnerAdminResponse` / `CreditHistoryResponse` 모두 partner UUID 미포함, partnerCode 만 노출. `PartnerInternalResponse` 만 partnerId 노출 (내부 형제 service 전용) |
| 함수 단위 한국어 Javadoc | 2 service / 2 controller / 2 entity / 4 dto / 4 config 모두 적용 |
| springdoc-openapi | `@Operation` + `@ApiResponses` 두 controller 적용 |
| 환경변수 chained-default | `${SAMHAN_PARTNER_DB_HOST:${LEGACY_DB_HOST:localhost}}` 패턴 적용 |
| GitGuardian 회피 | placeholder = `CHANGE_ME_LOCAL_ONLY` (env-template) + `dev-internal-token-change-me` (yml dev default) + `test-internal-token` (IT 전용) — `samhan_dev_pw` literal 0건 |
| InternalTokenGuard | `@PostConstruct` 검증 — prod 프로파일 + dev 기본값 시 `IllegalStateException` 부팅 거부 |
| IT mockbean 외부 client | partner-service self-contained — 외부 client 0 → @MockBean 불요 |
| 한글 path JDK 트랩 | `AbstractPostgresIT` 의 `DockerAvailableCondition` 가 Docker 미가용 시 IT skip → 로컬 한글 path 환경에서도 단위 테스트 PASS |

---

## 5. 테스트 결과

### 5-1. 단위 테스트 (`PartnerServiceTest`, 8 case)

```text
> ./gradlew :services:partner-service:test --tests *PartnerServiceTest

PartnerServiceTest > register_with_required_fields_initialises_active_status_and_zero_balance() PASSED
PartnerServiceTest > register_rejects_blank_required_fields() PASSED
PartnerServiceTest > changeCreditLimit_returns_delta_and_updates_state() PASSED
PartnerServiceTest > changeCreditLimit_rejects_negative_value() PASSED
PartnerServiceTest > increaseBalance_then_decreaseBalance_round_trip_is_consistent() PASSED
PartnerServiceTest > decreaseBalance_rejects_overpay() PASSED
PartnerServiceTest > canIssueSlip_blocks_when_limit_exceeded() PASSED
PartnerServiceTest > canIssueSlip_blocks_when_not_active() PASSED

BUILD SUCCESSFUL
```

### 5-2. assemble 검증

```text
> ./gradlew assemble

BUILD SUCCESSFUL in 21s
69 actionable tasks: 57 executed, 3 from cache, 9 up-to-date
```

15 service (14 + 1) 모두 컴파일 + bootJar PASS.

### 5-3. IT (Linux CI runner 위임)

`PartnerInternalControllerIT` (4 case) + `PartnerAdminControllerIT` (5 case) 는 Testcontainers PostgreSQL 16 + Spring Boot context 부팅이 필요. Windows 한글 path 로컬 환경 대신 GitHub Actions Ubuntu runner 에서 실행. `DockerAvailableCondition` 이 Docker 미가용 환경 자동 skip.

---

## 6. 다음 단계 (Phase 9 W2 진입)

본 PR 머지 후:
- W2 = `services/groupware-service` (8092) — 결재선 (ApprovalLine) + 메신저 (Message) + 일정 (Schedule) 도메인 모델
- 외부 의존 = user-service (직원 정보) → IT @MockBean 격리 의무 (memory feedback_it_mockbean_external_clients)
- ServiceDiscoveryClient 도입 패턴은 본 PR 의 partner-service 1:1 적용

---

## 7. 참조

- `services/partner-service/README.md`
- `docs/migration/phase9/M-PHASE-9-readiness.md` (4 service skeleton + 5주 roadmap)
- `migration/decisions/DECISIONS.md` D-P9-03 / D-P9-04 / D-P9-05
- `shared/discovery-abstraction/` (Phase 8 2차 도입, 본 PR 첫 소비자)
- `infrastructure/env-templates/partner-service.env`
