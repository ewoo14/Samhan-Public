# #809 - 전표/견적 (거래처+품목) 최근 수동단가 자동채움

- **상태**: 🔴 **R8(OPUS 4.8 1차 적대검증) 0수렴 실패 — 고유 28건(BLOCKING 2·HIGH 9·MEDIUM 8·LOW 9)
  · fix 진행 중**. R8 은 직전 세션의 *"lineId 왕복 계약이 근본원인을 제거했다"* 는 주장을 **반증**했다 —
  `BundleLineageResolver` 의 휴리스틱 제거는 참이나, 정확성 부담이 **서버 → FE 로 이전**됐을 뿐이고
  폴백을 의도적으로 없앤 탓에 **서버 방어가 0** 이라 클라이언트 결함이 조용한 데이터 파괴로 귀결된다.
  R4·R5 fix 완료(CI 36/36 · 라이브 QA 14/14) 후 R6·R7·R8 미수렴.
- **개발책임자 확정**: D-R8-5(BUNDLE_SET 기억 정의 — 아래 §BUNDLE 정책) · D-R8-6/D-R8-7/D-R8-8 ·
  **D-R8-9**(lineId 계약 마커 — D-R8-6 의 판정 기준 이전) (아래 §R8 확정 계약). 확인 대기 **0건**.
- **대상**: 전표(출고/입고) + 견적. 주문(partner-order)은 범위 밖이며 기존 DcConfig 규칙가를 유지한다.
- **저장소**: `slip-service` 단일 테이블 `partner_product_price_memory`

## 확정 결정

| # | 항목 | 결정 |
|---|---|---|
| ① | 대상 범위 | 전표(출고/입고)와 견적만 적용. 주문은 제외한다. |
| ② | 저장 위치 | `slip-service` 신규 테이블 `partner_product_price_memory` 에 `(partnerId, productId)` 단위로 저장한다. |
| ③ | upsert 시점 | 라인 저장 경로에서 후보를 수집하고, 원 전표/견적 트랜잭션 커밋 후 1회 flush 한다. |
| ④ | source/우선순위 | 저장된 라인 단가(effective)를 기억한다. 사용자 override 는 항상 우선하며 다음 저장 때 기억값을 갱신한다. |
| ⑤ | VAT 기준 | **VAT 포함 입력단가 기준**으로 저장/반환한다. 즉 `unitPriceWithVat` parity 가 권위값이고, `unitPrice` 는 공급가 파생값이다. |
| ⑥ | 거래처 키 | `partnerId(UUID)` 를 저장한다. UUID 는 화면 표시 금지이며 hidden state/API payload 전용이다. |

### VAT 기준 정정 사유

초기 spec 의 공급가 기준 전제는 오류다. 전표와 견적의 품목 입력 필드는 VAT 포함 단가를 사용하므로 공급가 기준으로 저장한 값을 다시 입력 필드에 채우면 재사용마다 약 9.1% 단가가 하락한다. 2026-07-15 개발책임자 승인에 따라 VAT 포함 입력단가를 단일 저장 basis 로 확정한다.

## BUNDLE 정책

- 구성품 라인은 기억하지 않는다. 구성품 확장 단가는 납품가/배분가일 수 있어 사용자가 거래처와 합의한 판매 단가가 아니다.
- 세트 parent 품목은 사용자가 입력한 세트 단가를 기억한다.
- 세트 parent 저장 source 는 `BUNDLE_SET` 이며, 단품 라인 저장 source 는 `LINE_SAVE` 다.

> ⚠️ 각주 (2026-07-16): invariant **"구성품 라인은 기억하지 않는다"는 유지된다.** 무수정 편집
> PUT 이 구성품을 일반 단품으로 평면화해 이 invariant 를 깨던 결함(R5-H1)은 서버측
> `BundleLineageResolver` 계보 resolve 로 차단했고, R6-H1 의 fallback 계보 오귀속은 lineId 왕복
> 계약(`34f978ec9`)이 휴리스틱을 제거해 해소했다. 단 **R8-QA-1 이 lineId 미전송 PUT 으로 이
> invariant 가 여전히 깨짐을 라이브 실증**했고 → **D-R8-6(400 거부)** + **D-R8-9(판정 = 계약 마커)**
> 로 fix 한다(아래 §R8 확정 계약).

