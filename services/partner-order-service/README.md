# partner-order-service

Phase 6 M4 — 거래처 주문 도메인 (legacy `partner-order/index.html` 9427 라인 + `Code.js doGet 4~23` 16종 prefetch) 대체 서비스.

- 포트: **8088**
- DB: PostgreSQL `partner_order_db` (service-per-DB), Flyway 자동 마이그레이션
- 외부 의존: M2 partner-auth (8091, JWT) / M3 dc-config (8089, DC) / product (8084) / inventory (8085) / slip (8086)
- confirm 패턴: **Sync REST + Outbox + Resilience4j Circuit Breaker** (M5 §3 옵션 A)

## Domain (8 entity + 3 enum + outbox)

| Entity | 비고 |
|---|---|
| `PartnerOrder` | 주문 헤더 (status / slipPublishStatus / idempotency_key) |
| `PartnerOrderLine` | 라인 (수량 / 단가 / 스냅샷 modelName/productName/categoryKey) |
| `PartnerOrderDraft` | 임시저장 (TTL 30일, DraftCleanupScheduler 매일 03:00) |
| `PartnerOrderHistory` | 이력 이벤트 |
| `PartnerOrderFrontEventLog` | logFrontEvent silent 적재 |
| `GateImage` | 모바일 게이트 prefetch |
| `TutorialState` | PC / MOBILE 튜토리얼 완료 표시 |
| `BootstrapCacheConfig` | 16종 bootstrap 시드 |
| `SlipPublishOutbox` | confirm 흐름 5xx 시 retry 큐 |

## confirm 흐름

```
DRAFT → POST /confirm → CONFIRMING (idempotency_key=PO-CONF-{draftSeq})
  ├ Idempotency 검사 → 기존 키면 즉시 반환
  ├ M3 dc-config Feign (server-side priceVat 적용) — fail-soft
  ├ M1a product lookup (라인 스냅샷)
  ├ M1b inventory reserve (라인별)
  ├ partner_order INSERT (status=CONFIRMING, slipPublishStatus=PENDING_RETRY)
  ├ SlipServiceClient.publishFromPartnerOrder(payload, "PO-CONF-{draftSeq}")
  │   ├ 200 → markSlipPublished(slipNo) + history SLIP_PUBLISHED
  │   ├ 409 → markSlipPublished(기존 slipNo, duplicate=true)
  │   └ 5xx → SlipPublishOutbox.queue + history SLIP_RETRY_QUEUED
  └ 응답: ConfirmResponse{orderNo, slipNo, status, slipPublishStatus}

Scheduler (5분):
  PENDING + nextAttemptAt ≤ now() → publish 재시도
    ├ 200/409 → COMMITTED + markSlipPublished
    ├ 5xx → markRetry (지수 백오프 5min × 2^attempt, max 60min)
    └ elapsed ≥ 24h → FAILED + markSlipFailedPermanent + alert
```

## REST endpoints (8 + bootstrap 1)

| legacy fn | endpoint | 권한 |
|---|---|---|
| `getGateImages()` | `GET /api/v1/partner-orders/gate-images` | 익명 |
| `getOrderHistory()` | `GET /api/v1/partner-orders/history` | PARTNER+ |
| `logFrontEvent()` | `POST /api/v1/partner-orders/log` | 익명 (silent fail) |
| `saveOrderSnapshot()` | `POST /api/v1/partner-orders/drafts` | PARTNER+ |
| `getOrderSnapshotHistory()` | `GET /api/v1/partner-orders/drafts` | PARTNER+ |
| `sendOrderFromUi()` | `POST /api/v1/partner-orders/{draftId}/confirm` | PARTNER+ |
| `saveTutorialState()` | `PATCH /api/v1/auth/partner-tutorial` | PARTNER+ |
| 신규 | `GET /api/v1/partner-orders/bootstrap` | 익명 (16종 prefetch) |

## 16종 bootstrap

`BootstrapCacheConfig` 시드 16 row.

키: `homemulti / singleSets / singleParts / homeDefaults / singleDefaults / singleMatPrices / commercialMulti / commercialParts / oldProducts / homeInc / commInc / singleInc / singlePartsInc / specDetailMap / config / logoData`

**DC 9키 제외 가드** (`BootstrapService.DC_SECRET_KEYS`):
`homeDiscount / commDiscount / singleDiscount / homePartsDiscount / commPartsDiscount / singlePartsDiscount / oldDiscount / incDiscount / specDiscount` — `config` 응답에서 strip 후 반환 (M3 가드 일관).

## Environment variables

| 변수 | 기본값 | 비고 |
|---|---|---|
| `DB_*` | `partner_order_db` 등 | placeholder |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | |
| `INTERNAL_TOKEN` | `dev-internal-token-change-me` | prod default 사용 시 부팅 거부 |
| `samhan.draft.ttl-days` | 30 | DraftCleanupScheduler |
| `samhan.outbox.max-retry-hours` | 24 | confirm 흐름 retry 한계 |

## Local run

```bash
./gradlew :services:partner-order-service:bootRun --args='--spring.profiles.active=local'
```

## Tests

```bash
./gradlew :services:partner-order-service:test
```

- 단위 테스트 — Draft TTL / DC fail-soft / Outbox retry 백오프
- IT — Testcontainers PostgreSQL + 외부 client `@MockBean` (DcConfig / Product / Inventory / Slip / PartnerAuth) 격리

## 후속 작업

- Phase 7 — `qa/playwright/confirm/` (3 spec, 6 case) 가 본 서비스의 confirm 흐름을 e2e 검증
  (slip 발행 + idempotency + inventory 차감)
- 자세한 매트릭스는 `docs/dev-reports/migration-be-m4-partner-order-service.md` 참조
