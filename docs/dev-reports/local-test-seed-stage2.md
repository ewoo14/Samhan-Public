# Local Test Seed — Stage 2 (Transactional)

## 작업 범위

`feature/local-test-setup` Stage 2 — 거래성(transactional) 데이터 시드.

| 서비스 | 시드 entity | 건수 |
|---|---|---|
| inventory-service | StockBalance | 200 (100 product × 2 warehouse) |
| slip-service | Slip | 100 (11 status 분포) |
| slip-service | SlipLine | ~300 (slip 평균 3 라인, 1~5 분포) |
| slip-service | DeliveryBatch | 30 (driver 10명 × batch 3건) |

## 활성 가드 (이중)

모든 시더는 두 조건이 모두 충족될 때만 실행 (CI/prod 기본 OFF).

```yaml
# inventory-service application.yml
app:
  inventory:
    seed-test-data: ${INVENTORY_SEED_TEST_DATA:false}

# slip-service application.yml
app:
  slip:
    seed-test-data: ${SLIP_SEED_TEST_DATA:false}
```

```java
@Profile("dev")
@ConditionalOnProperty(value = "app.inventory.seed-test-data", havingValue = "true")
```

## 실행 순서 (@Order)

| Order | 시더 | 의존 |
|---|---|---|
| 10 | StockBalanceSeeder | (Stage 1) product 100건 시드 |
| 20 | SlipSeeder | (Stage 1) partner 50건 + product 100건, OrgChart 16 employee |
| 30 | DeliveryBatchSeeder | SlipSeeder (SHIPPING+ 단계 슬립의 driver 정보 매핑) |

`@Order` 미적용 시 Spring Boot CommandLineRunner 의 실행 순서 비결정 → DeliveryBatchSeeder
가 SlipSeeder 보다 먼저 돌면 매핑 가능 슬립 0건. `@Order(20)` < `@Order(30)` 강제.

## Stage 1 의존성 — 결정성 UUID

| entity | UUID 생성 식 |
|---|---|
| Partner | `UUID.nameUUIDFromBytes("samhan-seed:partner:P-2026-{0001~0050}")` |
| Product | `UUID.nameUUIDFromBytes("samhan-seed:product:TEST-MODEL-{0001~0100}")` |
| StockBalance | `UUID.nameUUIDFromBytes("samhan-seed:stock-balance:{HQ-001\|VH-001}:TEST-MODEL-{NNNN}")` |

Stage 2 시드는 위 namespace 를 결정적으로 사용 — 동일 시드를 다시 돌려도 같은 UUID.

> **⚠️ Stage 1 ↔ Stage 2 coordination caveat (2026-05-09 작업 시점)**
>
> Stage 1 BE 1 agent 의 PartnerSeeder / HvacProductSeeder 는 partnerCode (P-2026-NNNN) /
> modelName (Samsung 실모델 PIPE-CU-15A 등) 를 비공개 식별자로 사용하지만, **UUID 자체는 JPA 생성**
> (deterministic 미사용). Stage 2 SlipSeeder 는 user spec 의 deterministic UUID 명세를 그대로
> 채용하므로 slip 의 partnerId / productId 는 partner-service / product-service DB 의 실 UUID 와
> 매칭되지 않을 수 있다. UI 노출은 partnerCode / modelName 으로 일관 (UUID 비공개 가드 충족) —
> cross-service E2E 테스트 시 Stage 1 시더가 deterministic UUID 로 정렬되도록 후속 통합 PR 필요.

## 1. StockBalance 200건 분포

| field | 값 |
|---|---|
| id | `UUID.nameUUIDFromBytes("samhan-seed:stock-balance:" + warehouseCode + ":" + productCode)` |
| warehouseId | V2 시드 HQ-001 또는 VH-001 (V2__seed_inventory_warehouses.sql) |
| productId | Stage 1 product UUID |
| availableQty | `30 + ((productSeq * 7 + warehouseSeq * 13) % 471)` (30~500 결정적) |
| reservedQty | 0 |
| totalQty | availableQty |

slip COMPLETED 시 차감 (수량 1~10) 을 충분히 견디는 분포 (최소 30 보장).

idempotency: id (deterministic UUID) EXISTS 체크 + 중복 시 skip. JdbcTemplate 직접 INSERT
(BeforeExecutionGenerator 가 Hibernate 6 의 `@UuidGenerator` 를 항상 덮어쓰므로 deterministic
UUID 보장 위해 native SQL 사용).

## 2. Slip 100건 + SlipLine ~300건 분포

### 2.1 status × type × deliveryTag 매트릭스