### `BUNDLE_SET` 기억의 정의 — **선택 시점에만 정의된다** (D-R8-5, 2026-07-16 개발책임자 확정)

**`BUNDLE_SET` 기억의 의미 = "세트를 선택하며 입력한 단가" 이며, 세트를 선택하는 그 시점에만 정의된다.**

따라서 **수정 경로에서 세트 가격을 바꿔도 `BUNDLE_SET` 기억은 갱신되지 않는다.** 이는 **결함이 아니라
위 정의의 귀결**이다 — 세트를 재선택하면 "직전에 그 세트를 선택하며 입력한 단가" 가 자동채움되며, 그것이
이 기억이 약속하는 값의 전부다.

> 📌 **이력 보존**: 2026-07-16 오전 확정(D-R8-3)은 이 동작을 **결함으로 처리·fix** 하도록 지시했으나,
> 그 전제(*"lineId 계약 도입으로 수정 경로 갱신이 기술적으로 가능해졌다"*)가 **R8-BE-2 로 코드
> 반증**되어 같은 날 **D-R8-5 로 번복**됐다. 대안 ⓐ `parent_set_product_id` 컬럼 신설 · ⓑ
> product-service modelCode 조회 RPC 신설은 **둘 다 기각** — 아래 ③ 때문에 어느 쪽도 문제를 해결하지
> 못한다.

**왜 수정 경로에서 갱신하지 않는가 = 갱신할 수 없는가 (근거 3종 · R8-BE-2 CONFIRMED)**

| # | 근거 | 실측/코드 |
|---|---|---|
| ① | **타입 불일치** — 세트 계보가 보유한 값은 **modelCode** 인데, 기억 테이블의 키는 **UUID** 다 | `parentSetModel` ← modelCode(`EstimateService:132`) vs `partner_product_price_memory.product_id` = **NOT NULL UUID** |
| ② | **역조회 경로 부재** — modelCode → productId 를 되찾을 RPC 가 없다 | `ProductClient` 전수: `lookup(List<UUID>)` · `requireExists(UUID)` · `lookupByModel(`**`modelName`**`)` · `expand(modelCode)→구성품`. `lookupByModel` 은 modelName 이고 `ProductSummary` 가 `modelName`/`modelCode` 를 **별도 필드로 보유**해 대체 불가 |
| ③ | 🔴 **애초에 일관된 "세트 단가" 가 존재하지 않는다** — ①②를 모두 해결해도 남는다 | `expand` 가 구성품에 **6:4 재배분**을 하고 사용자가 구성품 단가를 **임의 수정**한다. 수정된 문서에서 "그 세트의 단가" 를 역산할 유일해가 없다 |

③ 이 결정적이다 — ①②는 배관 문제라 원리상 해결 가능하지만, ③ 은 **역산 대상 자체가 정의되지 않음**을
뜻한다. 전개된 문서에는 BUNDLE parent 라인이 남지 않고 재배분·수정된 구성품만 남으므로, 갱신을 시도하면
"세트 단가" 가 아니라 **임의의 파생값**을 `BUNDLE_SET` 으로 각인하게 된다. 그래서 이 정의는 회피가 아니라
**정확성 선택**이다.

> 🚫 **다음 라운드 주의** — "수정 경로에서 BUNDLE_SET 기억이 갱신되지 않는다" 를 **결함으로 재제기하지
> 말 것.** D-R8-5 로 종결됐다(R6-M8 → D-R8-3 → **D-R8-5 번복·close**). 재제기하려면 위 ③(일관된 세트
> 단가의 부재)을 먼저 반증해야 한다.

