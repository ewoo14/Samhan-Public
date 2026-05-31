---
name: project_seed_product_uuid_catalog
description: 로컬 seeder product UUID 3-DB 정합 — modelName 기반 결정적 UUID single source (2026-05-31 머지
metadata: 
  node_type: memory
  type: project
  originSessionId: 527690b1-44aa-46ea-8abd-38502bfd74d3
---

**로컬 dev seeder 의 product UUID 정합 = product-service modelName 기반 결정적 UUID 가 single source** (2026-05-31 PR #327 `0299191b` 머지 완료).

**배경/문제**: 4개 seeder(product/inventory/slip/partner-order)가 모두 `UUID.nameUUIDFromBytes("samhan-seed:product:" + key)` 로 UUID 유도하지만 key(modelName) 가 서비스마다 달라 — product=Samsung 실모델(`AR05TXEAAWKNEU-01` 등 100개) / inventory·slip=`TEST-MODEL-%04d` / partner-order=하드코딩 `010001` — **product UUID 3-way 교집합 0**. 주문→전환→재고예약 등 cross-service 실데이터 시나리오 재현 불가.

**개발책임자 모델 정정(중요)**: "modelName 을 key 로 쓸 거면 UUID 는 왜 쓰나? UUID 가 key 가 되어야 한다." + "UUID=품목 시리얼 PK, product_code=같은 품목 그룹, 품목코드(1)→UUID(N)." → 이는 [[project_serial_inventory_model]](시리얼 인스턴스 재고)로 이어짐. 단 **2.6c 머지를 위한 즉시 정합은 평면(modelName 결정적 UUID 일치)으로 처리**(고정 UUID 상수 카탈로그는 채택 안 함 — 오버엔지니어링 판단).

**실제 구현(머지됨)**:
- inventory `StockBalanceSeeder`/`InventoryAuditSeeder` + slip `SlipSeeder` + partner-order `PartnerOrderSeeder`: product key 를 product-service `HvacProductSeeder` 의 실 modelName 100개로 통일(`samhan-seed:product:<modelName>` 동일 규칙).
- **product seeder 버그 수정**: `HvacProductSeeder` 가 `forceId(리플렉션)` 후 `productRepository.save` 했으나 `Product @GeneratedValue @UuidGenerator` 가 INSERT 직전 랜덤 UUID 로 덮어씀 → products.id 가 version-4 랜덤. **inventory StockBalanceSeeder 와 동일 jdbcTemplate native INSERT 로 EntityManager 우회** → 결정적 UUID 가 PK 에 직접 저장. Product 도메인 무영향.
- 검증: `AR05TXEAAWKNEU-01` → `01949ab7-e922-35c6-b289-5337d867a0ee` (products/stock_balances/partner_order_lines 동일). 3-DB 교집합 inv∩product=100, po∩product=6, po∩inv=6.

**재시드 절차(중요 — seeder 멱등)**: 모든 seeder 가 결정적 UUID EXISTS 체크로 멱등(데이터 있으면 skip). 재시드하려면 **3-DB product 관련 테이블 TRUNCATE CASCADE → 새 이미지 재기동**. 또 docker-compose.local-all.yml 이 product/inventory/slip/partner-order 에 `SAMHAN_<X>_SEED_TEST_DATA` 토글 미정의 → 재시드 시 환경변수 주입 필요(QA override 로 처리했음).

**Why**: cross-service join 가능 → gateway 경유 양성 시나리오(전환→예약 등) 실데이터 QA 가능. [[feedback_no_fake_data_ever]] 실 QA 전제 인프라.
**How to apply**: seeder 수정 시 product key 는 항상 product-service 실 modelName 기준. JPA save 로 PK 강제 주입 불가(@UuidGenerator) → jdbcTemplate native INSERT 사용.
