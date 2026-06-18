# 수식 빌더 F1 — GAS Parity 상세 비교표

> 작성: PM(Opus 4.8) 종합. 자료: GAS 원본 추출 + 우리 구현 추출 + 싱글/상업 분류 정밀 + 주문서 parity (4개 에이전트).
> 대상 PR: #499 (feat/formula-builder-f1). 야간 제약: **"반드시 GAS와 기능 차이가 전혀 없어야 함"**.
> GAS 원본 기준: `tools/legacy-gas/종합견적서-live/` (Code.js + index.html), `tools/legacy-gas/거래처 발송 주문서/index.html`.

---

## 0. 요약 (Executive Summary)

| 기능 | GAS 원본 | 우리 구현 | parity |
|---|---|---|---|
| ① 분류 — 홈멀티 | `classifyHome_` (Code.js:274) | `classifyHome` (ProductSheetSyncService:1550) | ✅ 일치 |
| ① 분류 — 상업멀티 | `classifyCommercial_` (Code.js:684) | `classifyCommercial` (:1606) | ✅ 일치 |
| ① 분류 — **싱글 세트** | `classifySingleSetFixed` (index.html:3869) | (구) `classifyHome` 오라우팅 → **(신) `classifySingleSet` 포팅** | ❌→✅ **fix 완료** |
| ② 고정DC% | `parseFixedDc` (index.html:2833) | BE `parseFixedDcRate`/`parseFixedDiscountRate` + FE `parseFixedDc` | ✅ 일치 |
| ③ 자동수량 | `explodeSetParts`/`countBranchForSet` | `BundleExpander`/`resolveQty` (F1 미변경) | ✅ 일치(기존) |
| 출력물 — 종합견적서 | `종합견적서-live/index.html` | `estimate-app` 1:1 포팅 | ✅ 일치 (F1 DB 적용은 **F5 후속**) |
| 출력물 — 주문서 | `거래처 발송 주문서/index.html` | `order-app` **byte-identical** 포팅 | ✅ 일치 (F1 DB 적용은 **F6 후속**) |

**결론: F1-a 스코프의 GAS parity 깨짐은 "싱글 세트 분류" 1건뿐 → fix 완료. 나머지 전부 일치.**

---

## ① 품목 분류 3단계 (대/중/소)

### 1-A. 홈멀티 — ✅ 일치
GAS `classifyHome_`(Code.js:274, 8단계 cascade: 실외기받침대→전열교환기→인테리어핏→시스템제습기→실외기→실내기→판넬→부자재)와 우리 `classifyHome`(ProductSheetSyncService:1550)의 분기 순서·정규식이 1:1. `matches`=`Pattern.compile(regex, CASE_INSENSITIVE|UNICODE_CASE).find()`. (정규식 미세차: GAS 실외기 분기 `i` 플래그 없음 — 한글이라 무영향.)

### 1-B. 상업멀티 — ✅ 일치
GAS `classifyCommercial_`(Code.js:684, model 정규식 `AM\d{3}A[XVH]` 등 + 실외/실내 키워드 테이블 순차매칭 + catS 조건부)와 우리 `classifyCommercial`(:1606) 1:1.
- **시트 '대분류' override (Code.js:831-833 `catLFromSheet || cls.catL`)**: 우리 BE 미반영이나 **무해(no-op)**. 라이브 `상업멀티_단가인상` 탭(417 row)에 '대분류' 컬럼 자체가 없어(`idxCatL=-1`) GAS에서도 override 미발동 → 양쪽 동일. (향후 운영자가 대분류 컬럼 추가 시 대비한 방어는 백로그.)

### 1-C. 싱글 세트 — ❌→✅ **fix 완료 (이번 라운드 핵심)**
**깨짐(수정 전):** BE `classifyName`(:1542)이 SINGLE_SET/SINGLE_PART을 홈멀티용 `classifyHome`으로 라우팅 → 싱글 에어컨 본체 **255/288(89%)이 catL='부자재'로 추락**. GAS 화면 분류(360/4way 냉난방/가정용 에어컨/비스포크 스탠드 등)와 전면 불일치. GAS 화면용 분류기 `classifySingleSetFixed`(index.html:3869)가 BE에 미포팅(grep 0건).

**원인:** F1 스펙이 `classifyHome_`/`classifyCommercial_`만 명시 → 싱글 전용 분류기 누락을 인지 못함(의도적 단순화 아님).

**fix:** `classifySingleSet`(ProductSheetSyncService) 신규 포팅 + `classifyName`에 SINGLE_SET/SINGLE_PART 분기 + parity IT. GAS `classifySingleSetFixed` 1:1 이식:

