# Slip Service 첫 슬라이스 QA 리포트

> 슬라이스: slip-first-slice | base: c6c0f9a | 작성일: 2026-05-04
> 팀명: Team-Slip (BE/FE/DevOps/QA 4-team parallel) | QA 담당: QA-AGENT-SLIP-01
> 테스트 유형: 내부 QA (initial slice)
> 대상 서비스: `services/slip-service` (PM 명시 BE spec 기준 first slice — STI Slip 1 테이블 + 11 SlipStatus state machine)

## 1. 검증 범위

- **IT 4개 클래스** (싱글턴 컨테이너 패턴, inventory-service IT pattern 동일):
  - `AbstractPostgresIT` — 베이스 (`slip_db`, Docker 미가용 시 자동 skip)
  - `SlipNumberServiceIT` — 같은 일자 atomic seq + 다른 일자 독립 시퀀스 (2개 시나리오)
  - `SlipDomainIT` — 출고 happy path 9 단계 + 입고 happy path + 잘못된 전이 4건 + applyDeliveryTagAutoMemo (STACK / DAY) 2건 (8개 시나리오)
  - `SlipControllerIT` — 권한 매트릭스 + InventoryClient mock verify (reserve / deduct / release) (9개 시나리오)
  - `SlipLifecycleControllerIT` — 출고 풀 9단계 + 입고 ship 스킵 (409) + slipNo 정규식 검증 (3개 시나리오)
- **fixtures.http** — VS Code REST Client / IntelliJ HTTP Client 형식 시나리오 8건
- **권한 매트릭스 검증** — PM 명시 7-tier role × 16 slip endpoint 전수

## 2. 테스트 환경

| 항목 | 내용 |
|------|------|
| 서버 환경 | Docker Compose (postgres:16-alpine, eureka, api-gateway, auth-service, user-service, inventory-service, slip-service) |
| JDK | 17 (Eclipse Temurin) |
| 빌드 | Gradle 8.10.2, `:services:slip-service:test` |
| DB | PostgreSQL 16 (Testcontainers, JVM 1회 부팅, singleton pattern, `slip_db`) |
| HTTP 클라이언트 | VS Code REST Client / IntelliJ HTTP Client (`src/test/resources/fixtures.http`) |
| 게이트웨이 | api-gateway 가 X-User-Id / X-User-Role 헤더 주입 |
| 외부 의존 | InventoryClient → inventory-service (`reserve` / `release` / `deduct` / `inbound`). IT 에서 `@MockBean`. |

> 본 리포트의 "PASS/FAIL" 칸은 PM 통합 후 실제 IT 실행 + 시연으로 채웁니다.

## 3. IT 결과

| 클래스 | 시나리오 수 | PASS | SKIP(Docker) | FAIL |
|---|---|---|---|---|
| SlipNumberServiceIT | 2 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| SlipDomainIT | 8 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| SlipControllerIT | 9 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| SlipLifecycleControllerIT | 3 | [PM 통합 시 채움] | [PM 통합 시 채움] | [PM 통합 시 채움] |
| **합계** | **22** | — | — | — |

## 4. 권한 매트릭스 검증 (PM 명시 BE spec)

✅ = 허용 (200/201/204), ❌ = 차단 (403)

