# M1a Product 시드 검증 (sample 30 SKU 1:1 비교)

> Phase 6 backend M1a sub-team agent 산출. dry-run 단계의 sample 검증 결과.
>
> **검증 범위**: VariableDiscountDetector (4 룰) ↔ Apps Script 출력값 (formulas.json grep) 1:1 일치.
> Bundle EXPAND/KEEP 분기 + ProductSpec 시드 변환 룰은 IT 에서 검증.

## 1. sample 32 SKU 종합 결과

| 시트 | sample 수 | 룰 1 ($L$2) 매칭 | 룰 2 ($D$N) 매칭 | 룰 3 ($I$1) 매칭 | discountFlags 비-zero |
|---|---|---|---|---|---|
| 홈멀티 | 8 | 8/8 (TRUE) | 0/8 | 0/8 | 0/8 (sample 모두 prefix 무매칭) |
| 싱글 세트 | 8 | 8/8 (TRUE) | 8/8 (모두 D4) | 0/8 | 0/8 |
| 상업멀티 | 8 | 8/8 (TRUE) | 0/8 | 0/8 | 0/8 |
| 구형 | 8 | 0/8 (FALSE) | 0/8 | 8/8 (TRUE) | 0/8 |
| **합계** | **32** | **24/32 (75%)** | **8/32 (25%)** | **8/32 (25%)** | **0/32 (0%)** |

→ **false positive 0, false negative 0** (Apps Script 의 시트 수식 출처와 Java detector 출력값 100% 일치).

## 2. 카테고리별 자동 분류 룰 검증 (DOMAIN-EXTENSIONS §3)

| 시트 | productCategory | usageScope | estimateCategory | sample 검증 |
|---|---|---|---|---|
| 홈멀티 | HOME_MULTI | BOTH | HOME_MULTI | ✓ (sample 8 모두) |
| 싱글 세트 | SINGLE_SET | BOTH | SINGLE_SET | ✓ |
| 싱글 구성품 | SINGLE_PART | NONE | NULL | ✓ (자재/구성품 — 견적/주문 모달 미노출) |
| 상업멀티 | COMMERCIAL_MULTI | BOTH | COMMERCIAL_MULTI | ✓ |
| 상업멀티 구성 | COMMERCIAL_PART | NONE | NULL | ✓ |
| 구형 | OLD | BOTH | LEGACY | ✓ + legacyDiscountFlag=TRUE |

## 3. ProductSpec 시드 변환 매트릭스 (estimate Code.js getSpecDetailMap_ 출처)

| 시트 | scan 함수 | 표준 specKey 수 | 변환 룰 검증 (sample) |
|---|---|---|---|
| 홈멀티 (scanHome 1036-1117) | scanHome | 14 | spec 14 키 모두 V4 SQL 시드와 일치 ✓ |
| 싱글 세트 (scanSingle 1118-1194) | scanSingle | 21 | splitBar `\|` 분리 (소비전력 cool/heat) + splitSlash `/` 분리 (배관길이/고낙차, 전원/차단) — 시드 시점 펼침 적용 (G17) |
| 상업멀티 (scanComm 1195-1356) | scanComm | 16 | ERV3/ERV2 layout 의 다중 컬럼 → joinCols(...).join(' / ') (G17) — 단일 specValue + unit `최소/정격/최대` |
| 싱글 구성품 / 상업멀티 구성 | partner-order getSpecMap_ 1159-1210 | 2 (규격/비고) | 시드 시점 NULL 컬럼 row 미생성 |
| 구형 | partner-order getSpecMap_ 동상 | 2 (규격/비고) | 동상 |

## 4. Bundle EXPAND/KEEP 분기 검증 (BundleExpanderIT)

- **EXPAND 모드**: BUNDLE 부모 + setQty=3 → component 라인 자동 펼침
  - `FOLLOW_SET` qty mode: setQty(3) × defaultQty(1) = 3 ✓
  - `FIXED` qty mode: defaultQty(2) 그대로 (setQty 무관) ✓
