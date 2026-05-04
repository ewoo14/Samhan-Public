# Inventory Service First Slice — 스크린샷 디렉터리

본 디렉터리의 PNG 파일들은 **PM 통합 후** 다음 절차로 생성됩니다.

## 자동 생성 (IT 실행)

`./gradlew :services:inventory-service:test` 결과를 IT report (HTML) 캡처:
- `01_it_repository_pass.png` — Repository IT 7개 통과 (Warehouse 4 + Stock 3)
- `02_it_controller_pass.png` — Controller IT 10개 통과 (Inventory 6 + Transfer 4)

## 수동 생성 (시나리오 시연)

`docker-compose up` 풀스택 부팅 후 `services/inventory-service/src/test/resources/fixtures.http` 의 8개 요청을 IntelliJ HTTP Client / VS Code REST Client / Edge headless 로 시연하며 응답 본문 캡처:

| 파일명 | 시나리오 |
|--------|---------|
| `03_warehouse_list.png` | 시나리오 1 창고 목록 조회 (SALES) |
| `04_warehouse_create.png` | 시나리오 2 매니저 차량창고 등록 (201) |
| `05_inbound_then_deduct_fifo.png` | 시나리오 3+4 입고→출고 FIFO |
| `06_reserve_release.png` | 시나리오 5 예약→해제 |
| `07_adjust.png` | 시나리오 6 재고 조정 |
| `08_transfer_lifecycle.png` | 시나리오 7 창고간 이동 라이프사이클 (REQUESTED→APPROVED→SHIPPED→RECEIVED→CONFIRMED) |
| `09_virtual_warehouse_jump.png` | 시나리오 8 가상창고 source ship() → 즉시 RECEIVED 점프 (Plan §3.1) |
| `10_warehouse_role_approve_403.png` | WAREHOUSE 의 approve 시도 → 403 |

## 방침

- 캡처 시 인증 토큰 등 secret 은 마스킹.
- 1280x800 이상 해상도 권장 (qa_report 본문에서 inline 표시 가능).
- PM 이 본 README 를 캡처 완료 후 갱신 또는 삭제.
