# #809 (거래처+품목) 최근단가 자동채움 — R2 라이브 QA 리포트 (R1 fix 후 재검증)

> Docker 실서버 + 실 GUI + 실 Postgres 실증. 합성/목업/fixture 없음.
> 스샷 13장 전부 실 캡처(`clients/desktop` Playwright, mock OFF, 실 게이트웨이 :8080).
> **결론: R1 라운드 미해소 4건(견적 DOA 포함) 전부 해소 실증. 신규 결함 0.**

## 1. 환경 (실측)

| 항목 | 값 |
|---|---|
| 브랜치 / HEAD | `feat/809-partner-product-price-memory` / **`ca3eee8193e8bc95e1592034f70d6d8954d7ed74`** |
| 직전 라운드 HEAD | `8c95408bf` (R1 라이브 QA — 견적 DOA) |
| jar 재빌드 | `:services:slip-service:bootJar :services:partner-service:bootJar` → BUILD SUCCESSFUL, jar mtime **13:13** |
| 이미지 재빌드 | `docker compose … up -d --build slip-service partner-service` — 이미지 Created **2026-07-15T04:13:5xZ (=13:13 KST)** |
| stale 이미지 아님 확인 | 이미지 Created(13:13) > jar mtime(13:13) · 컨테이너 StartedAt **04:14:34Z (=13:14 KST)** — `--build` 실효 확인 |
| 컨테이너 | slip-service / partner-service 둘 다 **healthy** |
| 마이그레이션 | `Successfully validated 58 migrations` · `Current version of schema "public": 58` · `Schema is up to date` — **에러 0** (V58 = R1 라운드에 이미 적용됨) |
| GUI | vite web 렌더러 **`:5211`** (`--strictPort`, **신규 포트** — 고아 vite 구코드 서빙 false-RED 회피. 사전 netstat 로 5199/5173/5174 미점유 확인) |
| mock | **OFF** (`VITE_MOCK_MODE` 미설정 = opt-in 게이트 비활성), `VITE_API_BASE_URL=http://localhost:8080` |
| 계정 | **`dev_manager`** = `a0000000-0000-0000-0000-000000000003` (auth_db 실조회) |
| 스펙 | `clients/desktop/playwright/809-price-memory-real-qa/price-memory-r2-live-real-qa.spec.ts` |
| 결과 | **7 passed (1.3m)** — 0 failed |

