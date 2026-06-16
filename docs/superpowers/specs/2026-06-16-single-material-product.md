# 싱글 자재 정정 — A안: 실 카탈로그 품목 통합 (가짜 MATERIAL 폐기)

> 에픽 = estimate-app 외부 시트 잔여 제거 + 품목 등록/관리 고도화([[product-master-registration]], [[project_sheets_to_db_full_migration]]).
> 개발책임자 2026-06-16: "싱글의 자재 같은 경우 품목으로 편입이 필요." → 1차 구현(V18 시드)에서 **"모델명이 MAT 으로 시작해서 이상하다"** 지적 → 정찰 결과 **자재는 이미 실모델코드를 가진 기존 카탈로그 품목**임이 드러남 → **A안(실 카탈로그 통합) 채택**.
> 워크플로우 = Opus 계획/조기PR → Codex 개발 → Opus 5-agent → Codex 5-agent → 수렴 → PM 머지. (Opus↔Codex 2모델, Fable5 제외.)

## 1. 근본 정정 사유 (실 DB 정찰)
1차 구현(V18)은 `material_price` 시트 28행(D2~D29)을 **새 Product(MATERIAL) 28개**로 시드했다. 그러나:
- **부품 21종**(FPH-1412XS3·AFR-TC9D·ARR-NK3F …)은 **이미 `SINGLE_PART` 품목으로 존재**한다(예: `model_name=FPH-1412XS3`, 품목명="냉난방 스탠드 자재"). V18 은 동일 품목을 중복 생성.
- **패널/리모컨 7종**(블랙판넬·승강판넬·공청판넬·1WAY 중형/대형 공청·유선/컬러유선 리모컨)은 estimate-app 의 **옵션**이고, 크기·WIFI별로 **여러 실제 카탈로그 모델코드**로 치환된다(공청판넬 → 소형 PC1MWCK3NW / 중형 PC1NWCK3NW / **대형 PC1BWCK3NW** / 360사각 PC6NUCK1N …). 단일 모델코드 1:1 매핑 불가.
- `material_price` 원천에는 **모델코드 컬럼 자체가 없다**(material_key=D2~D29 시트셀, name, price, option_label). 실모델코드는 카탈로그 품목 + estimate-app 옵션-치환 로직에만 존재.
- `model_name` 활성 유니크 인덱스(`ux_products_model_name_active`) 때문에 자재에 실코드를 박으면 기존 `SINGLE_PART` 21종과 충돌 → 그래서 V18 이 충돌 회피용 `MAT-`+md5 해시를 썼고, 그 가짜 코드가 화면에 노출돼 "이상한 모델명" 지적.

**결론**: 가짜 MATERIAL 품목 28개는 잉여이며 잘못된 모델이다. 자재는 이미 실 카탈로그 품목이다.

## 2. A안 결정 (개발책임자 2026-06-16 — [[project_estimate_spec_data_sources]] 연장)
- **가짜 MATERIAL 품목(V18) 폐기**: 마이그 삭제 + dev DB 28행 + flyway 이력 제거.
- **자재 = 실 카탈로그 품목**: 부품=`SINGLE_PART`, 패널/리모컨=`HOME_MULTI`/`COMMERCIAL_MULTI`(PC1BWCK3NW 등). 품목 등록/관리 화면에서 실제 품목으로 관리(이미 가능).
- **싱글 옵션 단가 = 실품목 단가 차액으로 동적 계산**(개발책임자 기존 지시와 일치). estimate-app `calcSetUnitPrice` 가 `partUnitPrice(chosen)-partUnitPrice(base)` 로 SINGLE_PARTS(=BundleComponent→실 Product `deliveryPrice`) 차액 계산 — **이미 구현됨**, 추가 작업 없음.
- **레거시 자재시트 가짜가격 은퇴 아님 — 참조 lookup 으로 보존**: `material_price`(28행)는 데스크톱 `LineLookupReferenceModal` 읽기전용 참조표로만 존속(가격계산 미참여). materialPrices 엔드포인트는 **main(레거시 material_price) 으로 복원**.

