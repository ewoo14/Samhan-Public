# #809 (거래처+품목) 최근단가 자동채움 — 라이브 QA 리포트

> Docker 실서버 + 실 GUI + 실 Postgres 실증. 합성/목업/fixture 없음.
> 스샷은 전부 실 캡처(`clients/desktop` Playwright, mock OFF, 실 게이트웨이 :8080).
>
> 📌 본 문서는 **최초 라운드(R1)의 실행 기록**이다. 라운드별 기록은 `r2/`·`r4/`·`r5/`·`r6/`·`r8/` 등
> 하위 디렉토리에 보존한다. **아래 §0 은 라운드 무관 상시 체크리스트**다.

## 0. 라운드 사전점검 — **매 라이브 QA 라운드 필수** (상시)

> 🔴 **이 절이 존재하는 이유** (#809 R8-DEVOPS-1): `SlipPriceMemoryUpsertFailure` 경보가 **13일간
> 런타임에 존재하지 않았고 R1~R7 일곱 라운드가 전부 놓쳤다.** rule 파일은 git 에 멀쩡히 있고 promtool 도
> 통과해서 **정적 리뷰로는 원리상 잡히지 않는다.** 라이브 QA 만이 잡을 수 있는 계열이며, 이는
> R8-QA-1(정적 4차원이 놓치고 라이브만 포착)과 같은 교훈이다.

| # | 단언 | 명령 | 기대 |
|---|---|---|---|
| 0-a | 🔴 **가격기억 경보가 런타임에 실재** | `.\infrastructure\scripts\verify-prometheus-rules.ps1` | **exit 0** · git 의 모든 rule 이 로드 + `health=ok` |
| 0-b | 경보 selector 가 실 job 에 결합 | `curl -s 'http://localhost:9090/api/v1/query?query=slip_price_memory_upsert_failed_total'` | `job="slip-service"` label 포함 결과 반환(빈 결과 = target down/label 불일치) |
| 0-c | slip-service scrape target 생존 | `curl -s 'http://localhost:9090/api/v1/targets?state=active'` | `slip-service` = `up` |

🚫 **`curl :9090/api/v1/rules` 가 `{"groups":[]}` 인 채로 라운드를 진행하지 말 것** — 이 상태에서는
"upsert 실패 0건" 이 **경보 부재를 뜻할 뿐 정상을 뜻하지 않는다.** 복구는 `docker restart` 가 아니라
`up -d --force-recreate --no-deps prometheus` 다(디렉토리 바인드는 컨테이너 생성 시점에만 붙는다).
배경: `infrastructure/README.md` §Alerting rules · `docs/runbooks/slip-price-memory-upsert-failure.md` §0차 확인.

⚠️ **라이브 수치는 휘발성이다** — DB 재시드/QA 쓰기로 바뀐다(R7 의 `2,028`·`1,926` 은 R8 시점에 이미
stale 이었다). **고정 수치를 단언하는 스펙을 만들지 말 것.**

## 1. 환경 (실측)

| 항목 | 값 |
|---|---|
| 브랜치 / HEAD | `feat/809-partner-product-price-memory` / `6a6eb44c719c3691a0bd1f06ff69f5cc1e901208` |
| 재배포 | `docker compose … up -d --build slip-service partner-service` (jar 재빌드 + **이미지 재빌드**) |
| 재배포 전 상태 | 컨테이너 3시간 stale — `flyway_schema_history` 최신 = **V55**, `partner_product_price_memory` 테이블 **부재** |
| 재배포 후 마이그 | `Successfully applied 3 migrations … now at version v58` (V56·V57·**V58**), 2026-07-15 11:33:11, 에러 0 |
| V58 실적용 확인 | 테이블 생성 + `ux_partner_product_price_memory_pair UNIQUE (partner_id, product_id)` 존재 |
| 컨테이너 | slip-service / partner-service `healthy` |
| GUI | vite web 렌더러 `:5199` (`--strictPort`, 신규 포트 — 고아 vite false-RED 회피), **mock OFF**, `VITE_API_BASE_URL=http://localhost:8080`, BrowserRouter 실경로 |
| 계정 | **`dev_manager`** (dev_master 아님 — §5 INFO-1) |
| 실 시드 | 거래처A `한울냉열시스템`(44f0cfc1…0922) / 거래처B `국민건강보험공단`(3b69ae15…45db) / 품목X `AJ030MXHNBC1 실외기_3HP 단배관`(a046f235…1f08) |
| 스펙 | `clients/desktop/playwright/809-price-memory-real-qa/price-memory-live-real-qa.spec.ts` |

실측 값: **정가 1,470,700 → 입력 P 999,000 → 재조회 999,000** (일치).

## 2. 시나리오별 판정

| # | 시나리오 | 판정 | 실측 |
|---|---|---|---|
| 1 | 거래처A + 품목X 선택 → 정가 채움 | ✅ PASS | 단가 = **1,470,700** (= catalog `sellingPrice`). `GET /slips/price-memory` 1회 호출 → 204(miss) → 정가 fallback |
| 2 | 단가 P 직접 입력(정가와 다른 값) | ✅ PASS | **999,000** 입력, 수량 2 |
| 3 | 저장 | ✅ PASS | 저장 성공 → `/sales` 목록 리다이렉트 |
| 4 | **[핵심] 새 전표 · 같은 거래처A+품목X → P 자동채움** | ✅ **PASS** | 단가 = **999,000** (정가 1,470,700 아님). 합계 999,000 / 공급 908,182 · VAT 90,818 |
| 5 | 다른 거래처B + 같은 품목X → 정가 (거래처별 격리) | ✅ PASS | 단가 = **1,470,700** (999,000 누출 없음) |
| 6 | override 보존 (단가 선입력 라인) | ✅ PASS | 선입력 **123,456** 유지 — 기억단가/정가 모두 덮어쓰지 않음 |
| 7 | upsert 재저장 (ON CONFLICT DO UPDATE) | ✅ PASS | 777,000 재저장 → 행 **1건 유지**(중복행 0), `unit_price=777000.00`, `modified_at`/`modified_by` 갱신, 재조회 자동채움 777,000 |
| 8 | **견적 화면 자동채움 (모델명 onBlur → lookup)** | ❌ **FAIL** | 기억단가 999,000 이 아니라 **정가 1,470,700** fallback — §5 HIGH-1 |

**전표(출고) 경로 = 기능 동작 실증 완료. 견적 경로 = 미동작(무증상 실패).**

## 3. DB 실증 (stub-success 판별)

`partner_product_price_memory` — 전표 저장 직후 실 조회:

```
             partner_id              |              product_id              | unit_price |  source   |              created_by              |         created_at         | modified_at | is_deleted
--------------------------------------+--------------------------------------+------------+-----------+--------------------------------------+----------------------------+-------------+------------
 44f0cfc1-4a5f-4206-85cd-04ad5fa70922 | a046f235-6d7d-49a5-b321-e2d533e1ff08 |  999000.00 | LINE_SAVE | a0000000-0000-0000-0000-000000000003 | 2026-07-15 11:45:59.996288 |             | f
```

- **행 생성됨 → WRITE 훅 살아있음.** #816 ③-A 형 stub-success **아님**.
- `partner_id`/`product_id` = 화면에서 고른 거래처A/품목X 와 일치.
- `created_by` = `a0000000-…-0003` = **dev_manager 실 계정 UUID** (auth_db 대조 확인).
- `source = LINE_SAVE`, `is_deleted = f`.
- upsert 후 최종: 동일 pair 행 **1건**, `modified_at=11:55:14`, `modified_by=a0000000-…-0003` → `ON CONFLICT DO UPDATE` 실동작.

**WRITE 훅 활성화 enabler 도 실증** — `slips` 테이블 신규 행:

```
              partner_id              |  partner_name  | business_number | partner_code
--------------------------------------+----------------+-----------------+--------------
 44f0cfc1-4a5f-4206-85cd-04ad5fa70922 | 한울냉열시스템 | 000011111111    | 000011111111
```

기존 null 이던 `slip.partner_id` 가 채워짐(=전표측 upsert 기능화). 커밋 메시지가 명시한 부작용
(dormant 이던 `business_number`/`partner_code` snapshot resolve 활성화)도 **정상 resolve, 에러 0**
— slip-service 로그에 fail-soft 경고(`partner-product price memory upsert failed`) **0건**.

## 4. VAT basis 라운드트립 실증

| 구간 | 값 |
|---|---|
| 화면 입력 단가 P (라벨 "단가(VAT포함)") | **999,000** |
| DB `unit_price` | **999000.00** |
| 차이 | **0 (무손실)** |

FE 가 전표 라인에 `priceVatInclusive: true` 를 전송 → BE `rememberPrice` 가 ×1.1 정규화를 건너뛰고
입력값 그대로 저장 → 자동채움 시 그대로 복원. **화면↔DB basis 일치 확인.**
(재조회 자동채움 999,000 의 화면 VAT 분해 = 공급 908,182 · VAT 90,818 = 합 999,000 정합.)

## 5. 발견 결함

### 🔴 HIGH-1 — 견적 화면 최근단가 자동채움 미동작 (무증상 실패)

개발책임자 결정 ①("전표 **+ 견적**") 중 **견적 절반이 실 GUI 에서 동작하지 않는다.**

**재현 절차**
1. `dev_manager` 로그인 → `/sales/estimates/new` (견적서 작성)
2. 거래처 검색 = `한울냉열` 선택 (거래처명 `한울냉열시스템` 채워짐 확인)
3. 라인 1 모델명 = `AJ030MXHNBC1` 입력 후 blur (다른 영역 클릭)
   - 사전 조건: (거래처A, 품목X) 기억단가 999,000 이 DB 에 존재

**실제 관측**
- `GET /slips/lookup-product?modelName=AJ030MXHNBC1` → **200**
- `GET /slips/price-memory?partnerId=44f0cfc1-4a5f-4206-85cd-04ad5fa70922` → **400**
  ← **`productId` 파라미터가 아예 빠져서 요청됨**
- FE `catch` 가 400 을 삼킴 → 단가 = **1,470,700 (정가 fallback)**
- 라인 1 **품목명 칸도 빈 채로 남음** (화면 안내문 "모델명을 입력하고 다른 영역을 클릭하면 품목명/단가가 자동 입력됩니다" 와 불일치)

**기대 결과**: 단가 = 999,000 (기억단가)

**근본 원인** — FE↔BE 계약 불일치 (검증 없는 캐스팅이라 tsc 통과, 런타임 `undefined`)

| | 필드 |
|---|---|
| BE 실응답 `ProductSummary` (`services/slip-service/.../client/ProductSummary.java`) | `id`, **`name`**, `modelName`, `sellingPrice`, `modelCode`, `productType` |
| FE `ProductLookupResult` (`clients/desktop/src/renderer/api/slip.ts:397`) | **`productId`**, **`productName`**, `modelName`, `sellingPrice`, … |

→ `result.productId` = `undefined` → `getPriceMemory(partnerId, undefined)` → axios 가 파라미터 생략
→ BE `@RequestParam UUID productId`(필수) 위반 → 400.
`modelName`/`sellingPrice` 는 이름이 맞아 채워지므로 **정가는 채워지고 기억단가만 조용히 실패** →
화면상 "동작하는 것처럼" 보이는 무증상 실패다. (`EstimateFormPage.tsx:517` `getPriceMemory(effectivePartnerId, result.productId)`)

**귀책**: 계약 불일치 자체는 **#809 도입 아님(선재)** — `git log -S` 결과 `ProductLookupResult.productId`
는 초기 커밋(`9ecbe444a`/`7cef5fcb8`)부터 존재. 다만 **#809 가 이 필드에 신규 의존**하면서 견적 기능이
DOA 가 됐다. 전표 경로는 `/api/products`(`p.id` 정상 매핑)를 쓰므로 무관 — 그래서 전표만 동작한다.

### 🔴 BLOCKING-1 (선재·#809 범위 밖 원인, 그러나 #809 견적 절반을 불능화)

**데스크톱 견적서 작성 화면은 모델명 lookup 경로로 라인을 저장할 수 없다.**

**재현**: 위 HIGH-1 절차 → 수량 2 입력 → `임시저장` 클릭
**실제 관측**: `POST /estimates` **요청 자체가 나가지 않음**, 화면 최상단 에러
`"라인 1개 이상 (모델명 lookup 성공 + 수량 > 0) 을 입력하세요."`, URL 그대로
**기대**: 견적 임시저장 성공

**원인**: 동일한 계약 불일치 — `line.productId`가 영원히 null → 저장 게이트
(`EstimateFormPage.tsx:608` `lines.filter(l => l.productId && …)`)가 전 라인을 탈락시킴.

**#809 함의**: 견적 **WRITE 훅**(`EstimateService.rememberPrice`)도 **영원히 발화 불가** —
저장 자체가 막히고, 설령 우회 저장돼도 `productId=null` 이라 null 가드에서 skip 된다.
즉 견적은 **읽기(자동채움)·쓰기(기억) 양방향 모두 미동작**.
※ 이 화면의 선재 파손은 주 견적 도구가 별도 `estimate-app` 이라 그동안 드러나지 않았을 가능성이 높다(추정 — 본 QA 범위 밖).

### ℹ️ INFO-1 — `dev_master` 는 전표 생성 권한이 없다 (#809 회귀 아님)

과업 브리프는 `dev_master` 지정이나, 실측상 **전표 QA 불가 계정**이다.

- `GET /slips/price-memory` (dev_master) → **403**
- `POST /slips` (dev_master, 유효 라인) → **403** `"전표 변경 권한이 없습니다"`
  로그: `[M4] slip mutation permission denied accountId=a0000000-…-0001 pageCode=sales.slip.create action=CREATE`
- auth_db 실조회: `dev_master` = **"마스터" 권한그룹** → slip 관련 행은 `sales.slip.list`·`hr.slip-cutoff` 뿐,
  **`sales.slip.create` / `purchases.slip.edit` 행 없음**. 해당 페이지코드 보유 그룹 = **매니저 / 영업원**(sales.slip.create), **매니저 / 창고원**(purchases.slip.edit).

→ price-memory 403 은 `checkPriceMemoryReadPermission`(OUTBOUND 생성 **또는** INBOUND 작성 OR 조건)이
기존 `checkCreatePermission` 과 **동등**하게 판정한 결과다. **비대칭 버그 아님**(전표 생성도 동일하게 403).
설계 의도대로 동작. 따라서 본 QA 는 두 권한 전권인 **`dev_manager`** 로 수행.

## 6. 스샷 (전부 실 캡처)

| 파일 | 증명 내용 |
|---|---|
| `01-partnerA-productX-catalog-list-price-1470700.png` | 거래처A+품목X 최초 선택 → 정가 1,470,700 채움(miss 시 fallback) |
| `02-partnerA-productX-manual-price-999000-entered.png` | 단가 P=999,000 직접 입력 |
| `03-slip-saved-redirect-to-sales-list.png` | 저장 성공 → 판매전표 목록 이동 |
| `04-KEY-new-slip-partnerA-productX-autofill-remembered-999000.png` | **[핵심 증거]** 새 전표 · 거래처A+품목X → **999,000 자동채움**(정가 아님) |
| `05-partnerB-productX-isolated-list-price-1470700.png` | 거래처B + 동일 품목X → 1,470,700 정가(거래처별 격리) |
| `06-override-preserved-123456-not-overwritten.png` | 단가 선입력 123,456 보존(덮어쓰기 없음) |
| `07-upsert-resaved-777000-autofilled-single-row.png` | 재저장 777,000 upsert → 단일행 갱신 + 자동채움 반영 |
| `08-DEFECT-estimate-autofill-fallback-to-list-price-1470700.png` | **[HIGH-1 증거]** 견적: 거래처 선택됐는데 단가 1,470,700 fallback + 품목명 빈칸 |

## 7. 결론

- **전표(출고) 최근단가 자동채움 = 실서버·실 GUI·실 DB 로 기능 동작 실증 완료** (핵심 시나리오 1~4 + 격리 + override + upsert 전부 PASS).
- **VAT basis 라운드트립 무손실**(화면 999,000 = DB 999000.00) 실증.
- **WRITE 훅 정상**(DB 행 생성 + upsert 갱신) — stub-success 아님.
- **견적 경로는 미동작(HIGH-1)** — 선재 계약 불일치에 신규 의존한 결과. 결정 ① 범위를 충족하려면
  `ProductLookupResult` ↔ BE `ProductSummary` 계약 정합(`id`→`productId`, `name`→`productName` 매핑)이 필요.
  이 수정은 BLOCKING-1(견적 저장 불가)도 함께 해소한다.

**재현 명령**

```powershell
cd C:\dev\Samhan-Public\clients\desktop
npx vite --config vite.web.config.ts --port 5199 --strictPort   # 별도 창, mock OFF
.\node_modules\.bin\playwright test --config=playwright.real-qa.config.ts `
  --reporter=line --timeout=120000 playwright/809-price-memory-real-qa
```

현재 기대 결과: **5 passed / 1 failed** — 실패 1건(`08 견적`)이 HIGH-1 결함 재현이다(의도된 red).