## 화면/경로 범위

- 신규 전표 생성, 전표 라인 추가, 전표 수정(출고/입고 direct PUT), 전표 복사 경로를 모두 배선한다.
- 견적 생성, 견적 수정, 모바일 견적 생성 경로를 모두 배선한다.
- 견적 수정 화면도 `단가(VAT포함)` 입력을 사용하고 신규/사용자 편집 라인은 `priceVatInclusive:true` 로 저장한다. 단, `unitPriceWithVat=null` 인 legacy 라인은 사용자가 단가를 건드리지 않은 경우에만 원 공급단가와 `priceVatInclusive:false` 를 보존하며, 한 번이라도 사용자/원격 편집된 뒤에는 원값으로 되돌려도 VAT 포함 입력으로 취급한다.

## 조회 및 자동채움

- 브라우저 호출용 일반 endpoint `GET /slips/price-memory?partnerId={uuid}&productId={uuid}` 를 사용한다. `/internal` endpoint 는 사용하지 않는다.
- 권한은 전표 생성/수정 또는 견적 생성/수정 권한 중 하나를 요구한다.
- 조회 hit 시 기억단가를 자동채움하고 `거래처 최근단가` 마커를 표시한다. miss 또는 조회 실패 시
  catalog 판매가(`product.sellingPrice` — 제품 등록 화면 라벨 `판매가`)로 폴백하고 `판매가` 마커를
  표시한다. `정가` 라벨은 금지한다 — 기존 용어체계에서 '정가'는 출고가(releasePrice) 계열 별칭이라
  같은 화면의 `출고가` 필드와 오도된다(D-R4-1, 2026-07-15 개발책임자 확정).
- 거래처 미선택 상태의 `판매가` 마커 설명은 거래처를 단정하지 않는 카피(`판매가를 적용했습니다`)로
  분기한다. 거래처 선택 상태에서는 `이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다` 를
  사용한다(R4-D4a).
- 단가 input은 마커를 `aria-describedby`로 참조한다. 마커(라인 칩)에는 `aria-live` 를 부착하지
  않는다 — 비동기 재적용 고지는 상시 마운트된 배너 1곳(`role="status"` + `aria-live="polite"`,
  텍스트만 토글)이 담당한다(R4-D2·R4-D9).
- 거래처 변경 시 자동채움 라인만 새 거래처 기준으로 재조회한다. 사용자 override 라인은 보존한다.
- 거래처 선택 해제 시 단가값은 유지하고 마커(저장일 표기 포함)만 해제한다. 단가값을 판매가로
  되돌리지 않으며, 라인 상태(priceSource)는 유지해 거래처 재선택 시 재조회 대상 자격을 보존한다
  (D-R4-4, 2026-07-15 개발책임자 확정).
- 거래처 변경으로 실제 단가가 바뀐 행을 강조하고 `거래처 변경으로 최근단가 재적용 · 변경된 행을
  확인해 주세요.` 배너를 표시한다.
- 단가가 바뀐 행에는 색상 강조 외에 **`단가 변경` 인디케이터**(아이콘+텍스트 칩)를 표시하고, 행
  요소(데스크톱 행/모바일 카드)가 이를 `aria-describedby` 로 참조한다 — 변경행 식별의 색상 단독
  의존을 배제한다(R5-M5 — R4-D8 수용 근거 철회).
- 품목 선택/모델 lookup 의 **최초 자동채움 결과는 페이지 단일 status region 1곳에서 1회 고지**한다
  (`라인 N 거래처 최근단가 적용` / `라인 N 판매가 적용`). 이 region 은 재적용 배너와 동일한 상시
  마운트 `role="status"` 요소를 공유하며 배너 비활성 시 sr-only 로 렌더된다. 라인 칩에 `aria-live`
  를 부착하지 않는 R4-D2 원칙은 유지한다(R5-M4).
