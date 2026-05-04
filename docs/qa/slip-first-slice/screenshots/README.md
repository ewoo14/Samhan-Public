# Slip Service First Slice — 스크린샷 디렉터리

본 디렉터리의 PNG 파일들은 **PM 통합 후** 다음 절차로 생성됩니다.

## 자동 생성 (IT 실행)

`./gradlew :services:slip-service:test` 결과를 IT report (HTML) 캡처:
- `01_it_domain_pass.png` — Domain IT 8개 (`SlipDomainIT`) + Number IT 2개 (`SlipNumberServiceIT`) 통과
- `02_it_controller_pass.png` — Controller IT 9개 (`SlipControllerIT`) + Lifecycle IT 3개 (`SlipLifecycleControllerIT`) 통과

## 수동 생성 (시나리오 시연)

`docker-compose up` 풀스택 부팅 후 `services/slip-service/src/test/resources/fixtures.http` 의 8개 요청을 IntelliJ HTTP Client / VS Code REST Client / Edge headless 로 시연하며 응답 본문 캡처:

| 파일명 | 시나리오 |
|--------|---------|
| `03_outbound_lifecycle_full.png` | 시나리오 1+3 출고 풀 라이프사이클 (DRAFT → CONFIRMED 9단계, 권한 SALES → WAREHOUSE → ACCOUNTANT 전환) |
| `04_inbound_skip_ship_409.png` | 시나리오 5 입고전표 ship() → 409 |
| `05_accept_calls_reserve.png` | accept 시 InventoryClient.reserve 호출 콘솔 로그 (Q2-A 결정) |
| `06_complete_calls_deduct.png` | complete 시 InventoryClient.deduct(fromReservation=true) 호출 콘솔 로그 (Q2-A 결정) |
| `07_reject_calls_release.png` | 시나리오 6 reject_after_accept 시 InventoryClient.release 호출 콘솔 로그 (Q2-A 결정) |
| `08_invalid_transition_409.png` | 시나리오 7 DRAFT 에서 accept 시도 → 409 BusinessException(CONFLICT) |
| `09_stack_auto_memo.png` | 시나리오 8 야적(STACK) 태그 → memo 자동 prepend 결과 본문 |
| `10_warehouse_reject_403.png` | WAREHOUSE 의 reject 시도 → 403 (`SlipControllerIT.reject_warehouseRole_returns403` 시연) |

## 방침

- 캡처 시 인증 토큰 등 secret 은 마스킹.
- 1280x800 이상 해상도 권장 (qa_report 본문에서 inline 표시 가능).
- PM 이 본 README 를 캡처 완료 후 갱신 또는 삭제.
