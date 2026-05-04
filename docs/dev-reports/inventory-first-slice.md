# Inventory Service 첫 슬라이스 개발 리포트

> **슬라이스**: inventory-first-slice | **base commit**: eb611bf | **머지 PR**: (PM 통합 후 기재)

본 슬라이스는 SamhanLogis 의 6번째 마이크로서비스(`inventory-service`)를 처음 도입하면서
`product-service` 에 internal-token 기반 service-to-service 보안 채널을 함께 추가한다.
3-layer 함수 단위 문서화 체계(Javadoc + springdoc-openapi + dev-reports)는 본 슬라이스부터
의무 적용한다 (memory: `feedback_function_documentation.md`).

## BE (Team-Inventory BE)

### 도메인 메서드

#### Warehouse (창고 마스터, plan §3.1, **4-tier**)
- `Warehouse.create(code, name, type, address, displayOrder, description)`: 새 창고 인스턴스 생성. code 중복 검증은 service 레이어가 담당. type=VIRTUAL 이면 이후 이동전표가 IN_TRANSIT 단계 스킵.
- `Warehouse.rename(name)` / `changeType(type)` / `changeAddress(address)` / `changeDisplayOrder(displayOrder)` / `editDescription(description)`: 부분 수정 mutator (PATCH 시맨틱).
- `Warehouse.isVirtual()`: type==VIRTUAL 판정 — 이동전표 워크플로우 분기에 사용.
- `WarehouseType` enum: `HEADQUARTERS(본사창고) / VEHICLE(차량재고) / CONSIGNMENT(거래처위탁) / VIRTUAL(가상창고)` — Plan §3.1 4-tier 분류 그대로.

#### StockBalance (잔량 집계, 낙관적 락)
- `StockBalance.create(productId, warehouse)`: (productId, warehouse) 단위 신규 잔량 레코드 (모두 0, version=0).
- `StockBalance.addInbound(amount)`: 입고 — availableQty + totalQty 동시 가산. amount<=0 이면 IllegalArgumentException.
- `StockBalance.reserve(amount)`: 예약 — available→reserved 이동. 가용 부족 시 IllegalStateException (서비스 레이어에서 CONFLICT 매핑).
- `StockBalance.release(amount)`: 예약 해제 — reserved→available 복원.
- `StockBalance.deduct(amount, fromReservation)`: 차감 — fromReservation true 면 reserved 풀, false 면 available 풀에서 빼고 totalQty 도 동시 감소.
- `StockBalance.adjust(delta)`: 실사 조정 — 부호 적용. 차감 결과 음수면 IllegalStateException → CONFLICT.

#### StockLot (FIFO 차감 단위)
- `StockLot.create(productId, warehouse, lotNo, quantity, receivedAt, unitCost)`: 일반 입고로 새 lot 생성. AVAILABLE 상태 시작.
- `StockLot.createFromTransfer(..., transferId)`: 이동전표 입고용 lot — sourceTransferId 로 출처 추적.
- `StockLot.deduct(amount)`: 잔량 차감. quantity==0 이 되면 자동 SOLD_OUT 전이.
- `StockLot.markInTransit()` / `markAvailable()`: 이동전표 ship/reject 시 source lot 상태 토글.
- `StockLot.adjustQuantity(newQuantity)`: 실사 조정으로 잔량 절대값 재설정. SOLD_OUT↔AVAILABLE 전이 자동.

#### StockMovement (감사 로그, append-only)
- `StockMovement.of(lotId, productId, warehouseId, movementType, quantityDelta, referenceType, referenceId, note, actorUserId)`: 변동 이벤트 1건 기록. occurredAt=now(). quantityDelta 부호로 입출 구분.

