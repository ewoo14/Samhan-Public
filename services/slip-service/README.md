# slip-service

Samhan Public 전표(출고/입고), 견적, 배송 첨부, 발행, 감사 이력, realtime SSE 를 담당하는 서비스다.

- 포트: **8086**
- DB: PostgreSQL `slip_db`
- 주요 의존: inventory-service, product-service, partner-order-service, partner-service, notification/SMS provider

## 주요 공개 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/slips/from-estimate` | 견적 기반 전표 발행 |
| POST | `/api/v1/slips/from-partner-order` | 주문 기반 전표 발행 |
| GET | `/api/v1/slips/by-source` | 발행 source 기반 전표 조회 |
| GET | `/api/v1/slips/{id}/revisions` | 전표 revision 조회 |
| POST | `/api/v1/slips/{id}/revisions/{n}/restore` | revision 기반 복원 |

## #809 거래처+품목 최근 수동단가 조회

`partner_product_price_memory` 는 전표와 견적 라인 저장 후 `(partnerId, productId)` 별 최근 사용 단가를 기억한다. 저장 basis 는 VAT 포함 입력단가이며, 수정 화면처럼 VAT 제외 단가를 입력받는 경로는 저장 전에 `×1.1` 로 정규화한다.

| Method | Path | 권한 |
|---|---|---|
| GET | `/api/v1/slips/price-memory?partnerId={uuid}&productId={uuid}` | `sales.slip.create` CREATE 또는 `purchases.slip.edit` UPDATE 또는 `estimates.list` CREATE/UPDATE |

응답은 hit 시 `200 { unitPrice, source, updatedAt }`, miss 시 `204 No Content` 다. 이 endpoint 는 브라우저 호출용 사용자 대면 endpoint 이므로 `/internal` 과 `X-Internal-Token` 을 사용하지 않는다.

## 내부 API

| Method | Path | 설명 |
|---|---|---|
| POST | `/internal/slips/{slipId}/signatures` | APP source 전자서명 등록 |
| GET | `/internal/slips/by-partner/{partnerId}/recent` | partnerId 기준 최근 활성 전표 조회 |

내부 API 는 `InternalTokenFilter` 와 `ROLE_MASTER` 전제를 사용한다. 브라우저에서 호출해야 하는 사용자 기능은 내부 API 로 추가하지 않는다.
