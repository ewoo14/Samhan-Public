# 사양 인지형 입력 (품목별 사양 + valueType + 순서/중복) — 개발 리포트 (PR #487)

> 2026-06-15 세션. 사양(스펙) 후속 #1 전면 재설계. 개발책임자 다회(8+) 정정 반영. 다모델(Opus 계획/리뷰 ↔ Codex 개발/교차).
> 스펙: `docs/superpowers/specs/2026-06-15-spec-aware-input.md`. 메모리 [[estimate-spec-data-sources]].

## 1. 배경·결정 (개발책임자 정정 누적)
단순 사양명 드롭다운(#487 초안)을 **사양 인지형 입력**으로 재설계. 개발책임자 요구:
1. 사양명 = 구글 시트/GAS **품목별**, "이름, 단위"(예 "냉방능력, kW"), **능력**(성능 아님).
2. 단위 자동부착, 값 입력 **valueType 동적**(숫자/크기3분할/텍스트).
3. 사양 순서 동적변경(**드래그 + 위/아래**).
4. 이미 추가된 사양 **드롭다운 제외**.
5. 추천사양/사양명 **단일 "사양" 필드 통합**(datalist 콤보박스, 자유입력+제안).
6. reorder 아이콘 = **≡(드래그) + ↑/↓**.
7. 값 필드 사양별 동적전환 = 확정(NUMBER 숫자+단위 / DIMENSION WxHxD / TEXT).
8. 시드 제품 사양 = edit-mode 그대로 조회 확인.

**결정(AskUserQuestion)**: ① 실제 시트 읽어 재시드 ② 명시 valueType 메타. 필터=홈/싱글/상업. RANGE/DUAL=TEXT 수용. datalist 콤보박스 현행유지.

## 2. GAS 품목별 표시 사양 (권위 = index.ejs render*Spec_, 원본 tools/legacy-gas 95%+ 동등)
홈멀티 실내/외(정격 SINGLE)·판넬(타공/볼트), 싱글세트(최소/정격/최대 RANGE), 상업멀티(정격·배관길이 조건부 TRIPLE), 전열교환기. 실 시트(SRC_SHEET_ID `1RJqO3jT…`) 사양 헤더 = 캐노니컬. 중복헤더 냉방성능(정격) 2컬럼(1st kW/2nd kcal/h).

## 3. BE (product-service)
- `SpecKeyValueType`(NUMBER/DIMENSION/TEXT) enum + `spec_key_template.value_type`. `SpecKeyTemplateResponse` + valueType.
- **V17 마이그**(forward): value_type 컬럼 + CHECK + 재시드(V4 system 행 삭제 + HOME 17/SINGLE 19/COMMERCIAL 20 = 56행, "이름, 단위"·품목별 value_type). **fresh-Postgres probe 56행·CHECK 검증.**
- `ProductSpecRequest` + unit. `ProductService` saveSpecs/replaceSpecs 가 unit 저장(displayOrder=배열 positional).
- 기존 ProductSpec 데이터 독립(템플릿 FK 아님) → 무영향.

## 4. FE (desktop ProductFormPage 사양 섹션 전면)
- **단일 "사양" 필드**(native `<input list>` + 행별 datalist `spec-key-options-{i}`): 자유입력 + 품목별 추천. 입력 specKey 가 템플릿 일치 시 unit+valueType 적용, 불일치 TEXT.
- **품목별 필터**: productCategory→estimateCategory(홈/싱글/상업, COMMERCIAL_PART 포함).
- **중복제외**: `availableTemplatesForRow` 가 추가된 specKey 제외(현재 행 허용).
- **valueType 입력**: NUMBER(숫자+단위 suffix) / DIMENSION(W×H×D 3분할, composeDimensionSpecValue "WxHxD") / TEXT.
- **순서변경**: 행 draggable + ≡ 핸들 + ↑/↓ 버튼 → 배열 재정렬.
- edit-seed: 기존 specs 를 valueType=TEXT 로 로드(구 데이터 자유편집), unit 매핑.

## 5. 다모델 리뷰 (수렴)
- **Opus 5-agent**: BE 0·DevOps 0(V17 CI-safe·additive·테스트 게이팅 OK). **FE 2 P2** → Opus fix: ① DIMENSION 부분입력 미저장(normalizedSpecValue 완전성 가드, 깨진 "1800xx" 방지) ② changeSpecKey valueType 변경 시에만 값 초기화(자유편집 보존). +P3 COMMERCIAL_PART 매핑.
- **Codex 교차**: (결과 — 머지 시 갱신).

## 6. QA (Docker 실서버, 실데이터)
라이브 :5173 FE + :8080 V17 product-service, dev_master. `playwright/spec-aware-input-real-qa`:
- 품목별 드롭다운(상업멀티 20)·valueType(냉방능력 kW 숫자+suffix / 제품크기 mm WxHxD / 냉매가스 TEXT)·중복제외(선택3→후보17 누수0)·순서변경.
- **시드 제품 AM100AXVHJH1 편집 — 기존 13 사양 그대로 로드**(구 표기 TEXT). typecheck 0·vitest 10.

## 7. 후속(별도)
- **시드 product_spec 데이터 정렬**: 기존 적재 사양은 구 표기(냉방성능(정격), 단위 값포함)라 새 템플릿과 미정합(TEXT 로드). 새 표기 정렬 원하면 product_spec 재시드(개발책임자 판단).
- **GAS estimate-app 판넬 버그**: 싱글/상업 판넬 타공사이즈/전산볼트간격 cool_cap 오매핑.
- LEGACY 사양 템플릿(홈/싱글/상업 스코프 외, 미시드).
