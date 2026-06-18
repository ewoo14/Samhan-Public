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

## 다음 (수식 빌더 후속 슬라이스)
F2(거래처별 DC 설정 UI) · F3(번들 자동구성=실내기 1대당 판넬/리모컨/유연호스 자동포함 설정화) · F4(옵션 매칭) · F5(estimate-app 설정 기반 전환) · F7(VAT/카드수수료 중앙화). + G1 카탈로그 DB 잔여(슬3-2 specDetailMap DB 승격). 멀티 세트 동적가격(#19)=정책 gate.

기획서 `.claude/tmp/estimate-formula-builder-plan.md`. [[project_quotation_estimate_app_state]] [[project_estimate_spec_data_sources]] [[feedback_stacked_pr_ci_false_green]]
