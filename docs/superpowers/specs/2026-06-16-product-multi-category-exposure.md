# 품목 다중 카테고리 노출 + 카테고리별 순서 (M:N)

> 에픽 = 품목 노출/구성품 모델 재설계([[product-master-registration]]). 슬라이스 1 = 다중 카테고리 노출 + 카테고리별 표시순서. (슬2 = 세트 구성품 정렬.)
> 개발책임자 2026-06-16: 한 단일 품목(판넬/리모컨/유연호스 등)을 여러 견적 카테고리(홈멀티/싱글세트/상업멀티/구형)에 **중복 노출** + **카테고리별 표시순서**(순서 변경 시 같은 카테고리 내 일괄 자동조정).
> 워크플로우 = Opus 계획/조기PR → Codex 개발 → Opus 5-agent → Codex 5-agent → 수렴 → PM 머지. (Opus↔Codex 2모델.)

## 1. 목표 / 현황 (정찰 확정)
- **현황**: `Product.estimate_category` **단일 컬럼**(1품목=1카테고리), `Product.display_order` **전역 단일 컬럼**(productCategory 레벨 순서). EstimateCategory enum = HOME_MULTI/SINGLE_SET/COMMERCIAL_MULTI/LEGACY(구형)/OTHER.
- **set 지점**: `ProductSheetSyncService` TAB_MAPPINGS(탭→단일 EstimateCategory 고정) + `PATCH /products/{code}/usage`(`markUsageManual` 단일) + Create/UpdateProductRequest + sync displayOrder(행순번).
- **read 지점**: `ProductRepository.findExposedCatalog`(productCategory+usageScope 필터, ORDER BY display_order — estimate_category 필터 없음) → `EstimateCatalogInternalController.products(category)`(EstimateCategory→ProductCategory 매핑 후 조회) → estimate-app `db-catalog.js`(카테고리별 별도 HTTP). `ProductCatalogResponse.estimateCategory`(단일) 데스크톱 표시.
- **estimate-app 수용성**: ✅ 카테고리별 별도 호출 구조라 한 품목이 여러 카테고리 응답에 자동 포함. **estimate-app 코드 무변경**.

## 2. 모델링 — M:N 단일 원천 (dual-source 금지, [[feedback_spec_sync_full_db_distribution_check]] 교훈)
**신규 테이블 `product_estimate_exposure`** = 품목 견적 노출의 **단일 진실원**. `products.estimate_category`/`products.display_order` 의 견적노출 역할을 대체한다.

| 컬럼 | 값 |
|---|---|
| id | UUID PK |
| product_id | UUID FK → products(id) |
| estimate_category | VARCHAR(20) (HOME_MULTI/SINGLE_SET/COMMERCIAL_MULTI/LEGACY/OTHER) |
| display_order | INTEGER (카테고리별 순서, NULLS LAST) |
| + BaseEntity 7 audit + is_deleted | |

- **UNIQUE 활성 인덱스** `(product_id, estimate_category) WHERE is_deleted=FALSE` — 품목×카테고리 1행.
- **인덱스** `(estimate_category, display_order) WHERE is_deleted=FALSE` — 카테고리별 정렬 조회.
- **usageScope 는 품목 레벨 유지**(products.usage_scope). 규칙: usageScope ∈ {ESTIMATE, BOTH} 일 때만 견적 노출 의미. M:N 행 0개 = 견적 미노출.
- **`products.estimate_category` 컬럼**: 마이그 후 **읽지/쓰지 않음**(M:N 단일 원천). 컬럼은 이번 슬라이스에서 drop 하지 않고 **deprecated 보존**(롤백 안전), 후속 cleanup 마이그에서 제거. (sync/usage/create-update/response 전부 M:N 으로 전환해 dual-write 금지.)