#### StockTransfer (이동전표 헤더, state machine)
- `StockTransfer.create(transferNo, source, destination, reason, reasonDetail, requesterId)`: REQUESTED 상태로 시작. source==destination 이면 IllegalArgumentException.
- `StockTransfer.addLine(line)`: 라인 추가 (cascade ALL).
- `StockTransfer.submitForApproval()`: REQUESTED→PENDING_APPROVAL.
- `StockTransfer.approve(approverId)`: REQUESTED/PENDING_APPROVAL→APPROVED. approvedAt 기록.
- `StockTransfer.reject(approverId, reasonText)`: REQUESTED/PENDING_APPROVAL→REJECTED. reasonText 가 있으면 reasonDetail 갱신.
- `StockTransfer.ship()`: APPROVED→SHIPPED. 가상창고 source/destination 한쪽이라도 있으면 IN_TRANSIT 스킵하고 즉시 RECEIVED 로 점프.
- `StockTransfer.markInTransit()`: SHIPPED→IN_TRANSIT (실물 창고 간 한정).
- `StockTransfer.receive()`: SHIPPED/IN_TRANSIT→RECEIVED. receivedAt 기록.
- `StockTransfer.confirm(approverId)`: RECEIVED→CONFIRMED. confirmedAt 기록.
- `StockTransfer.cancel(callerId)`: REQUESTED/PENDING_APPROVAL/APPROVED 단계까지만 가능 → CANCELED. SHIPPED 이후는 회수 절차 별도 (현 슬라이스 미구현).
- `StockTransfer.isVirtualSkip()`: ship() 시 IN_TRANSIT 스킵 여부 판정.
- 모든 상태 전이 위반은 `BusinessException(CONFLICT)` 로 통일.

#### StockTransferLine
- `StockTransferLine.create(transfer, productId, requestedQuantity)`: 라인 생성 — shipped/received quantity 0 으로 시작.
- `StockTransferLine.recordShipment(quantity, sourceLotId)`: ship() 시 출하 결과 기록.
- `StockTransferLine.recordReceipt(quantity, destinationLotId)`: receive() 시 신규 lot ID 기록.

### Service 메서드

#### WarehouseService
- `WarehouseService.listAll()`: read-only. displayOrder ASC + 활성만.
- `WarehouseService.getOne(id)`: read-only. NOT_FOUND 가드.
- `WarehouseService.create(req)`: code 중복 검증 (existsByCodeAndIsDeletedFalse) → 영속화. 중복이면 CONFLICT.
- `WarehouseService.update(id, req)`: PATCH 시맨틱 — null 이 아닌 필드만 적용. code 변경은 미지원.
- `WarehouseService.delete(id, callerId)`: BaseEntity.markDeleted 위임 (soft delete).

#### StockService
- `StockService.inbound(req, actorUserId)`: ProductClient.requireExists 로 productId 검증 → StockLot 생성 + StockBalance 가산 + INBOUND movement (단일 트랜잭션). product 미발견 시 NOT_FOUND, 5xx 시 INTERNAL_ERROR.
- `StockService.reserve(req, actorUserId)`: balance.reserve() + RESERVE movement. 가용 부족 또는 version 충돌 1회 재시도 후 실패 시 CONFLICT.
- `StockService.release(req, actorUserId)`: balance.release() + RELEASE movement. 예약 부족 시 CONFLICT.
- `StockService.deduct(req, actorUserId)`: 사전 lot 합계 검증 → FIFO 순회 (`findAvailableLotsForFifo`) → lot 별 deduct + balance.deduct + DEDUCT movement (lot 1건당 1 movement). 합계 부족 시 즉시 CONFLICT.
- `StockService.adjust(req, actorUserId)`: balance.adjust(delta) + ADJUST movement. lot 단위 분배는 별도 운영 정책 (현 슬라이스 미구현).
- `StockService.sumLotQuantities(productId, warehouseId)`: read-only. AVAILABLE lot 합계.
- 트랜잭션 경계: 모든 mutation 메서드는 클래스 레벨 `@Transactional` 적용. read-only 만 명시적 readOnly=true.
- 낙관적 락 정책: `applyWithRetry` 가 OptimisticLockException/OptimisticLockingFailureException 1회 재시도 후 실패 시 CONFLICT 매핑. IllegalStateException → CONFLICT, IllegalArgumentException → INVALID_INPUT.

