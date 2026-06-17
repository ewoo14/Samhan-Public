# 품목 세트 구성품 정렬(드래그) + display-orders 부분요청 가드 재도입 — 에픽 #18 슬2

> 작성: 2026-06-17 (회사 PC). 선행 = 슬1 다중 카테고리 노출 M:N (PR #494, merge `05c59aa2`).
> 결정: **D-PCE-08** (구성품 드래그 = 종류 내 한정·기본 고정), **D-PCE-09** (display-orders 부분요청 가드 재도입).
> 워크플로우: 조기 PR → Codex 구현(BE+FE) → Opus 4.8 5-agent 리뷰(+Docker 실QA) → Codex 5-agent 교차 → PM 종합 → 머지. error 0 · skip 0 · CI green.

---

## 0. 배경 / 현황 (정찰 2026-06-17, 회사 PC 실 DB)

- `bundle_component`: **1,584 링크 / 343 BUNDLE 세트**, 그중 327 세트가 ≥2 구성품. `display_order`·`component_kind`·`is_default` 시드 충실(전원 display_order 채워짐).
- 시드 sync 가 **이미 종류순 + 종류 내 '기본' 먼저** 정렬을 생성함. 예(9-구성품 세트):
  `INDOOR(기본) → OUTDOOR(기본) → PANEL(기본) → PANEL(블랙) → PANEL(승강) → PANEL(공청) → REMOTE(기본) → REMOTE(컬러유선) → REMOTE(유선)`.
- 따라서 슬2 핵심은 "정렬 생성"이 아니라 **그 구조 안에서 사용자 드래그 재정렬 + 서버 불변식 보장 + 부분요청 가드**.
- FE `clients/desktop/.../ProductCatalogPage.tsx` ComponentsModal: 현재 **△▼ 버튼만**(handleMoveUp/Down). dnd-kit 은 슬1 카탈로그 행 드래그(`SortableRow`)에 이미 도입됨 → 재사용.
- display-orders 가드: `2b69cf23` 이 슬1 "전체 활성 노출 포함 강제" 가드를 **CI IT 충돌(IT 가 부분요청 전송→400) + scope creep** 사유로 되돌림. 핸드오프가 "슬2 동반 후보"로 명시.

---

## 1. (A) 세트 구성품 드래그 정렬 — 대부분 FE

### 규칙 (D-PCE-08)
- **종류 순서 고정(구조)**: `INDOOR(실내기) → OUTDOOR(실외기) → PANEL(판넬) → REMOTE(리모컨) → MATERIAL(자재) → ACCESSORY → FOOT`. 사용자 변경 불가.
- **종류 내 '기본' 먼저(고정)**: `is_default=true` 항목은 해당 종류 그룹 최상단에 pin. 드래그로 내릴 수 없음.
- **사용자 드래그 = 종류 내 비-기본만**: 같은 `component_kind` 의 `is_default=false` 항목들끼리만 상호 재정렬. **종류 경계 넘기 금지**, 기본 항목 위로 이동 금지.
- 범위 = **per-SET (전역 아님)**.

### FE (`clients/desktop/src/renderer/routes/ProductCatalogPage.tsx` ComponentsModal)
- △▼ 버튼 → **dnd-kit 드래그**로 교체 (슬1 `SortableRow` 패턴 재사용, 구성품 모달용 분리 컴포넌트로).
- 드래그 제약: within-kind + non-default only. 기본 항목은 drag handle 비활성. 종류 그룹 **시각 구분**(종류 라벨 헤더 권장: 실내기/실외기/판넬/리모컨/자재…).
- 저장: 기존 `PUT .../components` (replace-all). 전송 배열 순서 = 최종 표시순(종류순 + 기본먼저 + 사용자 within-kind 순).
- design-system 컴포넌트 사용 + `data-testid` forward 확인 (DataTable/Modal testid 미forward 함정 — [[inprocess-mock-principles]], [[local-stack-qa-gotchas]]).
- mock(`api/mock.ts`) 핸들러 3원칙 준수 + 구성품 reorder 시나리오 seed.

### BE (`services/product-service`)
- `BundleComponentService.replaceComponents`: `displayOrder` 부여 시 incoming list 를 **`(kindRank ASC, isDefault DESC, incomingIndex ASC)`** 로 정렬 후 1..N 부여 → 불변식(종류순+기본먼저) **서버 권위 보장**. 클라이언트 배열이 규칙을 위반해도 서버가 정규화(단일 진실원).
  - `kindRank` 상수: INDOOR=0, OUTDOOR=1, PANEL=2, REMOTE=3, MATERIAL=4, ACCESSORY=5, FOOT=6.
  - within-kind 비-기본 항목의 상대 순서는 incomingIndex 로 보존(사용자 드래그 결과 유지).
- 단위 테스트: ① 규칙 위반 배열 입력 → 정규화된 displayOrder 검증, ② within-kind 사용자 순서 보존 검증, ③ 기본 항목 종류 내 최상단 검증.

---

## 2. (B) display-orders 부분요청 가드 재도입 (D-PCE-09)

> 별개 축 — 슬1 **카테고리 노출** 엔드포인트(`PUT /products/display-orders`)이며 구성품 reorder 와 무관. 핸드오프 후보 채택.

### 변경
- `ProductEstimateExposureRepository`: `findActiveProductExposuresByEstimateCategory(estimateCategory)` **복원**(2b69cf23 제거분).
- `BundleComponentService.updateDisplayOrders`: 가드 **복원** — `targetCategory` 의 요청 `productIds` 집합 **==** 해당 카테고리 활성 노출 전체 집합. 불일치 시 **400** (메시지: "표시 순서 일괄 갱신은 대상 견적 카테고리의 전체 활성 노출을 포함해야 합니다.").
- `BundleComponentServiceTest`: 제거됐던 stub 복원 + 가드 positive(전체→200)/negative(부분→400) 테스트.

### IT 교정 (핵심 — false-green 방지)
- `ProductCatalogControllerIT` 의 PUT display-orders 정상경로(역전) 테스트: 동일 카테고리 다품목 시드 시 부분요청 → 이제 400. **전체 세트 전송**하도록 fixture 교정.
- `ProductSheetSyncExposureReorderIT`(슬1 신규) 가드 양립 점검.
- **[[migration-fresh-postgres-probe]] · [[changed-module-full-test-before-push]] · [[ci-test-filter-false-green]]**: Testcontainers IT 실제 실행(로컬 Windows skip 금지) + **product 모듈 전체 test 완주 후 push**. (어제 2b69cf23 false-green 재발 방지.)

---

## 3. 비-목표 (Non-goals)
- estimate-app 변경 없음 (구성품 순서는 카탈로그 응답 순서 그대로 소비).
- 슬1 다중 카테고리 노출(M:N) 모델 변경 없음.
- G1 카탈로그 DB 승격 / 멀티 세트 동적가격(#19, 정책 후) 별도 슬라이스.
- slip BUNDLE usageScope=BOTH 회귀가드(bundle-set-options) 유지 — 깨지지 않게 확인.

---

## 4. QA (Docker 실서버 — [[qa-docker-real-test]] · [[no-fake-data-ever]])
- 실서버(게이트웨이 `:8080`, `dev_master` 로그인, `VITE_MOCK_MODE` off) ComponentsModal 에서 다-구성품 세트(샘플 9-구성품) 드래그 → 저장 → 재조회 순서 유지 **실캡처**.
- 기본 항목이 종류 최상단 고정·종류 경계 못 넘는 것 실증.
- 부분요청 가드: 부분 전송 400 / 전체 전송 정상 (실 HTTP 또는 Testcontainers IT).
- 라운드별 스크린샷 PR 인라인 게시 ([[temp-multimodel-workflow]] · [[overnight-live-capture]] · [[pr-qa-screenshots]]).

---

## 5. 문서 동기화 의무 ([[continuous-docs-sync]])
- `docs/dev-reports/2026-06-17-product-set-component-reorder.md` (함수 단위 + DECISIONS D-PCE-08/09)
- ROADMAP / `docs/samhan-public-overview.html` / 관련 README 갱신
- 머지 후 핸드오프(CURRENT-WORK.md) + 메모리 갱신
