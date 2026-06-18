# G1 — specDetailMap DB 승격 (estimate-app 마지막 런타임 Google Sheets 의존 제거)

> 2026-06-18 야간 자율 슬라이스. 수식 빌더 에픽 후속(F1 #499 / F6 #501 완결). [[project_sheets_to_db_full_migration]] 표준 지시("외부 4종 전면 DB 치환, 옵션C 폐기") 이행.

## 0. 배경 — 왜 G1 인가 (다른 슬라이스 게이트 판정)

야간 정찰 결과:
- **F2** (거래처 DC 설정 UI): ✅ 이미 구현됨 (`SalesPartnerDcConfigPage.tsx` 11컬럼 인라인 + CSV import + audit SSE). 재구현 금지.
- **F4** (번들 매칭): ✅ 수동 선택 구현됨. 자동매칭 룰 엔진 = 스펙 §4 D1 "신규 설계, **개발책임자 확인 필요**" → 게이트.
- **F3** (옵션 설정 UI): PARTIAL. F4 자동매칭과 묶여 게이트.
- **F7** (VAT/배분비율 중앙화): 기획서 §7 "설정화 우선순위 낮음, 현행 유지" → 비대상.
- **G1** (specDetailMap → DB): **비게이트·자율 가능** → 본 슬라이스.

## 1. 목표

estimate-app(`clients/web/estimate-app`)의 종합견적서 **사양 상세 맵(specDetailMap)**을 **런타임 Google Sheets 읽기 → DB(product-service) endpoint**로 전환.

- 현재: `code.js:1762` `t.specDetailMap = JSON.stringify(getSpecDetailMap_())` → `getSpecDetailMap_()`(`code.js:1242`)가 견적 부트스트랩마다 `SpreadsheetApp.openById(SRC_SHEET_ID)`(`code.js:1247`)로 HOME/SINGLE/COMM 3탭을 **라이브 스크랩**.
- 이것이 #30 카탈로그 DB 전환 이후 estimate-app에 남은 **마지막 런타임 시트 의존**. (`code.js:1713-1714` 가 DB 모드에서도 HOME/SINGLE/COMM 3탭을 prefetch 유지하는 이유 = specDetailMap + homeDefaults + singleDefaults.)
- **G1 스코프 = specDetailMap 만**. homeDefaults/singleDefaults/추천 homeEx는 후속 마이크로 슬라이스(3탭 prefetch 완전 제거는 그때).

## 2. 핵심 설계 결정 (PM, 비정책)

**접근 = 신규 시트 스크랩이 아니라 기존 ProductSpec reshape.**

근거:
- `ProductSpec` 엔티티(`services/product-service/.../domain/ProductSpec.java`) Javadoc 명시: *"출처: estimate Code.js getSpecDetailMap_() (line 1006-1364)의 scanHome/scanSingle/scanComm 함수의 idx(H,[...]) 호출 인자 매트릭스."* → **이미 getSpecDetailMap_이 스크랩하는 사양 키들을 담도록 설계·적재됨**(시트→DB 적재는 `ProductSheetSyncService`/`ProductSpecService` 담당, #485/#487/#488).
- `EstimateCatalogInternalController`에 이미 `loadSpecs()`(specKey IN 필터 → Map) + `/components` specs 반환 패턴 존재 → 재사용.
- 따라서 신규 시트 읽기 0, 신규 sync 0. **새 endpoint가 DB의 ProductSpec를 getSpecDetailMap_ 출력 shape로 변환만.**

## 3. 구현 (Codex)

### 3.1 BE — product-service
**신규 endpoint**: `GET /products/internal/estimate-catalog/spec-detail-map` (X-Internal-Token, `EstimateCatalogInternalController`에 추가).

응답 = `getSpecDetailMap_()` 출력과 **동일 shape**:
```
{ "<모델명/모델코드 키>": { "home"?: {...}, "single"?: {...}, "comm"?: {...} } }
```
- 키: getSpecDetailMap_은 **모델명(`row[iModel]`, code.js:1319)**으로 키잉. DB reshape도 **동일 키 규칙**(모델명 우선, 필요 시 modelCode 병행)을 따라야 parity. → Codex가 getSpecDetailMap_ 키잉을 정확히 확인 후 동일하게.
- 각 카테고리 sub-object 필드명 = getSpecDetailMap_의 JS 필드명 **그대로**:
  - **home** (code.js:1324-1345): `pipeDia, gas, breaker, powerLine, size, weight, packSize, packWeight, maxPipe, maxDrop, cool_kcal, cool_kw, cool_power, effGrade` + alias `cool_cap_kcal, cool_cap_kw, cool_pow_kw, grade`.
  - **single** (scanSingle, code.js:1354~): 성능·소비전력 cool|heat splitBar, 전원/차단 splitSlash, in/out 크기·중량·포장, 배관길이/고낙차. → Codex가 scanSingle 전체 읽고 필드셋·변환 1:1 재현.
  - **comm** (scanComm): ERV layout 감지 + joinCols, 냉난방 kcal/kW 4그룹. → Codex가 scanComm 전체 읽고 재현.

**필드 매핑**: ProductSpec.specKey(한글 라벨, 예: 배관경/냉매가스/차단기/전원선/제품크기…) → getSpecDetailMap_ JS 필드명. Codex가 `ProductSheetSyncService`의 ProductSpec 적재 코드를 읽어 specKey 셋을 확인하고, getSpecDetailMap_의 `idx(H,[...])` 라벨과 대조해 매핑 테이블을 코드에 명시(상수). 카테고리 판정(home/single/comm) = product의 estimateCategory/productCategory.

🚨 **성능 합성 금지**([[feedback_no_fake_data_ever]]): ProductSpec에 없는 값은 빈 문자열(''), 절대 추정/합성/보간 금지. getSpecDetailMap_도 빈 셀은 `''`(code.js:1325 `row[iPipe]||''`) → 동일.

### 3.2 FE — estimate-app
- `lib/db-catalog.js`: `async function specDetailMap()` 추가 — `GET /spec-detail-map` → 응답 그대로(또는 동일 shape) 반환. `module.exports`에 등재.
- `lib/code.js:1762`: DB 모드(`useDb`)에서 `t.specDetailMap = JSON.stringify(await dbCatalog.specDetailMap())`, 비-DB 모드는 기존 `getSpecDetailMap_()` 유지(fallback 보존). try/catch graceful(`'{}'`).
- `code.js:1712-1714` 주석 갱신(사양맵은 이제 DB, 3탭 prefetch는 homeDefaults/singleDefaults용으로 축소 — 단 이번 슬라이스에선 prefetch 목록 변경 금지, 다른 의존 잔존하므로 주석만).

### 3.3 Git
- Codex는 **파일만 수정, git commit 금지**([[feedback_codex_sandbox_git]] — 샌드박스 `.git` 쓰기 거부). approval-policy never. Claude(PM)가 commit 대행.

## 4. 검증 (Acceptance) — parity 최우선

1. **Golden parity 단위/통합 테스트**: DB-소스 specDetailMap 의 모델별 카테고리 sub-object가, 동일 모델의 ProductSpec→getSpecDetailMap_ 라벨 매핑과 일치. 최소: home/single/comm 각 대표 모델 N건 필드별 단언. (시트 직접 비교가 가능하면 더 강하게.)
2. **Java IT**: `/spec-detail-map` endpoint 200 + 응답 shape(중첩 home/single/comm) + 알려진 모델 사양값 단언 (Testcontainers PG + ProductSpec seed).
3. **estimate-app**: DB 모드 부트스트랩 시 specDetailMap 비어있지 않음 + 종합견적서 사양 표시 렌더(라이브 Docker QA, §5).
4. **회귀 0**: 기존 `/products /components /material-prices /odu-recommendations /price-baseline` endpoint 무변경. 비-DB 모드 getSpecDetailMap_ 경로 무변경.
5. **변경 모듈 전체 test 완주**([[feedback_changed_module_full_test_before_push]]): product-service 전체 + estimate-app test. 타깃만 실행 push 금지.

## 5. 실QA (Docker, 야간이라도 라이브)

docker compose 재빌드 → product-service(DB+ProductSpec seed) + estimate-app `CATALOG_SOURCE=db` 기동 → 종합견적서 실 견적 → **사양 표시 영역 라이브 캡처**(`docs/qa/formula-g1-specdetailmap/`). 가짜/합성 금지, 실 캡처만([[feedback_overnight_live_capture]] [[feedback_no_fake_data_ever]]). 캡처 불가 시 사유 정직 보고.

## 6. 리뷰 워크플로우 ([[feedback_temp_multimodel_workflow]])

Opus 5-agent(BE/FE/Designer/QA/DevOps, parity 회귀 최우선) → Codex 5-agent 교차(QA agent 포함, Docker 실QA 인라인) → **Opus 수렴 재리뷰(마지막 Codex fix 덮기)** → PM 종합 → 머지. 🚨 마지막 리뷰 모델이 마지막 fix 를 덮은 뒤에만 머지(원격 세션 끊김 대비 — [[feedback_dual_5agent_review]]).

## 7. 리스크

- **parity drift**: single/comm scan 의 splitBar/splitSlash/ERV joinCols 변환이 ProductSpec 적재 시 pre/post-transform 여부 불명 → Codex가 ProductSheetSyncService 적재 로직 확인 후 reshape에서 정확히 재현. parity 테스트가 게이트.
- **데이터 갭**: 싱글 실내/외 사양 0건 가능([[project_estimate_spec_data_sources]]) → 시트도 동일하면 parity 유지(빈 값). 합성 금지.
- **키잉 불일치**: 모델명 vs modelCode 키 → getSpecDetailMap_ 키 규칙 정확 확인.
