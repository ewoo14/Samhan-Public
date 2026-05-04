# Inventory Service

SamhanLogis MSA 의 재고 도메인 마이크로서비스 (plan §3 첫 슬라이스).

## 책임

- **창고 (`warehouses`)** — 자체/임대/가상 창고 마스터
- **재고 로트 (`stock_lots`)** — 입고 단위, FIFO 키는 `received_at`
- **재고 잔량 (`stock_balances`)** — `(product, warehouse)` 집계 + 낙관적 락
- **재고 이동 (`stock_movements`)** — append-only 감사 로그
- **이동전표 (`stock_transfers` + `stock_transfer_lines`)** — 창고 간 재배치 워크플로우

## 외부 의존

- **product-service** — `POST /products/internal/lookup` 으로 productId 검증 (`X-Internal-Token`)
- **eureka-server** — 서비스 디스커버리

## 포트

- HTTP `8085`

## 프로파일

- 기본 (`default`) — PostgreSQL `inventory_db` + Flyway + Eureka 활성
- `local` — H2 in-memory + Eureka 비활성 (단위 테스트용)
