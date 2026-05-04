# Inventory Service 첫 슬라이스 QA 리포트

> 슬라이스: inventory-first-slice | base: eb611bf | 작성일: 2026-05-04
> 팀명: Team-Inventory (BE/FE/DevOps/QA 4-team parallel) | QA 담당: QA-AGENT-INVENTORY-01
> 테스트 유형: 내부 QA (initial slice)
> 대상 서비스: `services/inventory-service` (Plan §4 권한 매트릭스 + §6 spec 기준 first slice)

## 1. 검증 범위

- **IT 4개 클래스** (싱글턴 컨테이너 패턴, product-service IT pattern 동일):
  - `AbstractPostgresIT` — 베이스 (Docker 미가용 시 자동 skip)
  - `WarehouseRepositoryIT` — V2 시드 4개 (HQ-001/VH-001/CS-001/VR-001) + partial unique + VIRTUAL 존재 (4개 시나리오)
  - `StockRepositoryIT` — FIFO ORDER BY received_at ASC + warehouse/product 격리 + @Version 충돌 (3개 시나리오)
  - `InventoryControllerIT` — 권한 매트릭스 + 입고 (`/inventory/lots/inbound` 201) + 출고 FIFO + 409 재고 부족 (6개 시나리오)
  - `StockTransferControllerIT` — 라이프사이클 (REQUESTED→APPROVED→SHIPPED→RECEIVED) + 가상창고 source IN_TRANSIT 스킵 + 권한 (4개 시나리오)
- **fixtures.http** — VS Code REST Client / IntelliJ HTTP Client 형식 시나리오 8건
- **권한 매트릭스 검증** — Plan §4 의 7-tier role × inventory endpoint 전수

## 2. 테스트 환경

| 항목 | 내용 |
|------|------|
| 서버 환경 | Docker Compose (postgres:16-alpine, eureka, api-gateway, auth-service, user-service, inventory-service) |
| JDK | 17 (Eclipse Temurin) |
| 빌드 | Gradle 8.10.2, `:services:inventory-service:test` |
| DB | PostgreSQL 16 (Testcontainers, JVM 1회 부팅, singleton pattern) |
| HTTP 클라이언트 | VS Code REST Client / IntelliJ HTTP Client (`src/test/resources/fixtures.http`) |
| 게이트웨이 | api-gateway 가 X-User-Id / X-User-Role 헤더 주입 |
| 시드 | `V2__seed_inventory_warehouses.sql` (HQ-001 / VH-001 / CS-001 / VR-001) |

> 본 리포트의 "PASS/FAIL" 칸은 PM 통합 후 실제 IT 실행 + 시연으로 채웁니다.

## 3. IT 결과

| 클래스 | 시나리오 수 | PASS | SKIP(Docker) | FAIL |
|---|---|---|---|---|
| WarehouseRepositoryIT | 4 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| StockRepositoryIT | 3 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| InventoryControllerIT | 6 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| StockTransferControllerIT | 4 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| **합계** | **17** | — | — | — |

## 4. 권한 매트릭스 검증 (Plan §4 + BE 실제)

✅ = 허용 (200/201/204), ❌ = 차단 (403)

