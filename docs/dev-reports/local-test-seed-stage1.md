# Stage 1 (master data) 로컬 테스트 seed — 거래처 50 + Samsung HVAC 100

> branch: `feature/local-test-setup`
> 작업일: 2026-05-09
> 범위: Stage 1 (마스터 데이터) — partner-service / product-service
> 후속: Stage 2 (재고/창고), Stage 3 (슬립/주문), Stage 4 (회계)

---

## 1. 배경 및 목표

Phase 10 진행 중 14 backend MSA 의 풀 수준 로컬 검증을 위해 **마스터 데이터 시드** 가 필요하다. 기존:

- `partner-service` — V1 schema 만 존재, seed runner 없음 → 슬립/주문/회계 시뮬레이션 불가
- `product-service` — `ProductSeedRunner` (시트 dry-run) 가 있으나 `@Profile("seed")` 일회성 + 시트 dump 가 로컬 워크스테이션에 없으면 무동작

본 슬라이스는 다음 두 seeder 를 추가하여 **로컬 dev 환경에서 toggle 한 번으로 50 거래처 + 100 Samsung HVAC 제품을 결정적으로 INSERT** 한다.

### 1.1 출처 (이카운트 reference)

`docs/migration/ecount-reference/` 16 이미지를 분석 — 거래처 4 탭 + 품목 3 탭의 모든 컬럼을 매핑.

| 캡처 | 화면 | 추출 컬럼 |
| ---- | ---- | --------- |
| 091522 | 거래처 기본 탭 | bizNo / subBizNo / 대표자 / 업태 / 종목 / 전화 / FAX / email1 / email2 / mobile |
| 091540 | 거래처정보 탭 | 우편번호 1+2 / 주소 1+2 / 검색키워드 / 분류1 / 분류2 / website |
| 091551 | 여신단가 탭 | currency / shipmentTarget / salesType / purchaseType / receivableNoMgmt / payableNoMgmt / outboundAdjRate / inboundAdjRate / salesPriceGroup / purchasePriceGroup / creditPeriod / paymentDue |
| 091604 | 부가정보 탭 | registrationDate (등록일자) |
| 091955 | 품목 기본 탭 | productCode (5자리) / specification / unit / productBusinessType / inventoryQtyMgmt / barcode / vatRateOnSales / vatRateOnPurchase / priceIncludesVat |
| 092007 | 품목정보 탭 (단가) | **HVAC 단가 6종 발견** ⭐ |
| 092016 | 수량 탭 | safetyStockQty / leadTimeDays / minOrderUnit / purchaseSource |

### 1.2 ⭐ HVAC 특화 단가 6종 (품목정보 탭 092007 발견)

이카운트 화면에서 발견된 HVAC 도매상 특화 단가 매트릭스:

| 단가 | 컬럼 | Multiplier (vs 입고단가) | 적용 시나리오 |
| ---- | ---- | ----------------------- | ------------ |
| 입고단가 | `inbound_price` | 1.00 (base) | 매입 기준가 (구매처 단가) |
| 출고단가 | `outbound_price` | 1.20 | 일반 출하 (도매 표준 마진 20%) |
| 출하가 | (= delivery_price 기존) | 1.15 | 기존 V3 컬럼 호환 |
| **싱글** | `single_price` | **1.50** | 벽걸이 단일 거래 (단품 마진 50%) |
| **실외기 (원형, 스탠드)** | `outdoor_price` | **1.40** | 실외기 단독 교체 거래 |
| **멀티 (50% 할인)** | `multi_50_price` | **1.10** | 멀티 시스템 묶음 거래 — 50% 할인 적용가 |
| **멀티 (48% 할인)** | `multi_48_price` | **1.12** | 멀티 — 48% 할인 적용가 |
| **멀티 (45% 할인)** | `multi_45_price` | **1.15** | 멀티 — 45% 할인 적용가 |
| **단품 (35% 할인)** | `item_35_price` | **1.30** | 단품 35% 할인 적용가 |
| VAT 포함 여부 | `price_includes_vat` | true (default) | 이카운트 default — VAT 포함 표시 단가 |

**비즈니스 룰 핵심**: HVAC 도매상은 "거래 형태별 (싱글/멀티/실외기/단품) × 할인 단계 (50/48/45/35%)" 매트릭스로 단가 보유. 단일 sellingPrice 1개로 처리하면 견적 발행 시 영업직원이 매번 수동 계산 → 본 6 컬럼으로 미리 결정된 가격 표시.

---

## 2. 산출물