- 최근단가 resolve 진행 중에는 **busy region**(`role="status"` + `aria-live="polite"`,
  `최근단가 확인 중…`)을 표시하고 저장을 차단한다. 모델 lookup 과 가격조회 사이에 거래처가 바뀌면
  현재 거래처+새 productId 로 **재resolve** 하고 완료까지 busy 를 유지한다 — 중간 상태(품목만
  바인딩된 0원 라인)의 저장을 금지한다(R5-H3).

### R3 bulk wire 계약

- 단건 endpoint 는 호환을 위해 유지한다.
- bulk 는 `POST /slips/price-memory/bulk` + JSON body
  `{"partnerId":"uuid","productIds":["uuid", ...]}` 를 사용한다. `productIds` 는 1~100개다.
- 응답은 `ApiResponse<List<PartnerProductPriceMemoryBulkItemResponse>>` 이며 각 hit 는
  `productId`, `unitPrice`, `source`, `updatedAt` 을 가진다. miss 는 배열에서 생략하고 전체 miss 도
  `200` + `data: []` 로 반환한다. 중복 productId 는 제거하고 결과는 최초 요청 순서를 보존한다.
- 최대 100 UUID query string 은 약 3.7KB 이상으로 보수적 2KB request-line 경계를 넘으므로 조회용
  POST body 를 선택했다. UUID query string 노출 자체는 D-R3-1 에 따라 정책 위반이 아니다.
- bulk 한 요청은 동일한 4종 OR 권한을 한 번만 판정한다. 단건과 bulk 의 값·인가 의미는 같다.

### R3 저장/최신성 계약

- 전표/견적 입력 라인은 최대 100개다. 실운영 예상 최대 20라인과 기존 내부 bulk 100건 표준을
  기준으로, 정상 대량 문서는 허용하면서 무제한 DB 작업을 차단한다.
- 원 전표/견적 트랜잭션에서 `remembered_at` 논리 저장 시각을 command 에 담는다. afterCommit
  flush 가 역전돼도 `existing.remembered_at <= EXCLUDED.remembered_at` 인 경우만 갱신한다.
- `modified_at` 은 실제 DB 변경 감사 시각으로 유지한다. API `updatedAt` 과 FE 최근가 tooltip 은
  더 정확한 원 저장 시각인 `remembered_at` 을 사용한다.
- 최대 100행은 단일 set-based `INSERT ... ON CONFLICT` 로 저장한다. 가격기억 전용 상한은
  `lock_timeout=1s`, `statement_timeout=3s`, transaction timeout `4s` 다.
- afterCommit callback 은 bounded async executor(core 2/max 4/queue 100)에 작업만 인계하여 outer
  connection 을 먼저 반환한다. DB/queue 실패는 fail-soft 로 계측하고 원 전표/견적 저장은 성공시킨다.

## R3 확정 정책

- D-R3-1: UUID 는 사용자 **화면 표시만 금지**한다. DevTools Network 탭은 사용자 화면이 아니며 기존
  `GET /slips/{id}` 등도 내부 UUID URL을 사용하므로 query string/API payload 사용은 유지한다.
  bulk POST 선택은 UUID 회피가 아니라 최대 100개 UUID의 순수 URL 길이 제약 때문이다.
- D-R3-3: soft-delete 된 거래처/품목이 연결된 기존 문서를 편집할 때도 활성 가격기억 row 를 반환한다.
  UI 검색은 삭제 엔티티를 제외해 신규 라인에서 도달할 수 없지만, 기존 문서 편집에서 기억값을
  숨기면 저장 단가가 훼손된다. 거래처/품목 생존 확인 외부 호출은 CH-8 호출 증폭을 재발시키므로
  추가하지 않는다.
- D-R3-4: bulk endpoint + 권한 OR short-circuit + auth connect/read timeout 을 함께 적용한다.

## lineId 왕복 계약 (`34f978ec9`)

