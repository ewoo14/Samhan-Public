# 품목 다중 카테고리 노출 (M:N) + 카테고리별 표시순서 — 에픽 #18 슬1 (PR #494)

> 2026-06-16. 브랜치 `feat/product-multi-category-exposure`. 워크플로우 = Opus 4.8 계획/조기PR → Codex 개발 → Opus 5-agent → Codex 5-agent → PM 종합 → 머지([[temp-multimodel-workflow]]).
> spec: `docs/superpowers/specs/2026-06-16-product-multi-category-exposure.md`.

## 1. 목표

한 단일 품목(판넬/리모컨/유연호스 등)을 여러 견적 카테고리(홈멀티/싱글중대형/상업멀티/구형)에 **중복 노출** + **카테고리별 독립 표시순서**(순서 변경 시 같은 카테고리 내 일괄 자동조정). 기존 `products.estimate_category`(단일 컬럼)·`display_order`(전역 단일)로는 1품목=1카테고리만 가능했던 한계 해소.

## 2. 모델 — M:N 단일 원천

**신규 `product_estimate_exposure`**(V18) = 품목 견적 노출의 단일 진실원.

| 컬럼 | |
|---|---|
| id UUID PK / product_id FK / estimate_category VARCHAR(20) / display_order INTEGER | + BaseEntity 7 audit |
| UNIQUE `(product_id, estimate_category) WHERE is_deleted=false` | 품목×카테고리 1행 |
| INDEX `(estimate_category, display_order) WHERE is_deleted=false` | 카테고리별 정렬 |
| CHECK `chk_pee_category` | enum 5값 |

- **백필**: 기존 `products.estimate_category`→M:N 1행 멱등 이식(NOT EXISTS, `gen_random_uuid()`/pgcrypto).
- `products.estimate_category`·`display_order` = **deprecated 보존**(읽지·쓰지 않음, 롤백 안전, dual-write 금지 — [[spec-sync-full-db-distribution-check]] 교훈). 후속 cleanup 마이그에서 drop 예정.
- `usageScope` = 품목 레벨 유지. ESTIMATE/BOTH 일 때만 노출 의미. M:N 행 0 = 미노출.

## 3. BE (product-service)

- `ProductEstimateExposure` 엔티티(raw productId UUID, 양방향 컬렉션 회피 — cascade/lock 부작용 차단 [[self-invocation-transactional-bypass]]) + repository.
- `findExposedCatalog`(theta-join) + `searchByUsageScope`(admin 리스트, native LEFT JOIN) **둘 다 M:N** — estimate_category 필터 + `e.display_order ASC NULLS LAST, model_code` 카테고리별 정렬. → SINGLE_PART(판넬/부품)도 여러 견적 카테고리 노출 가능.
- `PATCH /usage`: `UpdateProductUsageRequest.estimateCategories: List` replace(비-견적 scope→전체 soft-delete / 미포함→soft-delete / 신규→maxOrder+1). `markUsageManual(usageScope)`(exposure 동기화는 서비스).
- `ProductSheetSyncService`: exposure **additive upsert**(삭제 절대 안 함) + `usageScopeManual` skip + **명시 save**(syncTab self-invocation 트랜잭션 부재 detached flush 보장). `products.estimate_category`/`display_order` 쓰기 중단.
- `PUT /display-orders`: `DisplayOrderRequest.estimateCategory` 추가, 동일 카테고리 강제(D-PCE-02 축 교체) + 요청 순서대로 1..N 재번호(exposure 행) + **대상 카테고리 전체 활성 노출 미포함 시 400**(부분 재번호 붕괴 방지).
- soft-deleted product 의 exposure 동반 soft-delete + `maxDisplayOrder` 활성 product 한정.
- `ProductCatalogResponse.estimateCategories[{category,displayOrder}]` + deprecated 단일 파생.
- `EstimateCatalogInternalController.products` M:N 직접 조회 → **estimate-app 무변경**(카테고리별 별도 HTTP 자동 수용). `/components`(세트 전개)는 부모 productCategory 조회 유지 — 견적 노출과 직교(구성품 노출 무관 포함).

