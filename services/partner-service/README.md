# partner-service

Phase 9 W1 — 거래처 마스터 도메인.

- 포트: **8095**
- DB: PostgreSQL `partner_db` (service-per-DB), Flyway 자동 마이그레이션
- 외부 의존: 없음 (self-contained, M-PHASE-9-readiness §6 의존성 매트릭스 일관)

## 도입 배경

slip-service M5 (`/from-*` endpoint) 가 현재 partnerCode 만 받고 partnerId 정규화를 자체 보유한 lookup 없이 처리하고 있다. partner-service 가 `GET /internal/partners/{partnerCode}` endpoint 를 제공함으로써 형제 service 가 partnerCode → partnerId / 마스터 / 신용 정보를 단일 호출로 획득할 수 있도록 한다.

slip-service 측 client (PartnerClient) 구현 시점은 Phase 9 W5 또는 Phase 10 cutover 시점에 별도 PR 로 진행 (본 PR scope 외).

## Domain (2 entity + 2 enum)

| Entity | 비고 |
|---|---|
| `Partner` | 거래처 마스터 (partnerCode UK + bizNo + name + address + phone + creditLimit + outstandingBalance + status) |
| `PartnerCreditHistory` | 신용 거래 이력 (append-only, balance / creditLimit 스냅샷) |

| Enum | 값 |
|---|---|
| `PartnerStatus` | `ACTIVE` / `SUSPENDED` / `TERMINATED` |
| `CreditEventType` | `SLIP_ISSUED` / `PAYMENT` / `CREDIT_LIMIT_CHANGE` |

## REST endpoints

### Internal (X-Internal-Token 필수)

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| GET | `/internal/partners/{partnerCode}` | ROLE_MASTER (token) | partnerCode → 마스터 + partnerId UUID lookup. M5 의존성 해소 |

### Admin (X-User-* 헤더, gateway 경유)

| Method | Path | 권한 | 설명 |
|---|---|---|---|
| POST | `/admin/partners` | MASTER / MANAGER | 신규 거래처 등록 |
| GET | `/admin/partners/{partnerCode}` | MASTER / MANAGER / SALES / ACCOUNTANT | 단건 조회 |
| PUT | `/admin/partners/{partnerCode}` | MASTER / MANAGER | 프로필 수정 (name / address / phone) |
| DELETE | `/admin/partners/{partnerCode}` | MASTER | soft-delete |
| GET | `/admin/partners/{partnerCode}/credit-history` | MASTER / MANAGER / ACCOUNTANT | 신용 거래 이력 페이지 조회 |

응답 = `ApiResponse<T>` 봉투 (success / code / message / data / timestamp). UUID 비공개 가드 일관 — admin 응답에 partner UUID 미포함, partnerCode 만 노출.

## 환경변수

`infrastructure/env-templates/partner-service.env` 참조.

| 변수 | 표준 / legacy fallback | 용도 |
|---|---|---|
| `SAMHAN_PARTNER_DB_HOST` / `PORT` / `NAME` / `USER` / `PASSWORD` | LEGACY_DB_* | DataSource (chained-default) |
| `SAMHAN_INTERNAL_TOKEN` | `INTERNAL_AUTH_TOKEN` | X-Internal-Token expected 값 |
| `SAMHAN_PARTNER_SERVICE_URL` | (신규 표준만) | 형제 service 가 본 service 호출 시 base URL |
| `SAMHAN_DISCOVERY_PROVIDER` | `eureka` default | Phase 10 cutover 시점 `aws-cloud-map` 으로 전환 |
| `EUREKA_URL` | (legacy) | service discovery |

`InternalTokenGuard` 가 부팅 시 prod 프로파일 + dev 기본값 조합을 거부.

## 테스트

```bash
# 단위 (JDK 17 한글 path 환경에서도 PASS)
./gradlew :services:partner-service:test --tests *Test

# IT (Docker 가용 환경, Linux runner 권장)
./gradlew :services:partner-service:test --tests *IT
```

| 테스트 | 비고 |
|---|---|
| `PartnerServiceTest` | Partner 도메인 단위 (8 case) — register / changeCreditLimit / increase·decreaseBalance / canIssueSlip / 상태 전이 |
| `PartnerInternalControllerIT` | Internal endpoint (4 case) — 토큰 누락(403)/불일치(401)/일치+lookup(200)/일치+미존재(404) |
| `PartnerAdminControllerIT` | Admin CRUD (5 case) — 403 익명 / 403 SALES / 200 MANAGER 등록 / 409 중복 / DELETE soft |

IT 베이스 = `AbstractPostgresIT` (Testcontainers PostgreSQL 16 + Docker 미가용 환경 skip).

## Phase 10 cutover 영향

- `SAMHAN_DISCOVERY_PROVIDER=aws-cloud-map` 으로 토글 시 `shared:discovery-abstraction` 의 `AwsCloudMapServiceDiscoveryClient` 활성. 본 service 코드 변경 없음 (build.gradle / yml 한 줄 수준).
- DataSource 는 chained-default 패턴이므로 RDS 호환. AWS Secrets Manager 마이그레이션 시 `spring.config.import: aws-secretsmanager:samhan/<env>/...` 추가만 (코드 변경 없음).
- Flyway V1 = PostgreSQL standard SQL 만 사용 (RDS 미지원 extension 부재).

## 관련 문서

- `docs/migration/phase9/M-PHASE-9-readiness.md` — Phase 9 진입 plan
- `docs/dev-reports/phase9-step-1-partner-service.md` — 본 슬라이스 dev report
- `migration/decisions/DECISIONS.md` D-P9-03 / D-P9-04 / D-P9-05