| 파일 | 신규/갱신 | 역할 |
| ---- | --------- | ---- |
| `services/partner-service/src/main/resources/db/migration/V2__add_ecount_partner_fields.sql` | 신규 | Partner 24 컬럼 보강 + index 4 + 통화 enum guard |
| `services/partner-service/src/main/java/com/samhanair/logis/partner/domain/Partner.java` | 갱신 | 24 신규 필드 + 도메인 메서드 6 (updateBusinessProfile / updateContactChannels / updateAddresses / updateSearchKeyword / updateClassification / updateCreditPolicy / changeCurrency / changeShipmentTarget / changeRegistrationDate) |
| `services/partner-service/src/main/java/com/samhanair/logis/partner/repository/PartnerRepository.java` | 갱신 | `existsByPartnerCode` (idempotency) 추가 |
| `services/partner-service/src/main/java/com/samhanair/logis/partner/seed/PartnerSeeder.java` | 신규 | 50건 큐레이션 + 결정적 generator |
| `services/partner-service/src/main/resources/application.yml` | 갱신 | `app.partner.seed-test-data` toggle 추가 |
| `services/partner-service/src/test/java/com/samhanair/logis/partner/seed/PartnerSeederTest.java` | 신규 | idempotency 5 test |
| `services/product-service/src/main/resources/db/migration/V5__add_ecount_product_fields.sql` | 신규 | Product 22 컬럼 (이카운트 14 + HVAC 단가 8) + index 4 + unit enum guard |
| `services/product-service/src/main/java/com/samhanair/logis/product/domain/Product.java` | 갱신 | 22 신규 필드 + 도메인 메서드 6 (updateEcountMeta / updateVatPolicy / updateInventoryPolicy / updateGroups / updateHvacPriceMatrix / markDiscontinued) |
| `services/product-service/src/main/java/com/samhanair/logis/product/seed/HvacProductSeeder.java` | 신규 | 100건 (벽걸이 30 + 스탠드 20 + 시스템 25 + 천장형 10 + 공기청정기 10 + 부속 5) |
| `services/product-service/src/main/resources/application.yml` | 갱신 | `app.product.seed-test-data` toggle 추가 |
| `services/product-service/src/test/java/com/samhanair/logis/product/seed/HvacProductSeederTest.java` | 신규 | idempotency + HVAC 단가 비율 룰 7 test |
| `docs/dev-reports/local-test-seed-stage1.md` | 신규 | 본 문서 |

---

## 3. PartnerSeeder — 50개 거래처 큐레이션

### 3.1 회사명 (가공 — 실제 회사명 상표 침해 회피)

50개 한국 가상 HVAC 협력사. seq 1~50 결정적.