| Endpoint | MASTER | MANAGER | DEVELOPER | SALES | ACCOUNTANT | WAREHOUSE | INVENTORY |
|---|---|---|---|---|---|---|---|
| `GET    /inventory/warehouses` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST   /inventory/warehouses` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PATCH  /inventory/warehouses/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `DELETE /inventory/warehouses/{id}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `GET    /inventory/balances\|lots\|movements` | ✅ | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ |
| `POST   /inventory/lots/inbound` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `POST   /inventory/reserve` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| `POST   /inventory/release` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| `POST   /inventory/deduct` | ✅ | ✅ | ✅ | ✅ | ❌ | ✅ | ✅ |
| `POST   /inventory/adjust` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `GET    /inventory/transfers` | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST   /inventory/transfers` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `POST   /inventory/transfers/{id}/approve` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `POST   /inventory/transfers/{id}/reject` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `POST   /inventory/transfers/{id}/ship` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `POST   /inventory/transfers/{id}/receive` | ✅ | ✅ | ❌ | ❌ | ❌ | ✅ | ✅ |
| `POST   /inventory/transfers/{id}/confirm` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |
| `POST   /inventory/transfers/{id}/cancel` | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ✅ |

> IT 가 직접 검증하는 행: `salesRole_postWarehouse_returns403`, `salesRole_inbound_returns403`, `transferApprove_warehouseRole_returns403`. 나머지 칸은 fixtures.http + 수동 시연으로 PM 통합 단계에서 검증.

## 5. 핵심 시나리오 시연

1. **입고 → 출고 FIFO**: 100개 입고 (`POST /inventory/lots/inbound`) → 30개 출고 (`POST /inventory/deduct`). 가장 오래된 lot 부터 차감.
2. **가상창고 IN_TRANSIT 스킵**: VIRTUAL 창고 (VR-001) 를 source 로 한 transfer 생성 → ship() 호출 시 status 가 즉시 RECEIVED 로 점프 (Plan §3.1).
3. **창고간 이동 라이프사이클**: REQUESTED → APPROVED (MANAGER) → SHIPPED (WAREHOUSE) → RECEIVED (WAREHOUSE) → CONFIRMED (MANAGER).
4. **잘못된 상태 전이 차단**: SHIPPED 상태에서 reject 시도 → 409 (REQUESTED/PENDING_APPROVAL 에서만 가능).
5. **권한 부족 403**: SALES 의 입고/등록, WAREHOUSE 의 approve.
6. **재고 부족 409**: 잔액 0 인 product 출고 → BusinessException(CONFLICT).
7. **@Version 충돌**: 두 트랜잭션이 동일 StockBalance 동시 수정 → OptimisticLockingFailure.
8. **partial unique**: 동일 warehouse code 중복 등록 차단 / soft-delete 후 재등록 허용.

## 6. 스크린샷

`docs/qa/inventory-first-slice/screenshots/*.png` — PM 통합 후 commit-pinned raw URL 로 PR 본문에 첨부.

예정 캡처 파일:
- `01_it_repository_pass.png` — Repository IT 7개 (Warehouse 4 + Stock 3) 통과 화면
- `02_it_controller_pass.png` — Controller IT 10개 (Inventory 6 + Transfer 4) 통과 화면
- `03_warehouse_list.png` — 시나리오 1 창고 목록 조회
- `04_warehouse_create.png` — 시나리오 2 매니저 차량창고 등록
- `05_inbound_then_deduct_fifo.png` — 시나리오 3+4 입고→출고 FIFO
- `06_reserve_release.png` — 시나리오 5 예약→해제
- `07_adjust.png` — 시나리오 6 재고 조정
- `08_transfer_lifecycle.png` — 시나리오 7 전체 라이프사이클
- `09_virtual_warehouse_jump.png` — 시나리오 8 가상창고 ship() RECEIVED 점프
- `10_warehouse_role_approve_403.png` — WAREHOUSE 의 approve 시도 → 403

## 7. 버그 목록

| # | 심각도 | 제목 | 재현 단계 | 스크린샷 | 상태 |
|---|--------|------|----------|---------|------|
| - | - | [PM 통합 후 채움] | - | - | - |

## 8. 종합 판정

| 항목 | 결과 |
|------|------|
| IT 시나리오 | 17건 |
| HTTP fixtures 시나리오 | 8건 |
| 통과 | [PM 통합 후 채움] |
| 실패 | [PM 통합 후 채움] |
| **최종 판정** | [PM 통합 후 채움 — PASS / FAIL] |

## 9. Plan 대비 의도적 변경

- WarehouseType 4-tier (HEADQUARTERS/VEHICLE/CONSIGNMENT/VIRTUAL) — Plan §3.1 그대로. BE 초안은 OWNED/LEASED/VIRTUAL 3-tier 시도했으나 PM 통합 검증 단계에서 개발책임자 결정으로 4-tier 채택.
- VIRTUAL 창고는 source/destination 가드로 차단되지 않고 ship() 단계에서 IN_TRANSIT 스킵 → 즉시 RECEIVED 점프 (Plan §3.1 의 가상창고 정의 그대로).

## 10. PM 통합 후 검증 권고 순서

1. **컴파일 확인**: `./gradlew :services:inventory-service:compileTestJava` PASS.
2. **단위 테스트**: `./gradlew :services:inventory-service:test` (Docker 미가용 시 IT skip, unit test 38건만 PASS).
3. **IT 실행** (Docker 가용): Repository IT 7개 + Controller IT 10개 = 17개.
4. **수동 시나리오 (스크린샷)**: docker-compose 풀스택 부팅 후 `fixtures.http` 8개 요청 순차 실행. Edge headless 또는 IntelliJ HTTP Client 캡처.
5. **본 리포트 갱신**: "PASS/FAIL" / "스크린샷" / "버그 목록" / "종합 판정" 칸 채움.

---

| createdAt | createdBy | modifiedAt | modifiedBy |
|-----------|-----------|------------|------------|
| 2026-05-04 | QA-AGENT-INVENTORY-01 | 2026-05-04 | PM-AGENT (4-tier 정렬, BE 시그니처 정렬) |