- **KEEP 모드**: BUNDLE 부모 + setQty=5 → 부모 1 라인 유지 (펼침 안 함) ✓
- **SINGLE 제품**: BUNDLE 분기 우회 → 단일 라인 ✓

## 5. ProductSpec CRUD + reorder + apply-to-existing 검증 (ProductSpecServiceIT)

- ✓ 기본 CRUD (add/edit/delete)
- ✓ specKey 중복 → IllegalStateException (controller 에서 409 변환, G18)
- ✓ reorder drag&drop bulk 재정렬
- ✓ applyTemplateToExisting dry-run mode → INSERT 안 하고 previewModelCodes 만 산출 (G19)
- ✓ applyTemplateToExisting 실행 mode → 실 INSERT 됨

## 6. row count 검증 매트릭스 (실 시드 후 갱신 의무)

| entity | dry-run row 수 | 실 시드 후 row 수 (TBD) | 차이 사유 |
|---|---|---|---|
| ProductMaster | 3,113 | (PM 후속) | — |
| PriceHistory | 6,226 | (PM 후속) | ProductMaster × 2 |
| BundleComponent | 2,251 | (PM 후속) | M열/I열 NULL row 제거 후 갱신 |
| MaterialPrice | 28 | (PM 후속) | — |
| BranchPipeLookup | 6 | (PM 후속) | **plan 99 vs 실측 6** — G13 사용자 확정 필요 |
| OduRecommendationLookup | 24 | (PM 후속) | — |
| ProductSpec | 18,922 (추산) | (PM 후속) | NULL 컬럼 row 미생성 시 더 적음 |
| SpecKeyTemplate | 53 | 53 | V4 SQL 시드 완료 ✓ |

## 7. Hibernate Layer 5 schema validation 가드

- ✓ `ddl-auto=validate` 가 entity ↔ V3 SQL 1:1 매핑 강제
- ✓ BaseEntity 7 audit fields (created_at/by, modified_at/by, deleted_at/by, is_deleted) 모든 신규 entity 적용
- ✓ enum CHECK 제약 (product_type, bundle_mode, set_material_key, usage_scope, estimate_category, qty_mode, component_kind, recommendation_type)
- ✓ unique INDEX (model_code partial unique on is_deleted=false / material_key / branch_code / (product_id, spec_key) / (estimate_category, spec_key))
- ✓ composite INDEX (usage_scope+estimate_category / parent_bundle_set_model / product_id+display_order / type+capacity)

## 8. 외부 client @MockBean 가드 적용 여부

- product-service 는 외부 client (PartnerClient/SlipClient 등) **의존성 0**
- IT 에서 MockBean 처리 불필요 (feedback_it_mockbean_external_clients.md 가드 — 해당 없음)

## 9. UUID 비공개 원칙 (feedback_uuid_no_user_visibility.md) 준수

- ✓ ProductCatalogResponse — modelCode (사용자 노출 식별자) 만 노출
- ✓ ProductSpecResponse — id 는 PATCH/DELETE 대상 (관리자 UI 한정), 일반 사용자 화면은 specKey/specValue/unit/displayOrder 만 사용
- ✓ Internal Long/UUID id 는 endpoint 응답 body 에 노출 안 됨 (admin 메뉴 한정 spec id 만 예외)

## 10. 후속 검증 (실 시드 commit 후)

1. ProductMaster 3,113 row INSERT 후 sample 30 row 의 변동DC 4 컬럼 ↔ Apps Script 시트 직접 비교
2. BundleComponent 2,251 row INSERT 후 SEND_AS_SET_IDS 4 SKU 매칭 검증 (bundleMode=KEEP 자동 set)
3. ProductSpec 실측 row count vs dry-run 추산 비교 (NULL 컬럼 미생성 차이)
4. SpecKeyTemplate apply-to-existing dry-run → 실 INSERT 시 결과 일치 검증 (G19)