| Status \ Type | OUTBOUND DAY | OUTBOUND STACK | OUTBOUND RETURN_RENTAL | INBOUND null | 합계 |
|---|---|---|---|---|---|
| DRAFT | 5 | 1 | 1 | 3 | 10 |
| SAVED | 8 | 2 | 1 | 4 | 15 |
| SENT | 4 | 0 | 2 | 4 | 10 |
| ACCEPTED | 4 | 1 | 1 | 4 | 10 |
| PROCESSING | 4 | 1 | 1 | 4 | 10 |
| INSPECTING | 4 | 1 | 1 | 4 | 10 |
| COMPLETED | 4 | 1 | 1 | 4 | 10 |
| SHIPPING | 5 | 0 | 0 | 0 | 5 |
| DELIVERED | 7 | 2 | 1 | 0 | 10 |
| CONFIRMED | 4 | 0 | 0 | 1 | 5 |
| REJECTED | 1 | 1 | 1 | 2 | 5 |
| **합계** | **50** | **10** | **10** | **30** | **100** |

- SHIPPING/DELIVERED 는 OUTBOUND 한정 (Slip.ship/deliver 가 SlipType.INBOUND 거부)
- INBOUND CONFIRMED 는 COMPLETED 직접 → CONFIRMED (도메인 invariant)
- REJECTED 는 ACCEPTED 단계까지 진전 후 reject() — memo 에 `[반려: 사유] ` prepend

### 2.2 도메인 메서드 transition 패턴

```java
// 도메인 메서드 chain 으로 target status 까지 단계별 전이.
private void applyTransitions(Slip slip, SlipSpec spec) {
    SlipStatus target = spec.targetStatus();
    if (target == SlipStatus.DRAFT) return;
    slip.save();
    if (target == SlipStatus.SAVED) return;
    slip.send();
    if (target == SlipStatus.SENT) return;

    if (target == SlipStatus.REJECTED) {
        // ACCEPTED 단계까지 진전 후 reject (memo 에 사유 prepend).
        slip.accept(EMPLOYEE_LOGIN_IDS.get((idx + 1) % 16));
        slip.reject("재고 불일치 — 수량 재확인 필요");
        return;
    }

    slip.accept(...);  if (target == SlipStatus.ACCEPTED) return;
    slip.process();    if (target == SlipStatus.PROCESSING) return;
    slip.complete();   // PROCESSING → INSPECTING (Slice A hotfix 의미: 출고완료=검수단계 진입)
    if (target == SlipStatus.INSPECTING) return;
    slip.inspect(...); if (target == SlipStatus.COMPLETED) return;

    if (spec.type() == SlipType.OUTBOUND) {
        slip.ship();    if (target == SlipStatus.SHIPPING) return;
        slip.deliver(); if (target == SlipStatus.DELIVERED) return;
    }
    slip.confirm();
}
```

### 2.3 도메인 invariant 가드 — 회피 패턴

| invariant | seeder 회피 |
|---|---|
| INSPECTING 단계는 completedAt 비어있어야 함 | `complete()` 가 INSPECTING 으로만 전이 (완료시각 X), `inspect()` 가 COMPLETED + completedAt 기록 — 도메인 메서드 그대로 사용 시 자동 보장 |
| COMPLETED 후만 SHIPPING 가능 | applyTransitions 의 if-cascade 순서 보장 (INSPECTING → COMPLETED → SHIPPING) |
| SHIPPING/DELIVERED INBOUND 거부 | spec.type() == OUTBOUND 가드 + INBOUND 분포에 SHIPPING/DELIVERED 0건 |
| RETURN tag OUTBOUND 거부 (RETURN.direction=INBOUND) | RETURN 대신 RETURN_RENTAL (반납, OUTBOUND-direction) 사용 |
| reject 가능 단계 = SENT/ACCEPTED/INSPECTING | seeder 는 ACCEPTED 단계 후 reject (균등) |

### 2.4 SlipLine 신규 4 필드 — 이카운트 매핑 (V12 migration)

`docs/migration/ecount-reference/20260509_091636.png` (이카운트 판매입력 UI) 18 필드 중
SlipLine 컬럼에 매핑되는 4 필드:

| field | 값 | 비고 |
|---|---|---|
| unit_price_with_vat | `unitPrice * 1.1` | VAT 10% 포함 단가 |
| supply_amount | `unitPrice * quantity` | 공급가액 (lineTotal 동일 값, 의미상 별도 컬럼) |
| vat_amount | `supply_amount * 0.1` | 부가세 |

V12 migration: 모두 NULL 허용 (기존 라인 row 호환). SlipLine.create / changeQuantity /
changeUnitPrice 모두 자동 재계산.

```java
// SlipLine.java — 신규 helper.
private static BigDecimal computeVat(BigDecimal supplyAmount) {
    return supplyAmount.multiply(new BigDecimal("0.1")).setScale(2, RoundingMode.HALF_UP);
}
private static BigDecimal computeUnitPriceWithVat(BigDecimal unitPrice) {
    return unitPrice.multiply(new BigDecimal("1.1")).setScale(2, RoundingMode.HALF_UP);
}
```

### 2.5 Slip 헤더 필드 (이카운트 판매입력 18 필드 매핑)

