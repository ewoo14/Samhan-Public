# #809 R5 CODEX SOL 5.6 QA 스펙 재설계 — live real QA report

- 실행일: 2026-07-16 KST
- 대상: PR #820 HEAD `710979e26`, R5 fix 배포본, gateway `:8080`, Vite QA renderer `:5221`, 실 PostgreSQL `slip_db`
- 실행 명령: `playwright test playwright/809-price-memory-real-qa/price-memory-r2-live-real-qa.spec.ts --config=playwright.real-qa.config.ts --reporter=line --timeout=60000`
- 최종 판정: **14 PASS / 0 FAIL / 0 SKIP — suite GREEN**
- 기존 R4-postfix 10개: **10/10 PASS**
- R5 시나리오: **R5-H6 PASS / R5-H7 PASS(전표·견적) / R5-H8 PASS**

## R5-H6 재설계 판정 — PASS, R5-H2 fix 실증

### 도달 전제와 범위

- 실 대상: 견적번호 `2026/06/08-1924`, 모델 `AR16TXEAAWKNEU-08`, product `4599cfc1-35c1-3a8a-869b-f92f5f125b76`.
- 사전 DB: `partner_id=NULL`, `unit_price=1920000.00`, `unit_price_with_vat=NULL`, 대상 `(부산냉난방테크, 품목)` price-memory 0행.
- 정상 coedit provider 연결 중에는 거래처 autocomplete가 `disabled`라 이 legacy 문서의 `partner_id`를 복구할 GUI가 없다. H6는 앱에 이미 구현·단위검증된 **coedit provider 생성 실패 → 평문 폼 fallback**에 진입하도록 초기 coedit GET 1건만 abort했다.
- 이 fallback 진입을 정확히 1건으로 단언했다. 거래처 검색, 단가 상태, 견적 PUT, price-memory, DB 응답은 모두 실서버이며 응답 body 변조/합성은 없다.
- 거래처 재선택은 가격 편집을 대신하거나 우회하지 않는다. 저장 필수 전제인 `partner_id`만 복구하고, 검증 대상은 단가 입력을 건드리지 않은 `legacyPriceUntouched` 가격 basis다.

### Primary — 거래처 재선택, 가격 무수정

- 부산냉난방테크를 재선택한 뒤 GUI 단가는 계속 `1,920,000`이었다.
- `PUT /estimates/{id}` **2xx 관측**. 0건 허용 없음.
- 실 PUT body:
  - `partnerId=e8ae9c86-afe1-3364-b484-1f5a2bf31313`
  - `lines.length=1`
  - `unitPrice=1920000`
  - `priceVatInclusive=false`
- 저장 후 DB는 `product|1920000.00|NULL`로 사전값과 exact 동일:
  - `unit_price`: **1,920,000 → 1,920,000 불변**
  - `unit_price_with_vat`: **NULL 유지**
- 잘못 VAT 포함으로 해석했을 때의 `/1.1` 값 `1,745,455.00`을 별도 계산해 DB snapshot에 포함되지 않음을 명시 단언했다. **약 9.1% 하락 미발생**.
- 거래처가 채워졌으므로 price-memory는 새로 생성됐고, 값은 원 공급단가 기준 `1,920,000 × 1.1 = 2,112,000.00|LINE_SAVE`와 exact 일치했다. 하락값/원 공급단가 그대로 기억하는 오판은 허용하지 않는다.

### 역방향 provenance — 가격 실제 편집 후 원복

- 같은 legacy 라인에서 `1,920,000 → 999,000 → 1,920,000`을 실제 입력한 뒤 저장했다.
- 두 번째 `PUT /estimates/{id}`도 2xx였고, body는 최종 단가 `1,920,000` + `priceVatInclusive=true`였다.
- DB는 true 계약대로 `unit_price=1745455.00`, `unit_price_with_vat=1920000.00`; price-memory는 `1920000.00|LINE_SAVE`였다.
- 즉 최종 숫자가 원래와 같다는 이유로 `legacyPriceUntouched=true`로 되돌리는 역방향 오판이 없음을 실증했다.

### 반복 실행 복구

- H6 종료 시 정확한 legacy 견적 헤더 1건, 라인 1건, 해당 `(partner_id, product_id)` 기억쌍만 원상복구했다.
- 최종 확인: `2026/06/08-1924|partner=NULL|unit_price=1920000.00|unit_price_with_vat=NULL|memory=0`.
- 테이블 전체 DELETE/광역 DELETE는 없다.

## 전체 스위트 보조 판정

- R5-H7: 전표·견적 BUNDLE 신규 POST → 상세 무수정 PUT 뒤 계보 보존, 구성품 기억행 0, parent `BUNDLE_SET` 정확히 1행 — PASS.
- R5-H8: lookup 2xx 뒤 실 price-memory 응답 hold 중 저장 disabled/0원 POST 0건, 해제 뒤 `888000` 적용·POST 1건·DB `807273.00|888000.00` — PASS.
- Flyway V58 및 서비스 배포 상태는 PM 제공 실측(checksum `1743979716`)을 전제로 했으며, 본 라운드는 gradle/git을 실행하지 않았다.

## expect 변화 및 약화 0 증명

- 변경 전: `expect(...)` **155개**, test **14개**.
- 변경 후: `expect(...)` **171개**, test **14개**.
- 삭제된 단언 동작: **0개**.
- 추가 expect: **16개**(순증 +16).
- 약화: **0개**.
  - 기존 PUT `>=200` / `<300` 두 단언 유지.
  - 기존 DB `priceAfter === priceBefore` exact 단언 유지.
  - 기존의 잘못된 “price-memory 완전 불변” 기대만 올바른 “신규 생성” 계약으로 전환하고, 단순 `not equal`에 그치지 않고 exact `2112000.00|LINE_SAVE`를 2중 대조했다.
  - PUT body의 partnerId/라인수/unitPrice/`priceVatInclusive=false`, 명시적 9.1% 하락값 부재, coedit fallback 1건, 역방향 `priceVatInclusive=true`와 DB 두 필드를 추가했다.
  - 조건부 skip, 실패 catch 후 통과, PUT 0건 허용, DB-only 대체는 없다.

## 신규 캡처

1. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\33-KEY-legacy-estimate-partner-reselected-price-untouched-1920000.png`
2. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\34-KEY-legacy-estimate-after-put-supply-price-unchanged-memory-created.png`
3. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\35-KEY-legacy-price-edited-999000-restored-1920000-before-save.png`
4. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\36-KEY-legacy-price-edited-restored-saved-as-vat-inclusive.png`

기존 01~32 캡처는 보존했다.

## 정직한 잔여 미커버

- **정상 coedit 연결 상태의 legacy 거래처 재선택은 현재 GUI로 도달 불가**다. 정상 provider가 거래처 autocomplete를 잠그기 때문이다. H6는 앱의 의도된 coedit-unavailable 평문 fallback에서 가격 basis를 실증했다. 정상 협업 모드에 별도 거래처 변경 UX를 제공하는 작업은 본 QA 파일 범위 밖이다.
- legacy `QUOTE_SENT`: 현재 1,926개 표본은 모두 `QUOTE_DRAFT`; SENT 실표본 없음.
- R5-H7은 BUNDLE 무수정 PUT 계보 보존을 검증했으며 구성품 가격을 사용자가 직접 수정하는 경로는 범위 밖.
- R5-H8은 견적 `fillEstimateModel`의 단건 lookup↔price 창을 검증했으며 전표 autocomplete 선택의 별도 중간 창은 범위 밖.
- 기존 D-R4-4 거래처 해제 GUI 도달 불가는 종전과 동일하게 남는다.
