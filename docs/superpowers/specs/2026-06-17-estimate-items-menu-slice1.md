# 기초품목↔견적품목 분리 — 슬1: 견적품목 관리 메뉴/화면 신설

> 2026-06-17. 에픽 spec: `docs/superpowers/specs/2026-06-17-item-vs-estimate-item-separation.md` (D-IES-01~04).
> 슬1 = **UI/메뉴 재구성** (BE·데이터 모델 변경 없음 — 기존 엔드포인트 재사용).

## 범위

현 `clients/desktop/src/renderer/routes/ProductCatalogPage.tsx`(품목 관리)를 둘로 분화:

### (A) 견적품목 관리 — 신규 페이지 `EstimateItemsCatalogPage.tsx`, 라우트 `/products/estimate-items`
- **목록**: 견적/주문 노출 항목(usageScope ≠ NONE) 카테고리별 표시. 카테고리 필터 Select.
- **이관 UI**(현 ProductCatalogPage 에서): `ToggleCell`(견적/주문 노출 체크 + estimateCategories TagChip 칩 + 카테고리 추가), 노출 설정 컬럼, 표시순서 컬럼, dnd 드래그 정렬(`SortableRow`)+순서저장 버튼.
- **기초품목 선택 추가**(D-IES-03): `ProductAutocomplete`(기존 `searchProducts`/`GET products?q`) 로 **기초품목 master 에서 선택** → 선택 시 해당 품목을 현재 카테고리에 노출(PATCH `/usage` estimateCategories 추가). **신규 품목 등록 불가**(선택만).
- API(기존 재사용): `GET /api/v1/products`(카테고리 필터·displayOrder), `PATCH /products/{code}/usage`(M:N), `PUT /products/display-orders`, `GET products?q`(자동완성).
- page-code: `products.list` VIEW 가드(기존 재사용, 신규 page-code 불요 — 단 [[fe-canaccess-pagecode-be-match]] 확인).

### (B) 기초품목 관리 — `ProductCatalogPage.tsx` 슬림화 (등록 전용)
- 유지: 모델명 검색, 목록(모델명/품목명/카테고리/세트뱃지/수정), 신규 등록·수정(ProductFormPage).
- **제거**: ToggleCell(노출), 노출 설정 컬럼, 카테고리 필터, 표시순서 컬럼, 드래그 정렬·순서저장. (견적품목 관리로 이관.)
- ※ 세트 구성품 모달(ComponentsModal)은 **슬2 에서 견적품목 관리로 이관** — 슬1 에선 현 위치 유지(범위 최소화).

### (C) 메뉴/라우트
- `routes/index.tsx`: `/products/estimate-items` 라우트 추가(정적 path → 파라미터 순서 주의).
- `AppLayout.tsx` 사이드바(판매 그룹): "품목 관리"→**"기초품목 관리"** 라벨, 직후 **"견적품목 관리"** 링크 추가. `activeTargets` 갱신.

## 테스트/QA
- mock(`api/mock.ts`): 기존 핸들러 유지(분기 불요). 견적품목 화면용 노출 항목 응답 정합.
- Playwright: data-testid 를 두 화면으로 분리(`product-catalog-*` 기초품목 / `estimate-items-*` 견적품목). 견적품목 진입·기초품목 선택 추가(자동완성)·노출 토글·카테고리 추가·순서 저장 통합 시나리오. mock 회귀(슬1·2 자산) hard gate 유지.
- Docker 실QA: 견적품목 관리 진입 → 기초품목 선택 추가 → 노출/순서 → 실서버 캡처. 기초품목 관리=등록 전용 캡처.

## 비-목표
- 세트 구성/구성품 정렬 이관(슬2) · 변동DC/G1(슬3) · BE/데이터 모델 변경 · 신규 마이그.

## 워크플로우
조기 PR → Codex 구현 → Opus 5-agent → Codex 교차 → Opus 수렴 재리뷰 → PM 종합 → 머지.
