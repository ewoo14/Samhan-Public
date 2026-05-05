# Phase 6 M5 — slip-service 통합 발행 endpoint

**브랜치**: `feature/migration-be-m5-slip-service-integration`
**대상 서비스**: `services/slip-service` (port 8086, 운영 중)
**설계 문서**: `docs/migration/phase6/M5-slip-service-integration.md` (Sync REST + idempotency 3중 격리)
**일자**: 2026-05-05

---

## 1. 결정 사항 요약 (CONSISTENCY-MATRIX)

| 결정 | 선택 | 근거 |
|------|------|------|
| 통신 패턴 | **Sync REST** | 사용자 "바로 출고전표" 요구 — async event 는 사용자 latency 늘림 |
| 포트 | **8086 유지** (slip-service 그대로) | product-service 가 8084 점유, 기존 운영 중 |
| Idempotency | **3중 격리** | (1) DB partial UNIQUE INDEX (2) Service fingerprint (3) Outbox (별 슬라이스) |
| 출처 분류 | `SlipSourceType` enum (ESTIMATE/PARTNER_ORDER/MANUAL/MIGRATED_ECOUNT) | 회계 cross-check + 마이그 대응 |
| 발행 감사 | `SlipPublishAudit` 테이블 영구 보존 (soft-delete 만) | 회계 reference, supply/vat 합계 round-trip 검증 |
| Warehouse 코드 매핑 | `WarehouseCodeMapper` (env-driven map) | 후속에 warehouse-service RestClient 로 진화 |

## 2. 변경 파일

### Domain (3 파일)
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/Slip.java` — 정정: 3 컬럼 (`sourceType`, `sourceId`, `idempotencyKey`) + `assignPublishSource()` 1회성 setter 추가
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SlipSourceType.java` — 신규 enum
- `services/slip-service/src/main/java/com/samhanair/logis/slip/domain/SlipPublishAudit.java` — 신규 entity (회계 영구 보존)

### Repository (2 파일)
- `services/slip-service/src/main/java/com/samhanair/logis/slip/repository/SlipRepository.java` — 정정: `findByIdempotencyKeyAndIsDeletedFalse` + `findAllBySourceTypeAndSourceIdAndIsDeletedFalse`
- `services/slip-service/src/main/java/com/samhanair/logis/slip/repository/SlipPublishAuditRepository.java` — 신규

