# 슬3-1 — Java FORMULA read parity (변동DC useK2/matKey 정확 적재)

> 2026-06-17. 기초↔견적품목 분리 에픽 **슬3(데이터 인프라) 1/2**. 개발책임자 "데이터 인프라 먼저" 결정.
> [[temp-multimodel-workflow]] [[standalone-boot-real-qa]] [[migration-fresh-postgres-probe]]

## 1. 문제

product-service `ProductSheetSyncService` 가 시트 FORMULA(수식)에서 변동DC 마커
(`$L$2` useK2 / `$D$4·7·8` matKey / `$I$1` 구형)를 검출해 적재하는데, JS(estimate-app, 정답) 대비 **결정적 과소검출**:

| 카테고리 | JS(정답, 시트행) | 현 Java DB(품목, 실측) |
|---|---|---|
| 상업멀티 useK2 | **378**/417행 | **86** |
| 홈멀티 useK2 | **107**/122행 | **54** |

→ DB-mode 카탈로그(#455) 변동DC parity 미달 → estimate-app `CATALOG_SOURCE` 기본 db 전환 차단(opt-in 잔존).

## 2. 진단 (2026-06-17 PM, node googleapis 실측 — plan 입력)

- **node googleapis FORMULA read**: 상업멀티 `A1:Z`=**378**, `A1:ZZ`=**378** (F열 idx6 string 411/number 6). 홈멀티 행전체 join=**107**.
- → **REST 응답엔 378 수식 string 정상 존재**. range(A1:Z vs A1:ZZ)·dateTimeRenderOption **원인 아님**(investigation.md 배제 재확인).
- → 누락은 **Java google-api-client(GsonFactory) 파싱** 또는 **`readSheetDisplay`(FORMATTED) ↔ `readSheetFormulas`(FORMULA) 별도호출 행수/정렬 불일치 → `joinRowFormulas(formulaRows, i)` 의 i 인덱스 어긋남**.
- 신규단서: 수식이 멀티라인 `=LET(\n ...\n)`. 홈멀티 `$L$2`는 F열 아닌 타 열(행 전체 join 필수 — 현 로직과 일치).
- 참고: `docs/audit/gas-port-fidelity/java-formula-read-discrepancy-investigation.md` (5개 가설 배제 기록).

## 3. 임무 (Codex 구현)

Java FORMULA read 누락 **근본원인 확정**(임시 진단로그/IT로 `readSheetFormulas` 응답의 행수·F열 셀 타입·$L$2 행수를 직접 덤프 → JS 378/107과 비교) → **fix** → **검증**.

fix 후보(진단 결과로 택1):
- (a) `GsonFactory` → `JacksonFactory` (대용량 mixed-type 배열 파싱 차이)
- (b) JS `readSheetGrid` 패턴 이식 — values+formulas 행/폭 union 정렬(행 어긋남 방어)
- (c) `values().get` → `batchGet` (응답 일관성)
- (d) DISPLAY↔FORMULA 행정렬 가드 (modelCode 키 기반 매칭 등)

## 4. 검증 기준 (PM, 실 sync)

product-service 재빌드 → admin sync(또는 재기동) →
```sql
SELECT product_category, count(*) FILTER(WHERE has_variable_discount) vdc
FROM products WHERE is_deleted=false GROUP BY 1;
```
- COMMERCIAL_MULTI vdc 86 → **~306 기대**(시트 378/417 비율 × DB 338품목)
- HOME_MULTI vdc 54 → **~104 기대**(시트 107/122 × DB 119)
- **회귀가드**: matKey(SINGLE_PART 현 174 vdc / set_material_key 32), legacy(OLD 30) **비감소**.
- 멀티라인 `=LET` 수식 정상 검출 확인.

## 5. 개발책임자 추가 결정 (2026-06-17) — 변동DC 토글 통합

useK2(`$L$2`)=변동DC. 개발책임자: **변동DC를 멀티 카탈로그(견적품목 관리)에서 수동 토글로 관리**.

- **모델 = 초기값 + 수동 override**: 시트 sync로 초기/기본값 자동 적재(376 modelCode) + 멀티 카탈로그 토글로 수동 on/off. **`variableDiscountManual` 플래그**(usageScopeManual 동일 패턴 — 토글 후 sync가 그 값 미덮어씀).
- **적재 덮어쓰기 fix 동반** (실측 적발): 구성품 탭(상업멀티구성 useK2=0)이 견적 탭 품목 변동DC를 false로 덮어쓰던 버그(상업멀티 useK2 376 modelCode 중 DB true 86/false 227) → `applyDiscountRules`/`changeDiscountFlags`를 `productCategory 일치 && !variableDiscountManual` 가드 안으로.
- **범위 = 슬3-1 통합**: range fix + 적재 fix + variableDiscountManual + 멀티 카탈로그 토글 BE/FE.
- **검증 갱신**: 적재 fix 후 COMMERCIAL_MULTI vdc 86 → ~313, HOME_MULTI 55 → ~107 (useK2 unique modelCode 기준).

## 6. 비-목표 / 재배치

- **specDetailMap DB 승격 = 슬3-2**(다음). 변동DC **견적 할인 계산 적용 = 슬4**(토글된 플래그를 실제 단가 계산에 반영).
- **#19 멀티단가 동적화 = 현행 고정**(개발책임자 결정, `commUnitPrice` 유지).

## 7. 워크플로우

조기 PR → Codex 구현 → Opus 4.8 5-agent → Codex 5-agent 교차 → Opus 수렴 재리뷰 → PM 종합 → 머지.
각 리뷰 라운드 5-agent에 QA agent 포함 + Docker 실 QA(실 sync 카운트) 그 라운드 코멘트 인라인 게시.
