# 슬라이스 INV-S / S1 — 시리얼 인스턴스 재고 기반

> 2026-06-01 머지(#336 `c043e4b9`). spec `docs/superpowers/specs/2026-05-31-serial-instance-inventory-design.md`(§4 S1) / plan `docs/superpowers/plans/2026-05-31-serial-instance-s1.md` / 결정 D-SER-01~04.
> BE 전용. 입출고 전표 연동은 S2~S4 후속.

## 1. 목적
개별시리얼 품목(에어컨/판넬)의 재고 최소단위를 **UUID 인스턴스**(`stock_instances`)로 모델링. 품목코드=분류 그룹, UUID=시리얼 키. batch 품목(부자재)은 기존 stock_lots 유지.

## 2. 아키텍처
- **관리방식 판정 = 카테고리 속성**: product-service `categories.serial_managed`(에어컨 계열 true) → `ProductSummaryResponse.serialManaged` → inventory `ProductSummary.serialManaged` 소비. inventory 는 플래그만 읽고 판정(카테고리 의미는 product 단일 소유).
- inventory `stock_instances`: serial-managed 품목만 인스턴스 생성. status 전이(soft-delete 대신). FIFO=received_at, 역-FIFO=outbound_partner_code+outbound_at.
- S1 은 입출고 전표 연동 없이 인스턴스 수동 생성/조회 API 만.

## 3. 함수 단위 문서 (3-layer)
### product-service
- `Category.serialManaged` + `markSerialManaged(boolean)` — 관리방식 카테고리 플래그.
- `ProductSummaryResponse.serialManaged` — `from(p)=p.getCategory().isSerialManaged()`(@Transactional 경계). `/products/internal/lookup` 노출.
- V9: categories.serial_managed 컬럼 + 에어컨 계열 UPDATE. HvacProductSeeder markSerialManaged(비-Flyway 컨텍스트 대비).

### inventory-service
- `StockInstance` — `inbound(...)` 정적 팩토리(AVAILABLE) + `ship(partnerCode,slipNo,at)`/`recall()`/`reserve()`/`release()` 상태전이(requireStatus 가드 → BusinessException CONFLICT). BaseEntity + @SQLRestriction.
- `StockInstanceStatus` — AVAILABLE/RESERVED/SHIPPED/RECALLED.
- `StockInstanceRepository` — `findByProductCodeAndStatusOrderByReceivedAtAsc`(FIFO) / `findByOutboundPartnerCodeAndProductCodeAndStatusOrderByOutboundAtDesc`(역-FIFO) / `findByProductId`(인덱스 활용) / `findByProductIdAndStatus` / count.
- `StockInstanceService` — `create`(ProductClient.serialManaged()=false → 409) / `fifoCandidates` / `recallCandidates` / `byProduct`.
- `StockInstanceController` — `/inventory/instances` POST create + GET fifo/recall/by-product. 권한 `inventory.stock-balance`. UUID 비공개(productCode/status/슬립번호 표시).
- V15: stock_instances + FIFO/역-FIFO/product 인덱스 3개.

## 4. 테스트 / QA
- inventory `StockInstanceIT` 12 PASS(skipped=0): serial 생성 / batch 409+body / FIFO·역-FIFO **정확값 isEqualTo** / 상태전이 가드 / recall·release 전이 / soft-delete. 도메인 테스트 warehouseId randomUUID.
- product `ProductInternalControllerIT` 3 PASS: serialManaged true(에어컨)/false(batch)/mixed 값 단언. Category/ProductSummary 테스트.
- 5-team 사이클 N=2 APPROVE(사이클1: 예외 통일·byProduct 전체스캔·FIFO 정확값·recall/release·serialManaged e2e·INDOOR seeder fix).
- **Docker 실 QA PASS**: 실 gateway+JWT+inventory_db/product_db. 인스턴스 생성 201/AVAILABLE, batch(PIPING) 409 차단, FIFO received_at ASC(04-15→05-01→05-10), psql cross-DB(INDOOR_WALL serial_managed=t / PIPING=f).

## 5. 배포
product(V9)→inventory(V15). 순서 위반 시 serialManaged=false 기본 → 인스턴스 생성 409 안전 degrade. 기존 stock_lots/balances/2.6c 무변경(회귀 0).

## 6. 후속 (S2~S4 + 비차단)
- **S2 입고**: 구매전표(INBOUND)→구매/차용 인스턴스 생성(개별)/lot(batch). 전표↔inventory 연동(이벤트 vs REST 결정).
- **S3 출고**: 판매전표(OUTBOUND)→FIFO 인스턴스 소진(SHIPPED+출고처)/수량차감(batch).
- **S4 회수**: 반품/회차→거래처+품목코드 역-FIFO 재입고.
- 2.6c 수량 reserve↔인스턴스 RESERVED 통합 / 2.6d 재고조회 모달 시리얼 표시 / 판넬 카테고리 시드 추가.
