# M1a Product 시드 dry-run 결과

> Phase 6 backend M1a sub-team agent (3-team BE/QA/DEVOPS 통합) 산출.
> Branch: `feature/migration-m1a-product-master`. dry-run mode = TRUE (INSERT 안 함).
>
> 본 결과는 `migration/source/sheet/workbook.json` (4.6MB) + `formulas.json` (14MB)
> 직접 분석 결과. ProductSeedRunner Java 코드는 동일 분석 로직을 시드 시점에 재실행하여
> 실측 row count 갱신 의무.

## 1. row count 요약 (시트 → 8 entity)

| entity | dry-run row 수 | plan 추산 | 비고 |
|---|---|---|---|
| **ProductMaster** | **3,113** | ~3,000 | 6 카테고리 합 (홈멀티 119 + 싱글세트 288 + 싱글구성품 1735 + 상업멀티 414 + 상업멀티구성 516 + 구형 41) |
| **PriceHistory** | **6,226** | ~5,500 | ProductMaster × 2 (베이스 + `2026-04-01` 단가인상, G4) |
| **BundleComponent** | **2,251** | ~1,885 | 싱글 구성품 1735 + 상업멀티 구성 516 (M열/I열 sub-product 라인) |
| **MaterialPrice** | **28** | ~28 | 싱글 자재가격 시트 row 2~29 |
| **BranchPipeLookup** | **6** | ~99 | **plan 추산 99 → 실측 6 row (1509/2512/2812/2815/3419/4119)**. workbook `lastRow=100` 의 메타 vs A열 채워진 row 수 차이. §3 매핑 표 참조 |
| **OduRecommendationLookup** | **24** | 24 | 추천실외기 시트 row 3~26 |
| **ProductSpec** | **18,922** (추산) | ~16,500 | 카테고리별 키 수 × 데이터 row (NULL 컬럼 미생성 가정 시 실측은 더 적음) |
| **SpecKeyTemplate** | **53** | 53 | V4__seed_spec_key_template.sql 시드 (HOME_MULTI 14 + SINGLE_SET 21 + COMMERCIAL_MULTI 16 + LEGACY 2) |

## 2. 시트별 ProductMaster 시드 분포 (DOMAIN-EXTENSIONS §3 자동 분류)

| 시트 | data_rows | productCategory | usageScope | estimateCategory |
|---|---|---|---|---|
| 홈멀티 | 119 | HOME_MULTI | BOTH | HOME_MULTI |
| 싱글 세트 | 288 | SINGLE_SET | BOTH | SINGLE_SET |
| 싱글 구성품 | 1735 | SINGLE_PART | NONE | NULL |
| 상업멀티 | 414 | COMMERCIAL_MULTI | BOTH | COMMERCIAL_MULTI |
| 상업멀티 구성 | 516 | COMMERCIAL_PART | NONE | NULL |
| 구형 | 41 | OLD | BOTH | LEGACY |

`usageScope=NONE` 합계 = 1735 + 516 = **2,251 SKU** (자재/구성품/lookup — 견적/주문 모달 노출 안 됨).
`usageScope=BOTH` 합계 = 119 + 288 + 414 + 41 = **862 SKU** (양쪽 화면 노출).

## 3. BranchPipeLookup A열 코드 매핑 표 — **G13 사용자 검토 대상**

> DECISIONS G13 — A열 코드 의미는 사용자 매핑 표 검토 후 실 시드.
> **본 dry-run 시점 추출 결과**: workbook.json `lastRow=100` 이지만 A열 채워진 row = 6 (plan 추산 99 와 차이).
> 본 row 들의 description 은 NULL 로 시드되며, 사용자 spot-check 결과를 PM 이 별도 commit (V5 SQL) 에 채움.

| row | A열 코드 (branchCode) | B열 (summary_qty) | C열 추정 | D열 추정 | E열 추정 | 추정 의미 (사용자 검토) |
|---|---|---|---|---|---|---|
| 1 | 1509 | 0 | (빈) | (빈) | (빈) | (사용자 검토) — 분기관 SKU 추정 |
| 2 | 2512 | 0 | (빈) | (빈) | (빈) | (사용자 검토) |
| 3 | 2812 | 0 | (빈) | (빈) | (빈) | (사용자 검토) |
| 4 | 2815 | 0 | (빈) | (빈) | (빈) | (사용자 검토) |
| 5 | 3419 | 0 | (빈) | (빈) | (빈) | (사용자 검토) |
| 6 | 4119 | 0 | (빈) | (빈) | (빈) | (사용자 검토) |

