# slip-service

SamhanLogis 출고/입고 전표 (STI) 서비스 — 10단계 라이프사이클 + 모바일 전자서명 + Phase 6 M5 통합 발행 endpoint.

- 포트: **8086**
- DB: PostgreSQL `slip_db` (service-per-DB), Flyway 자동 마이그레이션 (V1 ~ V8)
- 외부 의존: inventory-service (8085, FIFO 차감) / product-service (8084, 라인 lookup) / partner-order-service (8088) / Solapi or 알리고 SMS

## 기능 요약

### Phase 2 / 3 (운영)
- 10단계 라이프사이클 (DRAFT → REQUESTED → APPROVED → DISPATCHED → DELIVERED → ...)
- dispatcher / inspector 자동 서명 + 라인 specification
- DeliveryBatch + Solapi/알리고 SMS 발송
- 모바일 전자서명 (Canvas + SHA-256, 인수자/기사 양측 캡처)
- DispatchView 인쇄 통합 (양측 서명 PNG 자동 통합)

### Phase 6 M5 — 통합 발행 endpoint (Sync REST + idempotency 3중 격리)

3중 idempotency 격리:
1. DB partial UNIQUE INDEX (`idempotency_key` IS NOT NULL)
2. Service fingerprint (sourceType + sourceId + 라인 hash)
3. Outbox (별 슬라이스 — partner-order-service 가 발행)

`SlipSourceType` enum: `ESTIMATE / PARTNER_ORDER / MANUAL / MIGRATED_ECOUNT`

| Method | Path | 권한 |
|---|---|---|
| POST | `/api/v1/slips/from-estimate` | SALES / MANAGER / MASTER / INTEGRATION |
| POST | `/api/v1/slips/from-partner-order` | MANAGER / MASTER / INTEGRATION / PARTNER_ADMIN |
| GET | `/api/v1/slips/by-source` | 인증 |

응답: 201 신규 / 200 replay (동일 idempotency_key 재호출) / 409 idempotency 충돌 / 400 입력 / 403 권한.

### 발행 감사 (`SlipPublishAudit`)

영구 보존 (soft-delete 만 허용). 회계 cross-check + supply/vat 합계 round-trip 검증.

## 30 endpoint

기존 Phase 2 / 3 endpoint 23 + Phase 6 M5 endpoint 3 + 부가 4 = **30 endpoint**.

## Domain 핵심 변경 (Phase 6 M5)

| 변경 | 위치 |
|---|---|
| `Slip` 3 컬럼 추가 | `sourceType` / `sourceId` / `idempotencyKey` |
| `assignPublishSource()` 1회성 setter | 재할당 차단 |
| `SlipSourceType` enum 신규 | 출처 분류 |
| `SlipPublishAudit` 신규 entity | 회계 영구 보존 |

## Flyway 마이그레이션 (Phase 6)

| 버전 | 내용 |
|---|---|
| V7 | Slip 3 컬럼 + partial UNIQUE INDEX + composite INDEX |
| V8 | slip_publish_audit 테이블 + jsonb 컬럼 |

## Environment variables

| 변수 | 기본값 | 비고 |
|---|---|---|
| `DB_*` | `slip_db` 등 | placeholder |
| `EUREKA_URL` | `http://localhost:8761/eureka/` | |
| `INTERNAL_TOKEN` | `dev-internal-token-change-me` | prod default 사용 시 부팅 거부 |
| `app.publish.warehouse-code-map` | env-driven (legacy 코드 → UUID) | 후속에 warehouse-service RestClient 진화 |
| `SOLAPI_API_KEY` / `SOLAPI_API_SECRET` / `SOLAPI_SENDER_PHONE` | (mock 활성 시 미사용) | dev/staging/prod 필수 |

H2 local 프로파일은 `MockSmsGateway` 자동 활성으로 SOLAPI 변수 미설정 가능.

## Local run

```bash
./gradlew :services:slip-service:bootRun --args='--spring.profiles.active=local'
```

## Tests

```bash
./gradlew :services:slip-service:test
```

- 단위 테스트 — 라이프사이클 transition / idempotency 검사 / payload 매핑
- IT — Testcontainers PostgreSQL + ProductClient / InventoryClient `@MockBean`
- `SlipPublishControllerIT` 7 case (M5 통합 발행)

## 후속 작업

- Phase 7 — `qa/playwright/confirm/confirm-slip-publish.spec.ts` 가 본 endpoint 의 idempotency 를 e2e 검증
- 자세한 매트릭스는 `docs/dev-reports/migration-be-m5-slip-service-integration.md` 참조

## Phase 8 호환성 가드 (PR #88 / #89 / #90)

- **chained-default 환경변수** — `SAMHAN_<KEY>:${LEGACY_KEY:default}` 패턴 적용 (legacy 호환 100%, 무중단 cutover 가능). `SAMHAN_INTERNAL_TOKEN:${INTERNAL_TOKEN:dev-internal-token-change-me}` 형태.
- **12-factor 12/12 OK** + RDS 호환 (V1~V8 standard SQL 만 — Flyway baseline PASS 검증 결과 22 file 중 본 service 7 file 포함)
- **AWS 서비스 매핑** — `docs/migration/phase8/M-AWS-COMPATIBILITY-guards.md` 본 service 항목 참조 (S3 endpoint override 대상 — signature PNG)
- **env-template** — `infrastructure/env-templates/slip-service.env` 보유 (`SOLAPI_*` 포함)
- **Secrets Manager rotation 대상** — `SAMHAN_INTERNAL_TOKEN` (90일) / `RABBIT_PASSWORD` / `SOLAPI_API_SECRET` 은 Phase 11 cutover 시점 `docs/migration/phase8/M-SECRETS-ROTATION-spec.md` 의 lambda 로 자동 rotation
- **ServiceDiscoveryClient (Phase 11 활성 대비)** — `shared:discovery-abstraction` 의존성 도입은 Phase 11 cutover 시점

## Phase 9 신규 service 매트릭스 (참조)

| Service                | Port | DB                | 도메인                              |
| ---------------------- | ---- | ----------------- | ----------------------------------- |
| partner-service        | 8095 | partner_db        | 거래처 마스터 + 신용한도 + 거래내역 |
| groupware-service      | 8092 | groupware_db      | 결재선 + 메신저 + 일정              |
| notification-service   | 8093 | notification_db   | 푸시/이메일/SMS 통합 라우터         |
| dashboard-service      | 8094 | dashboard_db      | KPI + 실시간 재고 + 매출            |

partner-service 도입 후 본 service 의 `/from-*` endpoint 가 사용하는 partnerCode → partnerId lookup 의존성이 정규화 예정. notification-service 도입 후 SMS Aligo 통합도 본 service → notification-service routing 으로 이전. 상세는 `docs/migration/phase9/M-PHASE-9-readiness.md` 참조.
