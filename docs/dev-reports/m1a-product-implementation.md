# M1a Product 마이그 구현 보고서

> Phase 6 backend M1a sub-team agent (3-team BE/QA/DEVOPS 책임 통합) 산출.
> Branch: `feature/migration-m1a-product-master`. base: main `a4cd5a4` (PR #34 merge).

## 1. 작업 범위

시트 27탭 → 8 entity 시드를 위한 product-service 확장 + 시드 dry-run.

## 2. 신규/변경 파일 목록

### Flyway 마이그
- `services/product-service/src/main/resources/db/migration/V3__migration_extension.sql`
  — products(ProductMaster) 10 신규 컬럼 ALTER + 7 신규 entity CREATE (PriceHistory / BundleComponent / MaterialPrice / BranchPipeLookup / OduRecommendationLookup / ProductSpec / SpecKeyTemplate) + composite index 3
- `services/product-service/src/main/resources/db/migration/V4__seed_spec_key_template.sql`
  — SpecKeyTemplate 53 row 시드 (HOME_MULTI 14 + SINGLE_SET 21 + COMMERCIAL_MULTI 16 + LEGACY 2)

### Java entity 8 (확장 1 + 신규 7)
- 기존 `Product.java` 확장 (V3 신규 14 필드 추가; modelCode + productType/bundleMode/hasVariableDiscount/fixedDiscountRate/setMaterialKey/legacyDiscountFlag/discountFlags/releasePrice/deliveryPrice/pyongSize/productCategory/usageScope/estimateCategory/specText/remark/parentBundleSetModel)
- 신규: `PriceHistory.java`, `BundleComponent.java`, `MaterialPrice.java`, `BranchPipeLookup.java`, `OduRecommendationLookup.java`, `ProductSpec.java`, `SpecKeyTemplate.java`
- 신규 enum: `ProductType.java`, `BundleMode.java`, `MaterialKey.java`, `UsageScope.java`, `EstimateCategory.java`, `ProductCategory.java`

### Repository 신규 7
- `PriceHistoryRepository`, `BundleComponentRepository`, `MaterialPriceRepository`, `BranchPipeLookupRepository`, `OduRecommendationLookupRepository`, `ProductSpecRepository`, `SpecKeyTemplateRepository`
- 기존 `ProductRepository` 확장 (`findByModelCodeAndIsDeletedFalse`, `searchByUsageScope`, `findByUsageScopeAndIsDeletedFalse`, `findByParentBundleSetModelAndIsDeletedFalse`)

### Service 신규 4
- `VariableDiscountDetector` — 4 룰 자동 판정 (Apps Script 출처 매트릭스 그대로) — 한국어 Javadoc + Layer 4 의미 정렬
- `MaterialPriceCalculator` — D4 default + D7/D8 분기
- `ProductSpecService` — CRUD + reorder + apply-to-existing dry-run 지원 (G18 409 strict + G19 admin trigger)
- `BundleExpander` — EXPAND/KEEP 분기 처리 (SEND_AS_SET_IDS 화이트리스트 4 SKU)

### Controller 신규
- `ProductCatalogController` — 9 endpoint (Migration Plan §2.1.7):
  - `GET /api/v1/products?usageScope&category` (필터)
  - `PATCH /api/v1/products/{code}/usage` (admin)
  - `GET /api/v1/products/{code}/specs`
  - `POST /api/v1/products/{code}/specs` (409 on dup)
  - `PATCH /api/v1/products/{code}/specs/{id}`
  - `DELETE /api/v1/products/{code}/specs/{id}` (Soft Delete)
  - `PATCH /api/v1/products/{code}/specs/reorder`
  - `GET /api/v1/spec-key-templates?category`
  - `POST /api/v1/spec-key-templates/{id}/apply-to-existing?dryRun` (G19)

### DTO 신규
- `ProductCatalogResponse` (UUID 비공개 충족 — modelCode 만 노출)
- `ProductSpecResponse`, `SpecKeyTemplateResponse`

### 시드 스크립트
- `services/product-service/src/main/java/.../seed/SheetWorkbookReader.java` — workbook.json + formulas.json read
- `services/product-service/src/main/java/.../seed/ProductSeedRunner.java` — `@Profile("seed")` CommandLineRunner, `--seed.dry-run=true` (default) 모드만 지원 (G13 게이트)

### IT (Spring Boot Test + Testcontainers PostgreSQL)
- `ProductMasterEntityIT` — 10 컬럼 round-trip + modelCode unique + searchByUsageScope 필터
- `BundleExpanderIT` — EXPAND/KEEP/SINGLE 분기 sample 3 BUNDLE
- `ProductSpecServiceIT` — CRUD + 409 중복 + reorder + applyToExisting dry-run
- `ProductCatalogControllerIT` — 4 endpoint smoke (usageScope 필터 + admin usage 변경 + 409 중복 + 53 row 템플릿 조회)
- `VariableDiscountDetectorTest` — 4 룰 18 단위 테스트 (룰 1 $L$2 / 룰 2 $D$N / 룰 3 $I$1 / discountFlags prefix 7-룰)

### 설정
- `application.yml` 에 `seed.dry-run`, `seed.report-dir`, `seed.sheet-dir` 추가

## 3. 빌드 결과

| 명령 | 결과 |
|---|---|
| `./gradlew :services:product-service:compileJava` | BUILD SUCCESSFUL |
| `./gradlew :services:product-service:compileTestJava` | BUILD SUCCESSFUL |
| `./gradlew :services:product-service:assemble` | BUILD SUCCESSFUL |
| `./gradlew :services:product-service:test --tests VariableDiscountDetectorTest` | 18/18 passed (단위) |
| `./gradlew :services:product-service:test` (전체) | BUILD SUCCESSFUL — IT 는 Windows Docker (npipe) 환경에서 skip (feedback_testcontainers_windows_docker.md 가드) |

## 4. 가드 적용 요약

| 가드 | 적용 |
|---|---|
| Layer 1 BACKEND 컴파일 | ✓ assemble + compileTestJava 성공 |
| Layer 2 QA IT | ✓ 4 신규 IT 클래스 작성 (Docker 가용 환경에서 자동 실행) |
| Layer 4 도메인 메서드 의미 정렬 | ✓ VariableDiscountDetector / MaterialPriceCalculator / BundleExpander 한국어 Javadoc + Apps Script 출처 함수명 + 시트 셀 위치 명시 |
| Layer 5 schema validation | ✓ ddl-auto=validate, V3 SQL ↔ entity 1:1 매핑 (BaseEntity 7 audit fields 포함) |
| `feedback_function_documentation.md` (3-layer) | (1) 한국어 Javadoc 모든 service/entity, (2) springdoc-openapi 자동 생성 활성, (3) 본 보고서 + dryrun + validation 3 dev-reports |
| `feedback_uuid_no_user_visibility.md` | ✓ ProductCatalogResponse 가 modelCode 만 노출, internal id (UUID) 미노출 |
| `feedback_korean_path_jdk.md` | worktree 가 영문 path → local `assemble` + 단위 test 모두 성공 |
| `feedback_testcontainers_windows_docker.md` | ✓ AbstractPostgresIT 기존 DockerAvailableCondition 활용, IT 는 Docker 가용 시만 실행 |
| `feedback_it_mockbean_external_clients.md` | (해당 없음 — product-service 는 외부 client 의존성 0; SecurityConfig 만 Eureka client 가드 사용) |

## 5. 후속 PM 작업 (G13 + 실 시드)

1. dry-run 실행 → `docs/dev-reports/m1a-product-seed-dryrun.md` 갱신 (BranchPipeLookup A열 코드 매핑 표 §3)
2. 사용자 검토 → BranchPipeLookup description 컬럼 채움 (별도 V5 SQL 시드)
3. ProductMaster + PriceHistory + BundleComponent + MaterialPrice + ODU + ProductSpec 실 시드 (V6 SQL + ProductSeedRunner 수정 — 현 dry-run 만 지원, INSERT 구현은 후속)
4. infrastructure/docker-compose.yml 에 product-service section 신규 추가 (현재 없음 — 인프라 PM 영역)

## 6. 주의 사항

- **BranchPipeLookup**: plan 추산 99 row vs 실측 6 row (1509/2512/2812/2815/3419/4119) — workbook.json `lastRow=100` 의 메타 vs 실 데이터 채워진 A열 코드. dryrun 보고서에 명기.
- **ProductSpec 추산**: NULL 컬럼 미생성 가정 시 ~18,922 row. 실측은 시드 시점 갱신 의무.
- **SEND_AS_SET_IDS 4 SKU**: BundleExpander 의 화이트리스트 — 시드 시점에 정확한 modelCode 매핑 필요 (현재 placeholder 상수).
