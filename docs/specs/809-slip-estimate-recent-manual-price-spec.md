# #809 - 전표/견적 (거래처+품목) 최근 수동단가 자동채움

- **상태**: 구현 및 R1 리뷰 보완 진행
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

## 화면/경로 범위

- 신규 전표 생성, 전표 라인 추가, 전표 수정(출고/입고 direct PUT), 전표 복사 경로를 모두 배선한다.
- 견적 생성, 견적 수정, 모바일 견적 생성 경로를 모두 배선한다.
- 수정 화면은 VAT 제외 단가 입력 UI 이므로 저장소에는 `unitPrice * 1.1` 로 VAT 포함 기준에 맞춰 정규화한다.

## 조회 및 자동채움

- 브라우저 호출용 일반 endpoint `GET /slips/price-memory?partnerId={uuid}&productId={uuid}` 를 사용한다. `/internal` endpoint 는 사용하지 않는다.
- 권한은 전표 생성/수정 또는 견적 생성/수정 권한 중 하나를 요구한다.
- 조회 hit 시 기억단가를 자동채움하고 `최근가` 마커를 표시한다. miss 또는 조회 실패 시 catalog 정가로 폴백한다.
- 거래처 변경 시 자동채움 라인만 새 거래처 기준으로 재조회한다. 사용자 override 라인은 보존한다.

## 테스트 기준

- 실 Postgres IT 로 V58 migration, unique 제약, upsert 충돌 갱신, soft-delete revive, VAT 포함 라운드트립을 검증한다.
- 원 전표/견적 트랜잭션 롤백 시 가격기억 row 가 남지 않아야 한다.
- 가격기억 flush 실패는 fail-soft 로 처리되어 전표/견적 저장을 깨지 않아야 한다.