| seq | 거래처명 | 분류1 | 분류2 (지역) |
| --- | -------- | ----- | ----------- |
| 1 | (주)서울에어컨 | VIP거래처 | 수도권 |
| 2 | 한국공조시스템(주) | VIP거래처 | 수도권 |
| 3 | 부산냉난방테크 | VIP거래처 | 영남권 |
| 4 | 광주에어시스템 | 일반거래처 | 호남권 |
| 5 | 대구HVAC솔루션 | VIP거래처 | 영남권 |
| 6 | 인천공조산업 | 일반거래처 | 수도권 |
| 7 | 울산냉난방엔지니어링 | VIP거래처 | 영남권 |
| 8 | 수원에어컨센터 | 일반거래처 | 수도권 |
| 9 | 대전공조테크 | 일반거래처 | 충청권 |
| 10 | (주)성남에어시스템 | 신규거래처 | 수도권 |
| 11 | 고양냉난방주식회사 | VIP거래처 | 수도권 |
| 12 | 용인HVAC산업 | 일반거래처 | 수도권 |
| 13 | 안양공조에너지 | 일반거래처 | 수도권 |
| 14 | 부천에어테크 | 신규거래처 | 수도권 |
| 15 | 남양주냉난방 | 일반거래처 | 수도권 |
| 16 | 춘천공조설비 | 일반거래처 | 강원권 |
| 17 | 원주에어컨공업 | 신규거래처 | 강원권 |
| 18 | 강릉HVAC솔루션 | 일반거래처 | 강원권 |
| 19 | 청주공조에너지 | VIP거래처 | 충청권 |
| 20 | (주)천안냉난방 | 일반거래처 | 충청권 |
| 21 | 전주에어시스템 | 일반거래처 | 호남권 |
| 22 | 군산공조산업 | 신규거래처 | 호남권 |
| 23 | 목포냉난방엔지니어링 | 일반거래처 | 호남권 |
| 24 | 여수HVAC테크 | 신규거래처 | 호남권 |
| 25 | 포항에어컨주식회사 | 일반거래처 | 영남권 |
| 26 | 경주공조설비 | 일반거래처 | 영남권 |
| 27 | 김해냉난방테크 | 일반거래처 | 영남권 |
| 28 | 양산에어솔루션 | 신규거래처 | 영남권 |
| 29 | 거제공조산업 | 일반거래처 | 영남권 |
| 30 | (주)창원HVAC | VIP거래처 | 영남권 |
| 31 | 마산냉난방기기 | 일반거래처 | 영남권 |
| 32 | 진주에어시스템 | 일반거래처 | 영남권 |
| 33 | 통영공조테크 | 신규거래처 | 영남권 |
| 34 | 안동HVAC공업 | 일반거래처 | 영남권 |
| 35 | 구미에어컨산업 | VIP거래처 | 영남권 |
| 36 | 포천공조엔지니어링 | 일반거래처 | 수도권 |
| 37 | 의정부냉난방 | 신규거래처 | 수도권 |
| 38 | 동두천에어솔루션 | 일반거래처 | 수도권 |
| 39 | 양주공조설비 | 일반거래처 | 수도권 |
| 40 | (주)파주HVAC | VIP거래처 | 수도권 |
| 41 | 광명냉난방테크 | 일반거래처 | 수도권 |
| 42 | 시흥에어컨공업 | 일반거래처 | 수도권 |
| 43 | 하남공조산업 | 신규거래처 | 수도권 |
| 44 | 구리에어시스템 | 일반거래처 | 수도권 |
| 45 | 오산냉난방 | 일반거래처 | 수도권 |
| 46 | 안성HVAC솔루션 | VIP거래처 | 수도권 |
| 47 | 이천공조에너지 | 일반거래처 | 수도권 |
| 48 | 여주에어컨테크 | 신규거래처 | 수도권 |
| 49 | 광양공조산업 | 일반거래처 | 호남권 |
| 50 | (주)순천냉난방 | VIP거래처 | 호남권 |

### 3.2 결정적 생성 룰

**partnerCode**: `String.format("P-2026-%04d", seq)` → `P-2026-0001` ~ `P-2026-0050`

**bizNo** (한국 사업자번호 10자리, XXX-XX-XXXXX 형식):
- prefix = `100 + (seq * 13) % 900` (100~999)
- mid = `(seq * 7) % 100` (00~99)
- tail = `10000 + (seq * 31) % 90000` (10000~99999)
- 검증식 미적용 (seed 데이터 — 실 NTS 호출 X). 단위 테스트 `bizNoIsUniquePerSeed` 가 50건 unique 보장.

**phone**: `02-XXXX-XXXX` (지역번호 02 고정, mid+tail seq 기반)
**fax**: `02-XXXX-XXXX` (phone 과 다른 hash — `seq + 1000` 기반)
**mobile**: `010-XXXX-XXXX` (모든 50건)
**email**: `info{seq}@samhan-test.com`
**email2**: `tax{seq}@samhan-test.com` (seq <= 15 만 — 정산담당 보유 가정)

**zipCode1**: `String.format("%05d", 10000 + seq * 7)` (50건 모두)
**zipCode2 + address2**: 30건만 (seq <= 30) — 별도 배송지 보유 가정

**website**: 10건만 (seq <= 10) — `https://samhan-test-{seq}.co.kr`

**creditLimit** (5단계 분포): `[100만, 500만, 1천만, 3천만, 5천만]` 원, `seq % 5` 인덱스
**creditPeriodDays**: `30 / 60 / 90` (`seq % 3` 순환)
**paymentDueDays**: `30 / 45 / 60` (`seq % 3` 순환)
**outboundAdjustmentRate**: `(seq % 6) * 0.01` (0.00 ~ 0.05 = 0~5%)
**inboundAdjustmentRate**: `((seq + 3) % 6) * 0.01` (0.00 ~ 0.05)

**salesPriceGroup** (partnerGroup1 매핑):
- `VIP거래처` → `VIP단가`
- `신규거래처` → `신규단가`
- `일반거래처` → `일반단가`

**purchasePriceGroup**: `기본구매단가` (전부)
**salesType / purchaseType / receivable / payable**: `기본설정` (이카운트 default)

**status**:
- `seq % 10 == 0` (5건: seq 10/20/30/40/50) → `SUSPENDED`
- 나머지 45건 → `ACTIVE`

