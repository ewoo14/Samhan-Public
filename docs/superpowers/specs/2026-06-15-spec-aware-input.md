# 사양 인지형 입력 (사양명 단위포함 + 값타입) — 스펙

> 사양 후속 #1 재설계. 개발책임자 추가 요구(2026-06-15): 사양명 = 구글 시트 단위포함, 단위 자동부착, 크기 3분할, 값타입(수치/텍스트) 구분.
> 개발책임자 결정 2건(AskUserQuestion): **① 실제 구글 시트 읽어 재시드 ② 명시 valueType 메타**.

## 1. 실 시트 조사 (SRC_SHEET_ID `1RJqO3jT…`, 탭 홈멀티/싱글세트/상업멀티_단가인상)
사양 헤더(=캐노니컬 사양명) 추출. 단위 일부 헤더포함("소비전력(kW)", "실내기 크기(mm)"), 일부 미포함("배관경","냉매가스","차단기"). **중복 헤더**: "냉방성능(정격)" 2컬럼(1st=kW, 2nd=kcal/h — code.js scanHome 매핑) → 단위별 분리 필요.

## 2. 데이터 모델 (개발책임자 ②)
- **`spec_key_template`**: `spec_key` = **"이름, 단위"**(단위 있으면; 예 "냉방성능, kW") 자체가 식별자 → 다단위 충돌 없음. `default_unit`(자동부착용), 신규 `value_type`(NUMBER/DIMENSION/TEXT).
- **`ProductSpec`**(기존): `spec_value`=숫자/치수문자열/텍스트, `unit`=단위(분리저장). 표시 시 조합(NUMBER `6.0`+`kW`→`6.0kW`, DIMENSION `947x365x947`+` mm`). **기존 ProductSpec 데이터는 독립(템플릿=FK 아님)** → 무영향.

## 3. valueType 정의
- **NUMBER**: 값 필드 = 숫자 1개. 단위 자동 부착(표시). 예 냉방성능/소비전력/차단기/중량/장배관.
- **DIMENSION**: 값 필드 = 숫자 3분할(가운데 `x`) → `WxHxD`. 단위 mm. 예 제품크기/포장치수.
- **TEXT**: 자유 텍스트. 단위 없음. 예 냉매가스/등급/규격/비고/에너지소비효율등급.

## 4. 사양 정의 시드 (제품 등록 폼 — 통합 단위, 실내/외 분리 제외)
| spec_key(표시·식별자) | default_unit | value_type | 비고 |
|---|---|---|---|
| 용량 | (none) | NUMBER | 단위 미정(개발책임자 확인 — HP/kW?) |
| 배관경 | (none) | TEXT | "6/12"(액관/가스관 2값) |
| 냉방성능, kW | kW | NUMBER | |
| 냉방성능, kcal/h | kcal/h | NUMBER | |
| 난방성능, kW | kW | NUMBER | |
| 난방성능, kcal/h | kcal/h | NUMBER | |
| 소비전력, kW | kW | NUMBER | |
| 냉매가스 | (none) | TEXT | R410A |
| 차단기, A | A | NUMBER | |
| 전원선, mm² | mm² | NUMBER | |
| 제품크기, mm | mm | DIMENSION | WxHxD |
| 제품중량, kg | kg | NUMBER | |
| 포장치수, mm | mm | DIMENSION | WxHxD |
| 포장중량, kg | kg | NUMBER | |
| 최대장배관, m | m | NUMBER | |
| 최대고저차, m | m | NUMBER | |
| 최대 연결 실내기 대수, 대 | 대 | NUMBER | |
| 에너지소비효율등급 | (none) | TEXT | "2/3등급" |
| 등급(냉방/난방) | (none) | TEXT | |
| 규격 | (none) | TEXT | |
| 비고 | (none) | TEXT | |

> **개발책임자 확인 사항**: ① 사양명 "냉방**성능**"(시트) vs "냉방**능력**"(개발책임자 표현) — 시트 따름(기본). ② 용량 단위. ③ 배관경 TEXT(2값) 수용 여부.

## 5. BE 변경 (product-service)
- `SpecKeyTemplate` + `value_type`(enum NUMBER/DIMENSION/TEXT, NOT NULL default TEXT). `SpecKeyTemplateResponse` + valueType.
- **V-migration(신규)**: `ALTER TABLE spec_key_template ADD value_type` + **재시드**(기존 system 행 삭제 + §4 신규 삽입, forward 마이그 — 기존 V4 수정 금지). HOME_MULTI 카테고리에 §4 통합 세트 시드(견적 카테고리 무관 제품폼 공용 → category 필터 없이 전체 노출).
- 사양 입력 요청 DTO(`ProductSpecInput`)에 `unit` 추가. `ProductService` create/update → `ProductSpec.create(specKey, specValue, unit, order)`.

## 6. FE 변경 (desktop ProductFormPage)
- 사양명 = `<datalist>`/Select 로 템플릿 `spec_key` 제안(#487 확장, 자유입력 유지). 선택 시 해당 템플릿의 `unit`+`valueType` 적용.
- **값 입력 valueType 분기**:
  - NUMBER: `type=number` 입력 1개 + 단위 suffix 표시(읽기전용). specValue=숫자문자열, unit=템플릿 단위.
  - DIMENSION: 숫자 입력 3개 + `x` 구분 + 단위 suffix. specValue=`W x H x D`(예 `947x365x947`).
  - TEXT: 기존 자유 텍스트.
- `productFormModel.buildSpecs`: valueType별 specValue 구성 + unit 포함. vitest 동반.
- mock: `MOCK_SPEC_KEY_TEMPLATES` value_type + §4 리스트.

## 7. QA / 워크플로우
실QA(라이브 :5173 FE + :8080): NUMBER(단위 부착)·DIMENSION(3분할 x)·TEXT 각 캡처 + 다단위(냉방성능 kW/kcal/h) 분리 확인. typecheck/vitest. Opus↔Codex 교대 리뷰. 브랜치 `feat/spec-name-dropdown` 확장(#487 PR 재스코프).
