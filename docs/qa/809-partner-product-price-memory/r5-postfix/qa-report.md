# #809 R5 CODEX SOL 5.6 QA fix — live real QA report

- 실행일: 2026-07-16 KST
- 대상: PR #820 R5 fix 배포본, gateway `:8080`, Vite QA renderer `:5220`, 실 PostgreSQL `slip_db`
- 실행 명령: `playwright test playwright/809-price-memory-real-qa/price-memory-r2-live-real-qa.spec.ts --config=playwright.real-qa.config.ts --reporter=line --timeout=60000`
- 최종 판정: **13 PASS / 1 FAIL / 0 SKIP — suite RED**
- 기존 R4-postfix 10개: **10/10 PASS**
- 신규 R5 시나리오 패밀리: **R5-H6 FAIL / R5-H7 PASS(전표·견적) / R5-H8 PASS**

## 신규 시나리오 판정

### R5-H6 — FAIL, R5-H2 fix 실증 불가

- 실 DB 편집 가능 legacy 라인: `1,926`; 그중 `partner_id IS NOT NULL`: `0`.
- 실 대상: 견적번호 `2026/06/08-1924`, 모델 `AR16TXEAAWKNEU-08`, `unit_price=1920000.00`, `unit_price_with_vat=NULL`.
- GUI 편집 hydrate는 1,920,000을 그대로 표시했다.
- 무수정 `임시저장` 클릭 결과: **PUT 0건**. 화면 오류는 `거래처 정보를 다시 불러올 수 없습니다. 거래처를 다시 선택해 주세요.`.
- DB before/after는 `product|1920000.00|NULL`로 완전 동일했고 대상 품목 price-memory도 빈 상태 그대로였다. 다만 PUT이 발생하지 않았으므로 `legacyPriceUntouched`의 서버 round-trip은 증명되지 않았다.
- 같은 거래처 재선택은 `partner_id`를 새로 채우는 수정이므로 “무수정 저장” 단언을 우회하지 않았다.
- 선택한 실 legacy 행에 대한 실제 편집→원복도 PUT 선행 차단 때문에 미수행했다.

### R5-H7 — PASS, `BundleLineageResolver` 실증

- 전표 `2026/07/16-17`: 신규 BUNDLE POST 후 상세 무수정 PUT 2xx.
- 견적 `2026/07/16-11`: 신규 BUNDLE POST 후 편집 재진입 무수정 PUT 2xx.
- 양쪽 모두 POST 직후와 PUT 직후 lineage snapshot이 완전 동일:
  - 구성품 A: `set_head=true`, `parent_set_model=QA797-SET-01`, `80000.00/88000.00`.
  - 구성품 B: `set_head=false`, `parent_set_model=QA797-SET-01`, `50000.00/55000.00`.
- 최종 price-memory:
  - 부산냉난방테크 + parent BUNDLE: `1100000.00|BUNDLE_SET`, 정확히 1행.
  - 전주에어시스템 + parent BUNDLE: `1100000.00|BUNDLE_SET`, 정확히 1행.
  - 두 거래처의 구성품 기억행: **0행**.

### R5-H8 — PASS, lookup↔price 중간상태 실증

- `GET /slips/lookup-product` 실 2xx를 먼저 관측했다.
- 이어지는 단건 `GET /slips/price-memory`는 `route.fetch()`로 실 upstream 2xx를 받은 뒤 gate 동안만 hold하고 `route.fulfill({ response })`로 원본 status/header/body를 무변조 전달했다.
- hold 중 GUI: 단가 `0`, `최근단가 확인 중…`, 저장 disabled; 강제 click 뒤에도 `POST /estimates` 0건.
- 응답 전달 후 GUI: 품목명 정상 바인딩, 단가 `888000`, `거래처 최근단가` 마커, 저장 enabled.
- 저장은 POST 정확히 1건. DB: `quantity=1`, `unit_price=807273.00`, `unit_price_with_vat=888000.00`; price-memory `888000.00|LINE_SAVE`.
- 공급단가는 BE 공식 `round(888000 / 1.1, HALF_UP)=807273`과 exact 일치.

## DB 및 오염 대조