#### StockTransferService
- `StockTransferService.create(req, requesterId)`: source/destination warehouse 로드 → 동일 ID 검증 → 라인 productId 일괄 ProductClient.lookup → transferNo 채번 (TR-YYYYMMDD-NNN) → 헤더+라인 영속화.
- `StockTransferService.approve/reject/ship/receive/confirm/cancel(id, ...)`: 도메인 메서드 위임. NOT_FOUND/CONFLICT 가드.
- `StockTransferService.getOne(id)` / `list(status, pageable)`: read-only 페이지 조회. status 가 null 이면 전체.
- `StockTransferService.nextTransferNo(date)`: prefix `TR-yyyyMMdd-` 의 발행 건수 + 1. 동시 충돌은 DB unique constraint 으로 방어 (별도 retry 정책 후속).

### Controller endpoint

| Method | Path | 권한 | Status | 비고 |
|---|---|---|---|---|
| GET | `/inventory/warehouses` | 인증된 모든 역할 | 200 | List&lt;WarehouseResponse&gt; |
| GET | `/inventory/warehouses/{id}` | 인증된 모든 역할 | 200/404 | WarehouseResponse |
| POST | `/inventory/warehouses` | MASTER/MANAGER/DEVELOPER | 201/409 | code 중복 시 409 |
| PATCH | `/inventory/warehouses/{id}` | MASTER/MANAGER/DEVELOPER | 200/404 | PATCH 시맨틱 |
| DELETE | `/inventory/warehouses/{id}` | MASTER/MANAGER/DEVELOPER | 204 | soft delete |
| GET | `/inventory/balances?productId=` | MASTER/MANAGER/DEVELOPER/WAREHOUSE/INVENTORY | 200 | Page&lt;StockBalanceResponse&gt; |
| GET | `/inventory/lots?productId=&warehouseId=` | 〃 | 200 | Page&lt;StockLotResponse&gt; |
| GET | `/inventory/movements?lotId=&productId=&warehouseId=` | 〃 | 200 | occurredAt DESC |
| POST | `/inventory/lots/inbound` | MASTER/MANAGER/WAREHOUSE/INVENTORY | 201/404/409 | StockLotResponse |
| POST | `/inventory/reserve` | MASTER/MANAGER/DEVELOPER/SALES/WAREHOUSE/INVENTORY | 200/409 | ReservationResponse |
| POST | `/inventory/release` | 〃 | 200/409 | ReservationResponse |
| POST | `/inventory/deduct` | 〃 | 200/409 | DeductionResponse, FIFO |
| POST | `/inventory/adjust` | MASTER/MANAGER/INVENTORY | 200/409 | DeductionResponse |
| GET | `/inventory/transfers?status=` | MASTER/MANAGER/DEVELOPER/SALES/ACCOUNTANT/WAREHOUSE/INVENTORY | 200 | Page&lt;TransferResponse&gt; |
| GET | `/inventory/transfers/{id}` | 〃 | 200/404 | TransferDetailResponse |
| POST | `/inventory/transfers` | MASTER/MANAGER/WAREHOUSE/INVENTORY | 201/400/404 | TransferDetailResponse |
| POST | `/inventory/transfers/{id}/approve` | MASTER/MANAGER/INVENTORY | 200/404/409 | 〃 |
| POST | `/inventory/transfers/{id}/reject` | 〃 | 200/404/409 | RejectRequest body |
| POST | `/inventory/transfers/{id}/ship` | MASTER/MANAGER/WAREHOUSE/INVENTORY | 200/404/409 | 가상창고면 즉시 RECEIVED |
| POST | `/inventory/transfers/{id}/receive` | 〃 | 200/404/409 | 〃 |
| POST | `/inventory/transfers/{id}/confirm` | MASTER/MANAGER/INVENTORY | 200/404/409 | 〃 |
| POST | `/inventory/transfers/{id}/cancel` | 〃 | 200/404/409 | APPROVED 단계까지만 |

총 22 endpoint (창고 5 + 재고 잔량/이력 3 + 재고 mutation 5 + 이동전표 9).

권한 매트릭스 출처: Plan §4 표. `X-User-Id` 헤더는 gateway 가 주입.

### Service-to-Service Client