### Service / Web (5 파일)
- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/PublishLineRequest.java` — 신규 DTO
- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/PublishFromEstimateRequest.java` — 신규 DTO
- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/PublishFromPartnerOrderRequest.java` — 신규 DTO
- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/PublishSlipResponse.java` — 신규 DTO
- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/WarehouseCodeMapper.java` — 신규 (legacy 코드 → UUID)
- `services/slip-service/src/main/java/com/samhanair/logis/slip/publish/SlipPublishService.java` — 신규 (idempotency 3중 격리 + payload 매핑)
- `services/slip-service/src/main/java/com/samhanair/logis/slip/web/SlipPublishController.java` — 신규 (`/api/v1/slips/from-estimate` + `from-partner-order` + `by-source`)

### Flyway (2 파일)
- `services/slip-service/src/main/resources/db/migration/V7__add_slip_source_columns.sql` — Slip 3 컬럼 + partial UNIQUE INDEX + composite INDEX
- `services/slip-service/src/main/resources/db/migration/V8__create_slip_publish_audit.sql` — slip_publish_audit 테이블 + jsonb 컬럼

### 설정
- `services/slip-service/src/main/resources/application.yml` — `app.publish.warehouse-code-map` env-driven 추가

### 테스트
- `services/slip-service/src/test/java/com/samhanair/logis/slip/publish/SlipPublishControllerIT.java` — 신규 IT (7 cases)

## 3. Endpoint 매트릭스

| Method | Path | 권한 | 응답 코드 |
|--------|------|------|-----------|
| POST | `/api/v1/slips/from-estimate` | SALES/MANAGER/MASTER/INTEGRATION | 201 신규 / 200 replay / 409 idem 충돌 / 400 입력 오류 / 403 권한 |
| POST | `/api/v1/slips/from-partner-order` | MANAGER/MASTER/INTEGRATION/PARTNER_ADMIN | 동일 |
| GET  | `/api/v1/slips/by-source` | 인증된 모든 사용자 | 200 + 매칭 슬립 목록 |

## 4. Payload 매핑 (설계서 §3 1:1)

### 헤더 매핑

| legacy 필드 (slip-bridge) | slip-service 도메인 |
|--------------------------|--------------------|
| `partnerCode` | partner-service lookup → `partnerId` (현 슬라이스 미지원, partnerName 만 보존) |
| `warehouseCode` ("00003"/"2"/"14"/"1") | `WarehouseCodeMapper.resolve()` → `Slip.sourceWarehouseId` |
| `employeeCode` (EMP-0001~0019) | `Slip.requesterId` |
| `ioDate` (yyyyMMdd) | `Slip.slipDate` |
| `shippingAddress` + `inspectionAddress` + `receiverPhone` + `paymentDueLabel` + `discountInfo` + `memo` | `Slip.memo` (1000자, 라벨 포맷 prepend) |

### 라인 매핑

| legacy `BulkDatas.*` | slip-service |
|---------------------|--------------|
| `productCode` (`PROD_CD`) | `ProductClient.lookupByModel()` → `productId` |
| `qty` (string) | `quantity` (int parse) |
| `unitPriceVat` (`USER_PRICE_VAT.abs()`) | `unitPrice` (BigDecimal, VAT 포함) |
| `spec` (`SIZE_DES`, zero-width 정규화) | `specification` |
| `remarks` (`REMARKS`) | `note` |
| `supplyAmount` / `vatAmount` | `SlipPublishAudit` 합계 (감사 영구 보존) |

## 5. Idempotency 3중 격리

1. **DB partial UNIQUE INDEX** — `uq_slips_idem_key ON slips (idempotency_key) WHERE idempotency_key IS NOT NULL AND is_deleted = FALSE` (V7). 동시 INSERT 충돌 차단 → `DataIntegrityViolationException` → race winner 재조회.
2. **Service fingerprint 비교** — request 본문을 canonical JSON 으로 직렬화 후 SHA-256. 같은 키 + 같은 fingerprint → 200 + replay flag. 같은 키 + 다른 fingerprint → 409.
3. **Outbox** (별 슬라이스) — async event 재발행 보호 (현 슬라이스 범위 밖, 후속 작업).

## 6. 후속 작업 (별도 PR)

- **estimate-app v2 의 `lib/slip-bridge.js` 정정** — `Idempotency-Key` 헤더 추가 + retry + `USE_MOCK_FALLBACK` 제거. 본 endpoint URL `/api/v1/slips/from-estimate` 사용.
- **partner-order-service M4 의 `SlipServiceClient`** — `/api/v1/slips/from-partner-order` 호출. M4 본 슬라이스에 통합.
- **legacy `MIGRATED_ECOUNT` 시드** — legacy 발행 전표 batch 포팅 (별도 batch 스크립트, sourceType=`MIGRATED_ECOUNT` + sourceId=원 ecount 전표 번호 + 시드 idempotencyKey 부여).
- **SlipDetailResponse 노출** — sourceType/sourceId/idempotencyKey 3 필드는 현 PR 에서 응답에 미노출 (필요 시 후속 작업).

## 7. 빌드 / 테스트 결과

- `:services:slip-service:compileJava` — **PASS**
- `:services:slip-service:compileTestJava` — **PASS**
- `:services:slip-service:assemble` — **PASS** (bootJar 생성)
- `:services:slip-service:test` 결과:
  - 단위 테스트 154개 — **모두 PASS**
  - IT 78개 (기존 71 + 신규 7) — **모두 SKIP** (로컬 Docker 미가용, `DockerAvailableCondition`). CI 에서 실행.
  - 실패 0, 에러 0

## 8. 가드 적용 확인

- 한국어 commit + PR 본문 + Javadoc — OK
- BaseEntity 7 audit 필드 — `SlipPublishAudit` 상속 OK
- Soft Delete only — `@SQLRestriction("is_deleted = false")` 적용
- IT @MockBean — `ProductClient` + `InventoryClient` 격리 (lookupByModel lenient mock)
- partial UNIQUE INDEX 구문 — PostgreSQL `WHERE` 절 사용 (Hibernate validate 와 충돌 방지: `unique=true` 미지정)
- gradlew chmod — 본 PR 범위 밖 (기존 git index 유지)
- 시크릿 — placeholder UUID 만 사용 (실 운영 env 변수 교체)
- legacy 비즈니스 로직 변형 — 매핑 1:1 유지 (USER_PRICE_VAT.abs / SIZE_DES zero-width 정규화 등 legacy 동작 보존)

## 9. 모호 / 미결 항목

1. **partner-service 미존재** — `partnerCode` → `partnerId` lookup 은 partner-service 가 아직 없으므로 현 슬라이스에서는 `partnerName` 만 보존. partner-service 도입 후 RestClient 추가 필요.
2. **InventoryClient 호출 없음** — 본 endpoint 들은 `Slip.createOutbound` 만 호출하고 라이프사이클 (accept/complete) 은 별도. 즉 발행 시 inventory reserve 가 자동으로 일어나지 않음. 사용자 "바로 출고전표" 요구 해석에 따라 후속 슬라이스에서 **자동 accept** 옵션 추가 검토.
3. **outbox** — 현 슬라이스 범위 밖. async event 재발행 보호는 후속 슬라이스에서 별도 처리.
4. **WarehouseCodeMapper dev 기본값** — 4개 placeholder UUID. 실 운영 진입 전 환경 변수 (WAREHOUSE_UUID_HQ 등) 로 교체 필수.