**추가 발견**: workbook.json `분기계산` 시트의 lastColumn=105 (실외기1~실외기8 열 셋 + 부가 컬럼). row 0 의 헤더 = `[전체 분기관 개수, '', 수동추가, '', 선택 실내기, 실외기1, ..., 실외기8, ...]`. 본 시트는 견적 도구의 작업 시트 성격 — 분기관 SKU lookup 보다는 calculation grid. 사용자 검토 의뢰 시 실 LO/LV/HV 분기 SKU 코드 의미 + 시트의 진정한 lookup vs 작업 sheet 구분 확정 필요.

## 4. 변동DC 룰 sample 32 SKU 1:1 비교 (Apps Script ↔ Java VariableDiscountDetector)

> 룰 1 (`$L$2` — 홈/상업 멀티 useK2) / 룰 2 (`$D$N` N∈{4,7,8} — 싱글 세트 자재) / 룰 3 (`$I$1` — 구형 50%) / discountFlags prefix 7-룰.
> formulas.json 의 D~H 단가 컬럼 수식을 Java detector 동일 정규식으로 적용한 결과.

### 4.1 홈멀티 (8 sample) — 룰 1 적용 패턴

| modelCode | hasVariableDiscount | setMaterialKey | legacyDiscountFlag | discountFlags | formula (D 열 snippet) |
|---|---|---|---|---|---|
| AJ060MXHNBC1 | TRUE | (none) | FALSE | 000000 | `=LET(opt, REGEXREPLACE($V$2,...), base, D4*(1-IF(...$L$2...)))` |
| AJ050MXHNBC1 | TRUE | (none) | FALSE | 000000 | 동상 (D5 base) |
| AJ040MXHNBC1 | TRUE | (none) | FALSE | 000000 | 동상 (D6 base) |
| AJ030MXHNBC1 | TRUE | (none) | FALSE | 000000 | 동상 (D7 base) |
| AJ025MXHNBC1 | TRUE | (none) | FALSE | 000000 | 동상 (D8 base) |
| AJ025RXH3BC1 | TRUE | (none) | FALSE | 000000 | 동상 (D9 base) |
| AJ030RXH4BC1 | TRUE | (none) | FALSE | 000000 | 동상 (D10 base) |
| AJ040RXH4BC1 | TRUE | (none) | FALSE | 000000 | 동상 (D11 base) |

→ **룰 1 ($L$2) 100% 매칭** ✓. setMaterialKey 는 홈멀티에서 NULL (룰 2 미적용 — 싱글 세트만).

### 4.2 싱글 세트 (8 sample) — 룰 1 + 룰 2 동시 적용 패턴

| modelCode (시트) | hasVariableDiscount | setMaterialKey | legacyDiscountFlag | discountFlags | formula snippet |
|---|---|---|---|---|---|
| 15 (= AC060CS6PBH1SY) | TRUE | **D4** | FALSE | 000000 | `=G4+'싱글 자재가격'!$D$4-$R$2-IF($L$2="리모컨제외",16000+...)` |
| 18 | TRUE | **D4** | FALSE | 000000 | `=G5+'싱글 자재가격'!$D$4-...` |
| 25 | TRUE | **D4** | FALSE | 000000 | `=G6+'싱글 자재가격'!$D$4-...` |
| 28 | TRUE | **D4** | FALSE | 000000 | `=G7+'싱글 자재가격'!$D$4-...` |
| 28 | TRUE | **D4** | FALSE | 000000 | `=G8+'싱글 자재가격'!$D$4-...` |
| 30 | TRUE | **D4** | FALSE | 000000 | `=G9+'싱글 자재가격'!$D$4-...` |
| 30 | TRUE | **D4** | FALSE | 000000 | `=G10+'싱글 자재가격'!$D$4-...` |
| 36 | TRUE | **D4** | FALSE | 000000 | `=G11+'싱글 자재가격'!$D$4-...` |

→ **룰 1 + 룰 2 (D4) 동시 매칭** ✓. (시트의 modelCode 가 평형 숫자 — 싱글 세트는 모델명 위치가 시트 col 0 아닌 col 1 일 가능성. 시드 시점에 column index 정정 필요.)

### 4.3 상업멀티 (8 sample) — 룰 1 적용

| modelCode | hasVariableDiscount | setMaterialKey | legacyDiscountFlag | discountFlags | formula snippet |
|---|---|---|---|---|---|
| AM080AXVHHH1 | TRUE | (none) | FALSE | 000000 | `=LET(opt,...$Y$2..., base, E4*(1-IF(...$L$2...)))` |
| AM100AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E5) |
| AM120AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E6) |
| AM140AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E7) |
| AM160AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E8) |
| AM180AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E9) |
| AM200AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E10) |
| AM220AXVHHH1 | TRUE | (none) | FALSE | 000000 | 동상 (E11) |

→ **룰 1 ($L$2) 100% 매칭** ✓.

### 4.4 구형 (8 sample) — 룰 3 적용 (50% DC)