| 이카운트 필드 | Slip 컬럼 | seeder 값 |
|---|---|---|
| 슬립번호 | slipNo | `2026/{MM}/{DD}-{seq}` 결정적 |
| 일자 | slipDate | 2026-01-01 ~ 2026-05-09 (idx % 129 분포) |
| 거래처 | partnerId / partnerName | 50개 partner 순환, partnerName="거래처-P-2026-{NNNN}" |
| 출고/입고 창고 | sourceWarehouseId / destinationWarehouseId | V2 HQ-001 |
| 요청자 | requesterId | 16 employee loginId 순환 |
| 배송태그 | deliveryTag | DAY 50 / STACK 10 / RETURN_RENTAL 10 / null 30 |
| 메모 | memo | 30건 프로젝트명, 10건 감리주소, 60건 표준 + 인수자 번호 |
| 출고인 | dispatcherUserId / dispatcherSignedAt | accept() 자동 기입 |
| 검수인 | inspectorUserId / inspectorSignedAt | inspect() 자동 기입 |
| 배송기사 | driverName / driverPhone | SHIPPING+ OUTBOUND 만 — 10명 driver 풀 순환 |

memo 에 임베드한 정보 (감리주소 / 인수자 전화번호 / 프로젝트명) 는 도메인에 별도 컬럼이
없으므로 시드 단계 메모 필드에 결합 — 향후 Stage 3+ 에서 컬럼 분리 시 migration 별도.

## 3. DeliveryBatch 30건 분포

| 가상 status | batch 건수 | seeder 처리 |
|---|---|---|
| PREPARED | 10 | batch 만 생성 (markSms\* 호출 X) |
| IN_PROGRESS | 10 | batch + markSmsSent() |
| COMPLETED (SMS 발송 완료) | 8 | batch + markSmsSent() |
| EXPIRED | 2 | batch + markSmsFailed("EXPIRED 시뮬") + tokenExpiresAt 만료 |

DeliveryBatch entity 에 status 컬럼이 없음 — 사용자 spec 의 "entity 에 없으면 추가" 옵션
대신 기존 도메인 의미 보존. 가상 status 는 `smsSentAt` / `smsLastError` / `tokenExpiresAt`
조합으로 표현 가능 (관리자 화면 분기 충분).

| field | 값 |
|---|---|
| driverName | 10명 풀: 김배송/이운송/박물류/최운반/정수송/강택배/조이동/윤보내/임가져/한받기 |
| driverPhone | "010-1000-{0001~0010}" |
| batchDate | 2026-01-15 + (batchIdx/10)*30 + driverSeq (4월/5월 분포) |
| batchToken | DeliveryBatch.create() 자동 (base64url 64자, SecureRandom 48 bytes) |
| tokenExpiresAt | batchDate + 1일 23:59:59 (도메인 default) |

slip-service 동일 트랜잭션에서 SHIPPING/DELIVERED/CONFIRMED OUTBOUND 슬립 (driver 정보 보유)
0~2건을 batch 와 매핑 — `DeliveryBatch.addSlip(slip)` (양방향 일관성).

## 4. 컴파일 검증

```bash
./gradlew :services:inventory-service:compileJava :services:slip-service:compileJava
# BUILD SUCCESSFUL in 5s
```

## 5. 실행 방법

```bash
# inventory-service
INVENTORY_SEED_TEST_DATA=true SPRING_PROFILES_ACTIVE=dev \
  ./gradlew :services:inventory-service:bootRun

# slip-service (Slip + DeliveryBatch 동일 toggle)
SLIP_SEED_TEST_DATA=true SPRING_PROFILES_ACTIVE=dev \
  ./gradlew :services:slip-service:bootRun
```

H2 in-memory (profile=local) + dev 동시 활성 시: profile precedence 는 SPRING_PROFILES_ACTIVE
순서 — `dev,local` 또는 `local,dev` 명시 (현재 application.yml 의 H2 fallback 은 profile=local
단일).

## 6. UUID 비공개 가드 (memory feedback_uuid_no_user_visibility)

| 사용자 노출 식별자 | seeder 값 |
|---|---|
| slipNo | "2026/{MM}/{DD}-{seq}" — 모든 화면/로그 표시 |
| partnerCode | "P-2026-{NNNN}" — UI 표시, partner UUID 비공개 |
| modelName | "TEST-MODEL-{NNNN}" — 라인 표시, product UUID 비공개 |
| 창고 코드 | "HQ-001" / "VH-001" — UI 표시, warehouse UUID 비공개 |
| driverPhone | "010-1000-{NNNN}" — 그룹 키, batch UUID 비공개 |

## 7. 향후 작업 (Stage 3 / Stage 4)

- Stage 3: signature/audit log 시드 (PR #99 W10-4 source LINK/APP 분포)
- Stage 4: cross-service E2E 시나리오 (slip COMPLETED → inventory deduct → accounting journal)
- Stage N: 이카운트 본격 데이터 마이그 (현 Stage 2 시드는 "테스트용 분포" — 실제 17,000+ 슬립
  마이그는 별도 Phase 11+ migration runner)