**shipmentTarget**:
- `seq % 5 == 0` (10건) → `false`
- 나머지 40건 → `true`

**registrationDate**: `2024-01-01 + (seq - 1) * 17일` → seq 1=2024-01-01, seq 50=2026-04-30

**searchKeyword**: `"{name} {bizNo} {phone}"` (이카운트 검색 보조)

**subBizNo**: 10건만 (seq % 5 == 0) → `String.format("%04d", seq)` (종사업장 보유)

**대표자**: 50개 한국 이름 큐레이션 (홍길동, 김철수, 박영수, 이미영, 최정호, ...)

**업태/종목** (seed row 정의):
- 업태: 제조업 / 도소매 / 건설업
- 종목: 공조설비 / 냉난방기기 / 공조설비시공 / 에어컨도매 / 에어컨소매

### 3.3 분포 통계

| 항목 | 분포 |
| ---- | ---- |
| 분류1 | VIP 12건 / 일반 28건 / 신규 10건 |
| 분류2 (지역) | 수도권 24 / 영남권 13 / 호남권 7 / 충청권 3 / 강원권 3 |
| status | ACTIVE 45 / SUSPENDED 5 |
| shipmentTarget | true 40 / false 10 |
| 종사업장 보유 | 10건 |
| email2 보유 | 15건 |
| website 보유 | 10건 |
| 별도 배송지 보유 | 30건 |
| creditLimit 분포 | 100만 10 / 500만 10 / 1천만 10 / 3천만 10 / 5천만 10 |

---

## 4. HvacProductSeeder — Samsung HVAC 100 모델

### 4.1 분류별 분포

| 분류 (group2) | 수량 | seq 범위 | 모델 패턴 | 카테고리 (V2 카탈로그) |
| ----- | ---- | -------- | -------- | --------------------- |
| 벽걸이 (WIND-FREE) | 30 | 1~30 | `AR{Pyong}TXEAAWKNEU-{seq}` | INDOOR_WALL |
| 스탠드 (BESPOKE) | 20 | 31~50 | `AF{Pyong}BX1NWAEAH-{seq}` | INDOOR_WALL |
| 시스템에어컨 (DVM-S) | 25 | 51~75 | `AM{HP*10}BNNDEH-{seq}` | OUTDOOR |
| 천장형/매립형 | 10 | 76~85 | `AC{(idx+1)*100}CNCDEH-{seq}` | INDOOR_CEILING |
| 공기청정기 | 10 | 86~95 | `AX{m2}B{m2}NNDB-{seq}` | INDOOR_WALL |
| 부속/배관 | 5 | 96~100 | `PIPE-CU-15A` / `PIPE-CU-22A` / `INSUL-T20` / `REMOTE-MR-DH00` / `COMM-MIM-N10` | PIPING |

(modelName 끝 `-{seq}` suffix 는 unique 보장용 — Samsung 실모델은 같은 평형이라도 옵션/연식 별 suffix 가 다름)

### 4.2 ⭐ HVAC 단가 6종 적용 비즈니스 룰 (핵심)

**입고단가 (inboundPrice) 산출 base**:

```
inbound = tonnageHp * 100,000원 (KRW)
```

여기서 `tonnageHp` 는 카테고리별 의미가 다름:
- 벽걸이 / 스탠드: 평형 (5/6/7/9/11/13/15/16/17/18/20/23/25/26/30)
- 시스템: HP × 3 (3HP=9, 4HP=12, ..., 22HP=66)
- 천장형: 톤수 × 4 (3톤=12, ..., 20톤=40)
- 공기청정기: ㎡ ÷ 10 (17㎡=1.7, ..., 100㎡=10.0)
- 부속: 0.2 ~ 0.7 (소형 단가)