> ⚠️ 이 계약은 커밋 `34f978ec9` 로 도입됐으나 **본 개정(R8 문서 배치) 이전까지 spec 에 미기재**였다.
> PR 의 간판 계약이 스펙 밖에 있었다는 사실 자체가 R8 이 드러낸 문제의 일부다.

**근본원인 진단**: update 계약이 라인 안정 ID 없이 전 라인을 통째 교체 → 서버가 "신규 라인" 과 "수정된
기존 라인" 을 구분 불가 → 버려진 세트 계보를 fingerprint 휴리스틱으로 되찾으려는 모든 시도가 반례에 붕괴
(R5·R6·R7 3라운드 연속).

**처방**: update 요청에 `lineId` 를 실어 왕복시켜 계보를 **애초에 버리지 않는다.**

- `BundleLineageResolver` 는 `Map<lineId, lineage>` **직접 조회만** 한다. Comparator/distance/tie/
  fingerprint/FallbackCandidate 계열 휴리스틱 **잔존 0** — R8 BE 차원이 전수 grep 으로 반증에 실패해
  이 주장은 **참으로 확인**됐다.
- 타 문서의 `lineId` · 중복 `lineId` → **400**. 403 이 아닌 이유 = 문서 존재 여부 oracle 노출 회피.
- 견적 `EstimateLineResponse` 에 `setHead`/`parentSetModel` 노출을 추가해 전표와의 비대칭을 해소한다.

🔴 **그러나 이 계약은 정확성 부담을 제거한 게 아니라 서버 → FE 로 이전했다.** `BundleLineageResolver`
는 입력을 전면 신뢰하는 순수 함수이고, **그 전제를 강제하는 주체가 없다.** 폴백을 의도적으로 없앤 탓에
서버 방어가 0 이라, 클라이언트 결함은 휴리스틱 오작동이 아니라 **조용한 데이터 파괴**로 귀결된다.
D-R8-6·D-R8-8 이 이 구멍을 서버측에서 닫는다.

## R8 확정 계약 (2026-07-16 개발책임자 확정)

### D-R8-6 — `lineId` 미전송 PUT = **400 거부** (판정 기준은 D-R8-9 가 이전)

**구 클라이언트의 PUT 을 `400` 으로 거부한다.**

- **근거(R8-QA-1 · BLOCKING · 라이브 실증)**: 세트 전표를 **아무것도 수정하지 않고** 왕복 PUT(lineId
  없음) → **200** → 계보 `GZN:t:GZS｜DCX:f:GZS` 가 **`GZN:f:-｜DCX:f:-`** 로 **전량 소실** + 구성품
  배분가(`501600`·`752400`)가 `LINE_SAVE` 로 각인.
- **"구 클라이언트 호환" 은 호환이 아니라 조용한 파괴다** — 사용자는 `200` 을 받고 데이터를 잃는다.
  `lineId` optional + 7필드 호환 생성자가 노렸던 하위호환은 이 실측으로 **기각**됐다.
- 기각된 대안: ⓑ 미전송 시 기존 계보 보존(**폴백 부활**) — *"폴백을 남기면 같은 결함 재발"* 로 이미
  배제된 방향 · ⓒ 현행 유지 + spec 명시 — 서버 방어가 0 인 채 남는다.

> ⚠️ **최초 판정 기준("계보 보유 문서인데 전 라인이 `lineId` 미전송")은 D-R8-9 로 대체됐다.**
> 그 기준은 **전 라인을 새 라인으로 교체하는 정상 저장**(역시 `lineId` 0개)을 함께 막는 오탐이
> 있었다. **거부 자체(D-R8-6)는 유지되고, 판정 기준만 옮겨졌다.**

### D-R8-9 — 판정 기준 = **요청 레벨 계약 마커** (`lineIdContract`)

**구 클라이언트 판정을 "`lineId` 개수" → "요청 레벨 마커 유무" 로 옮긴다.**