- `ProductClient.lookup(productIds)`: gateway 우회 직접 호출 (`http://product-service/products/internal/lookup`). X-Internal-Token 헤더로 인증. 4xx → INVALID_INPUT, 5xx/네트워크 실패 → INTERNAL_ERROR, 응답 항목 수&lt;요청 수 → NOT_FOUND. envelope 의 `data` 키를 `Map<String,Object>` 로 받아 `ObjectMapper.convertValue` 로 ProductSummary 리스트 변환 (shared ApiResponse 의 Jackson 호환성과 무관).
- `ProductClient.requireExists(productId)`: 단건 검증 편의 메서드.
- `ProductClient` batch 한도: 100건 (LOOKUP_BATCH_MAX). 초과 시 INVALID_INPUT.
- `InternalTokenGuard`: `@PostConstruct` 부팅 검증 — prod 프로파일 + dev 기본 토큰 조합이면 IllegalStateException 으로 부팅 거부, 비프로덕션이면 경고만 로깅. inventory-service 와 product-service 양쪽 동일 패턴.

### product-service 측 (본 슬라이스 보강)

- `ProductInternalController.lookup(LookupRequest)`: `POST /products/internal/lookup`. InternalTokenFilter 가 X-Internal-Token 검증 후 진입하므로 별도 @PreAuthorize 불필요. 401 = 토큰 누락/불일치, 200 = List&lt;ProductSummaryResponse&gt;.
- `InternalTokenFilter.doFilterInternal(...)`: `/products/internal/**` 경로만 가드. SecurityContext 에 ROLE_INTERNAL + `system-internal` principal 주입. 그 외 경로는 HeaderAuthenticationFilter 에 위임.

### 시드 데이터 (V2__seed_inventory_warehouses.sql)

Plan §3.1 의 4-tier 분류 그대로:
- `HQ-001` 본사창고 (HEADQUARTERS)
- `VH-001` 1호차 차량재고 (VEHICLE)
- `CS-001` 거래처 위탁창고 (CONSIGNMENT)
- `VR-001` 가상창고 (VIRTUAL) — 삼성 직배/서비스 인보이스 시 IN_TRANSIT 스킵

## FE (Team-Inventory FE)

### 신규 컴포넌트

- `WarehouseSelector` (`clients/web/design-system/src/components/WarehouseSelector/`)
  - 창고 선택 dropdown. native `<select>` + `FormField` 통합.
  - Props 8개: `warehouses` / `value` / `onChange` / `label` / `placeholder` / `hideVirtual` / `disabled` / `error` / `required`.
  - VIRTUAL 창고는 옵션 라벨에 "(가상)" 표기 + 선택 시 우측 "가상" Badge(warning variant) 표시.
  - `hideVirtual={true}` 토글로 VIRTUAL 옵션을 목록에서 제외 (출고/이동 화면 권장 사용).
  - 비활성 창고(`active: false`)는 native `<option disabled>` + `optionInactive` 클래스로 회색/이탤릭 처리.
  - controlled 컴포넌트 (내부 state 미보유) — `value`/`onChange` 만으로 동작.
  - Storybook 5 stories: Default / HideVirtual / WithInactive / Empty / WithError.

### 디자인 시스템 export

`clients/web/design-system/src/index.ts` 에 `./components/WarehouseSelector` re-export 추가:
- `WarehouseSelector` (컴포넌트)
- `Warehouse` (interface)
- `WarehouseType` (literal union: `'HEADQUARTERS' | 'VEHICLE' | 'CONSIGNMENT' | 'VIRTUAL'`) — BE enum 과 동일
- `WarehouseSelectorProps` (interface)

소비자(앱 패키지)에서 `import { WarehouseSelector, type Warehouse } from '@samhan/design-system'` 형태로 사용.

### 의존성 변경

- 없음. 기존 React 18 + Storybook 8 + Vite 5 스택만 사용. 신규 NPM 패키지 추가 0건.

### 빌드 검증