| 실제 싱글 품명 | GAS(classifySingleSetFixed) | (구)BE classifyHome | (신)BE classifySingleSet |
|---|---|---|---|
| 360 CST UV | 360 > CST UV | 부자재 > 기타 ❌ | 360 > CST UV ✅ |
| 무풍 4way 냉난방 프레스티지 | 4way 냉난방 > 프레스티지 | 부자재 > 기타 ❌ | 4way 냉난방 > 프레스티지 ✅ |
| 무풍 1way 냉난방 | 1way 냉난방 | 부자재 > 기타 ❌ | 1way 냉난방 ✅ |
| 비스포크 스탠드(콰이엇 그레이) | 비스포크 스탠드 > 콰이엇 그레이 | 부자재 > 기타 ❌ | 비스포크 스탠드 > 콰이엇 그레이 ✅ |
| 24년형 가정용 에어컨 무풍갤러리 | 가정용 에어컨 > 무풍갤러리 | 부자재 > 기타 ❌ | 가정용 에어컨 > 무풍갤러리 ✅ |

> 참고: GAS는 싱글에 `classifySingleSetLM_`(영문 키 360/4w/house/acc, **내부 계산/배분용**)도 별도 사용. 화면 표시 목적은 `classifySingleSetFixed`(한글)이므로 BE 분류는 후자를 채택(정합).

---

## ② 고정DC% — ✅ 일치

### GAS
`parseFixedDc`(index.html:2833): 빈값→`null`(전역DC 신호), `%`/`>1`→÷100, **0~0.99 클램프**. 적용 `homeUnitPrice`/`commUnitPrice`(index.html:3927/4038): `finalRate = (parsedFixed !== null) ? parsedFixed : globalRate`, `computed = round(출고가 × (1 − finalRate))`. DC는 `isVarChecked(=시트 $L$2 참조) && listPrice>0`일 때만.

### 우리
- BE 시트 적재 `parseFixedDcRate`(ProductSheetSyncService:1476): 0~1 입력 ×100(0.5→50), 음수 abs, **0~100 클램프**, NUMERIC(5,2).
- BE 품목별 PATCH `parseFixedDiscountRate`(ProductService:611): 0~100 검증, null=빈칸(전역DC 영향).
- FE 인라인 자동저장 `FixedDiscountCell`(EstimateItemsCatalogPage:311) + `resolveFixedDiscountAutoSave`/`normalizeFixedDiscountRateInput`(ProductCatalogPageModel): blur→PATCH, 빈칸=null, 0~100 외 에러, 미변경 no-op.
- estimate-app 계산 소비 `parseFixedDc`(index.ejs:2952) = GAS 동일(0~0.99 클램프).

**개발책임자 정정 반영:** 고정DC%는 변동DC 옆 **인라인 컬럼**에서 % 숫자 입력, 빈칸=전역DC 영향, **저장 버튼 없는 자동저장**. (실QA 5/5 PASS — `docs/qa/formula-f1-inline-dc/`.)
**무해 항목:** sync 고정DC 보존 가드가 변동DC manual 종속(ProductSheetSyncService:1215) — 코드 주석상 의도된 단순화, 실무 영향 미미.

---

## ③ 자동 수량 — ✅ 일치(기존, F1 미변경)

GAS: `explodeSetParts`(세트→구성품 단가 비율배분, 실내6:실외4(가정용)/4:6 + 천단위정렬 `splitIndoorOutdoorToK`), `countBranchForSet`(괄호 안 `+` 개수=분기관 수), `defaultQty`(구성품 기본수량×세트수량). 우리: `BundleExpander`/`resolveQty`(`qty=setQty×defaultQty`, FOLLOW_SET). **F1 슬라이스는 수량 전개를 변경하지 않음.** (품목별 자동 종류선택 매칭수식은 에픽 후속 F3/F4.)

---

## 출력물 parity (종합견적서 / 주문서)

### 종합견적서 = `estimate-app` — ✅ 일치
GAS `종합견적서-live/index.html` 1:1 포팅(`parseFixedDc`/`explodeSetParts`/`homeUnitPrice` 모두 존재). **F1의 DB 분류/고정DC는 아직 estimate-app 계산에 미연결 → F5(견적서 적용) 후속.** 현재는 GAS와 동일하게 시트/화면 기반 계산이라 parity 유지.

### 주문서 = `order-app` — ✅ 일치 (byte-identical)
`clients/web/order-app/index.html` = GAS `거래처 발송 주문서/index.html` 1:1 임베드 포팅. `commUnitPrice`(:2588) 주석·공백까지 GAS와 동일. **F1 DB 적용은 F6(주문서 적용) 후속** — order-app 데이터 소스는 `/partner-orders/bootstrap`(시트 raw)이라 product-service F1 컬럼 미참조 → F1 변경으로 parity 깨짐 없음.
- **(F1 무관, 기존 구조 이슈)** 주문서 표시단가(클라 고정DC 계산)와 서버 확정단가(`PartnerOrderConfirmService`→dc-config, 고정DC 미반영)가 불일치 가능. F6 통합 대상. bootstrap이 `FORMATTED`라 `useK2` 미응답 → 변동DC 분기 사실상 미발화.