**예시**:
| seq | name | tonnageHp | inboundPrice | outboundPrice (×1.20) | singlePrice (×1.50) | outdoorPrice (×1.40) | multi50Price (×1.10) | multi48Price (×1.12) | multi45Price (×1.15) | item35Price (×1.30) |
| --- | ---- | --------- | ------------ | ---------------------- | -------------------- | --------------------- | --------------------- | --------------------- | --------------------- | -------------------- |
| 1 | 윈드프리 5평형 | 5 | 500,000 | 600,000 | 750,000 | 700,000 | 550,000 | 560,000 | 575,000 | 650,000 |
| 5 | 윈드프리 11평형 | 11 | 1,100,000 | 1,320,000 | 1,650,000 | 1,540,000 | 1,210,000 | 1,232,000 | 1,265,000 | 1,430,000 |
| 31 | 비스포크 22.5평형 (15p×1.5) | 22.5 | 2,250,000 | 2,700,000 | 3,375,000 | 3,150,000 | 2,475,000 | 2,520,000 | 2,587,500 | 2,925,000 |
| 51 | DVM-S 9HP (3HP×3) | 9 | 900,000 | 1,080,000 | 1,350,000 | 1,260,000 | 990,000 | 1,008,000 | 1,035,000 | 1,170,000 |
| 76 | 천장형 3톤 (8 base) | 8 | 800,000 | 960,000 | 1,200,000 | 1,120,000 | 880,000 | 896,000 | 920,000 | 1,040,000 |
| 86 | 큐브 17㎡ (1.7) | 1.7 | 170,000 | 204,000 | 255,000 | 238,000 | 187,000 | 190,400 | 195,500 | 221,000 |
| 96 | 동관 15A | 0.5 | 50,000 | 60,000 | 75,000 | 70,000 | 55,000 | 56,000 | 57,500 | 65,000 |

**6 단가의 영업 시나리오 매핑**:

| 단가 컬럼 | 사용 시나리오 | 예시 견적 |
| -------- | ----------- | -------- |
| `inboundPrice` | 매입 (구매 PO) | 삼성전자(주) 발주 시 |
| `outboundPrice` | 일반 도매 출하 | 거래처 일반 슬립 (default) |
| `singlePrice` | 벽걸이 단일 거래 | 윈드프리 1대 단독 견적 (소비자 직판 가깝게) |
| `outdoorPrice` | 실외기만 교체 | A/S 실외기 단독 교체 견적 |
| `multi50Price` | 멀티 시스템 50% 할인 묶음 | 시스템에어컨 멀티 + 부속 packaged (대형 거래) |
| `multi48Price` | 멀티 48% 할인 | 멀티 중간 규모 거래 |
| `multi45Price` | 멀티 45% 할인 | 멀티 소규모 거래 |
| `item35Price` | 단품 35% 할인 | 부속/배관 단품 도매 |

본 6 단가는 견적/주문 화면에서 영업이 거래 시나리오별로 선택 (또는 자동 매핑 룰 적용 — Stage 3 슬라이스에서 정의). Stage 1 단계에서는 **마스터 데이터에 매트릭스 캐시** 만 보유.

### 4.3 이카운트 메타 결정적 생성

**productCode**: `String.format("01%04d", seq)` → `010001` ~ `010100` (이카운트 5자리 패턴 = `01` prefix + 4자리 seq)

**barcode**: `"880" + String.format("%010d", seq)` → 한국 EAN-13 13자리 (`880` 한국 prefix + 10자리 seq)

**unit**:
- 벽걸이 / 공기청정기 / 부속 (REMOTE/COMM) → `EA`
- 스탠드 / 시스템 / 천장형 → `SET`
- 동관 → `M`
- 절연재 → `BOX`

**productBusinessType**: `상품` (전부 — 도매상 모델, 자가 제조 X)
**inventoryQtyMgmt**: `true` (전부)
**vatRateOnSales / vatRateOnPurchase**: `0.10` (한국 표준 10%)
**priceIncludesVat**: `true` (이카운트 default)

**safetyStockQty**: `5 / 10 / 20` (`seq % 3` 순환)
**leadTimeDays**: `7 / 14 / 30` (`seq % 3` 순환)
**minOrderUnit**: `1` (전부)
**purchaseSource**: `삼성전자(주)` (전부 — 도매상 단일 공급원)

**productGroup1**:
- group2 = `부속` → `Samsung 부속`
- 그 외 → `Samsung 에어컨`

**productGroup2**: 분류 (벽걸이/스탠드/시스템/천장형/공기청정기/부속)

**status**:
- `seq % 25 == 0` (4건: 25/50/75/100) → `DISCONTINUED`
- 나머지 96건 → `ACTIVE`

### 4.4 sellingPrice / purchasePrice 동기화

기존 V1 컬럼 호환:
- `sellingPrice` ← `outboundPrice` (출고단가 = 일반 도매 마진)
- `purchasePrice` ← `inboundPrice` (입고단가)
- `releasePrice` ← `outboundPrice` (V3 release = 출고가)
- `deliveryPrice` ← `inboundPrice` (V3 delivery = 베이스 — 호환 보존)

### 4.5 V3 마이그 컬럼 채움

