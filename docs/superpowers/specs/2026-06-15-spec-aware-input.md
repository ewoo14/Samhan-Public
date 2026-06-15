# 사양 인지형 입력 (품목별 사양 + 단위 + 값타입 + 순서/중복) — 스펙

> 사양 후속 #1 전면 재설계. 개발책임자 추가 요구(2026-06-15, 다회):
> 1. 사양명 = 구글 시트/GAS 기준 **품목별** 단위 포함, 단위 자동 부착.
> 2. 값 입력 = **valueType**(숫자/크기3분할/텍스트) 분기.
> 3. **사양 순서 동적 변경**(드래그 reorder).
> 4. **이미 추가한 사양은 드롭박스에서 제외**(중복 방지).
> 5. **GAS 코드 참조 + 표시 사양 품목별 확인**.
> 결정(AskUserQuestion): ① 실제 시트 읽어 재시드 ② 명시 valueType 메타.

## 1. GAS 품목별 표시 사양 (권위 소스 = index.ejs render*Spec_, 원본 tools/legacy-gas/종합견적서 95%+ 동등)

품목 타입별 표시 사양 집합(값구조 표기):

| 품목 타입 | 표시 사양 (값구조) |
|---|---|
| **홈멀티 실내기** | 배관경(DUAL), 냉방성능 kcal/h(N), 냉방성능 kW(N), 냉방소비전력 kW(N), 냉매가스(T), 에너지소비효율등급(T), 전원선 mm²(N), 차단기 A(N), 제품크기 mm(DIM), 제품중량 kg(N), 포장치수 mm(DIM), 포장중량 kg(N), 배관길이 m(N), 고낙차 m(N) |
| **홈멀티 실외기** | (실내기 동일) + 최대 연결 실내기 대수 대(N) |
| **홈멀티 판넬** | 타공사이즈 mm(N), 전산볼트간격 mm(N), 제품크기 mm(DIM), 제품중량 kg(N), 포장치수 mm(DIM), 포장중량 kg(N) |
| **싱글세트 실내/외** | 배관경(DUAL), 냉방성능 kcal/h(RANGE), 난방성능 kcal/h(RANGE), 냉방성능 kW(RANGE), 난방성능 kW(RANGE), 냉방소비전력 kW(RANGE), 난방소비전력 kW(RANGE), 냉매가스(T), 효율등급(T), 전원선 mm²(N), 차단기 A(N), 제품크기 mm(DIM), 제품중량 kg(N), 포장치수 mm(DIM), 포장중량 kg(N), 배관길이 m(N), 고낙차 m(N) |
| **싱글세트 판넬** | 타공사이즈 mm(N), 전산볼트간격 mm(N), 제품크기 mm(DIM), 제품중량 kg(N), 포장치수 mm(DIM), 포장중량 kg(N) |
| **상업멀티 실내/외** | 배관경(DUAL), 냉방성능 kcal/h(N), 난방성능 kcal/h(N), 냉방성능 kW(N), 난방성능 kW(N), 냉방소비전력 kW(N), 난방소비전력 kW(N), 냉매가스(T), 소비효율등급(T), 전원선 mm²(N), 차단기 A(N), 제품크기 mm(DIM), 제품중량 kg(N), 포장치수 mm(DIM), 포장중량 kg(N), 배관길이 m(N/TRIPLE), 고낙차 m(N/TRIPLE) |
| **상업멀티 실외기** | (실내기 동일) + 최대 연결 실내기 대수 대(N) |
| **상업멀티 판넬** | 타공사이즈 mm(N), 전산볼트간격 mm(N), 제품크기 mm(DIM), 제품중량 kg(N), 포장치수 mm(DIM), 포장중량 kg(N) |
| **전열교환기** | 덕트구경(T), 소비전력(전열환기)(T), 소비전력(일반환기)(T), 제품크기 mm(DIM), 제품중량 kg(N) |

> 차이: 홈/상업=정격 SINGLE, 싱글=최소/정격/최대 RANGE, 상업 배관길이/고낙차=조건부 TRIPLE.
> **🐞 GAS 버그(estimate-app 별도 후속)**: 싱글/상업 판넬의 타공사이즈/전산볼트간격이 cool_cap_kcal/cool_pow_kw로 오매핑(index.ejs 3461-2,3526-7). 본 슬라이스는 사양 정의만, 버그 fix는 estimate-app 후속.

## 2. valueType (제품 등록 = 단일 품목, 개발책임자 3종)
- **NUMBER**: 숫자 1개 + 단위 자동부착(표시). SINGLE 매핑.
- **DIMENSION**: 숫자 3분할(가운데 `x`) `WxHxD` + 단위 mm. DIMENSION 매핑.
- **TEXT**: 자유 텍스트. TEXT/DUAL(배관경 "6/12")/RANGE(싱글 "10/12/15")/TRIPLE 매핑(제품 등록은 단일품목이라 RANGE/TRIPLE은 텍스트로 입력, 세트 표시 전용 구조).

## 3. 데이터 모델 (개발책임자 ②)
- **`spec_key_template`**: `spec_key`="이름, 단위"(예 "냉방성능, kW") 식별자(다단위 충돌 없음), `default_unit`, 신규 `value_type`(NUMBER/DIMENSION/TEXT), `estimate_category`(품목별 필터 축), `display_order`.
- **`ProductSpec`**(기존): `spec_value`=숫자/치수문자열/텍스트, `unit` 분리, `display_order`(reorder). 표시=조합.
- 기존 ProductSpec 데이터 독립(템플릿=FK 아님) → 무영향.

