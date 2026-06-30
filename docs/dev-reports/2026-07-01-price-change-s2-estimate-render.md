# #17 단가변동 S2 — 견적 렌더 endpoint 배선 (estimate-app)

## 목적
#17 S1(price_change_schedule #686) 후속. 종합견적서(estimate-app)가 S1 `GET /products/internal/price-change-schedule` 소비. **D3=(a) 렌더 기본 동작/토글 불변 + endpoint 데이터 배선(표시 회귀 0)**. 경량(c) 변동일 자동 기본체크는 D3 확인 후(미적용).

## 구현 (estimate-app, Node/Express+EJS)
- **lib/db-catalog.js**: `priceChangeSchedule()` 전용 axios fetcher(`${PRODUCT_BASE}/products/internal/price-change-schedule`, X-Internal-Token, `resp.data.data||{}`) + export. (estimate-catalog BASE 하위 아님 — 전용 ax.get.)
- **lib/code.js**: bootstrap DB모드 `t.priceChangeSchedule` 주입(try/catch fallback `'{}'`, priceInc 패턴) + sheet모드 `'{}'`.
- **views/index.ejs**: `PRICE_CHANGE_SCHEDULE = J(<%- priceChangeSchedule %>, {})` 노출만 — 기존 토글(chkHomeInc/Comm/Single)/getBaseListPrice/렌더 동작 불변.
- **test/db-catalog.test.js**: fetcher mock + 단위테스트(맵·X-Internal-Token).

## 듀얼리뷰 (Opus ↔ Codex 0수렴)
- **Opus(FE/계약)**: FE 0 BLOCKING/HIGH/MED · **계약 5/5 정합**(경로·X-Internal-Token·ApiResponse.data·category 4종·LocalDate ISO). FE LOW(test mockClear)+계약 LOW(mock URL `$` 앵커) fix.
- **Codex 라운드**: 0수렴(fetcher timeout/계약·bootstrap 격리·EJS raw 안전·S3 계약 재검).

## 검증
jest **96/96**(5 suites, 회귀 0) · 계약 fetcher↔S1 5/5 · CI green. PR #687 squash `a6edadf3e`.

## 후속
- **S3 주문 자동전환**(order-app, D4): order-app `PRICE_INC_DATE` 하드코딩 제거 → 카테고리별 변동일 소비·`due` KST 비교·`*_INC` no-op 해소. **⚠️ order-app은 estimate Node bootstrap 비공유** → partner-order-service `BootstrapService` 경로로 별도 배선(Codex 라운드 확인).
- S4 관리UI(ProductCatalogPage)·S5 견적↔주문 일관성(D5). 가격 정책 D3~D5 오전 확인.