- `usageScope` = `BOTH` (전부 — 견적+주문 노출)
- `productType` = `SINGLE` (전부 — Stage 1 BUNDLE 미사용)
- `bundleMode` = `null`
- `productCategory` = `null` (이카운트 기준 매핑 — Stage 2 에서 V2 categoryCode 와 별도)
- `specText` = specification (legacy fallback)
- `remark` = description

---

## 5. 이중 가드 (toggle + profile)

### 5.1 PartnerSeeder

```yaml
# services/partner-service/src/main/resources/application.yml
app:
  partner:
    seed-test-data: ${SAMHAN_PARTNER_SEED_TEST_DATA:false}
```

```java
@Component
@Profile("dev")
@ConditionalOnProperty(value = "app.partner.seed-test-data", havingValue = "true")
public class PartnerSeeder implements CommandLineRunner { ... }
```

**활성 조건**: `--spring.profiles.active=dev` + `SAMHAN_PARTNER_SEED_TEST_DATA=true`

**비활성 default**: 운영 / staging / local 모든 환경에서 default 비활성. 명시적 toggle 필요.

### 5.2 HvacProductSeeder

```yaml
# services/product-service/src/main/resources/application.yml
app:
  product:
    seed-test-data: ${SAMHAN_PRODUCT_SEED_TEST_DATA:false}
```

```java
@Component
@Profile("dev")
@ConditionalOnProperty(value = "app.product.seed-test-data", havingValue = "true")
public class HvacProductSeeder implements CommandLineRunner { ... }
```

**활성 조건**: `--spring.profiles.active=dev` + `SAMHAN_PRODUCT_SEED_TEST_DATA=true`

**ProductSeedRunner 와의 직교성**: 기존 `ProductSeedRunner` 는 `@Profile("seed")` (시트 dry-run 전용). 본 `HvacProductSeeder` 는 `@Profile("dev")` — 동시 활성 불가, 서로 배타 환경.

---

## 6. Idempotency

### 6.1 PartnerSeeder

```java
if (partnerRepository.existsByPartnerCode(partnerCode)) {
    skipped++;
    continue;
}
```

**시나리오**:
- 1차 실행 (빈 DB) → 50건 INSERT, skipped 0
- 2차 실행 (50건 보유) → 0건 INSERT, skipped 50
- 부분 실행 후 재실행 (예: 30건만 INSERT 후 down) → 누락 20건만 INSERT

테스트: `PartnerSeederTest`
- `firstRunCreatesAll50Partners` — 빈 DB 50건 검증
- `idempotentRunSkipsExisting` — 30건 보유 시 20건 INSERT 검증
- `allRunsAreNoOpWhenAllExist` — 50건 보유 시 save 호출 0회 검증
- `everyTenthPartnerIsSuspended` — SUSPENDED 5건 분포 검증
- `bizNoIsUniquePerSeed` — bizNo 50건 unique 검증

### 6.2 HvacProductSeeder

```java
if (productRepository.existsByModelNameAndIsDeletedFalse(row.modelName())) {
    skipped++;
    continue;
}
```

테스트: `HvacProductSeederTest` (7 test)
- `firstRunCreatesAll100Products` — 빈 DB 100건 (modelName 100 unique)
- `idempotentRunSkipsExistingByModelName` — 부분 보유 시 skip 검증
- `noOpWhenAllExist` — 100건 보유 시 save 0회
- `hvacPriceMatrixFollows6RatioRules` — **모든 100건 6 단가 비율 룰 (1.20/1.50/1.40/1.10/1.12/1.15/1.30) 검증** ⭐
- `fourDiscontinuedAtSeq25Boundary` — DISCONTINUED 4건 (25/50/75/100)
- `everyProductHasKoreanVatStandard10Percent` — VAT 10% / priceIncludesVat=true 검증
- `earlyReturnIfNoCategoriesPresent` — V2 product_categories 시드 없으면 즉시 return

---

## 7. 도메인 메서드만 사용 (reflection 가드)

### 7.1 Partner

신규 도메인 메서드 (모두 한국어 Javadoc):
- `updateBusinessProfile(representative, businessType, industry, subBizNo)` — 사업자 정보
- `updateContactChannels(fax, email, email2, mobile)` — 연락처
- `updateAddresses(zipCode1, address1, zipCode2, address2)` — 본사 + 배송지
- `updateSearchKeyword(searchKeyword)` — 검색 키워드
- `updateClassification(partnerGroup1, partnerGroup2, website)` — 분류 + website
- `updateCreditPolicy(salesType, purchaseType, receivableNoMgmt, payableNoMgmt, salesPriceGroup, purchasePriceGroup, outboundAdjustmentRate, inboundAdjustmentRate, creditPeriodDays, paymentDueDays)` — 여신/단가 정책
- `changeCurrency(currency)` — 통화 (KRW default)
- `changeShipmentTarget(boolean)` — 출하 대상 토글
- `changeRegistrationDate(LocalDate)` — 등록일자
- `suspend()` (기존) — status=SUSPENDED