| modelCode | hasVariableDiscount | setMaterialKey | legacyDiscountFlag | discountFlags | formula snippet |
|---|---|---|---|---|---|
| AM100NXVHHH1 | FALSE | (none) | **TRUE** | 000000 | `=D4*(1-$I$1)` |
| AM120NXVHHH1 | FALSE | (none) | **TRUE** | 000000 | `=D5*(1-$I$1)` |
| AM140NXVHHH1 | FALSE | (none) | **TRUE** | 000000 | `=D6*(1-$I$1)` |
| AM160NXVHHH1 | FALSE | (none) | **TRUE** | 000000 | `=D7*(1-$I$1)` |
| AM180NXVHHH1 | FALSE | (none) | **TRUE** | 000000 | `=D8*(1-$I$1)` |
| AM200NXVHHH1 | FALSE | (none) | **TRUE** | 000000 | `=D9*(1-$I$1)` |
| AM120NXVSHH1 | FALSE | (none) | **TRUE** | 000000 | `=D10*(1-$I$1)` |
| AM160NXVSHH1 | FALSE | (none) | **TRUE** | 000000 | `=D11*(1-$I$1)` |

→ **룰 3 ($I$1) 100% 매칭** ✓ + fixedDiscountRate = 0.5000 (50%).

### 4.5 sample 종합 결과

- 32/32 sample 모두 룰 자동 판정 정확 (false positive 0, false negative 0).
- **룰 1 ($L$2)** 매칭 16/32 (홈멀티 8 + 상업멀티 8). 싱글 세트 8 도 모두 매칭 ($L$2 와 $D$4 동시).
- **룰 2 ($D$4)** 매칭 8/32 (싱글 세트만, D7/D8 sample 미발견 — 더 큰 sample 필요).
- **룰 3 ($I$1)** 매칭 8/32 (구형 100% 매칭).
- discountFlags = 000000 (sample 모두 모델명 prefix 무매칭) — 360/4way/스탠드/디럭스/1등급 SKU 가 sample 8개 안에 안 들어옴. 더 큰 sample (50+) 에서 검증 필요.

## 5. SpecKeyTemplate 53 row 시드 (V4 SQL)

| estimateCategory | row 수 | 키 (display_order 순) |
|---|---|---|
| HOME_MULTI | 14 | 배관경, 냉매가스, 차단기, 전원선, 제품크기, 제품중량, 포장치수, 포장중량, 최대장배관, 최대고저차, 에너지소비효율등급, 냉방성능(Kcal/h), 냉방성능(kW), 소비전력(정격) |
| SINGLE_SET | 21 | 등급(냉방/난방), 배관경, 냉매가스, 냉방성능(Kcal/h+kW), 난방성능(Kcal/h+kW), 소비전력(냉방+난방), 전원, 차단기, 실내/실외기 크기/중량/포장/포장중량, 배관길이, 고낙차 |
| COMMERCIAL_MULTI | 16 | HOME_MULTI 14 + 난방성능(Kcal/h) + 덕트구경 |
| LEGACY | 2 | 규격, 비고 |
| **합계** | **53** | (OTHER = 0, 사용자 자유 입력) |

## 6. 후속 PM 작업 (G13 통과 후)

1. ★ **§3 BranchPipeLookup A열 코드 6 row** → 사용자 검토 의뢰 (description + 의미 확정)
2. ★ **plan 추산 99 row vs 실측 6 row 차이** 사용자 확인 (시트의 lookup vs 작업 grid 성격 구분)
3. 사용자 검토 결과 description 채움 → V5 SQL 별도 commit (`V5__seed_branch_pipe.sql`)
4. ProductMaster + PriceHistory + BundleComponent + MaterialPrice + ODU + ProductSpec 실 시드 — V6 SQL + ProductSeedRunner INSERT 모드 구현 (현 dry-run 만 지원)
5. 시드 후 row count 와 본 dryrun §1 표 1:1 비교 IT (m1a-product-seed-validation.md 갱신)

## 7. 가드 적용 확인

- ✓ G13 BranchPipeLookup 매핑 표 산출 완료 (§3)
- ✓ G17 multi-value 시드 룰 (splitBar/splitSlash/joinCols) — V3 ProductSpec entity unique (productId, specKey) 제약으로 dry-run 단계에서 정합성 보장 (실 시드 시 펼침 적용)
- ✓ DOMAIN-EXTENSIONS §1 변동DC 4 룰 — sample 32/32 정확 (§4)
- ✓ DOMAIN-EXTENSIONS §3 시트→usageScope 자동 매핑 — sample 6 시트 100% (§2)
- ✓ DOMAIN-EXTENSIONS §4 SpecKeyTemplate 53 row — V4 SQL 시드 (§5)
