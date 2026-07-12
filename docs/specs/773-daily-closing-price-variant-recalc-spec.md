# #773 일마감 단가변동 재계산 토글 — 설계 스펙 (개발책임자 검토용)

- **일자**: 2026-07-12 · **작성**: PM(Opus) 심화 정찰 기반 · **상태**: 🟢 전 결정 확정 · **S1a 완료(#800 dev 시더+정가 endpoint)** → S2(재검증 엔진) 착수 가능. S1d(실 시트 sync·자격) 격리 대기

## ✅ 확정 결정 요약 (2026-07-12 개발책임자)
- **D1 = ⓐ 할인율 재검증 워크시트** (레거시 동등·마감 금액 불변·`확인` 플래그·read-time 감사).
- **D5+D6 = 주문/전표 계층**(출고전표/주문 라인, productId 보유 → price_history 직접 join·fuzzy 불필요).
- **D3 = price_history 채택 + 실시드 선행**(S1에서 실 단가시트 sync[dev 0행 해소]+구성품/구형 baseline[#777 잔여] 먼저 채움).
- D2(카테고리 축)=마감 스키마 불필요(감사 리포트 표시 그룹, price_change_schedule 재사용)·D4=read-time·D7=lock 무관(D1=ⓐ 파급).

## 확정 아키텍처 (감사 파이프라인)
```
마감일 D 기준 → 해당일 출고전표/주문 라인(productId 보유) 집계
  → 각 라인: price_history.findApplicableLatest(productId, 인상전 or 인상후 기준일)로 시점별 정가 lookup
  → 기대 할인율 = 1 - (라인 stamp 단가 / 시점별 정가) vs 거래처 약정할인(dc-config) 대조
  → `확인` 플래그 산출 (금액 불변·read-time)
  → 전역 토글(isBeforeHike 동등): 검증 기준 정가를 인상전/후 중 선택
```
- **연관**: #17 단가변동(S1~S4b 완료) · 레거시 `tools/legacy-gas/일마감 프로그램/Code.js` · 개발책임자 2026-07-08 "별도 대규모 슬라이스" 규정

---

## 0. 🚨 최우선 발견 — "재계산"의 실제 의미 (레거시 코드 정독 결과)

**레거시 `isBeforeHike`는 "가격을 다시 매기는(re-pricing)" 것이 아니라 "할인율 재검증(re-validation) 워크시트"다.**

`Code.js:420 processDailyData(ecountData, isMultiApplied, isBeforeHike)`:
- raw 이카운트 export의 각 라인에 대해 **시점별 단가시트(인상전/후)를 referent로** 출고가·할인율을 재산출.
- 거래처 DC 약정(홈멀티DC/상업멀티DC/360/4way/1way/스탠드/디럭스/1등급/할인제외)과 대조 → **`확인` 플래그만** 산출.
- **공급가액/부가세/합계는 이카운트 raw 그대로 통과**(재산출 안 함, `Code.js:11-14,458-471` FINAL_HEADERS 매핑).
- `isBeforeHike` = 전역 단일 토글: 검증 기준 정가 시트를 인상전/후 중 무엇으로 볼지 고르는 스위치(`Code.js:424-441`).

→ **즉 마감 금액을 바꾸는 게 아니라, 이카운트가 찍어온 단가가 "시점별 정가 × 거래처 약정할인"과 맞는지 감사**하는 것. 이 발견이 스펙 전체 방향을 좌우.

---

## 1. 현대 시스템 현황 (재계산 referent 부재)

| 항목 | 현황 | 근거 |
|---|---|---|
| **일마감 파이프라인** | `DailyClosingService.close()`가 이미 stamp된 최종 합계를 **SUM+lock만**. 단가 재산출 코드 없음 | `DailyClosingService.java:104-163` |
| **DailyClosing 그룹 축** | date+partnerId+closingKind+sourceKind. **카테고리 축 없음**·라인 detail 없음(총액만) | `DailyClosing.java:66-112` |
| **단가변동 stamp 상류** | 견적(S2 수동 체크박스)·주문(S3 납기일 자동전환)에 이미 stamp. 마감 재선택은 상류와 **중복·충돌** | dev-reports S2/S3 |
| **시점별 정가 referent** | product-service `price_history`(product당 인상전 2000-01-01/인상후 2026-04-01) 존재. 단 **dev 0행**(실 sync 필요) | `PriceHistory.java`·`PriceHistoryRepository.findApplicableLatest` |
| **🚨 referent 갭(핵심)** | 마감이 집계하는 회계 문서(TaxInvoiceLine·Sales/PurchaseAccountingSlipLine)는 **productId 미보존·텍스트 itemName/productCode만**. 시점별 정가 join 불가. 견적/주문 라인엔 productId 있으나 **회계 변환서 소실** | `TaxInvoiceLine.java:55-75`·`SalesAccountingSlipLine.java:40-52`·`MonthEndCloseService.java:209` |
| **원단가/변동전단가 보존** | 회계 라인은 **최종 stamp 단가만**. 원정가·할인율·인상플래그 미보존 | 상동 |
| **전역 vs 카테고리별** | 레거시=전역 1토글 / 현대 S4=카테고리별(price_change_schedule 4행) | `Code.js:424-441` vs `PriceChangeSchedule.java` |

**결론**: 단순 토글 신설이 아니라 **매핑 인프라 + 재계산(재검증) 엔진 + (선택)스키마 축 + FE**의 4~5 슬라이스 에픽.

---

## 2. 🟡 개발책임자 결정 필요 (구현 전 확정)

### D1 (최우선) — "재계산"의 의미 ✅ **확정: ⓐ 할인율 재검증 워크시트** (2026-07-12)
- **ⓐ 레거시식 할인율 재검증 워크시트** (감사 리포트·**마감 금액 불변**·`확인` 플래그) ← *레거시 실제 동작·회계 원장 수정금지 원칙과 정합. **개발책임자 확정.***
- ~~ⓑ 인상 전 가격으로 what-if 총액 재산출(마감 금액 변경)~~ — 미채택(원장/마감 불변 충돌)
- ~~ⓒ 마감 SUM referent 전환~~ — 미채택

> **D1=ⓐ 확정 파급**: D4(시점)=**read-time 감사**(마감 금액 불변→무결성 안전, 저장 불필요)·D7(소급)=lock 무관(감사는 read-time이라 언제든 조회 가능)·D2(카테고리 축)=DailyClosing 스키마 축 **불필요**(감사 리포트의 표시 그룹으로 축소, price_change_schedule 카테고리 재사용). → **잔여 핵심 결정 = D3(데이터소스)·D5(계층)·D6(대상문서)**.

### D2 — 전역 vs 카테고리별
레거시 전역 1토글 UX 유지 vs 현대 S4 카테고리별 정합.

### D3 — Referent 데이터소스
price_history(2000-01-01 baseline) 채택 여부·dev 0행 실 sync·구성품/구형 미커버(#777 item3 미해결) 처리.

### D4 — 재계산 시점 + 무결성
마감 실행 시 저장 vs 조회 시(read-time) 산출. **회계 원장 수정금지·마감 불변**(메모리 `accounting_ledger_edit_policy`)과 정합 — D1=ⓐ면 read-time 감사라 무결성 안전.

### D5 — 재계산 계층 ✅ **확정: 회계 문서 텍스트 매칭 endpoint** (2026-07-12)
- **회계 라인(TaxInvoiceLine·SalesAccountingSlipLine) itemName/productCode 텍스트 → product-service 조회 endpoint로 productId 런타임 매핑.** 스키마 무변경·기존 문서 불변·read-time 감사(D4)와 정합. ← *개발책임자 확정.*
- ~~productId 컬럼 플러밍(회계 문서 스키마 신설+백필+변환경로)~~ — 미채택(다서비스 스키마 마이그·기존 데이터 백필·대규모·무결성 주의).
- ~~상류(견적/주문) productId 경로~~ — 미채택(회계 변환서 소실·referent 갭 미해소).
- **파급**: S1 = "회계 라인 텍스트→productId 매핑 endpoint"(런타임·product-service). 동명이인/코드변경 매칭 실패 대비 fallback 설계 필요. 레거시 Code.js 매칭 로직(정가 시트 join 방식) 참조.

### D6 — 대상 문서
세금계산서 vs 매출전표 vs (레거시처럼) 이카운트 raw(출고전표) 중 referent.

### D7 — 기존 마감본 소급
lock된 마감 소급 재계산 허용 여부·AccountingPeriod 잠금/역마감 관계.

> **PM 권고**: **D1=ⓐ(할인율 재검증 워크시트)** — 레거시 실제 동작과 일치하고, 회계 무결성(원장/마감 금액 불변)을 지키며, read-time 감사(D4)라 안전. ⓑ/ⓒ는 마감 금액을 사후 변경해 무결성 도메인 정책 위반 소지. D1이 ⓐ로 확정되면 D5는 "감사 대상 문서 라인 → product 매핑"만 필요(스키마 축 D2·카테고리는 감사 리포트 그룹으로 축소 가능).

---

## 3. 슬라이스 분할 제안 (D1=ⓐ 가정)

| 슬 | 범위 | 산출 |
|---|---|---|
| **S0** | 정책 확정(비-코드) | §2 D1~D7 개발책임자 결정 |
| **S1** | Referent 인프라 | 회계 라인 텍스트→productId→시점별 정가(price_history) 매핑 endpoint(또는 productId 플러밍) + price_history 실시드 + 구성품/구형 baseline(#777 잔여) |
| **S1.5**(조건부) | 검증 config | 거래처 약정할인(dc-config) 검증 노출 + 세트 구성품 분해(BundleExpander) 재사용 |
| **S2** | 재계산(재검증) 엔진 BE | 문서 집계→매핑→시점별 정가→기대 할인→`확인` 플래그. 레거시 확인 로직 포팅 |
| **S3**(조건부) | 결과 표현 | daily_closings 라인 detail 부재 → 조회시 on-the-fly 산출 or 검증결과 테이블 |
| **S4** | FE 토글 + 결과 뷰 | DailyClosingPage "인상 전 적용" 토글 + 출고가/할인율/확인 결과표 + 라이브 QA |

---

## 4. 다음 단계
D1(재계산 의미)이 스펙 전체를 좌우하므로 **§2 결정, 특히 D1을 먼저 확정** → 확정 후 S1부터 정식 캐논(Codex 구현 + Opus 5-agent + 라이브 QA)으로 착수.

---

## 5. S1 심화 정찰 (2026-07-12·3-agent) — referent 매핑 실태 + S1 재분할

> D5=텍스트 매칭 endpoint 확정 후, 회계 라인/product-service/레거시 Code.js 3면 정찰. **S1이 단일 슬라이스가 아니라 매핑 파이프라인 + 검증 소스임을 규명.**

### 5.1 회계 라인 실태 (accounting-service)
- **productId 전무 확증**: `TaxInvoiceLine`(`item_name` NOT NULL / `spec` / `unit`, 부모 `tax_invoice_id` FK만) · `SalesAccountingSlipLine`·`PurchaseAccountingSlipLine`(`product_name` / `product_code`, 부모 `slip_id` FK만).
- **일마감 집계 경로** = `MonthEndCloseService.getTaxInvoiceDailyDetail`(:177-257). group 키 = **텍스트**(`byModel: Map<String,ModelAccumulator>`) — TAX_INVOICE=`itemName`(:226) / SALES_SLIP=`productName`(:285) / PURCHASE=`productName`(:319). L209 주석 "productId 미보존→itemName 키". `DailyProductLine.modelName`은 현재 항상 null(:242-245, productClient가 UUID lookup만이라 placeholder 회피).
- **텍스트 실값 = 이카운트 `품목명[규격]` 원문 라벨**(미정규화): TaxInvoiceLine.item_name ← EcountTaxInvoiceImporter c[11], spec/unit=NULL insert. **SalesAccountingSlipLine.product_code = 리터럴 "MIG4" 하드코딩**(조인키 무용). 실 월마감 `close()`는 JournalLine을 계정코드 prefix로 SUM(품목 무관).

### 5.2 product-service 조회 자산 (이미 존재)
- **텍스트/코드→product endpoint 다수 기존**(`ProductInternalController` `/products/internal`): `/lookup-by-model`(modelName 정확·단건·404)·`/lookup-by-code`(productCode 정확·단건)·`/by-name`(name 정확·**404/409중복**)·`/lookup-by-model-codes`(bulk)·**`/resolve-ecount-aliases`**(aliasCode→ProductAlias 해석)·`/expand`(세트 전개).
- **매칭 안전 키 = 코드류**(`productCode`/`modelCode`/`modelName` 각 active partial-unique 단건 보장). **`name`은 유니크 아님→다건 시 409**(`lookupSummaryByName` CONFLICT). `ProductAlias`(alias_code→main_product N:1)·native LIKE `search()` 재사용 가능.
- S1a(#800) `PriceHistoryInternalController /applicable`(productId+asOf→시점정가)로 **뒷단(productId→정가) 이미 완성**. 갭 = **앞단(품목명[규격] 라벨→productId)**.

### 5.3 레거시 매칭·확인 로직 (Code.js:420-749)
- **매칭 = 4단 fallback + 토큰 정규화**(단순 exact 아님): `extractModelToken_`(:167 괄호제거→대문자→모델코드 정규식 `AC|AP|AR|AF|AM|AJ|AXJ|PC|AWR|ARR` 접두 추출) → ①OLD 시트 우선 ②액세서리 키워드 부분일치(유연호스/방진가대) ③zone별(`^AXJ`=COMM_MULTI 강제) ④UNKNOWN fallback ⑤miss=0. 현대 `/lookup-by-model`은 exact only → **토큰 정규화+다단 fallback 계층 앞단 필요**(그냥 쓰면 매칭율 급락).
- **isBeforeHike = 전역 배치 토글**(앞 5행 첫 날짜로 suffix 1회 결정·임계 20260401=price_history 인상후 2026-04-01 일치). true=날짜무시 인상전 강제 → 현대는 asOf=baseline(2000-01-01) 고정으로 등가.
- **`확인` 판정 = 정가만으로 불가**: `출고가(price)`·**`납품가(deliveryPrice)`**·**`고정dc(fixedDc)`** 3종 의존(:551-558,680,688,721). 회계 라인은 stamp 단가만·price_history는 정가만 → **납품가/고정dc 소스 매핑 별도 필요**(dc-config/product). 구형(OLD) 50%고정/납품가완전일치 분기는 **구형 baseline(#777 잔여) 없으면 재현 불가**.
- 허용오차: epsilon 없음. 할인율=`Math.round(rate*100)` 정수% 동등 / 가격=`money_to_int_` 정수원 완전일치 / 세트합=`Math.abs` 정수원. **BigDecimal scale/반올림 모드 차이 시 경계값 확인 뒤집힘**.
- 약정DC=Notion 거래처코드(숫자정규화 키)→홈/상업멀티율(rate)+360/4way/1way/스탠드/디럭스/1등급(정액원). 현대=dc-config `DcConfig`+`PriceCalculationService`. **이카운트 거래처코드→partnerId 매핑 선행 필요**. 세트=역-BundleExpander(flat 라인 재조립·구성품수 내림차순 그리디·원단위 완전일치).

### 5.4 S1 재분할 (정찰 반영)
| 슬 | 범위 | 비고 |
|---|---|---|
| **S1a** ✅ | price_history 시더 + productId→시점정가 endpoint (#800) | 완료 |
| **S1b** | **품목명[규격] 라벨 → productId 매핑 endpoint** (accounting→product) | 토큰 정규화(`extractModelToken_` 포팅)+4단 fallback(코드→modelName→alias→LIKE)+다의성(409)/미매칭(404) 리포팅. **S2 최소 선결**. 순수 조회·무결성 무관 |
| **S1c** | 납품가·고정dc referent 소스 | `확인` 판정 3종값 중 정가 외 2종. product/dc-config 매핑. **레거시 정합 필수** |
| **S1d** | 구형(OLD) baseline + 실 시트 sync | #777 잔여·Google 자격·격리 운영 |
| **S1.5** | dc-config 검증 노출 + 이카운트 거래처코드→partnerId + 역-BundleExpander 세트 매처 | 세트/약정DC 확인 로직 |

> **PM 판단**: **S1b(라벨→productId 매핑 endpoint)가 S2 최소 선결이자 결정불요·무결성무관·순수조회**라 **다음 착수 1순위**. S1c/S1.5는 `확인` 판정 정확도 슬라이스(레거시 정합·중형)로 S2와 함께/후속. S1d는 자격·격리로 별개 트랙.

### 5.5 🚨 S1b 착수 blocker — dev 데이터 세계 불일치 (2026-07-12 실 DB 확인)
착수 전 실 데이터로 `품목명[규격]` 파싱 규칙 실증 시도 → **dev DB와 레거시 매칭 규칙이 다른 세계임을 확인**:
- **accounting_db `tax_invoice_lines` = 16행 전부 서비스 품목**(운반료·수수료·보험료·QA테스트) — HVAC 제품 라벨 0. `sales_accounting_slip_lines` = **0행**.
- **product_db products(100행) = 삼성 유통품**(`product_code` 전부 6자리 `010xxx`·`model_code` 비어있음·specification="N평형 / R32 / 인버터 / 윈드프리"). **레거시 모델코드 체계(AC/AP/AR/AF/AM/AJ/AXJ/PC/AWR/ARR 접두)를 가진 삼한 자체제작 제품 0개**(model_code 접두=QA7…4·TES…1 테스트시드뿐).
- 레거시 AC/AP 모델코드는 **실 파일(계산서 발행용.xlsx·종합견적서)에 실재** → 레거시 세계엔 있으나 dev DB엔 부재.
- **함의**: S1b(`extractModelToken_` 포팅+4단 fallback) 구현해도 **dev에 매칭 대상(AC… 모델코드 제품)이 없어 genuine 매칭 실증 불가**. 합성 AC… 제품 시드는 [[feedback_no_fake_data_ever]] 위반. 실 삼한 카탈로그·실 이카운트 raw는 **S1d(Google Sheets sync·자격·격리)**에 묶임.
- **PM 권고(개발책임자 판단 필요)**: ⓐ S1d(실 시트 sync)를 S1b보다 **먼저**(실 카탈로그 확보 후 매핑 실증) — 단 Google 자격·격리 운영 필요 / ⓑ S1b를 **IT 픽스처 기반**으로 구현(실 라벨 샘플을 계산서 발행용.xlsx에서 추출해 테스트 리소스화·dev 라이브 실증은 S1d 후로 유예)·순수 매퍼 로직은 unit/IT로 genuine 검증 / ⓒ #773 전체를 S1d(실 데이터 확보) 전까지 **보류**하고 결정불요 소형 백로그 우선.

### 5.6 ✅ 개발책임자 결정 = ⓑ IT 픽스처 기반 S1b 착수 (2026-07-12)
- **실 라벨 근거 확보**: `tools/legacy-gas/계산서일괄등록양식 생성/계산서 발행용.xlsx` sheet1에서 실 삼한 HVAC 라벨 **267개 unique** 추출(총 791 매치). 형식 = `<모델코드12자> [<설명>] [<옵션>]`(예 `AC023CN1DBC1 [CN냉전 실내기]`·`AJ040RXH4BC1 (RX냉방기)`). 규격표기 = 대괄호[] 252·소괄호() 43·무괄호 11. **매칭 키 = 선두 모델코드 토큰**(공백 전·`extractModelToken_` 정규식 `(AC|AP|AR|AF|AM|AJ|AXJ|PC|AWR|ARR)[A-Z0-9\-]{4,}`과 정확 일치), 대괄호 설명은 표시용·매칭 무관.
- **S1b 범위**: accounting 회계 라인 텍스트(`품목명[규격]` 라벨) → **모델코드 토큰 추출** → product-service 조회(modelName/modelCode exact → alias → LIKE 4단 fallback) → productId. 다의성(409)/미매칭(404) 구조화 리포팅. **순수 조회·무결성 무관·결정불요.**
- **genuine 검증 = IT 픽스처**: 실 라벨 267개(합성 아님·실 레거시 데이터)를 테스트 리소스화 → 토큰 추출·fallback·다의성 매퍼를 unit/IT로 검증. **라이브 라벨→productId hit 실증은 S1d(실 카탈로그) 후로 유예**(dev product_db=삼성 유통품이라 AC… 모델코드 매칭 대상 부재 — 명시적 유예·합성 시드 금지).