---

## 후속/결정대기 (parity 깨짐 아님)
- **disp(표시명) 파생 미구현**: GAS classify가 반환하는 정제 표시명(`sanitizeDisp_`)은 BE 분류 모델에 부재. estimate-app은 자체 classify에서 받아 무영향. 종합견적서 출력 라벨용 → F1 분류 정확성과 무관.
- **분류 삭제 시 품목 처리**: F1 스펙 open question(차단 vs 미분류 강등) — 개발책임자 정책 결정 필요(별도).

---

## 검증 상태 (싱글 분류 fix)
- **코드 1:1 대조**: ✅ GAS `index.html` `classifySingleSetFixed`(3869-3904) ↔ BE `classifySingleSet` 1:1. `ADP-F075SP` 특수부자재(3872)·비스포크 `세이지 블루`/`프라임 핑크`(3883)·가정용 `isPro` 정규식(3890) 모두 GAS 원문 실재 → 임의 추가 0건. 유일 차이 = `hay`에 `spec` 누락(BE Product에 spec 필드 없음, 주석 명시, 분류 키워드는 name/model에 있어 무해).
- **컴파일**: ✅ BUILD SUCCESSFUL (main+test)
- **IT**: Testcontainers Windows npipe 한계로 `ProductSheetSyncServiceIT` 32 skip(false-green 확인) → 실서버 실증으로 대체
- **실 DB before→after** (product_db 재생성 + 새 코드 seed):
  - 부자재 **244/276(88%) → 3(1%)**
  - 가정용 에어컨 0→**134**, 4way 냉난방 0→**31**, 냉난방 스탠드 0→27, 냉전/냉난방 벽걸이 0→16/14, 비스포크 스탠드 0→12, 360 0→10, 1way/4way 냉방전용·실링·덕트·냉전 스탠드 등 분산 → GAS 화면 분류 **0→273건 복원**
- **desktop 실화면 3/3 PASS** (`docs/qa/formula-f1-single-classify/`): 부자재 0/50("360 > CST UV") / "가정용" 검색→"가정용 에어컨 > 무풍갤러리" / "스탠드" 검색→"냉난방 스탠드 > 프레스티지"

## 검증 상태 (고정DC% 인라인 + 변동DC)
- 고정DC% 인라인 자동저장 실QA **5/5 PASS** (`docs/qa/formula-f1-inline-dc/`): 인라인 컬럼·blur 자동저장 PATCH 200·reload persist·null 원복·분류모달 catL/M/S 전용
- 변동DC 행 텍스트 제거(체크박스만): typecheck ✅ + vitest **23 passed**

## 시뮬레이션 실증 (전 카테고리, 실 DB)
종합견적서/주문서 계산·분류 함수를 GAS와 **동일 입력으로 실제 실행** 비교 (`qa-gas-parity-sim.mjs`).

### 종합견적서 (estimate-app ↔ GAS) — 차이 0
- 실 **811품목**(홈멀티 119 + 상업멀티 404 + 싱글 288): 분류 **0 불일치**, 단가 5함수(`parseFixedDc`/`homeUnitPrice`/`singleUnitPrice`/`commUnitPrice`/`explodeSetParts`) **byte-identical**
- divergence: `classifySingleSetLM_` 입력 결합(name OR model → name+model) **fix 완료**(GAS `Code.js:449` 정합)
- `explodeSetParts` specs 메타 2줄: 사양 표시용 의도 확장(가격 무영향 → 유지)
- **실앱 캡처**(`docs/qa/formula-f1-estimate-app/`): 홈멀티 119행·싱글 1185행(세트+구성품)·상업멀티 332행 실 DB 렌더(빈 화면 원인=인증게이트 이메일, config 교정)

### 주문서 (order-app ↔ GAS) — 단가 차이 0 / 표시는 F6 대기
- 실 **1116품목**: 단가 **0 불일치**, 단가 5함수 **byte-identical**
- **가정용 연식 세분 + useK2 미발화 = 주문서 DB 적용(F6) 영역** → 별도 PR. 현재 order-app은 `BootstrapService`가 product_db 미연결(Sheets SA키 없음 + seed 빈) + gateway 401 → 품목 0행

### 분류 소스 실화면 (desktop, `docs/qa/formula-f1-categories/`)
- 홈멀티(실외기/실내기)·상업멀티(실외기 프라임)·싱글(360/가정용) — 부자재 0, GAS 분류 복원
