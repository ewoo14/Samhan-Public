# 세트 구성품 정렬(드래그) + display-orders 부분요청 가드 재도입 — 에픽 #18 슬2 (PR #495)

> 2026-06-17. 브랜치 `feat/product-set-component-reorder`. 워크플로우 = Opus 4.8 계획/조기PR → Codex 개발 → Opus 5-agent → Codex 5-agent 교차 → Opus 수렴 재리뷰 → PM 종합 → 머지([[temp-multimodel-workflow]]).
> spec: `docs/superpowers/specs/2026-06-17-product-set-component-reorder.md`. 선행 슬1 = #494(M:N 노출).

## 1. 목표 (2갈래·독립 축)

- **(A) 세트 구성품 정렬** — 한 세트(BUNDLE)의 구성품을 종류순(실내기→실외기→판넬→리모컨→자재) + 종류 내 '기본' 먼저로 정렬하고, **사용자가 같은 종류 내 비-기본 변형끼리 드래그 재정렬**(per-SET, 전역 아님).
- **(B) display-orders 부분요청 가드 재도입** — 슬1 카테고리 노출 표시순서 일괄 갱신(`PUT /products/display-orders`)이 부분 요청으로 기존 순서를 붕괴시키지 않도록, `2b69cf23`(CI IT 충돌·scope creep 사유 revert)에서 되돌렸던 가드를 **IT 교정과 함께** 올바르게 재도입.

> 현황 정찰(회사 PC 실 DB): `bundle_component` 1,584 링크/343 BUNDLE 세트, 327 세트 ≥2 구성품. 시드 sync 가 **이미 종류순+기본먼저** 정렬을 생성 → 슬2 = 그 구조 위의 사용자 드래그 + 서버 불변식 보장 + 가드.

## 2. (A) 세트 구성품 드래그 정렬 (D-PCE-08)

### 규칙
- 종류 순서 **고정(구조)**: `INDOOR=0 → OUTDOOR=1 → PANEL=2 → REMOTE=3 → MATERIAL=4 → ACCESSORY=5 → FOOT=6`. 사용자 변경 불가.
- 종류 내 `is_default=true` **최상단 고정**(드래그 핸들 비활성).
- 사용자 드래그 = **같은 종류의 비-기본끼리만**. 종류 경계·기본 위 이동 금지.

### BE (`product-service`) — 서버 단일 진실원
- `BundleComponentService.replaceComponents`: 저장 전 요청을 `(kindRank ASC → isDefault DESC → incoming index ASC)` **안정 정렬** 후 `display_order` 1..N 부여(`normalizeComponentRequestsForDisplayOrder` + `IndexedComponentRequest`). 클라이언트 배열이 규칙을 위반해도 서버가 불변식 보장. within-kind 비-기본 사용자 순서는 incoming index 로 보존.
- `BundleComponent.ComponentKind.rank()` 추가(enum 순위 상수). null kind → ACCESSORY(INSERT 기본값과 일치 → 정렬키·영속값 괴리 없음).
- 저장 경로(replace-all)·중복/자기참조/미해소 검증·soft-delete·PESSIMISTIC 락 불변.

### FE (`clients/desktop` ProductCatalogPage ComponentsModal) — BE 와 이중 방어
- `componentsModalModel.ts`: `COMPONENT_KIND_ORDER`(=BE rank 동일) + `normalizeBundleComponentDraftOrder`(동일 정렬키) + `groupBundleComponentDrafts`(종류 그룹, 빈 종류 생략) + `canReorderBundleComponentDrafts`(같은 종류·비-기본끼리만) + `reorderBundleComponentDrafts`(`@dnd-kit/sortable arrayMove`, 위반 시 정규 순서 반환). `buildBundleComponentInputs` 가 전송 전 정규화.
- △▼ 버튼 → **dnd-kit 드래그**(슬1 `SortableRow` 패턴 재사용, `SortableComponentRow`). `canDrag = canEdit && !isSaving && !draft.isDefault` → 기본행 `useSortable({disabled})`. PointerSensor + KeyboardSensor(접근성). 핸들 `canEdit=false` 숨김 + `!canDrag` 전반 dimmed/title. 저장 후 react-query `['product-catalog']`+`['bundle-components']` invalidate.

## 3. (B) display-orders 부분요청 가드 (D-PCE-09)