## 4. FE (desktop ProductCatalogPage)

- ToggleCell: 단일 select → **design-system TagChip 다중 칩**(label=카테고리명, value=카테고리별 순서) + '카테고리 추가' Select([[chip-ui-multi-input]]). PATCH `estimateCategories` 배열. all-selected 시 add-Select 숨김.
- `handleSaveOrder`: order 항목에 `estimateCategory`(committedCategory) — 카테고리별 1..N(전 페이지 수집).
- 카테고리 컬럼 다중 Badge(`brand` — productCategory 평문과 구분), 표시순서 컬럼 = 선택 카테고리 exposure 순서(미선택 시 "카테고리별" title 툴팁).
- `ProductCatalogPageModel.ts`(+vitest) 순수 헬퍼(normalizeEstimateCategoryExposures/estimateCategoryValues/exposureDisplayOrder/buildCategoryDisplayOrderInputs). estimateCategories=[] 가 legacy 로 fallback 하던 버그 수정.
- mock: estimateCategories 다중노출 fixture + usage 배열 replace + display-orders estimateCategory 검증. 🪤 **slip 라인 BUNDLE usageScope=BOTH 회귀가드 보존**(bundle-set-options, 슬립 검색 별도 데이터셋).

## 5. 카테고리 라벨 정정 — SINGLE_SET = "싱글중대형" (개발책임자)

- SINGLE_SET 사용자 노출 라벨 "싱글세트/싱글 세트/단일 세트" → **"싱글중대형"** 전역 통일(27파일/97 spot: design-system·desktop·order-app·mobile·estimate-app UI·인쇄). 사유 = 세트 아닌 단일 품목도 포함.
- **enum 식별자 SINGLE_SET + 시트 탭 매처("싱글 세트", `_단가인상`, range-map, code.js 수식, legacy GAS SINGLE_NAME) 보존** — 변경 시 sync/단가계산 파손. 계약 가드(full-menu-contract·sp-07) + calc-fidelity 로 보존 검증. [[item-exposure-and-menu-5cat]].

## 6. 워크플로우 / 리뷰 / QA

- **Opus 5-agent**(BE/FE/Designer/DevOps/QA): BE P1(sync save 누락)·P2(create scope) fix, Designer P2(칩 접두 중복)·P3, DevOps P2(reorder IT 공백). FE 0 blocking. → Opus 직접 fix.
- **Codex 5-agent 교차**: Opus fix 무회귀 확인 + reorder IT(비-@Transactional)·estimateCategories=[] fix·orphan exposure·display-orders 가드 + Designer P3·dead method. → Codex 직접 fix.
- **실서버 QA**(게이트웨이 :8080 + product-service V18): AJ060MXHNBC1 단일품목 홈멀티+싱글중대형 2카테고리 동시 노출 실증(API) + 데스크톱 UI 다중칩 실캡처(`docs/qa/product-multi-category-exposure/`).
- **검증**: product-service test green, partner-order test green, estimate-app calc-fidelity 77, FE typecheck/vitest, playwright 18/18(product-catalog 11 + 시트매처 계약가드).

## 7. 결정 기록

- **D-PCE-03**: 견적 노출 = M:N(`product_estimate_exposure`) 단일 원천. products.estimate_category/display_order deprecated.
- **D-PCE-04**: sync exposure = additive only(삭제 금지) + usageScopeManual skip. 미노출 전환은 수동 PATCH /usage.
- **D-PCE-05**: display-orders = 대상 카테고리 전체 활성 노출 포함 요청만(부분 1..N 붕괴 방지).
- **D-PCE-06**: 세트 노출 + 구성품 미노출 → 구성품은 세트 전개 시 노출 무관 포함(/components usageScope 미필터, 의도). 미노출=단독 판매 여부만.
- **D-LABEL-01**: SINGLE_SET 라벨 = "싱글중대형"(식별자·시트매처 보존, 라벨만).

## 8. 후속

- products.estimate_category/display_order cleanup 마이그(컬럼 drop) — 별도.
- 멀티(홈멀티/상업멀티) 세트 단가 동적화(#19, 개발책임자 정책 대기).
