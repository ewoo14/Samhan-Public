# 견적품목 관리 메뉴/화면 신설 — 기초품목↔견적품목 분리 슬1 (PR #496)

> 2026-06-17. 브랜치 `feat/estimate-items-menu`. 에픽 spec `docs/superpowers/specs/2026-06-17-item-vs-estimate-item-separation.md`(D-IES-01~04), 슬1 spec `docs/superpowers/specs/2026-06-17-estimate-items-menu-slice1.md`.
> 워크플로우 = Opus 계획/조기PR → Codex 개발 → Opus 3-agent → Codex 교차 → Opus 수렴 재리뷰 → PM 종합 → 머지.

## 1. 목표
판매(견적/주문) 카탈로그의 카테고리별 SKU/단가/번들 복잡도를 물리 품목 마스터와 분리. 현 `ProductCatalogPage`(품목 관리)를 **기초품목 관리**(등록 전용) + **견적품목 관리**(판매 노출/순서, 신규 메뉴)로 분화. **BE·데이터 모델 변경 없음**(기존 엔드포인트 재사용).

## 2. 구현
- **견적품목 관리 신규** `EstimateItemsCatalogPage.tsx`, 라우트 `/products/estimate-items`(PermissionGuard `products.list` view): 노출 항목(usageScope≠NONE) 카테고리별 목록 + ToggleCell(견적/주문 노출 + estimateCategories TagChip + 카테고리 추가) + 표시순서 드래그(SortableRow)+순서저장(PUT display-orders) **이관**(near-verbatim, ProductCatalogPageModel 공유 #494 M:N 로직 재사용) + **기초품목 선택 추가**(ProductAutocomplete, PATCH /usage append, 신규등록 불가 D-IES-03, MATERIAL/비카탈로그/이미노출 필터 + "이미 노출됨" 가드).
- **기초품목 관리** `ProductCatalogPage.tsx` 슬림화: 등록/수정/검색/목록/세트뱃지/구성품 모달 유지(구성품 모달은 슬2 이관), 노출/카테고리/표시순서 UI 제거(734줄). subtitle 에 "노출·순서는 견적품목 관리에서" 안내.
- 사이드바 "품목 관리"→**"기초품목 관리"** + **"견적품목 관리"** 링크, `activeTargets` 갱신.
- 마지막 카테고리 chip 제거 시 scope 보정(ESTIMATE→NONE, BOTH→PARTNER_ORDER) — "노출 checked·카테고리 0" 모순 방지. count/pagination NONE 정합.

## 3. 리뷰 수렴 (듀얼, error 0)
- **Opus 3-agent**(FE/Designer/DevOps): P0 0, **P1×2** — (a) 이관 real-QA 4스펙이 구 `/products/catalog` 노출 UI 단언(contract sweep [[fe-guard-removal-contract-tests]]), (b) mock 시나리오 8/9 드래그 reorder 단언 삭제(헤드리스 키보드 hang 회피) → reorder 회귀가드 손실. + P2(메뉴계약 substring·add 가드·count).
- **Codex fix** → **Opus 수렴 재리뷰**: 미수렴(잔여 P1 stale testid 부분 rename + P2 chip scope/count) → **Codex 교차** 동일 적발 합치 → **Codex fix2**: real-QA testid 교정 + 마우스드래그 reorder 복원 + chip scope + count 페이지네이션. **계열 전수 grep 으로 잔여 stale 0 확인**(다른 product-catalog-* 는 /products/catalog 기초품목 대상이라 정당).

## 4. QA (Docker 실서버 — 실 게이트웨이 :8080·dev_master·mock OFF)
- `docs/qa/estimate-items-menu/estimate-items-page.png` — 견적품목 관리(기초품목 선택 추가 + 노출 M:N 칩 + 카테고리별 표시순서), 사이드바 기초품목+견적품목 분화 실증.
- `docs/qa/estimate-items-menu/basic-items-page.png` — 기초품목 관리 등록 전용.
- 재포인트된 `multi-category-exposure-real-qa.spec.ts` **필터 테스트 PASS**(estimate-items 카테고리 필터·표시순서 실서버). 
- 🪤 **M:N 테스트는 데이터-게이트**: 실 DB 에 2+ 카테고리 노출 품목 0건(슬2 product-service 재빌드 시 1-카테고리 재시드된 로컬 artifact; #494 당시 AJ060 2-cat). M:N 기능은 #494 머지+slice-1 필터 PASS+vitest mock 으로 검증, 다중 카테고리 데이터는 견적품목 관리 add-from-master 사용으로 채워짐.
- 독립 검증: typecheck OK + vitest 5/72(+2 회귀) + CI green.

## 5. 결정/비목표
- D-IES-01~04(분리/메뉴명/참조제약/세트구성 소관). 세트 구성(bundle_component)·구성품 정렬·옵션은 **슬2** 이관. 변동DC/G1 카탈로그 DB=슬3.
- estimate-app/슬1·2(#494/#495) 자산 무회귀. BE/마이그 변경 없음.
