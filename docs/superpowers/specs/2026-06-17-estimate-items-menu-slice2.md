# 기초품목↔견적품목 분리 — 슬2: 세트 구성품 모달 견적품목 관리 이관

> 2026-06-17. 에픽 spec `docs/superpowers/specs/2026-06-17-item-vs-estimate-item-separation.md`(D-IES-04: 세트 구성품 구성도 견적품목 관리 소관). 선행 슬1 머지 #496.
> 슬2 = **UI 이관** (BE·데이터 모델 변경 없음 — components 엔드포인트 재사용).

## 범위
현 **기초품목 관리**(`ProductCatalogPage.tsx`)에 남은 **세트 구성품 모달(ComponentsModal)** — 세트(BUNDLE) 구성(bundle_component) 정의 + 구성품 정렬(#495 D-PCE-08 종류그룹·기본고정·dnd) + 종류(판넬/리모컨/자재 등) 옵션 — 을 **견적품목 관리**(`EstimateItemsCatalogPage.tsx`)로 이관.

### (A) 견적품목 관리 — 추가
- 카테고리 탭 목록의 **BUNDLE 행**에 **세트 배지**(`estimate-items-set-badge-{code}`, 세트·componentCount) + **구성품 버튼**(`estimate-items-components-button-{code}`) 컬럼.
- `ComponentsModal` + `SortableComponentRow` + `COMPONENT_KIND_OPTIONS` 이관(ProductCatalogPage 에서 이동, near-verbatim). 상태 `componentsModalCode`. import(listBundleComponents/updateBundleComponents/componentsModalModel).
- 저장 후 react-query invalidate(`bundle-components` + `product-catalog`/estimate 목록).

### (B) 기초품목 관리 — 제거 (등록 전용 완성)
- `ComponentsModal`·`SortableComponentRow`·`COMPONENT_KIND_OPTIONS`·구성품 버튼 컬럼·모달 렌더·관련 상태/import/스타일 제거.
- **set-badge 는 유지**(기초품목 = 물리 SKU 종류 표시: 단일/세트 정보). 깨진 import/dead state 없게.

### (C) BE / 데이터
- **무변경**: `GET/PUT /products/{code}/components` 재사용. 데이터 모델(BundleComponent/Product) 무변경. 마이그 없음.

### (D) '옵션' 범위 명확화
- 슬2 = ComponentsModal 의 **componentKind(실내기/실외기/판넬/리모컨/자재/부속/받침대) 선택 + isDefault + 수량 + #495 종류 내 드래그 정렬**. estimate-app `BundleExpander.ExpandOptions`(ss_panel/ss_remote 가격 변동DC)는 **슬3** 범주(무관).

## 테스트/QA
- mock(`api/mock.ts`): `MOCK_BUNDLE_COMPONENTS` + components 핸들러 **유지**(분기 불요).
- Playwright: `t2-bundle-components-modal-real-qa.spec.ts` 등 구성품 모달 스펙을 `/products/estimate-items` + `estimate-items-components-button-*` 로 repoint. `product-catalog/product-catalog.spec.ts` 의 구성품 시나리오를 견적품목 경로로 이동(기초품목엔 구성품 모달 없음 단언). #495 종류그룹/기본고정/마우스드래그 reorder 단언 유지.
- componentsModalModel vitest 유지(import 경로만).
- Docker 실QA: 견적품목 관리 BUNDLE 행 구성품 버튼 → 모달(종류그룹·기본고정·드래그) 실서버 캡처. 기초품목 관리=구성품 모달 없음 확인.

## 비-목표
- 변동DC/번들 옵션·G1 카탈로그 DB = 슬3. estimate-app 무변경. #494/#495 자산 무회귀.

## 워크플로우
조기 PR → Codex 구현 → Opus 리뷰 → Codex 교차 → Opus 수렴 재리뷰 → PM 종합 → 머지.