## 3. 변경 = 선택적 revert + KEEP (NEW 코드 거의 없음)
**REVERT (materials-as-Product → main 복원):**
- `V18__seed_material_products.sql` **삭제**.
- `EstimateCatalogInternalController.materialPrices()` / `ProductLookupController.listMaterialPrices()` → `material_price` 레거시 복원.
- `MaterialPriceResponse.from(Product)` 제거(`from(MaterialPrice)` 유지).
- `ProductLookupSheetSyncService` 자재탭 sync 은퇴 가드 제거(레거시 sync 복원).
- 관련 IT 3종(EstimateCatalogInternalControllerIT/ProductLookupControllerIT/ProductLookupSheetSyncServiceIT) main 복원.
- desktop `mock.ts` 자재 참조모달 rows → 레거시 D-key 정렬.

**KEEP (자재와 독립한 정당한 개선 — 세트 구성품 모델 완성 + 운영버그 픽스):**
- **usageScope IN-확장**(`ProductRepository.search` + countQuery) — `SlipFormPage` 전표 라인 자동완성 운영버그(PARTNER_ORDER exact-match→0건) 픽스. README 동기화.
- `Create/UpdateProductRequest.usageScope/estimateCategory` 필드 + `ProductService` usageScope 처리 + `applyMaterialDefaults`(MATERIAL 방어적 정규화, 휴면이라 무해).
- `ProductCatalogResponse.productCategory` 노출 + `productCatalogApi` 타입(FE 카테고리 표시).
- **세트 구성품 모델**: `ComponentsModal` `기본`(isDefault) 토글 + `componentKind`(실내기/실외기/판넬/리모컨) + `ProductAutocomplete` 자동완성, `componentsModalModel(.test)`, `ProductFormPage` componentKind, `productFormModel(.test)`.
- **estimate-app baseline `isDefault` 일관화**(index.ejs: `isDefault===true` 우선 + `feat="기본"` fallback) + `default-component-baseline.test.js`.
- BundleExpander(견적/주문/전표 폭발)는 이미 `p.isDefault` 사용 — 무변경.

## 4. 세트 구성품 6대 기능 (개발책임자 확장 요구 — 모두 충족 확인)
| # | 요구 | 상태 |
|---|---|---|
| a | 세트가 자신의 구성품 설정 | ✅ ComponentsModal |
| b | 구성품 검색 자동완성 | ✅ ProductAutocomplete |
| c | 스펙 = 구성품 내역 합산 표시 | ✅ estimate-app 구성품 spec 집계 |
| d | 옵션 변경 금액차 = 실 구성품 단가 차액 동적 | ✅ calcSetUnitPrice partUnitPrice 차액 |
| e | 실내기/실외기 가격 = 세트 총액 기준 동적 | ✅ BundleExpander split 6:4/4:6 |
| f | 구성품 `기본` = 옵션 미적용 시 견적/주문/전표 기준선 | ✅ isDefault 토글 + BundleExpander/estimate-app isDefault |
| g | 품목 종류(실내기/실외기/판넬/리모컨) 설정 | ✅ ProductFormPage componentKind |

## 5. QA 계획 (Docker 실서버 — 매 라운드, [[feedback_no_fake_data_ever]])
- **품목 관리 화면에 실모델코드 노출**: 공청 패널 검색 → `PC1BWCK3NW`(판넬 1way 무풍+공기청정 대형 WIFI) 등 실 카탈로그 모델코드 표시. **`MAT-`해시 0건**(회귀 가드).
- 자재 참조모달(`material-prices`) = 레거시 28행 복원(실 curl + 화면).
- **단일세트 견적 단가 무회귀**(자재 정정 전후 동일) — 실 견적 라인 캡처.
- 세트 구성품 6기능(a~g) 실화면 캡처(기본 토글·componentKind·자동완성·스펙합산·옵션 델타).
- 전표 라인 자동완성 usageScope=PARTNER_ORDER 실 HTTP(운영버그 회귀가드).

## 6. 워크플로우 / 머지 게이트 (고정)
Opus 계획/조기PR → Codex 개발+게시 → Opus 5-agent(리뷰+fix+실QA) → Codex 5-agent(교차+fix+실QA) → 0에러 수렴 → PM 머지.
**머지 게이트(개발책임자)**: fix 진행 시 다음 리뷰 1회 추가 필수. Codex fix 후 Opus 재리뷰 완전 무결 시 PM 판단 머지. error0·skip0 + CI green + 단일세트 단가 무회귀 실QA + 품목화면 실모델코드 캡처.
