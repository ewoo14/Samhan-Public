# #17 단가변동 S1 — BE 가격 모델 (price_change_schedule)

## 목적
#17 단가변동 에픽 **토대(BE)**. product-service 카테고리별 변동일 config = **단가변동 단일 진실원**. 후속 S2(견적 렌더 토글)·S3(주문 자동전환)이 소비.

## 구현 (product-service)
- **PriceChangeSchedule 엔티티**: BaseEntity 7 audit + soft delete(@SQLRestriction·@UuidGenerator), category(`homemulti`/`singleSets`/`commercialMulti`/`oldProducts` = `PartnerOrderLine.categoryKey` 정합) + effectiveDate(LocalDate, KST), create/updateEffectiveDate 도메인 메서드(직접 set 금지).
- **V22 Flyway**: price_change_schedule(CHECK category IN 4종 + 활성행 partial unique index + 4 seed `2026-04-01`). **fresh-postgres probe 검증**(DROP/CREATE + psql ON_ERROR_STOP → INSERT 0 4).
- **내부 endpoint** `GET /products/internal/price-change-schedule` → category→effectiveDate 맵(ApiResponse, 토큰 거부 **401**=product-service 표준).
- **PriceChangeScheduleInternalControllerIT**(Testcontainers: 토큰 401·CHECK 거부·활성행 partial unique·soft-delete 대체행·조회).

## 듀얼리뷰 (Opus ↔ Codex 0수렴)
- **Opus(BE/DevOps)**: BE 0 BLOCKING·MED(repo @SQLRestriction 중복 메서드명 정리)+LOW(403·VARCHAR30) fix. DevOps 0(Flyway V22 정확·마이그 안전·CI 자동커버·KST 무관).
- **Codex**: **403→401 표준 정정**(Opus fix 가 BE 리뷰어 오판 기반 → ProductInternalController 8곳 실 표준으로 정정 = **듀얼리뷰 가치 실증**) + IT 강화(토큰 401·partial unique·soft-delete 대체).

## 검증
CI `PriceChangeScheduleInternalControllerIT` green(Testcontainers 실 DB) · fresh-postgres probe · compileTestJava PASS · CI 전 잡 green. PR #686 squash `220282900`.

## 후속
- **S2 견적 렌더 토글**(estimate-app index.ejs 가 신규 endpoint 소비, D3) → **S3 주문 자동전환**(order-app PRICE_INC_DATE 제거·no-op 해소, D4) → S4 관리UI(ProductCatalogPage) → S5 견적↔주문 일관성(D5).
- ⚠️ 가격 정책 D3~D5 = spec `2026-07-01-price-change-epic-design.md` 박제, **오전 개발책임자 확인**.
