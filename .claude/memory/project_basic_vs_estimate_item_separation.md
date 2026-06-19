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

## 슬라이스 계획 / 진행
- ✅ **슬1 머지(PR #496, `bb21de5f`)**: 견적품목 관리 메뉴/화면 신설(기초품목 선택추가 + 노출M:N 이관 + 카테고리 탭 고정4 + 카테고리 컬럼 캡슐만). 동적 카테고리 추가/삭제=개발책임자 폐기(고정, EstimateCategory enum 유지).
- ✅ **슬2 머지(PR #497, `8c7fe7d8`, 2026-06-17)**: 세트 구성품 모달(ComponentsModal/SortableComponentRow/COMPONENT_KIND_OPTIONS) 기초품목→견적품목 이관 + 기초품목 등록전용화(set-badge 유지) + P2 모달제목 품목명 병기. 듀얼리뷰 **사이클2**: Opus 5-agent+실QA 4/4 → CI mock 회귀 4건 적발·fix → **Codex 교차가 mock-BE false-green P1 적발**(fix 이 mock `updateProductUsage` 에서 PARTNER_ORDER 노출 유지로 바꿨으나, 실 BE `ProductService.syncEstimateExposures` 는 NONE/PARTNER_ORDER 시 활성 노출 soft-delete — `UpdateProductUsageRequest` 계약. Opus 라운드 'BE정합' 오판을 교차가 교정)·fix2 → CI green. 🔵Claude·🟣Codex·🟢PM 종합 PR 게시(실QA 4장 인라인).
- ✅ **슬3-1 머지(`d508a020`/`33fc375f`, 2026-06-17)**: Java FORMULA read parity fix(useK2 GsonFactory/행정렬) + 변동DC 멀티 카탈로그 수동 토글(`variableDiscountManual` V19) + 적재 덮어쓰기 가드 + 게이트웨이 변동DC 라우트/UI/Badge. **실측 검증(2026-06-19)**: DB useK2 COMMERCIAL_MULTI **313/338**·HOME_MULTI **107/119** = 스펙 기대치(~313/~107) 달성(정찰의 '86'은 pre-fix 버그 기록).
- ✅ **슬3-2(specDetailMap DB 승격) = formula-builder G1 #502(`a2d36319`)로 완료.**
- 🔚 **에픽 완료**(슬1·슬2·슬3-1·슬3-2). CATALOG_SOURCE=db 기본전환=3번 G2 소관(#507 완료).
- ✅ **슬4(변동DC 실 단가 적용) = moot — 현행 이미 정확**(2026-06-19 실측+개발책임자 확정): estimate-app `homeUnitPrice`/`commUnitPrice`(index.ejs:4256-4264, GAS 100% 동일) = **변동DC 체크→할인**(`listPrice×(1−finalRate)`, finalRate=고정DC 또는 전역 0.45) / **미체크→기초납품가 그대로**(`sheetPrice`). 개발책임자 확정("체크→할인율 현행대로, 미체크→기초납품가 그대로")+이전 실QA 스크린샷과 일치. 🪤 스펙 `2026-06-17-formula-builder-epic.md:30`("변동DC=전역할인율 영향없이 기초납품가 그대로")가 토글 의미를 **반대로 기술** → 정찰이 "fix 필요" 오판. 실 코드+의도+QA 모두 "체크→할인"=현행 정확. 구현 불요(정확 동작 보존). DB-mode useK2=hasVariableDiscount 전파(db-catalog.js:77)도 동일 경로.

**🪤 슬2 교훈**: mock 은 list-filter(`usageScope IN ESTIMATE/PARTNER_ORDER/BOTH`)가 아니라 **update-behavior(NONE/PARTNER_ORDER → 노출 soft-delete)와 정합**해야 false-green 회피. 듀얼 교차가 단일 라운드 오판 적발한 모범 사례. 리뷰는 채팅 아닌 **PR 에 즉시 게시**(개발책임자 지적) — Claude TM·Codex TM·PM 종합 + Docker 실QA 인라인. ([[ci-test-filter-false-green]] [[dual-5agent-review]] [[review-posting-and-zero-skip]])

## 재배치 / 잔존
- **G1 카탈로그 DB 승격 + Java FORMULA read fix**(google-api-client FORMULA 누락: 상업멀티 useK2 Java 86 vs JS 378, `GoogleSheetsClient`+GsonFactory 가설, `docs/audit/gas-port-fidelity/java-formula-read-discrepancy-investigation.md`) = 폐기 아님 **슬3 재배치**.
- 멀티 세트 동적가격(#19) = **정책 gate**(멀티도 구성품 합산 동적화 시 견적금액 변동 → 개발책임자 결정 필요).
- estimate-app 은 견적품목 관리 데이터 소비자. 데이터 모델(products + product_estimate_exposure + bundle_component) 대체로 유지, **메뉴/화면/참조제약 재구성**이 핵심.

관련: [[project_quotation_estimate_app_state]] [[project_item_exposure_and_menu_5cat]] [[project_product_master_registration]] [[realqa-run-and-false-red]].