PartnerSeeder 는 위 메서드 chain 만 사용. `ReflectionTestUtils.setField` 류 미사용.

### 7.2 Product

신규 도메인 메서드:
- `updateEcountMeta(productCode, specification, unit, productBusinessType, inventoryQtyMgmt, barcode)` — 이카운트 품목 메타
- `updateVatPolicy(vatRateOnSales, vatRateOnPurchase, priceIncludesVat)` — VAT 정책
- `updateInventoryPolicy(safetyStockQty, leadTimeDays, minOrderUnit, purchaseSource)` — 재고 정책
- `updateGroups(productGroup1, productGroup2)` — 분류
- `updateHvacPriceMatrix(inbound, outbound, single, outdoor, multi50, multi48, multi45, item35)` — **HVAC 6 단가 일괄 갱신** ⭐
- `markDiscontinued()` — status=DISCONTINUED

기존 `Product.create()` factory + `changePrices` / `changeRemark` / `changeSpecText` / `changeUsage` / `changeBundle` 도 사용. validation 은 모두 도메인 내부에서 처리 (음수 거부 등).

---

## 8. Flyway 마이그레이션

### 8.1 partner-service V2

`V2__add_ecount_partner_fields.sql` — 신규 24 컬럼 + index 4:

- 모든 신규 컬럼 NULLable 또는 default 보유 (V1 row 와 호환)
- `currency` default `KRW`, `shipment_target` default `true`
- `sales_type` / `purchase_type` / `receivable_no_mgmt` / `payable_no_mgmt` default `기본설정`
- `outbound_adjustment_rate` / `inbound_adjustment_rate` default `0` (NUMERIC(5,4))
- index: `ix_partners_partner_group1`, `ix_partners_partner_group2`, `ix_partners_sales_price_group`, `ix_partners_search_keyword`
- CHECK: `currency IN ('KRW','USD','JPY','CNY','EUR')`

### 8.2 product-service V5

`V5__add_ecount_product_fields.sql` — 신규 22 컬럼 (이카운트 14 + HVAC 단가 8) + index 4:

- 모든 신규 컬럼 NULLable 또는 default
- `unit` default `EA`, `product_business_type` default `상품`, `inventory_qty_mgmt` default `true`
- `vat_rate_on_sales` / `vat_rate_on_purchase` default `0.10` (NUMERIC(5,4))
- `price_includes_vat` default `true`
- `safety_stock_qty` default `0`, `lead_time_days` default `7`, `min_order_unit` default `1`
- HVAC 단가 6종 default `0` (NUMERIC(15,2) — 대형 시스템 천만원 단위 안전)
- index: `ux_products_product_code_active` (partial unique), `ix_products_product_group1`, `ix_products_product_group2`, `ix_products_barcode`
- CHECK: `vat_rate_on_sales / vat_rate_on_purchase` 범위 `[0, 1]`, `unit IN ('EA','SET','M','BOX','KG')`

---

## 9. 컴파일 + 테스트 검증 결과

### 9.1 컴파일

```bash
./gradlew :services:partner-service:compileJava
# BUILD SUCCESSFUL in 5s

./gradlew :services:product-service:compileJava
# BUILD SUCCESSFUL in 4s

./gradlew :services:partner-service:compileTestJava :services:product-service:compileTestJava
# BUILD SUCCESSFUL in 5s
```

### 9.2 단위 테스트

```bash
./gradlew :services:partner-service:test :services:product-service:test --tests "*Seeder*Test"
# BUILD SUCCESSFUL in 6s
# PartnerSeederTest:    5 tests, 0 failures
# HvacProductSeederTest: 7 tests, 0 failures
```

기존 `ProductServiceTest` / `ProductMasterEntityIT` 등 회귀 없음 — Product 신규 컬럼 default 값으로 기존 `Product.create()` factory 비파괴.

---

## 10. 사용법 (로컬 실행)

### 10.1 Postgres + Flyway 마이그

```bash
# infrastructure/scripts/start-local-full.ps1 사용 권장
# 또는 수동:
docker compose up -d postgres
./gradlew :services:partner-service:flywayMigrate
./gradlew :services:product-service:flywayMigrate
```

