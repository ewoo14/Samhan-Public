---
name: product-master-registration
description: 품목 등록/관리 고도화 에픽 — 종류 2구분(단일/세트, 2026-06-16 #493 정정), 상품/비상품(재고게이트), 자동완성 방향키 전역. 개발책임자 2026-06-15.
metadata:
  type: project
---
🔴 **2026-06-16 정정 (PR #493 머지, `75c4daca`)**: ① 품목 종류 **3구분→단일/세트 2구분**(D-SMP-02, '세트구성품' 종류 폐기 — 구성품 지정은 세트측 ComponentsModal 에서만 단일품목 선택. 판넬/리모컨/유연호스=단일품목, 노출⊥구성품 독립 축). ② 싱글자재=가짜 MATERIAL 폐기, 실 카탈로그 품목으로 통합(A안 D-SMP-01 — 1WAY 대형 공청=PC1BWCK3NW). ③ **다음 에픽 = 품목 노출/구성품 모델 재설계**: 다중 카테고리 노출(estimate_category 단일→M:N) + 카테고리별 표시순서(일괄 자동조정) + 세트 구성품 정렬(실내기→실외기→판넬→리모컨→자재, 각 종류 내 기본 먼저, 드래그 reorder). ④ 대기결정: 멀티 세트 단가 구성품 합산 동적화(견적 금액 변동 가능, 정책 확인). 아래 원 에픽 요구(특히 #1 3구분)는 이력.

🟢 **2026-06-19 핵심 완료 확인 + 재고 세트 제외(#508 `970110a5`)**: 정찰 결과 등록폼(`ProductFormPage` 종류 단일/세트·상품/비상품 goodsType·카테고리·사양·가격·`CreateProductRequest` 완비)·노출 M:N(#494)·구성품 정렬(#495)·`AsyncAutocomplete` 방향키 모두 기구현(에픽 ~98%, 세트구성품 부모검색은 #493서 폐기). **개발책임자 재고 모델(2026-06-19): 세트(BUNDLE)는 재고 무관·세트 내부 구성품만 재고·단일+구성품(SINGLE)만 재고** → 재고게이트 `isInventoryExcluded`=`!goods()||"BUNDLE".equals(productType)` **5경로**(StockService inbound/deduct/adjust·StockInstanceService create/inboundBatch·InboundInspectionService completeInspection[교차 적발 우회]) 적용. **세트는 싱글세트(271)뿐 아니라 상업멀티(72)에도** → category 아닌 productType 기준(개발책임자 주의). productType wire=product-service ProductSummaryResponse `from`+3 endpoint(ProductInternalControllerIT 계약). 미해결 P3(reserve/release/audit 기존 balance defense=현재 도달 불가, 후속).

2026-06-15 개발책임자 결정. 결정 #6(시드1회→DB원천)이 요구하는 '인앱 품목 관리 화면'의 본격 구현. **착수 순서 = 이 에픽 먼저**, G1+G2(estimate-app 카탈로그 DB전환 [[quotation-estimate-app-state]])는 다음.

**요구사항:**
1. **품목 종류 3구분** 등록 폼: 일반품목 / 세트(BUNDLE) / 세트구성품. 모델링 = 일반품목→SINGLE 무부모, 세트→BUNDLE, 세트구성품→SINGLE+부모세트 BundleComponent 링크 필수(+category *_PART, usageScope NONE). 종류는 (productType, 부모링크 유무)로 표현.
2. **세트구성품 등록 = 부모 세트 검색 필수 + 자동완성**. 세트구성품 표시정책(개발책임자): **견적서(기본)=세트명 1줄 / 견적서(세트상세)+출고전표=구성품 폭발**(기존 estimate-app·`EstimateToSlipConverter` 구현 — 등록 슬라이스는 부모링크만 보장).
3. **상품/비상품 구분(신규)**: 비상품(운임·영업수수료·설치비)=재고 미생성. 게이트 3지점 = `StockInstanceService.create`/`inboundBatch`, `StockService.inbound` + `ProductSummary`/`ProductSummaryResponse` goods 전파(serialManaged 배선 패턴 복제). 신규 명시 enum 필드(死필드 `inventoryQtyMgmt` 재활용보다 명확). 비상품용 카테고리(서비스/요금, serial_managed=false) 신설. **견적/전표 라인엔 사용 가능(재고만 차단)**.
4. **자동완성 전역 표준**: 위/아래 방향키 선택 + Enter 확정 + Esc 닫기, 활성 옵션 하이라이트. 자동완성 쓰는 **모든 기능 일관** → 공용 `AsyncAutocomplete`(design-system) 한 곳 표준화로 전 소비처 반영.

**현재 상태(중복 회피)**: 품목 등록 폼 자체 없음(`ProductCatalogPage`=관리 전용: 목록·노출토글·구성품편집·순서). `POST /products` 있으나 `CreateProductRequest` 빈약(종류/분류/세트 못 받음→SINGLE 단품만). 구성품 편집은 '세트 안에서 자식 추가'(요구는 역방향=구성품에서 부모검색). 운임/수수료/설치비 카탈로그 전무. 관련: [[project_serial_inventory_model]] [[feedback_enum_expansion_check_constraint]] [[project_item_exposure_and_menu_5cat]] [[feedback_chip_ui_multi_input]].
