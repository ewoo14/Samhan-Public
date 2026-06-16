# 싱글 자재 → 품목(Product) 편입

> 에픽 = estimate-app 외부 시트 잔여 제거 + 품목 등록/관리 고도화([[product-master-registration]], [[project_sheets_to_db_full_migration]]). 슬라이스 = 싱글 자재 품목 편입.
> 개발책임자 2026-06-16: "싱글의 자재 같은 경우 품목으로 편입이 필요." (G2 머지 후 착수 선택.)
> 워크플로우 = Opus 계획/조기PR → Codex 개발 → Opus 5-agent → Codex 5-agent → 수렴 → PM 머지. (Opus↔Codex 2모델, Fable5 제외.)

## 1. 목표 / 현황
estimate-app 이 시트('싱글 자재가격' 28행 D2~D29)에서 읽던 **싱글 자재**(유선리모컨·콘센트 등 단가 lookup)를 **품목(Product) 1급 엔티티**로 편입 → 품목 등록/관리 화면에서 관리. 현재 = product-service `MaterialPrice` 테이블(28행, `ProductLookupSheetSyncService.syncMaterialPricesTab` 시트 sync) → `EstimateCatalogInternalController.materialPrices()` 가 `{name:price}` 맵 반환 → estimate-app `db-catalog.js`. 내부 원가 lookup(usageScope NONE, 판매 라인 아님).

## 2. 모델링 (정찰 확정)
- **`ProductCategory.MATERIAL` 이미 존재**(V3 `chk_pm_product_category` CHECK 포함) → **enum/CHECK 마이그 불필요**.
- **`ProductGoodsType.NON_GOODS`·`usageScope NONE` 존재** → 자재 = `MATERIAL`+`NON_GOODS`(재고 미생성)+`usageScope NONE`(견적/주문 라인 직접선택 비노출, 기존 게이트 재사용).
- **matKey 보존**: 단일세트 단가계산은 `Product.setMaterialKey`(D4/D7/D8, SINGLE_SET 가 참조하는 "자재 합계" 행)와 **별개** — 자재 편입은 setMaterialKey 무변경. 단가계산 무회귀.
- **무손실 핵심**: material-prices 엔드포인트는 `{자재명: price}` 맵만 반환(`computed_formula` **미사용**, GAS 잔재) → Product(name+price)로 재구현해도 맵 동일 → estimate-app 단가 무변경.

**자재 Product 필드값:**
| 필드 | 값 |
|---|---|
| productCategory | `MATERIAL` |
| goodsType | `NON_GOODS` |
| usageScope | `NONE` |
| productType | `SINGLE` |
| estimateCategory | null |
| name | MaterialPrice.name (예 "유선리모컨") |
| modelCode | 자재 식별 코드(개발책임자 확인 §6 — 기본 name 기반 안정 코드) |
| releasePrice = deliveryPrice | MaterialPrice.price |
| unit | "EA" |
| status | ACTIVE |

## 3. BE 변경 (Codex 구현, product-service)
1. **시드/마이그**: 28개 MaterialPrice → Product(MATERIAL) 1회 편입. 결정적 UUID(자재 name/key 파생, [[project_seed_product_uuid_catalog]] 패턴 — @UuidGenerator 버그 회피 jdbcTemplate native INSERT) 시더 또는 Flyway. 멱등(재실행 무중복).
2. **`EstimateCatalogInternalController.materialPrices()` 재구현**: `materialPriceRepository.findAll()` → `productRepository.findByProductCategoryAndIsDeletedFalse(MATERIAL)` → `MaterialPriceResponse{name, price}` 매핑. **응답 shape `{name, price}` 보존**(db-catalog.js 무변경). `ProductRepository.findByProductCategoryAndIsDeletedFalse` 신규.
3. **자재 등록/수정 지원**: `CreateProductRequest`/`ProductService.create·update` 가 MATERIAL 카테고리 + NON_GOODS + usageScope NONE 수용(품목 폼에서 자재 등록·단가 수정). 재고 게이트 no-op(기존 NON_GOODS 패턴 [[product-master-registration]] D-PMR-02).
4. **시트 sync 은퇴**: `syncMaterialPricesTab` 시드-1회/은퇴(Product 가 자재 원천 → 시트 재sync 가 편집 덮어쓰기 방지, 에픽 "시드1회→DB원천"). 결정 §6.
5. IT: material-prices 엔드포인트 Product 기반 동등(28 자재 name/price), 자재 등록 201, 단일세트 단가계산 무회귀.

## 4. FE 변경 (Codex 구현, desktop 품목 화면)
- `ProductCatalogPage`/`ProductFormPage`: MATERIAL 카테고리 자재 **목록 노출 + 편집 + 등록**(category=MATERIAL 선택, goodsType NON_GOODS, usageScope NONE). 견적/주문 라인 선택에서는 usageScope NONE 으로 비노출(기존 가드).
- mock/vitest 동기화.

## 5. QA 계획 (Docker 실서버 — 매 라운드)
- material-prices 엔드포인트 Product 기반 반환 = 기존 28 자재 동일(실 curl).
- **단일세트 견적 단가 무회귀**(자재 편입 전후 동일 단가) — 실 견적 라인 캡처(핵심 회귀가드).
- 품목 관리 화면에 자재(MATERIAL) 목록 노출 + 편집 실화면 캡처.
- 자재 등록(품목 폼 category=MATERIAL) 201 + 목록 반영.
- 가짜 금지([[feedback_no_fake_data_ever]]).

## 6. 개발책임자 확인(비차단, 기본값 진행)
1. **자재 편집 범위**: 품목 화면에서 **add/edit 가능**(기본, "관리"=편집) vs sync-only(목록만). → 기본 **편집 가능**(에픽 시드1회→DB원천).
2. **modelCode 형식**: name 기반 안정 코드(기본) vs materialKey "D2"(시트행 기반·불안정). → 기본 **name 기반**.
3. **시트 sync 은퇴 vs 유지**: Product 원천화 시 syncMaterialPricesTab 은퇴(기본) vs 시드-only 유지.

## 7. 워크플로우 (고정)
Opus 계획/조기PR → Codex 개발+게시 → Opus 5-agent(리뷰+fix+실QA) → Codex 5-agent(교차+fix+실QA) → 0에러 수렴 → PM 머지. 머지 게이트=error0·skip0+CI green+단일세트 단가 무회귀 실QA.
