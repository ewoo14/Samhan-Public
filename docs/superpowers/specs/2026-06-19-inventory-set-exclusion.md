# 재고게이트 세트(BUNDLE) 제외 — 단일+구성품만 재고

> 2026-06-19 개발책임자 지시: "세트 품목은 재고와 상관 없음 / 세트 내부 구성품만 재고로 잡히는것 / 따라서 단일, 구성품만 재고로 잡혀야함". 품목 고도화 에픽([[product-master-registration]]) 상품/비상품 재고게이트 정합.

## 0. 모델 (개발책임자 확정)
- **재고 대상 = productType=SINGLE 품목**(단일 standalone + 세트 내부 구성품[SINGLE, BundleComponent 링크]) 중 **상품(GOODS)**.
- **재고 제외 = (a) productType=BUNDLE(세트 SKU 자체) + (b) 비상품 NON_GOODS(운임/영업수수료/설치비)**.
- 세트는 견적/전표서 BundleExpander 로 구성품 전개 → 구성품(SINGLE)이 재고 차감/입고. 세트 SKU 는 재고 없음.

## 1. 문제 / 갭 (정찰)
- 현 `StockService.inbound/deduct/adjust`·`StockInstanceService.create/inboundBatch` 게이트 = `isNonGoods`(=`!product.goods()`)만 차단. **BUNDLE 미제외**.
- 견적→전표 경로(EstimateService.expand→EstimateToSlipConverter→SlipService.complete)는 세트 전개되어 구성품만 게이트 도달(현 갭 없음). 단 **수동 입고/직접 inventory 호출/이카운트 연동**서 세트 SKU(GOODS 기본)가 게이트 도달 시 재고 생성(갭). → 게이트 enforce 필요.
- `ProductSummary`(inventory-service client) 가 `productType` **미노출**(세트 판별 불가). slip-service ProductSummary 는 productType 보유(wire-format 별개).

## 2. 구현 (Codex)
### inventory-service
- **`ProductSummary`(client record)에 `productType`(또는 `isBundle`) 추가**. product-service summary endpoint 응답에 productType 포함 확인/추가(slip-service 는 이미 받음 — inventory-service wire-format 동기).
- **게이트 강화**: `isNonGoods` → `isInventoryExcluded`(또는 유지+추가): `!product.goods() || "BUNDLE".equals(productType)`. `StockService.inbound/deduct/adjust` + `StockInstanceService.create/inboundBatch` 동일 적용. 세트 도달 시 no-op skip(비상품과 동일 반환 패턴).
- IT: BUNDLE 품목 inbound/deduct → 재고 미생성(skip), 단일/구성품(SINGLE GOODS) → 정상, 비상품 → skip(회귀).

### product-service (필요 시)
- summary 응답(inventory-service 가 read 하는 endpoint)에 productType 노출(이미면 무변경).

## 3. parity / 안전
- 견적→전표 정상 경로는 이미 세트 전개 → 본 변경은 **방어(직접/수동/이카운트 경로 세트 제외)** 강화. 정상 흐름 회귀 0(구성품·단일·비상품 동작 불변).
- 견적 금액/계산 무관(재고 게이트만).

## 4. 검증
- IT: 세트(BUNDLE GOODS) inbound→skip·deduct→0, 단일 GOODS→정상, 비상품→skip 유지. Testcontainers.
- Docker 실QA: 세트 품목 직접 입고 시도→재고 미생성 + 구성품 입고→재고 생성(실 DB).
- 변경 모듈 전체 test.

## 5. 리뷰
조기 PR → Codex 구현 → Opus + Codex 교차 리뷰 → Docker 실QA → 머지. (inventory 게이트·방어 강화 → BE 포커스.)