## 4. FE (desktop ProductFormPage 사양 섹션 전면)
- **품목별 드롭다운**: 품목 카테고리(estimateCategory 매핑)별 사양 제안. 자유입력 유지.
- **중복 제외(요구4)**: 이미 추가된 사양(specKey)은 드롭다운 후보에서 제외.
- **순서 동적 변경(요구3)**: 사양 행 드래그 reorder(design-system 드래그 패턴 or 위/아래 버튼) → display_order 반영.
- **valueType 입력(요구2)**: NUMBER(type=number + 단위 suffix) / DIMENSION(숫자3 + x + 단위) / TEXT(자유). 선택 시 템플릿 unit+valueType 적용.
- `productFormModel.buildSpecs`: valueType별 specValue 구성 + unit + display_order. vitest.
- mock: MOCK_SPEC_KEY_TEMPLATES value_type + 품목별 GAS 리스트.

## 5. BE (product-service)
- `SpecKeyTemplate` + `value_type` enum. `SpecKeyTemplateResponse` + valueType. (display_order 기존.)
- V-migration(신규, forward): value_type 컬럼 + **재시드**(기존 system 행 삭제 + §1 품목별 GAS 사양 세트 삽입, estimate_category별). 
- `ProductSpecInput` DTO + unit + displayOrder. `ProductService` create/update → `ProductSpec.create(specKey, specValue, unit, displayOrder)`.

## 6. 개발책임자 확정 (2026-06-15)
1. **사양명 = "냉방능력"/"난방능력"**(능력, GAS "성능" 아님). 그 외 GAS/시트 표기 따름.
2. **품목별 필터 축 = estimate_category(홈/싱글/상업) 단위**. kind(실내기/실외기/판넬) 세분 안 함 → 카테고리 내 전체 사양(판넬·실외기 전용 포함) 노출.
3. **RANGE/DUAL/TRIPLE = TEXT 입력 수용**(싱글 성능 최소/정격/최대, 배관경 액관/가스관, 상업 배관길이 트리플 → 자유텍스트). 단일값 NUMBER 와 구분.
4. **순서 변경 = 드래그 + 위/아래 버튼 둘 다**.

## 8. 최종 품목별 시드 테이블 (능력 표기, category별 value_type)
> spec_key = "이름, 단위"(단위 있으면). display_order = 표 순서. **같은 사양명도 category별 value_type 다름**(싱글 성능=TEXT[RANGE], 홈/상업=NUMBER[정격]).

**HOME_MULTI** (홈멀티): 배관경(TEXT) · 냉방능력, kcal/h(N) · 냉방능력, kW(N) · 냉방소비전력, kW(N) · 냉매가스(T) · 에너지소비효율등급(T) · 전원선, mm²(N) · 차단기, A(N) · 제품크기, mm(DIM) · 제품중량, kg(N) · 포장치수, mm(DIM) · 포장중량, kg(N) · 배관길이, m(N) · 고낙차, m(N) · 최대 연결 실내기 대수, 대(N) · 타공사이즈, mm(N) · 전산볼트간격, mm(N)

**SINGLE_SET** (싱글세트): 배관경(TEXT) · 냉방능력, kcal/h(TEXT) · 난방능력, kcal/h(TEXT) · 냉방능력, kW(TEXT) · 난방능력, kW(TEXT) · 냉방소비전력, kW(TEXT) · 난방소비전력, kW(TEXT) · 냉매가스(T) · 에너지소비효율등급(T) · 전원선, mm²(N) · 차단기, A(N) · 제품크기, mm(DIM) · 제품중량, kg(N) · 포장치수, mm(DIM) · 포장중량, kg(N) · 배관길이, m(N) · 고낙차, m(N) · 타공사이즈, mm(N) · 전산볼트간격, mm(N)

**COMMERCIAL_MULTI** (상업멀티): 배관경(TEXT) · 냉방능력, kcal/h(N) · 난방능력, kcal/h(N) · 냉방능력, kW(N) · 난방능력, kW(N) · 냉방소비전력, kW(N) · 난방소비전력, kW(N) · 냉매가스(T) · 소비효율등급(T) · 전원선, mm²(N) · 차단기, A(N) · 제품크기, mm(DIM) · 제품중량, kg(N) · 포장치수, mm(DIM) · 포장중량, kg(N) · 배관길이, m(TEXT) · 고낙차, m(TEXT) · 최대 연결 실내기 대수, 대(N) · 타공사이즈, mm(N) · 전산볼트간격, mm(N)

> N=NUMBER, DIM=DIMENSION, TEXT/T=TEXT. 단위 있는 N/DIM 은 spec_key 에 ", 단위" 포함. TEXT 무단위는 spec_key=이름만.

## 7. QA / 워크플로우
실QA(라이브 :5173 + :8080): 품목별 드롭다운·valueType 입력(숫자단위/크기3분할/텍스트)·중복제외·순서변경 캡처. typecheck/vitest. Opus↔Codex 교대. 브랜치 `feat/spec-name-dropdown`(#487 재스코프).
