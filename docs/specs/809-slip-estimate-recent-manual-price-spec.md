# #809 - 전표/견적 (거래처+품목) 최근 수동단가 자동채움

- **상태**: R4·R5 적대검증 fix 완료(R5 fix 후 CI 36/36 · 라이브 QA 14/14) · **R6(FABLE5 재수렴)
  0수렴 실패 — HIGH 6·MEDIUM 10·LOW 6 · fix 진행 중** · 개발책임자 확인 대기 3건(R6-H6 coedit
  legacy 저장 데드락 · R6-M1 Hikari 4s 전역화 · R6-M8 BUNDLE_SET 기억 갱신)
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

> ⚠️ 각주 (2026-07-16): ① invariant **"구성품 라인은 기억하지 않는다"는 유지된다.** 무수정 편집
> PUT 이 구성품을 일반 단품으로 평면화해 이 invariant 를 깨던 결함(R5-H1)은 서버측
> `BundleLineageResolver` 계보 resolve 로 차단했으나, R6-H1 이 resolver fallback 의 계보 오귀속을
> 라이브로 재적발해 **fix 진행 중**이다. ② 세트 parent 의 `BUNDLE_SET` 기억은 현재 **생성 시점
> 1회뿐**이다 — 수정 경로에서 세트 가격을 바꿔도 기억이 갱신되지 않아 재선택 시 구값이
> 자동채움된다(R6-M8 · 라이브 실증). 의도인지 결함인지 **개발책임자 확인 대기**이며 확정 시 본
> 절을 갱신한다.

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