| 명령 | 결과 |
| --- | --- |
| `npm install` | 380 packages 설치 성공 |
| `npm run build` (tsc + vite + dts) | 통과 — `dist/index.js` 24.75 kB, `dist/style.css` 15.18 kB |
| `npm run build-storybook` | 통과 — `WarehouseSelector.stories-*.js` 7.36 kB 산출 확인 |

### 한국어 JSDoc

`WarehouseSelector.tsx` / `index.ts` / `*.stories.tsx` 의 모든 public symbol(컴포넌트, props interface 각 필드, `Warehouse` interface 각 필드, `WarehouseType` 각 멤버 의미)에 한국어 JSDoc 부착. `feedback_function_documentation.md` (3-layer 문서화 의무) 준수.

## QA (Team-Inventory QA)

### IT 클래스
- `AbstractPostgresIT`: 싱글턴 컨테이너 패턴 (PR #13 race condition 사고 회피, product-service 동일 hotfix 패턴)
- `WarehouseRepositoryIT`: V2 시드 4개 (HQ-001/VH-001/CS-001/VR-001) + partial unique + soft-delete 후 재등록 + VIRTUAL 존재 (시나리오 4건)
- `StockRepositoryIT`: FIFO ORDER BY received_at ASC + warehouse/product 격리 + @Version 충돌 (시나리오 3건)
- `InventoryControllerIT`: 권한 매트릭스 (SALES POST 403 / SALES inbound 403 / 미인증 403) + 입고→출고 FIFO + 재고 부족 409 + MANAGER 등록 201→GET 200 (시나리오 6건)
- `StockTransferControllerIT`: 라이프사이클 REQUESTED→APPROVED→SHIPPED→RECEIVED + VIRTUAL source 즉시 RECEIVED 점프 + SHIPPED 에서 reject 409 + WAREHOUSE approve 403 (시나리오 4건)

총 IT 시나리오 17건. **PM 통합 단계에서 BE 실제 시그니처 (Warehouse.create 6 인자, StockLot int quantity, endpoint `/inventory/lots/inbound` 등) 에 맞춰 재작성**.

### 시나리오 fixtures
- `services/inventory-service/src/test/resources/fixtures.http`: 시나리오 8건
  - 1) 창고 목록 조회 (SALES)
  - 2) 신규 창고 등록 (MANAGER)
  - 3) 입고 lots/inbound (WAREHOUSE)
  - 4) 출고 — FIFO (WAREHOUSE)
  - 5) 예약 → 해제
  - 6) 재고 조정 (INVENTORY)
  - 7) 창고간 이동 — 전체 라이프사이클 (REQUESTED→APPROVED→SHIPPED→RECEIVED)
  - 8) 가상창고(VR-001) source 시도 → ship() 즉시 RECEIVED 점프 시연

### QA 산출물
- `docs/qa/inventory-service-report.md` — IT 결과 표 + 권한 매트릭스 22 endpoint × 7-tier role 전수 + 핵심 시나리오 8건 + 스크린샷 placeholder
- `docs/qa/inventory-first-slice/screenshots/README.md` — 스크린샷 캡처 가이드

### 알려진 제약
- Docker 미가용 환경에선 IT 자동 skip (DockerAvailableCondition)
- BE 시그니처 정렬 → PM 통합 단계에서 IT 4개 + fixtures.http + qa_report 모두 BE 실제와 정렬 완료 (Phase 4)

## DevOps

### 인프라 변경
- **신규 모듈** (BE 가 작성, DevOps 영향 없음 — 점검만 수행):
  - `services/inventory-service/build.gradle` (Spring Boot + JPA + Flyway + RestClient + Testcontainers + springdoc)
  - `services/inventory-service/src/main/resources/application.yml` (port 8085, DB inventory_db, Eureka 자동 등록)
- **인프라 파일 수정 없음** — 본 슬라이스의 모든 인프라 의존성이 이미 사전 등록되어 있음:
  - `inventory_db`: `infrastructure/postgres/init/01-create-databases.sql:8` 존재
  - `uuid-ossp`/`pgcrypto` 확장 (inventory_db): `02-extensions.sql:17-19` 존재
  - API Gateway 라우트 `/api/inventory/**`: `services/api-gateway/src/main/resources/application.yml:54-60` 존재 (`lb://inventory-service` + StripPrefix=1 + JwtAuthentication)
  - `INTERNAL_AUTH_TOKEN` / `JWT_SECRET` env var: `infrastructure/.env.example:31, 36` 존재
