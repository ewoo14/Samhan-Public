---
name: project-formula-builder-epic
description: "수식 빌더 에픽 — 종합견적서/주문서 하드코딩 수식 → 메뉴 설정 기반 계산 전환. F1+F6 완결, F2~F7 다음"
metadata: 
  node_type: memory
  type: project
  originSessionId: b9ce13e8-ed39-4a45-89a5-ff3c53f85914
---

수식 빌더 에픽 = estimate-app(종합견적서)·order-app(주문서)의 BE 하드코딩 수식 → **메뉴 설정 기반 계산** 전환 (개발책임자 방향).

## 완결 (2026-06-18)
- **F1 (#499, `ab0093ea`)**: 품목 분류 3단계(catL/M/S — classifyHome/classifyCommercial/classifySingleSet GAS 1:1 포팅) + 고정DC% 인라인 컬럼(변동DC 옆, 빈칸=전역DC, blur 자동저장·저장버튼 없음) + 변동DC 체크박스-only + 분류 관리 메뉴(ProductClassificationsPage) + V20 classification 테이블. 싱글 분류 GAS parity fix(SINGLE_SET이 classifyHome 오라우팅 → 부자재 88%→1%). 종합견적서 시뮬 811품목 단가 차이 0.
- **F6 (#501, `68b0d634`)**: 주문서 product_db 적용(EstimateCatalogClient+BootstrapService, **inc map=인상후 catalog**[price-baseline 2000-01-01 인상전 아님], modelCode→model/hasVariableDiscount→useK2/fixedDiscountRate→고정DC 변환) + DC율 + gateway bootstrap 공개 route + dc-config 단건 @EntityGraph + **비번 4자리 PIN**(@Pattern \d{4}, 거래처코드/사업자번호 10자리는 로그인 ID 별개) + 분기계산/모바일서랍 데스크톱 숨김. 노션 거래처 DC율 재시드 259. 실QA 제이시스템(8428102605).

- **G1 (#502, `a2d36319`)**: estimate-app specDetailMap 런타임 Google Sheets → product-service DB endpoint(`/products/internal/estimate-catalog/spec-detail-map`, 이미 적재된 ProductSpec reshape·신규 스크랩/sync 0). specKey(한글 라벨, ProductSheetSyncService 저장형식)→getSpecDetailMap_ JS 필드명 매핑(home 18/single 21/comm 17/ERV 23 + **판넬 overlay** 타공사이즈/전산볼트간격→cool_kw·cool_power). estimate-app 마지막 런타임 시트 의존 제거(homeDefaults/singleDefaults 잔존→후속). 다모델 Opus5→Codex fix(canary)→Codex 교차(**판넬 회귀 P1 단독 적발**)→판넬fix→Opus 수렴 blocking0. 라이브QA 733모델·판넬 타공860/볼트798. 🪤 #488이 판넬 타공/볼트를 전용 specKey로 정규화 → reshape가 렌더 legacy 필드로 안 돌리면 시트모드 대비 회귀(교차리뷰 적발).

## 다음 (수식 빌더 후속 — 정찰 확정)
**F2 = ✅ 이미 구현됨**(SalesPartnerDcConfigPage 거래처 DC 11컬럼 인라인+CSV+audit, 재작업 불요). **F3(옵션 설정 UI)+F4(번들 자동매칭 룰엔진) = 🔒 게이트**(스펙§4 D1 "옵션 자동매칭 신규 설계·개발책임자 확인"; F4 수동선택은 BundleExpander 기구현, 자동매칭만 신규). **F5 = F3/F4 선행 의존**. **F7(VAT/배분) = 기획서§7 비대상**(우선순위 낮음·현행 유지). **멀티 세트 동적가격 #19 = 🔒 견적금액 변동 정책**. **수식 빌더 Phase 우선순위(기획서§8 A 파라미터/B 템플릿/C 노코드빌더) = 🔒 개발책임자 확정 필요**. **비게이트 자율 후속 = homeDefaults/singleDefaults DB 승격**(3탭 prefetch 완전 제거 → estimate-app 시트 의존 0 완결).

기획서 `.claude/tmp/estimate-formula-builder-plan.md`. [[project_quotation_estimate_app_state]] [[project_estimate_spec_data_sources]] [[feedback_stacked_pr_ci_false_green]]