| Endpoint | MASTER | MANAGER | SALES | ACCOUNTANT | WAREHOUSE | INVENTORY | DEVELOPER |
|---|---|---|---|---|---|---|---|
| `GET    /slips`                  | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `GET    /slips/{id}`             | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `POST   /slips`                  | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `PATCH  /slips/{id}/header`      | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST   /slips/{id}/lines`       | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `DELETE /slips/{id}/lines/{lid}` | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST   /slips/{id}/save`        | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST   /slips/{id}/send`        | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |
| `POST   /slips/{id}/accept`      | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| `POST   /slips/{id}/process`     | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| `POST   /slips/{id}/complete`    | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| `POST   /slips/{id}/ship`        | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| `POST   /slips/{id}/deliver`     | ✅ | ✅ | ❌ | ❌ | ✅ | ✅ | ❌ |
| `POST   /slips/{id}/confirm`     | ✅ | ✅ | ❌ | ✅ | ❌ | ❌ | ❌ |
| `POST   /slips/{id}/reject`      | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ | ❌ |
| `POST   /slips/{id}/cancel`      | ✅ | ✅ | ✅ | ❌ | ❌ | ❌ | ❌ |

> IT 가 직접 검증하는 행: `unauthenticated_get_returns403`, `salesRole_postSlip_returns201`, `warehouseRole_postSlip_returns403`, `salesRole_acceptSlip_returns403`, `warehouseRole_acceptSlip_returns200_andCallsInventoryReserve`, `confirm_accountantRole_returns200`, `reject_warehouseRole_returns403`. 나머지 칸은 fixtures.http + 수동 시연으로 PM 통합 단계에서 검증.

## 5. 핵심 시나리오 시연 (8건)

1. **출고 풀 라이프사이클**: DRAFT → SAVED → SENT → ACCEPTED → PROCESSING → COMPLETED → SHIPPING → DELIVERED → CONFIRMED 9단계, 권한 SALES → SALES → SALES → WAREHOUSE → WAREHOUSE → WAREHOUSE → WAREHOUSE → WAREHOUSE → ACCOUNTANT.
2. **입고 라이프사이클 ship 스킵**: 입고전표 ship() → 409. COMPLETED 후 바로 confirm.
3. **accept → InventoryClient.reserve**: Q2-A 결정 검증 (SlipControllerIT.warehouseRole_acceptSlip_returns200_andCallsInventoryReserve).
4. **complete(출고) → InventoryClient.deduct(fromReservation=true)**: Q2-A 결정 검증 (SlipControllerIT.complete_outbound_callsInventoryDeduct_withFromReservationTrue).
5. **reject_after_accept → InventoryClient.release**: Q2-A 결정 검증 (SlipControllerIT.reject_afterAccept_callsInventoryRelease).
6. **권한 부족 403**: SALES 의 accept, WAREHOUSE 의 reject (SlipControllerIT 직접 검증).
7. **잘못된 상태 전이 409**: DRAFT 에서 accept 시도 → BusinessException(CONFLICT).
8. **STACK(야적) 자동 메모**: deliveryTag=STACK 이면 memo 에 `{slipDate}상차 {slipDate+1}하차` prepend (SlipDomainIT.applyDeliveryTagAutoMemo_stackTag_prependsLoadingDates).

## 6. 스크린샷

`docs/qa/slip-first-slice/screenshots/*.png` — PM 통합 후 commit-pinned raw URL 로 PR 본문에 첨부.

예정 캡처 파일:
- `01_it_domain_pass.png` — Domain IT 8개 + Number IT 2개 통과 화면
- `02_it_controller_pass.png` — Controller IT 9개 + Lifecycle IT 3개 통과 화면
- `03_outbound_lifecycle_full.png` — 시나리오 1 출고 풀 라이프사이클 (DRAFT→CONFIRMED 9단계)
- `04_inbound_skip_ship_409.png` — 시나리오 2 입고 ship() → 409
- `05_accept_calls_reserve.png` — 시나리오 3 InventoryClient.reserve 콘솔 로그
- `06_complete_calls_deduct.png` — 시나리오 4 InventoryClient.deduct(fromReservation=true) 콘솔 로그
- `07_reject_calls_release.png` — 시나리오 5 reject 후 InventoryClient.release 콘솔 로그
- `08_invalid_transition_409.png` — 시나리오 7 DRAFT 에서 accept → 409
- `09_stack_auto_memo.png` — 시나리오 8 야적 자동 메모 응답 본문
- `10_warehouse_reject_403.png` — WAREHOUSE 의 reject 시도 → 403

## 7. 버그 목록

| # | 심각도 | 제목 | 재현 단계 | 스크린샷 | 상태 |
|---|--------|------|----------|---------|------|
| - | - | [PM 통합 후 채움] | - | - | - |

## 8. 종합 판정

| 항목 | 결과 |
|------|------|
| IT 시나리오 | 22건 |
| HTTP fixtures 시나리오 | 8건 |
| 통과 | [PM 통합 후 채움] |
| 실패 | [PM 통합 후 채움] |
| **최종 판정** | [PM 통합 후 채움 — PASS / FAIL] |

## 9. 개발책임자 결정 사항 (Plan 대비)

- **Q1=A**: STI (Slip 1 테이블 + slip_type enum) — OUTBOUND/INBOUND 가 같은 11-state 머신을 공유하므로 단일 테이블이 자연스럽다.
- **Q2=A**: accept → reserve / complete → deduct(fromReservation=true) / reject_after_accept → release. 재고 사이드이펙트는 ACCEPTED 진입 시점에 reserve, 차감 확정은 complete 시점.
- **Q3=B**: OUTBOUND + INBOUND 만 (TRANSFER 는 inventory-service `/inventory/transfers` 가 담당).
- **Q4=A**: BaseEntity 7 필드 + `@Version` (낙관적 락).
- **Q5=B**: 낙관적 락 + 도메인 메서드 상태 전이 가드 (BusinessException(CONFLICT)).

## 10. PM 통합 후 검증 권고 순서

1. **컴파일 확인**: `./gradlew :services:slip-service:compileTestJava` PASS.
2. **단위 테스트**: `./gradlew :services:slip-service:test` (Docker 미가용 시 IT skip).
3. **IT 실행** (Docker 가용): Domain IT 8 + Number IT 2 + Controller IT 9 + Lifecycle IT 3 = 22개.
4. **수동 시나리오 (스크린샷)**: docker-compose 풀스택 부팅 후 `fixtures.http` 8개 요청 순차 실행. Edge headless 또는 IntelliJ HTTP Client 캡처.
5. **본 리포트 갱신**: "PASS/FAIL" / "스크린샷" / "버그 목록" / "종합 판정" 칸 채움.

---

| createdAt | createdBy | modifiedAt | modifiedBy |
|-----------|-----------|------------|------------|
| 2026-05-04 | QA-AGENT-SLIP-01 | 2026-05-04 | QA-AGENT-SLIP-01 |