> ⚠️ 계정 정직 기록(R1 INFO-1 재확인): 과업 브리프의 `dev_master` 는 auth_db "마스터" 권한그룹에
> `sales.slip.create` 행 자체가 없어 **전표 생성이 403** 인 계정이다(#809 회귀 아님).
> 본 QA 는 `sales.slip.create` + `purchases.slip.edit` 전권인 "매니저" 그룹 `dev_manager` 로 수행.

### 실 시드 (실 DB 조회로 확정)

| 구분 | 값 |
|---|---|
| 거래처A | 한울냉열시스템 `44f0cfc1-4a5f-4206-85cd-04ad5fa70922` |
| 거래처B | 국민건강보험공단**종로지사** `dba5051b-6f22-4ae0-8277-2a162e0f4367` |
| 품목X | `AJ030MXHNBC1` 실외기_3HP 단배관 `a046f235-…-e2d533e1ff08` — 정가 **1,470,700** |
| 품목Y | `AJ040MXHNBC1` 실외기_4HP 단배관 `3612c28e-…-26367a8d3e3c` — 정가 1,731,400 |
| 세트 | `AC023CS1DBC1SY` 무풍 1way 냉방전용 `b63f676c-…-68d3d2c8d293` — 정가 **1,204,500**, `product_type=BUNDLE`, `bundle_mode=EXPAND` |
| 세트 기본 구성품 4종 | INDOOR `21dec2cc…` / OUTDOOR `8015b3da…` / REMOTE `8f0becf3…` / PANEL `3325f787…` (전부 등록품목으로 resolve 확인 — expand 가능) |

> 거래처B 를 **종로지사**로 바꾼 이유: 검색어 `국민건강` 이 `국민건강보험공단`/`국민건강보험공단종로지사`
> **2건에 매칭**되어 `.first()` 선택 시 어느 거래처가 잡혔는지 불확정 → UUID 대조가 무의미해진다.
> 유일매치 검색어(`국민건강보험공단종로`)로 교체해 DB 대조의 확정성을 확보했다.

## 2. 시나리오별 판정

| # | 시나리오 | 판정 | 실측 |
|---|---|---|---|
| **A** | **🔴 견적 자동채움 (R1 B-3 해소)** | ✅ **PASS** | 아래 §3 상세 — **R1 DOA 완전 해소** |
| **B** | **🔴 BUNDLE 세트 (결정 ②)** | ✅ **PASS** | parent `1100000.00` / **`source=BUNDLE_SET`**, 구성품 기억행 **0건**, 세트 재선택 자동채움 1,100,000 |
| **C** | **🔴 거래처 변경 재조회 (R1 B-2)** | ✅ **PASS** | A(888,000) → 거래처 B 변경 → **555,000 재조회 재적용**(888,000 잔류 없음), 사용자 입력 라인 **111,111 보존** |
| **D** | **🟠 '최근가' 마커 (H-6)** | ✅ **PASS** | hit 라인 마커 표시 + tooltip `최근 단가 · 2026-07-15 저장` / **miss·USER 라인 마커 0건** |
| **E** | **🟠 수정 경로 기억 (결정 ④)** | ✅ **PASS** | 상세 `단가(VAT제외)` 500,000 저장 → DB **550,000.00** (×1.1 정규화 정확) → 새 전표 **550,000 자동채움** |
| **F** | 전표 회귀 없음 | ✅ **PASS** | hit 자동채움 · 거래처 격리 · **override 보존(123,456)** · **upsert 단일행(1건)** 전부 유지 |

### 실측 수치 흐름 (핵심 요구)

| 구간 | 값 |
|---|---|
| 정가 (catalog `sellingPrice`) | **1,470,700** |
| 최초 조회 | `GET /slips/price-memory` → **204 (miss)** → 정가 fallback, 마커 없음 |
| 입력 P (화면 "단가(VAT포함)") | **888,000** |
| DB `unit_price` | **888000.00** (`LINE_SAVE`) — 차이 **0 (무손실)** |
| 재조회 자동채움 | **888,000** (정가 아님) + `최근가` 마커 |
| 견적 화면 재조회 | **888,000** (정가 아님) — 합계 888,000 / 공급 807,273 · VAT 80,727 정합 |
| 수정경로 입력 Q (VAT**제외**) | **500,000** |
| 수정 후 DB `unit_price` | **550000.00** = 500,000 × 1.1 (정확) |
| 수정 후 재조회 자동채움 | **550,000** (VAT 포함 단가) |

## 3. A. 견적 자동채움 — R1 DOA 해소 실증 (최우선)

**R1 관측**: 모델명 onBlur → `price-memory?partnerId=…` **productId 누락 → 400** → catch 삼킴 →
정가 1,470,700 fallback + **품목명 칸 공백**. 저장도 `POST /estimates` 요청조차 안 나감(BLOCKING-1).

**R2 실측 (`04-KEY-…png`)** — 3개 확인점 전부 충족:

| 확인점 | R1 | R2 실측 |
|---|---|---|
| ⓐ 품목명 칸 채움 | ❌ 공백 | ✅ **`실외기_3HP 단배관`** |
| ⓑ 단가 = 기억단가 P | ❌ 정가 1,470,700 | ✅ **888,000** |
| ⓒ price-memory 요청 | ❌ productId 누락 → **400** | ✅ `?partnerId=44f0cfc1…&**productId=a046f235…**` → **200** (400 **0건**) |
| ⓓ 임시저장 | ❌ 요청 자체 미발생 | ✅ **`POST http://localhost:8080/slips/estimates`** 발생 → DB 견적라인 생성 |

**근본 원인 해소 확인** — `lookupProductByModelName` 이 BE wire shape 를 명시 매핑:

```
BE 실응답: {"id":"a046f235-…","name":"실외기_3HP 단배관","modelName":"AJ030MXHNBC1","sellingPrice":1470700.0,…}
FE 매핑  : id→productId, name→productName  (+ 계약 위반 시 throw)
```

(직접 curl 로 BE 응답 실측 대조 완료 — `id`/`name` 확인.)

**DB 견적라인 실측**:

```
 derived_excl_vat | authoritative_incl_vat | quantity | supply_amount | vat_amount | line_total
        807272.50 |              888000.00 |        2 |    1614545.00 |  161455.00 | 1776000.00
```

- 권위 필드 `unit_price_with_vat` = **888000.00** = 화면 입력값 **정확 일치(무손실)**.
- `line_total` 1,776,000 = 2 × 888,000 정합.

## 4. B. BUNDLE 세트 — 개발책임자 결정 ② 실증

세트 `AC023CS1DBC1SY` (정가 1,204,500) 를 **1,100,000** 으로 전표 저장 후 DB 실측:

| 요구 | 실측 | 판정 |
|---|---|---|
| 세트 parent 행이 `source='BUNDLE_SET'` | `1100000.00\|BUNDLE_SET` | ✅ |
| **구성품 productId 로는 기억행 생성 금지**(납품가 각인 방지) | 구성품 4종 기억행 **`0`건** | ✅ |
| 세트 재선택 시 자동채움 | **1,100,000** + `최근가` 마커 (`07-KEY-…png`) | ✅ |

> dev 시드에 BUNDLE 품목 **343건** 실재 → **미실증 아님, 라이브 실증 완료.**
> 구성품 미기억은 코드상으로도 정합: `SlipService.addSlipLinesExpanded` 의 구성품 루프에서
> `rememberPrice(… el.productId() …)` 호출이 R1 fix 로 제거됐고, parent 만 `SOURCE_BUNDLE_SET` 로 수집된다.

## 5. DB 실증 (stub-success 판별)

라운드 종료 시점 `partner_product_price_memory` 전수 (테스트 07 override 저장 반영 후):

```
              partner_id              |              product_id              | unit_price |   source   |              created_by              |        modified_at         | is_deleted
--------------------------------------+--------------------------------------+------------+------------+--------------------------------------+----------------------------+------------
 44f0cfc1-…-04ad5fa70922 (거래처A)    | a046f235-…-e2d533e1ff08 (품목X)      |  123456.00 | LINE_SAVE  | a0000000-0000-0000-0000-000000000003 | 2026-07-15 13:32:17.175986 | f
 dba5051b-…-2a162e0f4367 (거래처B)    | a046f235-…-e2d533e1ff08 (품목X)      |  555000.00 | LINE_SAVE  | a0000000-0000-0000-0000-000000000003 |                            | f
 b63f676c… 세트 parent (거래처A)      | b63f676c-…-68d3d2c8d293 (세트)       | 1100000.00 | BUNDLE_SET | a0000000-0000-0000-0000-000000000003 |                            | f
```

- **행 실제 생성 → WRITE 훅 살아있음.** #816 ③-A 형 stub-success **아님**.
- `created_by` = `a0000000-…-0003` = **dev_manager 실 계정 UUID** (auth_db 대조 일치).
- 구성품 4종 productId 행 **부재**(전수 3행뿐) → 납품가 각인 방지 실증.
- (A,X) 는 888,000 → 550,000 → 123,456 으로 **3회 갱신되며 행 수 1건 유지** → `ON CONFLICT DO UPDATE` 실동작.
- **afterCommit 배치 flush 정상**: slip-service 로그에 `price memory … upsert failed` / 배치 실패 경고 **0건**.

## 6. R1 대비 해소 / 미해소

| R1 지적 | 상태 | 근거 |
|---|---|---|
| **B-3 / HIGH-1 — 견적 자동채움 DOA** (productId 누락 400 → 정가 fallback + 품목명 공백) | ✅ **해소** | §3 — productId 실려 200, 품목명 채움, 888,000 자동채움 (`04-KEY`) |
| **BLOCKING-1 — 견적 저장 불가** (`POST /estimates` 미발생) | ✅ **해소** | `POST /slips/estimates` 발생 + DB 견적라인 생성 (`05`) |
| **B-1 — BUNDLE 구성품 납품가 각인** (결정 ②) | ✅ **해소** | §4 — parent 만 `BUNDLE_SET`, 구성품 0건 (`06`,`07-KEY`) |
| **B-2 — 거래처 변경 시 이전 거래처 단가 잔류** | ✅ **해소** | §2 C — 888,000 → 555,000 재조회, USER 라인 보존 (`09`,`10-KEY`) |
| **H-2 — 수정 경로 기억 미배선** (결정 ④) | ✅ **해소** | §2 E — 500,000(VAT제외) → DB 550,000.00 → 자동채움 550,000 (`11`,`12-KEY`) |
| **H-6 — '최근가' 마커 부재** | ✅ **해소** | §2 D — hit 표시 / miss·USER 미표시 + tooltip 저장일 (`01`,`03-KEY`,`10-KEY`,`13`) |
| INFO-1 — `dev_master` 전표 권한 없음 | ↔ **유지**(#809 무관) | auth_db 상 `sales.slip.create` 행 부재 — 설계대로 |
| R1 PASS 항목(전표 hit·격리·override·upsert) | ✅ **회귀 없음** | §2 F |

**미해소 0건. 신규 결함 0건.**

## 7. 발견 사항 (참고 — 전부 #809 귀책 아님)

### ℹ️ INFO-1 — 견적 `estimate_lines.unit_price` = 807272.50 은 결함 아님

리뷰어가 오판하기 쉬운 지점이라 선제 기록한다. 888,000 / 1.1 = 807,272.727 인데 DB 는 **807272.50** 이다.

- `EstimateLine.createFromVatInclusive` 규약: **합계(VAT포함)=수량×unitPriceWithVat, 공급가액=round(합계/1.1), 부가세=차액(모두 원 단위)**.
- 실계산: 합계 2×888,000=1,776,000 → 공급가액 round(1,776,000/1.1)=**1,614,545**(원 단위) → `unit_price`=1,614,545/2=**807,272.50**.
- 즉 `unit_price` 는 코드 주석이 명시한 **"공급단가, 비권위(non-authoritative)"** 파생값이고,
  권위 필드 `unit_price_with_vat` = **888000.00** 은 화면 입력과 **정확 일치**한다.
- `SlipLine` 과 동일 규칙(1:1 변환 보장). **#809 가 도입한 드리프트 아님 — 정상.**

### ℹ️ INFO-2 — `AsyncAutocomplete` 로딩행이 `role="option"` (선재 a11y 스멜, #809 무관)

`AsyncAutocomplete.tsx:402-404` 의 "검색 중…" `statusRow` 가 `role="option"` `aria-selected={false}` 로
렌더된다(id 없음). 선택 불가한 상태 표시가 listbox 의 option 으로 노출돼 있다.

- **영향(실측)**: `getByRole('option').first()` 로 후보를 기다리면 **로딩행에 먼저 걸려** 결과 도착 전에
  ArrowDown+Enter 가 발사되고, 선택이 무효화된 채 드롭다운이 잔류 → **자동화 false-RED**. 본 라운드에서 실제로 밟았다.
- **회피**: 실 후보만 `li[id^="ds-aac-list-"]` 로 좁혀 대기(R2 스펙에 주석과 함께 반영).
- **귀책**: **선재 · #809 범위 밖**(R1 스펙도 동일 취약점을 안고 있었고 타이밍 운으로 통과). 스크린리더가
  "검색 중…" 을 선택지로 읽는 문제라 별도 슬라이스에서 `role="status"`/`aria-live` 로 교정 권고.

### ⚠️ MEDIUM-1 — R1 결함재현 스펙이 stale 됐다 (정리 필요)

`playwright/809-price-memory-real-qa/price-memory-live-real-qa.spec.ts:349-352` 의 test 08 은
**결함이 남아있는 한 통과하도록** 작성된 의도된 red 스펙이다:

```ts
expect(statuses.some((s) => s.startsWith('400')),
  'productId 누락 400 이 관측되지 않음(결함 양상 변화 — 재조사 필요)').toBeTruthy()
```

R2 에서 **400 은 0건 · 200 만 관측**되므로 이 단언은 이제 **반드시 실패**한다(결함이 고쳐졌기 때문).
즉 R1 스펙은 현재 HEAD 기준 **false-RED 자산**이다.

- **CI 영향 없음**(확인): `clients/desktop/playwright.config.ts:17-22` `testIgnore` 에
  `'**/*-real-qa.spec.ts'`, `'**/*-real-qa/**'` 가 등재돼 mock 게이트에서 제외된다.
- **권고**: R1 스펙 08 을 삭제하거나 R2 스펙과 동일한 기대(200 + 기억단가)로 갱신해 리포 정합을 맞출 것.
- **정직 기록**: 본 판정은 **정적 대조 + R2 실측(400 0건)으로부터의 연역**이다. R1 스펙을 직접 실행하지는
  않았다 — 해당 스펙 `beforeAll` 이 `DELETE FROM partner_product_price_memory` (조건 없는 전체 삭제)를
  수행해 무관 데이터까지 파괴하므로 실행하지 않았다. (R2 스펙은 테스트 대상 7개 (거래처,품목) 쌍만
  좁혀 정리한다.)

## 8. 스샷 (전부 실 캡처 · 1440×1000 PNG)

| 파일 | 증명 내용 |
|---|---|
| `01-slip-miss-list-price-1470700-no-recent-marker.png` | miss(204) → 정가 1,470,700 fallback + **최근가 마커 없음**(D-miss) |
| `02-slip-manual-price-888000-entered.png` | 단가 P=888,000 직접 입력 |
| `03-KEY-slip-autofill-888000-with-recent-marker.png` | **[핵심]** 새 전표 → **888,000 자동채움 + 최근가 마커**(D-hit) |
| `04-KEY-estimate-autofill-888000-productname-filled-recent-marker.png` | **[최우선 증거·A]** 견적: **품목명 채움 + 888,000 자동채움 + 최근가 마커** (R1 DOA 해소) |
| `05-estimate-saved-after-draft-save.png` | 견적 임시저장 성공(`POST /slips/estimates` 발생 · DB 라인 생성) |
| `06-bundle-set-price-1100000-entered.png` | 세트 단가 1,100,000 입력 |
| `07-KEY-bundle-set-refill-1100000-bundle-set-source.png` | **[핵심·B]** 세트 재선택 → **1,100,000 자동채움**(`BUNDLE_SET` 출처) |
| `08-partnerB-isolated-list-price-1470700.png` | 거래처B + 동일 품목X → 정가(거래처별 격리, 888,000 누출 없음) |
| `09-before-partner-change-A-888000-user-111111.png` | 거래처 변경 **전**: 라인1 자동채움 888,000 / 라인2 사용자입력 111,111 |
| `10-KEY-partner-changed-to-B-refetched-555000-user-line-preserved.png` | **[핵심·C]** 거래처 B 변경 후: 라인1 **555,000 재조회**(최근가 마커) / 라인2 **111,111 보존**(마커 없음) |
| `11-slip-detail-edit-unit-price-500000-vat-excluded.png` | 상세화면 **단가(VAT제외)** 500,000 수정 |
| `12-KEY-new-slip-autofill-550000-after-edit-path.png` | **[핵심·E]** 수정경로 반영 → 새 전표 **550,000 자동채움**(×1.1 정규화) |
| `13-override-preserved-123456-no-marker.png` | override 보존 123,456(기억단가 550,000·정가 1,470,700 모두 덮어쓰지 않음) + 마커 없음 |

## 9. 결론

- **R1 미해소 6건(견적 DOA·견적 저장불가·BUNDLE·거래처변경·수정경로·마커) 전부 라이브 해소 실증.**
- **BUNDLE 은 dev 시드에 실물 343건 존재 → 미실증 없이 라이브 실증 완료** (구성품 각인 0건).
- **WRITE 훅 정상**(afterCommit 배치 flush, fail-soft 경고 0건) — stub-success 아님.
- **VAT basis 무손실**: 전표 888,000 ↔ DB 888000.00 / 견적 권위필드 888000.00 / 수정경로 500,000×1.1=550000.00.
- **신규 결함 0 · 전표 회귀 0.** 잔여 지적은 전부 **선재·#809 범위 밖**(INFO-2 a11y) 또는 **리포 정합 정리**(MEDIUM-1).

**재현 명령**

```powershell
# 1) 재배포 (jar → 이미지 --build 필수)
.\gradlew.bat :services:slip-service:bootJar :services:partner-service:bootJar
docker compose -f infrastructure/docker-compose.yml -f infrastructure/docker-compose.local-all.yml `
  up -d --build slip-service partner-service

# 2) 렌더러 (mock OFF · 신규 포트)
cd C:\dev\Samhan-Public\clients\desktop
npx vite --config vite.web.config.ts --port 5211 --strictPort   # 별도 창

# 3) 실행
.\node_modules\.bin\playwright test --config=playwright.real-qa.config.ts `
  --reporter=line --timeout=180000 playwright/809-price-memory-real-qa/price-memory-r2-live-real-qa.spec.ts
```

기대 결과: **7 passed** (R2 스펙 기준).
