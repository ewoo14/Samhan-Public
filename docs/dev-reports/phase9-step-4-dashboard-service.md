# Phase 9 W4 — dashboard-service skeleton dev-report

## 1. 슬라이스 개요

| 항목 | 값 |
|---|---|
| 슬라이스 | Phase 9 W4 (4차) |
| Service | dashboard-service |
| Port | 8094 |
| DB | dashboard_db (service-per-DB) |
| Branch | `feature/integrated-phase-9-step-4-dashboard-service` |
| Base | `main` (HEAD `199d88e` Merge PR #93) |
| 외부 의존 | inventory-service / accounting-service / partner-order-service / partner-service (4 client) + ServiceDiscoveryClient 네 번째 소비자 |

## 2. 도메인 모델 (3 entity + 2 enum + 2 materialized view)

### 2-1. KpiSnapshot

KPI 일/주/월 스냅샷. `(snapshotDate, category)` 활성 행 unique. value 는 NUMERIC(20,4) 단일 컬럼으로 금액 / 카운트 / 비율 모두 처리.

- `of(date, category, value)` — 신규 생성
- `updateValue(value)` — 재집계 시점 갱신

### 2-2. RealTimeStock

실시간 재고 캐시. `(productId, warehouseCode)` 활성 행 unique. quantity NUMERIC(20,4), refreshedAt 별도 보유 (BaseEntity.modifiedAt 과 별개 신선도 의미).

### 2-3. SalesAggregate

일별 / 거래처별 매출 집계. `(aggregateDate, partnerId)` 활성 행 unique. amount NUMERIC(20,4) + itemCount INT.

### 2-4. Materialized view

| View | 설명 |
|---|---|
| `mv_realtime_stock_summary` | 창고별 SKU 수 / 총수량 / latest_refreshed_at |
| `mv_sales_daily_summary` | 일별 거래처 수 / 총금액 / 총항목수 |

CONCURRENTLY refresh — unique index 의무 보유 (V1 SQL).

### 2-5. Enum

- `KpiCategory`: DAILY_SALES / WEEKLY_SALES / MONTHLY_SALES / ORDER_COUNT / ACTIVE_PARTNERS / STOCK_TURNOVER
- `AggregateInterval`: DAILY / WEEKLY / MONTHLY

## 3. REST API

### Internal (X-Internal-Token + ROLE_MASTER)

```
GET /internal/dashboard/kpi/{category}?from=&to=  → 200 List<KpiSnapshotResponse>
```

### Admin (X-User-Role + ROLE_MANAGER 이상)

```
GET  /admin/dashboard/kpi?category=&from=&to=                          → 200 (Caffeine cache 60s)
GET  /admin/dashboard/realtime-stock?warehouseCode=&productCode=        → 200
GET  /admin/dashboard/sales-aggregate?from=&to=&interval=DAILY          → 200
POST /admin/dashboard/refresh                                            → 200 (REFRESH + KPI cache invalidate)
```

## 4. 4 외부 client (skeleton fail-soft 정책)

| Client | Target | 정책 |
|---|---|---|
| `InventoryClient` | inventory-service:8085 | Optional.empty() |
| `AccountingClient` | accounting-service:8087 | BigDecimal.ZERO |
| `PartnerOrderClient` | partner-order-service:8088 | 0 |
| `PartnerClient` | partner-service:8095 (W1) | Optional.empty() |

본 슬라이스는 호출 자체만 보장 (실 응답 파싱 / DTO 매핑은 Phase 10).

## 5. shared:user-client-abstraction (W3 backlog #1 채택)

`shared/user-client-abstraction/` 신규 모듈:

- `UserVerifier` interface — 단건 / bulk verify 표준
- `DefaultUserVerifier` — RestClient + Caffeine TTL 60s, max 10000, fail-soft / fail-fast 토글
- `UserVerifierProperties` — 공통 설정 POJO

notification-service / groupware-service 의 기존 `UserClient` 클래스를 본 abstraction 의 thin delegate 로 변환. 기존 `@MockBean UserClient` 패턴은 그대로 유지 (회귀 0). 기존 12 + 16 단위 + 21 IT case 모두 PASS 검증.

dashboard-service 도 `implementation project(':shared:user-client-abstraction')` 의무 의존성 추가 — 본 W4 시점에는 직접 사용 0, Phase 10 시점 user lookup 통합 시 활용.

## 6. § 캐시 전략 (D-P9-12, DevOps W3 backlog #4 채택)

DevOps reviewer 가 W3 시점에 제기한 "Caffeine in-process vs Redis 공유 캐시" 트레이드오프 — W4 통합 PR 에서 정식 결정:

| 단계 | 결정 |
|---|---|
| W3 (notification) | Caffeine in-process (TTL 60s) — single-instance 적합 |
| W4 (dashboard, 본 PR) | Caffeine 일관 유지 — KPI 응답 60s TTL (`samhan.cache.kpi.ttl-seconds`) |
| Phase 10 | multi-instance scaling 시점에 Redis 전환 검토 |
| 토글 | `samhan.cache.provider=caffeine\|redis` (env 표준 — 코드 변경 없이 전환) |

**근거**: W4 시점 dashboard-service 는 single-instance 가동 + 5분 간격 materialized view REFRESH 가 데이터 일관성의 1차 갱신 메커니즘. 60초 KPI cache TTL 은 REFRESH 주기보다 짧아 stale 위험 없음. multi-instance 전환 시점 (Phase 10) 에 Redis 공유 캐시 + ttl 길이 재검토.

## 7. Materialized view REFRESH (D-P9-13)

- 5분 간격 scheduled (`MaterializedViewRefreshConfig` `@Scheduled`)
- `POST /admin/dashboard/refresh` 수동 트리거 (KPI cache invalidate 동시 호출)
- `REFRESH MATERIALIZED VIEW CONCURRENTLY` (unique index 의무 — V1 SQL 보유)
- fail-soft — REFRESH 실패 시 silent skip + warn log (다음 주기 재시도)

## 8. 테스트 (17 단위 PASS + 9 IT skip / Linux CI PASS 예정)

| Test | 케이스 |
|---|---|
| `KpiServiceTest` | 6 (null/range/delegate/upsert insert+update/cache evict) |
| `RealTimeStockServiceTest` | 4 (전체 list/warehouse 필터/refreshOne fail-soft/400) |
| `SalesAggregateServiceTest` | 5 (range 400/partner 필터/aggregate fail-soft ZERO/0) |
| `MaterializedViewRefreshTest` | 2 (concurrent / fail-soft 예외 미전파) |
| `DashboardInternalControllerIT` | 4 (토큰 누락 403 / 불일치 401 / 정상 200 / 잘못된 enum 400) |
| `DashboardAdminControllerIT` | 5 (KPI / stock / sales / refresh / range 400) |

shared:user-client-abstraction `DefaultUserVerifierTest` 6 case 별도 PASS — abstraction 자체 단위 검증.

## 9. 가드 일관 적용

- BaseEntity 7 audit + Soft Delete (`@SQLRestriction`)
- VARCHAR(N) only / NUMERIC(20,4) money / DATE / TIMESTAMP — RDS 호환
- UUID 비공개 — DTO 응답은 productCode / partnerCode 만 노출 (admin 한정)
- 한국어 Javadoc + dev-report (본 문서) + springdoc-openapi
- IT 외부 client `@MockBean` 격리 (4 client 모두) + lenient setup
- AbstractPostgresIT + Docker skip (한글 path 회피)
- gradlew exec bit 보존
- InternalTokenFilter `/internal/**` prefix 한정 (PR #91 fix 일관)
- prod + dev 기본 토큰 부팅 거부 가드
- chained-default 환경변수 (SAMHAN_DASHBOARD_* + LEGACY_*)
- GitGuardian 시크릿 placeholder (`CHANGE_ME_LOCAL_ONLY`)

## 10. 환경변수 / 인프라 영향

- `infrastructure/env-templates/dashboard-service.env` 신규
- `infrastructure/postgres/init/01-create-databases.sql` `dashboard_db` 이미 보유 (W3 시점 추가됨)
- `infrastructure/prometheus/prometheus.yml` `dashboard-service:8094` scrape target 추가

## 11. W3 backlog 흡수 (5건 채택)

| # | 출처 | 항목 | 결과 |
|---|---|---|---|
| 1 | BE | `shared:user-client-abstraction` 모듈 신규 + notification/groupware delegate | ✅ 본 PR — 6 case PASS + 회귀 0 |
| 2 | Designer | 3 channel badge 토큰 (`b-channel-push/email/sms`) | ✅ design-system tokens.css + dashboard QA HTML |
| 3 | Designer | W4+ baseline = W3 Google Material method 컬러 + PR template | ✅ `docs/templates/PR-template-color-reference.md` 신규 |
| 4 | DevOps | Caffeine vs Redis 트레이드오프 검토 | ✅ § 6 + D-P9-12 + `samhan.cache.provider` 토글 |
| 5 | FE | `notification-slice-B` → `link-dispatch-slice` rename | ✅ desktop 12 file + design-system 4 file + 3 README |

## § 후속 backlog (W5 회고 대상)

- Inventory / Accounting / PartnerOrder Internal API 정착 후 client 응답 파싱 + DTO 매핑 (Phase 10 cutover)
- KPI 산출 batch job (Spring Batch / Quartz, 별도 PR scope)
- Dashboard 화면 — design-system Chart / Sparkline 컴포넌트 신규 (Designer 협업, W5 또는 별도 PR)
- Materialized view 성능 모니터링 (Micrometer + REFRESH 시간 metric) — DevOps Phase 10
- ServiceDiscoveryClient `aws-cloud-map` 토글 활성 — Phase 10 cutover
- Caffeine → Redis 전환 — multi-instance scaling 시점
- W3 BE backlog #2 (UserClient.verifyBulk fail-fast 토글) — 현재 properties 만 보유, 활성은 Phase 10
- W3 BE backlog #3 (NotificationGatewayResult 자동 재시도 큐) — Phase 10 cutover
- W3 DevOps #6 (Resilience4j) / #7 (FCM secrets manager) / #10 (Micrometer counter/timer)
- W3 QA #11/#12/#13 (재시도 한도 / payload size / fail-mode IT)

## 12. 5 reviewer 토론 준비 (TM 발행 후)

본 PR 은 TM 자체 발행 후 BE / FE / Designer / QA / DevOps 5 reviewer 가 PR comment 로 토론. TM 이 종합하여 후속 commit 발행 (W2/W3 패턴 일관). 본 W4 통합 PR 시점에 W3 backlog 5건 흡수 완료 — 잔여 backlog 는 Phase 10 cutover 또는 W5 회고 시점 처리.
