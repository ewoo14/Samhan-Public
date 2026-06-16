# dev-report — 싱글 자재 정정(A안) + 품목 종류 단일/세트 (PR #493)

> 슬라이스 = estimate-app 외부 시트 잔여 제거 에픽 + 품목 등록/관리 고도화의 일부.
> 워크플로우 = Opus 계획/조기PR → Codex 개발 → Opus 5-agent 리뷰 → Codex 교차 → 수렴 → PM 머지 (Opus↔Codex 2모델).

## 1. 배경 / 정정 사유
1차 구현(V18)은 `material_price` 시트 28행(D2~D29)을 **새 Product(MATERIAL) 28개**로 시드하면서, `model_name` 활성 유니크 인덱스 충돌을 피하려 `MAT-`+md5 해시 가짜 코드를 부여했다. 개발책임자가 QA 캡처에서 "모델명이 `MAT-`으로 시작해 이상하다 / 1WAY 대형 공청은 `PC1BWCK3NW`인데 왜 MAT인가"를 지적.

실 DB 정찰 결과:
- 모델코드형 부품 21종(FPH-1412XS3·AFR-TC9D 등)은 **이미 `SINGLE_PART` 품목으로 존재**(V18이 중복 생성).
- 패널/리모컨 7종은 estimate-app **옵션**이고 크기·WIFI별 **여러 실 카탈로그 모델코드**로 치환(공청판넬 → 대형 `PC1BWCK3NW` 등).
- `material_price` 원천엔 모델코드 컬럼이 없음. estimate-app 단가는 이미 실 구성품(SINGLE_PARTS=BundleComponent→실 Product `deliveryPrice`) 차액으로 동적 계산.

→ 가짜 MATERIAL 품목은 잉여·오류. **A안(실 카탈로그 통합) 채택**.

## 2. 변경 요약
### A안 — 가짜 자재 폐기, 실 카탈로그 통합 (REVERT)
- `V18__seed_material_products.sql` 삭제 + dev DB 28행 + flyway 이력 제거.
- `EstimateCatalogInternalController.materialPrices()` / `ProductLookupController.listMaterialPrices()` → `material_price`(구형 참조 lookup) 복원.
- `MaterialPriceResponse.from(Product)` 제거, `ProductLookupSheetSyncService` 자재탭 sync 은퇴 가드 제거, 관련 IT 3종 + desktop mock 자재 rows 복원.

### KEEP — 자재와 독립한 정당 개선
- `ProductRepository.search` usageScope **IN-확장**(PARTNER_ORDER/ESTIMATE→+BOTH) — SlipFormPage 전표 라인 자동완성 운영버그 픽스(main query + countQuery).
- `ProductCatalogResponse.productCategory` 노출 + `Create/UpdateProductRequest.usageScope/estimateCategory`.
- 세트 구성품: ComponentsModal `기본`(isDefault) 토글 + per-row `componentKind` + `ProductAutocomplete`, estimate-app baseline `isDefault` 일관화.

### 품목 종류 단일/세트 (개발책임자 Option B)
- ProductFormPage 품목 종류 = `단일(GENERAL)`/`세트(SET)` 2가지. `세트구성품` 종류 + 제품측 부모세트/구성분류 UI 제거. 구성품 지정은 **세트측 ComponentsModal 에서만**. BE enum 은 backward-compat 유지.
- 노출 설정 견적 카테고리 사용자 라벨 `레거시`→`구형`.

### P1 데이터 손상 픽스 (머지 게이트가 적발)
- 세트 구성품인 단일 품목 편집(itemKind=GENERAL) 저장 시 BE가 부모 세트 `BundleComponent` 링크를 soft-delete 하던 회귀 제거(`ProductService.applyUpdateFields` GENERAL 분기). 구성품 링크는 세트측에서만 관리.
- 단위테스트 + 실 BE 회귀 IT(`componentProductPatchAsGeneral_preservesParentBundleComponentLink`) 추가.

## 3. 검증 (Docker 실서버 / 실 HTTP — [[feedback_no_fake_data_ever]])
- **자재=실 카탈로그**: 품목 화면 `PC1B` 검색 → `PC1BWCK3NW`(1WAY 대형 공청) 실모델코드, **MAT-해시 0건**. material-prices 28건 D-key 복원.
- flyway V17(V18 미적용) / MATERIAL 품목 0.
- **단일/세트**: 실 QA A2 — 종류 단일·세트만, 제품측 부모세트/구성분류 부재.
- **P1 실 BE**: `PATCH /api/products/{PC6NUDK1NW} itemKind=GENERAL` → HTTP 200, AC110CS6PBH1SY 링크 13건 보존, 응답 itemKind=SET_COMPONENT(구성품 역할 보존). (mock 미재현 경로 직접 입증.)
- FE typecheck + vitest 16 PASS, compileTestJava PASS, ProductServiceTest PASS.

## 4. 워크플로우 회고
- 머지 게이트(fix 후 Opus 재리뷰)가 mock-가림 P1 데이터 손상을 단독 적발 → Codex 픽스 → Opus 재리뷰 무결 → 실 BE 검증. [[feedback_preauth_migration_lessons]] / [[feedback_spec_sync_full_db_distribution_check]] 계열 "IT/mock-가림 운영 파손" 패턴 재확인.
- 실 DB 정찰이 "가짜 품목 28개" 근본 오류를 조기 발견 → 큰 재작업(M:N 노출 모델) 대신 선택적 revert + KEEP 로 수렴.

## 5. 후속 (별도 슬라이스)
- 에픽: 품목 노출/구성품 모델 재설계 — 다중 카테고리 노출(M:N) + 카테고리별 표시순서(일괄 자동조정) + 세트 구성품 정렬(실내기→실외기→판넬→리모컨→자재, 각 종류 내 기본 먼저, 드래그 reorder).
- 멀티 세트 구성품 단가 합산 동적계산(싱글 일관화) — 견적 금액 변동 가능 → 정책 확인 후.
