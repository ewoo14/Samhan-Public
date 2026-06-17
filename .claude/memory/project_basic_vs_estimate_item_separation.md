---
name: basic-vs-estimate-item-separation
description: 기초품목 관리(물리 SKU 마스터) ↔ 견적품목 관리(판매 카탈로그) 메뉴 2분화 에픽 — 개발책임자 2026-06-17 결정
metadata:
  type: project
---

2026-06-17 개발책임자 결정. 에픽 #18(품목 노출/구성품 모델 재설계, #494/#495 완결) 후속 방향 전환.

## 동기
견적/주문 카탈로그 = 카테고리 컨텍스트를 본질적으로 품은 **판매 도메인**(데이터 실증: 같은 기능 판넬이 시스템별 다른 SKU·단가 — 홈멀티 공청 PC4NUCK4NW 611,050 vs 싱글 PC6EUCK1NW 556,600; 같은 SKU 카테고리별 다른단가 0건). 거기에 번들 전개(싱글세트 실내:실외 6:4/4:6 재배분, BundleExpander)·변동DC(useK2/$L$2)·옵션 SKU 선택까지. 이 판매 복잡도를 물리 품목 마스터에 욱여넣지 말고 분리.

## 결정
- **D-IES-01 분리**: 물리 SKU 마스터 ↔ 판매 카탈로그 분리, SKU 로 연결.
- **D-IES-02 메뉴명**: 마스터 = **"기초품목 관리"**(구 "품목 관리"). 판매 카탈로그 = **"견적품목 관리"**(신규).
- **D-IES-03 참조 제약**: 견적품목 관리에서는 **기초품목 등록분만 선택 추가**(미등록 신규불가, AsyncAutocomplete). 기초품목이 단일 진실원.
- **D-IES-04 세트 구성 소관**: 세트 구성품 구성(bundle_component, 무엇이 들어가나)도 **견적품목 관리** 소관(기초품목은 세트 SKU 존재/종류만).

## 소관
- **기초품목 관리**: SKU·모델명·규격·종류(단일/세트)·납품가/출고가(SKU별 1개)·재고/매입/회계. 신규 등록.
- **견적품목 관리**: 노출 카테고리(M:N, #494)·카테고리별 표시순서(#494)·세트 구성(bundle_component)·구성품 정렬(#495)·판넬/리모컨/자재 옵션·변동DC. 추가=기초품목 선택만.
- **#494 슬1·#495 슬2 자산은 견적품목 관리로 귀속(폐기 아님)**. 현 ProductCatalogPage 2분화. 단가는 SKU 1개로 충분(per-카테고리 override 불요).

## 슬라이스 계획
슬1=견적품목 관리 메뉴/화면 신설(기초품목 선택추가 + 노출M:N 이관, 기초품목=등록전용) → 슬2=세트구성+구성품정렬(#495)+옵션 이관 → 슬3=변동DC + G1 카탈로그 DB 승격(견적품목 도메인 내, Java FORMULA read parity 포함).

## 재배치 / 잔존
- **G1 카탈로그 DB 승격 + Java FORMULA read fix**(google-api-client FORMULA 누락: 상업멀티 useK2 Java 86 vs JS 378, `GoogleSheetsClient`+GsonFactory 가설, `docs/audit/gas-port-fidelity/java-formula-read-discrepancy-investigation.md`) = 폐기 아님 **슬3 재배치**.
- 멀티 세트 동적가격(#19) = **정책 gate**(멀티도 구성품 합산 동적화 시 견적금액 변동 → 개발책임자 결정 필요).
- estimate-app 은 견적품목 관리 데이터 소비자. 데이터 모델(products + product_estimate_exposure + bundle_component) 대체로 유지, **메뉴/화면/참조제약 재구성**이 핵심.

관련: [[project_quotation_estimate_app_state]] [[project_item_exposure_and_menu_5cat]] [[project_product_master_registration]] [[realqa-run-and-false-red]].