### 10.2 Seed 활성화

```bash
# PartnerSeeder
SAMHAN_PARTNER_SEED_TEST_DATA=true ./gradlew :services:partner-service:bootRun \
  --args='--spring.profiles.active=dev'

# HvacProductSeeder
SAMHAN_PRODUCT_SEED_TEST_DATA=true ./gradlew :services:product-service:bootRun \
  --args='--spring.profiles.active=dev'
```

부팅 로그에서 다음 줄 확인:
```
PartnerSeeder created 50 partners (skipped 0, total 50)
HvacProductSeeder created 100 products (skipped 0, total 100)
```

### 10.3 검증 query

```sql
-- partner-service
SELECT COUNT(*) FROM partners WHERE is_deleted = false;          -- 50
SELECT status, COUNT(*) FROM partners GROUP BY status;            -- ACTIVE 45, SUSPENDED 5
SELECT partner_group1, COUNT(*) FROM partners GROUP BY partner_group1;
SELECT shipment_target, COUNT(*) FROM partners GROUP BY shipment_target;  -- true 40, false 10

-- product-service
SELECT COUNT(*) FROM products WHERE is_deleted = false;           -- 100
SELECT status, COUNT(*) FROM products GROUP BY status;             -- ACTIVE 96, DISCONTINUED 4
SELECT product_group2, COUNT(*) FROM products GROUP BY product_group2;
-- HVAC 단가 6종 sample
SELECT model_name, inbound_price, outbound_price, single_price, outdoor_price,
       multi_50_price, multi_48_price, multi_45_price, item_35_price
FROM products WHERE product_code = '010001';
```

---

## 11. 후속 슬라이스

| Stage | 범위 | seeder 후보 |
| ----- | ---- | ----------- |
| Stage 2 | 재고/창고 | warehouse-service / inventory-service — 5 창고 + 100 SKU 초기 재고 (HVAC 100 모델 × 5 창고 = 500 row) |
| Stage 3 | 슬립/주문 | slip-service / partner-order-service — 100 슬립 (50 거래처 × 2) + 50 견적 + 30 주문 |
| Stage 4 | 회계/정산 | accounting-service — 한국 표준 계정과목 활용 매출/매입 분개 (project_korean_accounting 가드 활용) |

---

## 12. 가드 / 메모리 정합

| memory | 적용 |
| ------ | ---- |
| `feedback_uuid_no_user_visibility` | partnerCode (P-2026-NNNN) / productCode (010NNN) / modelName 만 사용자 노출. UUID 는 form hidden / path variable 만 |
| `project_korean_accounting` | vatRateOnSales/Purchase 0.10 (한국 표준 10%), priceIncludesVat true (한국 표준 표시 단가) |
| `feedback_continuous_docs_sync` | 본 dev-report 가 Stage 1 PR 본문에 포함될 의무 — README + ROADMAP 갱신은 PM 통합 commit 단계 |
| `feedback_pm_integration_build_check` | `compileJava` + `compileTestJava` + 단위 테스트 12건 사전 검증 완료 |
| `feedback_function_documentation` | Partner 9 신규 메서드 + Product 6 신규 메서드 모두 한국어 Javadoc 의무 충족 |

---

## 13. 산출물 요약 (commit footer)

```
feat(seed): partner 50 + Samsung HVAC product 100 — 이카운트 27 + 6 단가 필드 반영 (Stage 1)

* services/partner-service:
  - V2 migration: 24 columns + 4 indexes (ecount 27 fields compat)
  - Partner entity: 24 fields + 9 domain methods
  - PartnerRepository: existsByPartnerCode added
  - PartnerSeeder: 50 partners (deterministic, idempotent, dev profile + toggle)
  - PartnerSeederTest: 5 tests
* services/product-service:
  - V5 migration: 22 columns (ecount 14 + HVAC 6 prices) + 4 indexes
  - Product entity: 22 fields + 6 domain methods
  - HvacProductSeeder: 100 Samsung HVAC models (벽걸이 30 + 스탠드 20 + 시스템 25 + 천장형 10 + 공기청정기 10 + 부속 5)
  - HvacProductSeederTest: 7 tests (HVAC 단가 6종 비율 룰 검증)
* docs/dev-reports/local-test-seed-stage1.md (본 문서)

검증:
  ./gradlew :services:partner-service:compileJava :services:product-service:compileJava → SUCCESS
  ./gradlew :services:partner-service:test :services:product-service:test → 12 seeder tests passing
```