- **CI 영향 없음** — `.github/workflows/ci.yml` 의 `assemble`/`test` 가 전 모듈 와일드카드, 테스트 리포트 아티팩트 패턴 (`services/*/build/reports/tests/test/`) 도 와일드카드. `settings.gradle` 등록만 추가되면 자동 픽업 (BE 가 이미 등록).

### 검토 산출물
- `docs/devops/inventory-service-review.md`: 인프라 7항목 점검 + 보안 가드 + CI 영향 + 모니터링 + Slip 슬라이스 권고 4항목 + Plan 의도적 변경 (Q1=택B, WarehouseType 4-tier)

### 후속 슬라이스 권고
- **Phase 2 마무리**: Electron skeleton (Inventory UI 첫 활용처), Storybook GitHub Pages 배포 (누적 컴포넌트 13종)
- **Slip Service 슬라이스 (Phase 3)**:
  - 재고 차감 동기 REST 트리거 (`/inventory/deduct` — 보상 트랜잭션)
  - stock_movement RabbitMQ 이벤트 발행 → Slip 수정이력 / Dashboard 위젯 구독
  - VIRTUAL 창고 패턴 Slip 으로 확장 (서비스 인보이스 시 재고 차감 skip)
  - StockTransfer RECEIVED → 자동 입고 Slip 발행 여부 결정
- **운영 부채**:
  - `JwtSecretGuard` 추가 (prod 프로파일 + dev 기본값 사용 시 부팅 거부 — `InternalTokenGuard` 와 동일 패턴)
  - 권한 매트릭스 controller IT 자동 생성 (각 endpoint × 7 role)
- **본 슬라이스 신규 보안 작업**: product-service 에 `InternalTokenGuard` 신규 적용 (이전 product 슬라이스에서는 미적용 — inventory→product 내부 호출이 본 슬라이스부터 시작되므로 필수)

## Plan 대비 의도적 변경

- **Q1 = 택B 게이트웨이 우회 (BE)**: 서비스 간 호출은 gateway 를 통하지 않고 Eureka 의 `lb://product-service` 를 통한 직접 호출로 결정. 인증은 공유 시크릿(X-Internal-Token) 으로 단순화. 후속 슬라이스에서 mTLS 등 강화 검토.
- **WarehouseType 4-tier 채택 (개발책임자 결정 2026-05-04)**: BE 초안은 OWNED/LEASED/VIRTUAL 3-tier (전통 ERP 분류) 로 단순화 시도했으나, PM 통합 검증 단계에서 개발책임자가 Plan §3.1 의 4-tier (HEADQUARTERS/VEHICLE/CONSIGNMENT/VIRTUAL) 채택 결정. 이유 — 차량재고/거래처위탁이 비즈니스 도메인의 핵심 분류이며, Phase 6 모바일 듀얼 앱 (창고원/거래처) 컨셉과 직접 매핑됨. V2 시드 + WarehouseType enum + Warehouse Javadoc + FE WarehouseSelector + QA IT 모두 4-tier 로 정렬. 향후 슬라이스에서 `Warehouse.active`/`Warehouse.ownerPartnerId` 등 위탁/차량 부가 메타 추가 검토.
- **3-layer 문서화 의무 신규 적용 (BE)**: 본 슬라이스부터 (1) 한국어 Javadoc + (2) springdoc-openapi + (3) dev-reports 누적 의무. product-service 는 본 슬라이스에서만 retroactive 보강 (InternalTokenGuard / ProductInternalController / SecurityConfig swagger permitAll). 다른 5 기존 마이크로서비스는 별도 retro 슬라이스에서 보강 예정.
- **springdoc-openapi 도입 범위 (BE)**: 본 슬라이스에서는 의존성 + UI 노출 + 핵심 endpoint @Operation 만. yaml 자동 추출 Gradle 태스크는 후속 인프라 슬라이스로 분리.