## 3. BE 변경 (Codex 구현, product-service)
1. **마이그 Vxx**: `product_estimate_exposure` 생성 + 기존 데이터 이식 — `INSERT ... SELECT id-derived, p.id, p.estimate_category, p.display_order FROM products WHERE estimate_category IS NOT NULL AND is_deleted=FALSE`. 멱등(NOT EXISTS). **fresh Postgres probe 검증**([[feedback_migration_fresh_postgres_probe]]).
2. **엔티티**: `ProductEstimateExposure` + `Product` 1:N(`@OneToMany` 또는 별도 repository). `ProductEstimateExposureRepository`.
3. **`ProductRepository.findExposedCatalog`**: M:N LEFT JOIN — `JOIN ProductEstimateExposure pee ON pee.product_id=p.id AND pee.estimate_category=:estimateCategory AND pee.is_deleted=false` + `ORDER BY pee.display_order ASC NULLS LAST, p.model_code ASC`. (productCategory 인자 제거 또는 estimate_category 로 대체 — `EstimateCatalogInternalController.products(category)` 가 M:N 카테고리로 직접 조회.)
4. **`PATCH /products/{code}/usage`** + `markUsageManual`: `UpdateProductUsageRequest.estimateCategory`(단일) → **`estimateCategories: List<EstimateCategory>`**. usageScope=NONE/PARTNER_ORDER → M:N 행 전부 soft-delete. ESTIMATE/BOTH → 요청 목록으로 M:N replace(upsert+미포함 soft-delete). usageScopeManual=true.
5. **`ProductSheetSyncService`**: 탭별 estimate_category 결정 → **M:N upsert**(품목이 그 탭에 분류되면 (product, category) 행 보장, display_order=행순번). 1탭=1카테고리 현행 유지(다중탭 분류는 데이터 자연 발생 — 같은 modelCode 가 여러 탭에 있으면 여러 M:N 행). rowHash 캐시 키 영향 점검.
6. **Create/UpdateProductRequest**: `estimateCategory`(단일) → `estimateCategories: List<EstimateCategory>` + 서비스 M:N 반영.
7. **`PUT /products/display-orders`**: 카테고리 컨텍스트 추가 — `DisplayOrderRequest{modelCode, estimateCategory, displayOrder}` → 해당 (product, category) M:N 행의 display_order 갱신. **같은 estimateCategory 내 일괄 재번호**(개발책임자: 순서 변경 시 같은 카테고리 일괄 자동조정). 기존 productCategory-동일군 검증(D-PCE-02) → estimateCategory-동일군 검증으로 전환.
8. **`ProductCatalogResponse`**: `estimateCategory`(단일) → **`estimateCategories: List<{category, displayOrder}>`**(또는 카테고리 목록 + 별도 순서). 데스크톱 표시용.
9. IT: M:N 다중 노출(한 품목 2카테고리 조회 양쪽 등장), PATCH /usage 다중 카테고리 replace, 카테고리별 display_order 정렬, fresh Postgres probe, estimate-catalog 무회귀.

## 4. FE 변경 (Codex 구현, desktop ProductCatalogPage)
- **노출 설정 = 다중 카테고리 칩**([[feedback_chip_ui_multi_input]]): 단일 select → design-system `TagChip` 다중 선택(홈멀티/싱글세트/상업멀티/구형 중 N개). PATCH /usage 가 `estimateCategories` 전송.
- **카테고리별 순서**: 카테고리 필터 선택 후 드래그 reorder → `PUT /display-orders`(modelCode, **선택 카테고리**, displayOrder) 일괄. 표시순서 컬럼은 (선택 카테고리) 기준.
- **목록 표시**: `estimateCategories` 다중 칩 렌더(품목 행에 여러 카테고리 뱃지).
- mock/vitest 동기화 — **slip 라인 BUNDLE usageScope=BOTH 회귀 가드 유지**([[project_local_stack_qa_gotchas]] bundle-set-options).

## 5. estimate-app (무변경, 검증만)
- `db-catalog.js` multiCatalog/singleSets `/products?category=` 카테고리별 호출 그대로. 한 품목이 여러 카테고리 응답에 등장 → 각 탭에 노출. **회귀 검증만**(종합견적서 탭별 품목 노출 동일).

## 6. QA 계획 (Docker 실서버 — 매 라운드, [[feedback_no_fake_data_ever]])
- **다중 노출 실증**: 한 단일 품목(예 판넬 PC6NUDK1NW)을 홈멀티+싱글세트 2카테고리 노출 설정 → `/products?category=HOME_MULTI` 와 `?category=SINGLE_SET` 양쪽 응답에 등장(실 curl + estimate-app 탭).
- **카테고리별 순서**: 같은 품목이 카테고리A 3번·카테고리B 1번 등 독립 순서 + 순서 변경 시 같은 카테고리 일괄 재번호(실 화면).
- **마이그 무손실**: 기존 estimate_category 품목 전부 M:N 1행 이식(카테고리별 0-노출 품목 수 전수 query, [[feedback_spec_sync_full_db_distribution_check]]).
- **estimate-app 무회귀**: 종합견적서 카테고리 탭별 품목/단가 동일.
- **slip 라인 무회귀**: 전표 라인 자동완성(usageScope=PARTNER_ORDER) BUNDLE/단품 노출 동일(bundle-set-options).
- 데스크톱 노출 설정 다중 칩 + 카테고리별 드래그 실 화면 캡처.

## 7. 워크플로우 / 머지 게이트 (고정)
Opus 계획/조기PR → Codex 개발+게시 → Opus 5-agent(리뷰+fix+실QA) → Codex 5-agent(교차+fix+실QA) → 0에러 수렴 → PM 머지.
**머지 게이트**: fix 후 Opus 재리뷰 무결 시 PM 판단 머지. error0·skip0 + CI green + 마이그 fresh probe + 다중노출/카테고리순서 실QA + estimate-app·slip 무회귀.