| 요청 | 판정 |
|---|---|
| 마커 **부재**(또는 `null`/`false`) | **400** — 구 클라이언트 |
| 마커 **존재**(`true`) | `lineId` 계약 활성 — **`lineId` 0개(전 라인 교체)도 정상 허용** |

- **왜 라인을 세면 안 되나**: 신규 라인의 `lineId == null` 은 **정상**이고, 전 라인 교체 저장은
  `lineId` 가 0개다. 즉 **라인만 보고는** "계약을 아는 클라이언트가 새 라인만 보낸 것" 과 "계약을
  모르는 구 클라이언트가 통째로 보낸 것" 을 **영원히 구분할 수 없다**. 마커는 클라이언트가 *자기
  자신에 대해* 하는 선언이라 그 구분을 라인과 무관하게 성립시킨다.
- **즉시 필수화 근거**: **구버전 desktop 은 사실상 없다 — 전원 최신본**(개발책임자 확인). 점진
  마이그레이션 창이 불필요하며, 호환 창을 두면 그 창이 곧 R8-QA-1 의 파괴 경로로 남는다.
- **계약 표면**: `SlipUpdateRequest.lineIdContract` · `UpdateEstimateRequest.lineIdContract`
  (`Boolean`, 요청 레벨 · per-line 아님). 판정·사유 문구는 공용 `LineIdContractGate` 단일 구현.
- **게이트는 문서 계보와 무관**(무조건 필수). 계보 보유 문서로 한정하면 구 클라이언트가 평면
  문서에서 `200` 을 받는데, 구 클라이언트는 `partnerId`(D-R8-7 신규)도 보내지 않아 거래처를 바꾼
  저장이 기억을 **원 거래처**에 각인시킨다(R8-QA-3). 계보 유무와 무관하게 구 클라이언트의 쓰기는
  전부 위험하다.
- **거부는 어떤 상태 변경보다 먼저** — 세 서비스 모두 `update()` 진입 직후 게이트. 특히 견적은
  `editHeader` 가 라인 검증보다 앞서고 `lines == null` 이면 라인 검증을 아예 건너뛰므로, 게이트를
  라인 검증 안에 두면 헤더 전용 수정이 게이트를 **통째로 우회**한다.
- **400 사유(한국어)**: 원인·결과·조치 명시 — *"구버전 앱에서 보낸 저장 요청입니다. 이대로 저장하면
  세트 구성품 정보가 사라질 수 있어 요청을 거부했습니다. **앱을 업데이트한 뒤 다시 저장해 주세요.**"*
- **FE 배선**: 마커는 `updatePurchaseSlip`/`updateSalesSlip`/`updateEstimate` **api 함수 내부**에서
  스탬프한다(`withLineIdContract`). 호출자 타입에 노출하지 않아 **호출자가 잊을 수 없다**.

### D-R8-7 — 전표 거래처 = **`PartnerAutocomplete` 단일 경로 · 자유입력 봉쇄**

**전표 수정 화면의 거래처 자유입력을 봉쇄하고 `PartnerAutocomplete` 로 통일한다. 계약
(`SlipUpdateRequest`)에 `partnerId` 를 추가한다.**

- **근거(R8-BE-3 = R8-QA-3 · HIGH · 라이브 실증)**: 거래처명 `R8검증-다른거래처` + 단가 `277000` 저장
  → **200** → `partner_name` 은 변경 / `partner_id` **불변** → 기억 `304700` 이 **원 거래처**
  `44f0cfc1` 에 각인. 화면의 거래처와 기억의 귀속처가 갈린다.