- `ProductEstimateExposureRepository.findActiveProductExposuresByEstimateCategory` 복원 → **가드 모수 = 대상 카테고리 활성 노출 중 `usageScope IN (ESTIMATE/PARTNER_ORDER/BOTH)`**(NONE 제외). FE 전송 모수(`buildCategoryDisplayOrderInputs` 의 `usageScope!=='NONE'`)와 **집합 동일** → P1(NONE+활성노출 시 영구 400) 차단.
- `BundleComponentService.updateDisplayOrders`: 요청 productId 집합 ≠ 가드 모수 → `BusinessException(INVALID_INPUT)` 400 "표시 순서 일괄 갱신은 대상 견적 카테고리의 전체 활성 노출을 포함해야 합니다."

## 4. 테스트

- 단위(`BundleComponentServiceTest`): 정규화 3건(규칙 위반→정규화·within-kind 보존·기본 최상단) + 가드 부분→400.
- IT(`ProductCatalogControllerIT`, Testcontainers): 정상경로 전체세트(API_HOME_01 포함)→204 / 부분→400 / **NONE+활성노출 제외→204(ESTIMATE·PARTNER_ORDER·BOTH IN 목록 전체 커버)**. reorder 기대순서 정규화(INDOOR 먼저) 반영. ← 어제 false-green(로컬 Windows IT skip) 원인을 CI Linux 실Postgres 실행으로 차단(CI product 잡 `--tests` 필터 없음).
- FE(vitest): `componentsModalModel`(그룹화/정렬/드래그 제약/하향 reorder) + `ProductCatalogPageModel`(NONE 제외) — 70 통과.
- mock(`api/mock.ts`): display-orders mock 에 BE D-PCE-09 동형 가드 + `MOCK-NONE-ITEM`(NONE+HOME_MULTI 활성노출) P1 재현 → mock Playwright 가 부분 payload 회귀 적발(false-green 경화).
- real-qa(`t-slice2-component-reorder-real-qa.spec.ts`): 실 게이트웨이 드래그·저장·영속 실증(1 passed).

## 5. QA (Docker 실서버 — 실 게이트웨이 :8080 · 실 product_db · dev_master · mock OFF)

- `AC100CS6PHH1SY`(13구성품): 판넬 비기본 `PC6NUDK1NW` **2→4위 드래그 → 저장(PUT 200) → 재오픈 영속**, 기본 `PC6NUNK1NW` 종류 최상단 고정. 실캡처 `docs/qa/product-set-component-reorder/reorder-{initial,after-drag,after-save}.png`.
- 가드 실HTTP: 부분→400(정확 메시지)·전체→204. `docs/qa/product-set-component-reorder/be-guard-real-http.txt`.

## 6. DECISIONS

- **D-PCE-08**: 세트 구성품 드래그 = 종류순·종류 내 기본먼저 **구조 고정** + 사용자는 **같은 종류 비-기본끼리만** 재정렬(per-SET). 서버가 `replaceComponents` 정규화로 불변식 단일 진실원.
- **D-PCE-09**: display-orders 부분요청 가드 재도입. 가드 모수 = 대상 카테고리 활성 노출 중 `usageScope≠NONE`(FE 전송 모수와 집합 동일). 부분 요청 → 400.

## 7. 비-목표 / 회귀가드

- estimate-app(clients/web) 무변경 · 슬1 M:N 노출 모델 무변경 · slip BUNDLE usageScope=BOTH 회귀가드(bundle-set-options) 보존.

## 8. 워크플로우 교훈

- **가드 모수 비대칭(P1)**: BE 가드 모수(전체 활성 노출, usageScope 무관) vs FE 전송 모수(usageScope≠NONE) 불일치 → NONE+활성노출 시 영구 400(어제 revert 와 동일 경로). Opus BE 리뷰 단독 적발 → 가드 모수를 FE 와 한 축으로 정렬.
- **real-qa 스펙 false-RED**: `componentCode` 가 행 첫 토큰(드래그 핸들 글리프 `⠿`)을 추출 → 모든 코드 동일 → 이동 단언 **항상 실패**(기능 정상인데 스펙이 실패). 모델코드 span 직접 추출 + keyboard→마우스 드래그(헤드리스 신뢰성)로 교정. 스펙 통과 ≠ 보장, 실패도 스펙 버그일 수 있음 — 실 DOM/스크린샷으로 교차 확인.