- Flyway V58: checksum `1743979716`, success `true`.
- 최종 `partner_product_price_memory`: 5행; BUNDLE 구성품 product 2종 행은 0.
- 전체 테이블 DELETE는 없다.
  - `beforeAll`: 거래처 A/B × 품목 X/Y/BUNDLE/구성품 2종의 교집합만 `WHERE`로 정리.
  - 시나리오 helper: 정확한 `(partner_id, product_id)` 한 쌍만 `WHERE`로 정리.
- 실험 잔여 중 시나리오 대상 밖 행은 삭제하지 않았다.

## 단언 약화 0건

- 편집 전 계측: `expect(...)` 104개, test 10개.
- 최종: `expect(...)` 155개, test 14개.
- 삭제된 expect: **0개**.
- 삭제 expect 대응쌍: **0쌍**.
- 추가: expect 51개, 독립 test 4개(H6 1, H7 전표/견적 2, H8 1).
- H6의 PUT 2xx 단언은 실패 상태 그대로 유지했고, 같은 거래처 재선택/POST 대체/DB-only 대체로 약화하지 않았다.

## 정직한 잔여 미커버

- legacy 실제 편집→원복: 모든 실 legacy 편집 가능 행의 `partner_id`가 NULL이라 무수정 PUT부터 차단되어 미수행.
- legacy `QUOTE_SENT`: 현재 1,926행은 모두 `QUOTE_DRAFT`; SENT 실표본 없음.
- H7은 무수정 PUT 계보 보존을 검증했으며 BUNDLE 구성품 가격 자체를 사용자가 수정하는 경로는 범위 밖.
- H8은 `fillEstimateModel` 견적 경로의 단건 lookup↔price 창을 검증했으며 전표 autocomplete 선택의 별도 중간 창은 범위 밖.
- 기존 D-R4-4 거래처 해제 GUI 도달 불가는 기존 리포트와 동일하게 남음.

## 캡처 파일 전체 목록

1. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\01-slip-miss-list-price-1200000-catalog-marker-no-recent-marker.png`
2. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\02-slip-manual-price-888000-entered.png`
3. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\03-KEY-slip-autofill-888000-with-recent-marker.png`
4. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\04-KEY-estimate-autofill-888000-productname-filled-recent-marker.png`
5. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\05-estimate-saved-after-draft-save.png`
6. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\06-bundle-set-price-1100000-entered.png`
7. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\07-KEY-bundle-set-refill-1100000-bundle-set-source.png`
8. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\08-partnerB-isolated-list-price-1200000.png`
9. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\09-before-partner-change-A-888000-user-111111-autoY-1440000.png`
10. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\10-KEY-partner-changed-to-B-refresh-banner-visible.png`
11. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\11-KEY-partner-changed-bulk1-highlight-row1-555000-user-preserved-missY-1440000.png`
12. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\12-slip-detail-edit-unit-price-500000-vat-excluded.png`
13. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\13-KEY-new-slip-autofill-550000-after-edit-path.png`
14. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\14-override-preserved-123456-no-marker.png`
15. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\15-estimate-3lines-A-888000-user-111111-autoY-1440000.png`
16. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\16-KEY-estimate-partner-change-inflight-busy-save-disabled.png`
17. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\17-KEY-estimate-partner-changed-to-B-banner-highlight-row1-555000.png`
18. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\18-estimate-line1-x-hit-888000-remembered-2000-01-01.png`
19. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\19-KEY-estimate-swap-x-to-y-sellingprice-1440000-no-inheritance.png`
20. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\20-estimate-swap-back-to-x-rehit-888000.png`
21. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\21-estimate-final-y-1440000-saved.png`
22. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\22-KEY-estimate-no-partner-sellingprice-copy-without-partner-claim.png`
23. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\23-KEY-estimate-late-partner-select-rehit-888000-banner-highlight.png`
24. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\24-legacy-estimate-draft-before-nochange-save.png`
25. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\25-legacy-estimate-after-nochange-save-attempt.png`
26. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\26-slip-bundle-detail-before-nochange-put.png`
27. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\27-slip-bundle-after-nochange-put.png`
28. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\28-estimate-bundle-detail-before-nochange-put.png`
29. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\29-estimate-bundle-after-nochange-put.png`
30. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\30-KEY-estimate-model-lookup-done-price-memory-held-save-disabled-zero.png`
31. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\31-KEY-estimate-price-memory-resolved-888000-save-enabled.png`
32. `C:\dev\Samhan-Public\docs\qa\809-partner-product-price-memory\r5-postfix\32-estimate-price-memory-resolved-888000-saved.png`