- **선례 정렬**: `SlipFormPage:956-957` 이 동일 결함을 **"P0 D-AC3-01"** 로 이미 제거했다.
- 🔴 **공통 필수**: `collectPriceMemory` 를 **헤더 갱신 이후로 이동**한다(견적 순서와 정렬).
- 🔗 **견적도 동시 처리**(R8-DESIGN-1) — 견적 폼은 거래처 입력 경로가 2개인데 권위 있는
  `PartnerAutocomplete` 만 `disabled={coeditActive}` 이고 `거래처명` **자유입력은 미잠금**이라
  `partnerIdSnapshot` 과 괴리된다. 한쪽만 고치면 **이 PR 의 slip/estimate 비대칭 재발 패턴**이 반복된다.

### D-R8-8 — 세트 구성품의 **품목 교체 = 계보 승계 안 함**

**도메인 확정: 세트 구성품의 정체성은 품목에 묶여 있다. 품목을 교체하면 그 라인은 더 이상 그 세트의
구성품이 아니다.**

- **BE (심층방어)** — `BundleLineageResolver.assign()` 에 **productId 동일성 검증**을 추가한다.
  `BundleLineage` record 에 `productId` 를 실어 **옛 productId ≠ 새 productId 면 계보 승계를 금지**한다.
- **FE (근본 fix)** — `SlipDetailPage.coeditLinesToEditLines` 가 위치복원 대신 **Y.Doc 에서 `lineId`
  직독**한다.
- **근거(R8-BE-1 = R8-QA-6 · HIGH · 라이브 실증)**: head 라인을 무관 단품 `ACD-2558G` 로 교체 + 단가
  `150000` → **200** → `ACD-2558G:t:AF17B6474GZS` (단품이 **세트 head 로 각인**) · 기억행 **NONE**
  (가격기억 조용히 유실).
- **FE 차원 반대 의견의 disposition**: FE 는 *"정상 품목 교체가 lineId 계약상 합법이므로 BE cross-check
  는 오탐으로 계보를 날린다"* 며 반대했다. *"근본 원인은 FE"* 라는 지적은 **옳고 수용**한다(Y.Doc 직독을
  함께 한다). 그러나 위 도메인 확정에 따라 **productId 불일치 승계는 오탐이 아니라 정탐**이며,
  R8-QA-1 이 "서버 방어 0" 을 라이브 실증한 이상 클라이언트 신뢰만으로는 부족하다.

> 🔴 **fix 지뢰 (R8-FE-9)** — `seedEstimateCoeditProvider:220-229` 가 `lineId` 를 pick 하지 않아 견적
> Y.Doc 의 lineId 는 전부 **클라 랜덤 UUID** 다. Y.Doc 직독을 **견적에 그대로 복사하면 전 라인 400**
> 이 난다. 전표만 먼저 고치거나, 견적은 seed 에 `lineId` 를 **먼저** 추가한 뒤 적용한다.

## 테스트 기준

- 실 Postgres IT 로 V58 migration, unique 제약, upsert 충돌 갱신, soft-delete revive, VAT 포함 라운드트립을 검증한다.
- 원 전표/견적 트랜잭션 롤백 시 가격기억 row 가 남지 않아야 한다.
- 가격기억 flush 실패는 fail-soft 로 처리되어 전표/견적 저장을 깨지 않아야 한다.
- 구매 PUT, non-legacy 전표 복사 payload, 모바일 견적의 `(저장 VAT 포함 P → 조회 P)`를 경로별로
  검증한다. 주문 저장은 가격기억 서비스를 호출하지 않는 음성 계약으로 잠근다.
- 한 문서에 같은 `(partnerId, productId)`가 여러 번 나오면 문서 순서상 마지막 라인의 단가가 이긴다.
  이는 단일 PostgreSQL `INSERT ... ON CONFLICT` statement에 중복 키가 들어가는 하드 오류를 막는
  load-bearing dedupe 계약이다.
- 라이브 QA는 POST 2xx 응답에서 신규 문서 ID를 회수해 그 ID만 DB 대조하고, bounded async flush는
  5초 유한 폴링으로 정확한 P를 기다린다. timeout은 명시적으로 실패하며 선행 테스트 데이터에
  의존하지 않는다.
