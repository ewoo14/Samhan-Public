# Slip Service 첫 슬라이스 개발 리포트

> **슬라이스**: slip-first-slice | **base commit**: c6c0f9a | **머지 PR**: (PM 통합 후 기재)

본 슬라이스는 Samhan Public 의 7번째 마이크로서비스(`slip-service`)를 도입한다. Plan §3.1
전표 관리 시스템의 첫 컷으로 **출고전표(OUTBOUND) + 입고전표(INBOUND)** 만 구현하며,
입금/출금/이동 전표는 후속 슬라이스에서 추가한다 (Q3=B 결정 사항).

3-layer 함수 단위 문서화 체계(Javadoc + springdoc-openapi + dev-reports) 가 본 서비스에도
의무 적용된다 (memory: `feedback_function_documentation.md`).

## 개발책임자 결정 사항 (5건, ground truth)

- **Q1=A**: Single Table Inheritance — Slip 1 테이블 + `slip_type` enum + nullable 필드
- **Q2=A**: 출고전표 ↔ Inventory 연계 = `accept` → reserve / `complete` → deduct(fromReservation=true) / `reject after accept` → release
- **Q3=B**: 첫 슬라이스 = 출고/입고만
- **Q4=A**: 수정이력 = BaseEntity 7 audit + `@Version`. HISTORY 복원 + snapshot 은 후속
- **Q5=B**: 락킹 = 낙관적 락(@Version) + 상태 전이 가드 (수락 이상 mutation 시 BusinessException CONFLICT)

## BE (Team-Slip BE)

### 도메인 메서드

#### Slip (전표 헤더, STI)
- `Slip.createOutbound(slipNo, slipDate, seqNo, sourceWh, destWh, partnerId, partnerName, deliveryTag, memo, requesterId)`: 출고전표 생성. sourceWarehouseId 필수. deliveryTag 의 direction 이 INBOUND 이면 IllegalArgumentException. status=DRAFT.
- `Slip.createInbound(slipNo, slipDate, seqNo, destWh, partnerId, partnerName, deliveryTag, memo, requesterId)`: 입고전표 생성. destinationWarehouseId 필수, sourceWarehouseId=null 강제. deliveryTag 의 direction 검증.
- `Slip.addLine(line)` / `removeLine(line)`: 양방향 연관관계 유지 (cascade ALL + orphanRemoval).
- `Slip.editHeader(partnerId, partnerName, deliveryTag, memo)`: DRAFT/SAVED 만 허용. null 필드는 보존. deliveryTag direction 호환 검증. 그 외 단계 호출 시 BusinessException(CONFLICT).
- `Slip.save()`: DRAFT → SAVED. 다른 상태에서 호출 시 CONFLICT.
- `Slip.send()`: SAVED → SENT. 다른 상태에서 호출 시 CONFLICT.
- `Slip.accept(acceptorUserId)`: SENT → ACCEPTED. acceptedBy / acceptedAt 기록. 다른 상태에서 호출 시 CONFLICT.
- `Slip.process()`: ACCEPTED → PROCESSING.
- `Slip.complete()`: PROCESSING → COMPLETED. completedAt 기록.
- `Slip.ship()`: COMPLETED → SHIPPING. **OUTBOUND 한정** — INBOUND 호출 시 CONFLICT.
- `Slip.deliver()`: SHIPPING → DELIVERED. OUTBOUND 한정.
- `Slip.confirm()`: 출고 DELIVERED → CONFIRMED / 입고 COMPLETED → CONFIRMED. confirmedAt 기록.
- `Slip.reject(reasonText)`: SENT 또는 ACCEPTED → REJECTED. reasonText 가 있으면 `[반려: ...]` 형식으로 memo 앞에 prepend.
- `Slip.cancel()`: DRAFT/SAVED/SENT → CANCELED. ACCEPTED 이상 단계는 CONFLICT (현 슬라이스 정책).
- `Slip.applyDeliveryTagAutoMemo()`: 야적/지방 등 `autoMemo=true` 태그면 `[태그명] MM/dd 상차 MM/dd 하차` 자동 메모 prepend. 그 외 no-op.
- `Slip.isEditable()` / `requireEditable()`: DRAFT/SAVED 단계 가드 헬퍼 (서비스 레이어 라인 mutation 사전 체크).
- 모든 잘못된 상태 전이 → `BusinessException(CONFLICT)` 통일.

#### SlipLine (전표 라인)
- `SlipLine.create(slip, productId, productName, modelName, quantity, unitPrice, note)`: 양수 수량 + 비음수 단가 검증 후 lineTotal 자동 계산 (HALF_UP scale 2).
- `SlipLine.changeQuantity(newQuantity)` / `changeUnitPrice(newUnitPrice)` / `changeNote(newNote)`: 부분 수정 mutator. 헤더 상태 가드는 서비스 레이어 책임. 양/단가 변경 시 lineTotal 재계산.

#### SlipNumberSequence (날짜별 채번 보조)
- `SlipNumberSequence.create(slipDate)`: lastSeq=0 의 신규 시퀀스.
- `SlipNumberSequence.next()`: lastSeq +1 후 반환. 동시 충돌은 `slips(slip_no) WHERE is_deleted=false` partial unique 인덱스로 백업.

#### Enum
- `SlipType`: OUTBOUND("출고전표") / INBOUND("입고전표"). 첫 슬라이스 한정 2종.
- `SlipStatus`: DRAFT / SAVED / SENT / ACCEPTED / PROCESSING / COMPLETED / SHIPPING / DELIVERED / CONFIRMED / REJECTED / CANCELED. 9 단계 + 분기 2.
- `DeliveryTag`: 11종 (Plan §3.3) — DAY/STACK/REGION/LOGEN/GYEONGDONG_PARCEL/GYEONGDONG_FREIGHT/RETURN_TRIP/RETURN/BORROW/RENTAL/RETURN_RENTAL. 각 멤버에 `displayName`, `direction`(SlipType), `autoMemo`(boolean). STACK/REGION 만 autoMemo=true.

### Service 메서드

#### SlipNumberService
- `SlipNumberService.next(slipDate)`: 시퀀스 조회 → 없으면 새로 생성 → next() → `yyyy/MM/dd-N` 포맷 반환. `@Transactional(REQUIRED)` — 호출자 트랜잭션 합류. 2026-05-16 D-AX21 이후 신규 코드는 `next(slipDate, slipType)` 로 날짜 + 전표유형별 독립 순번을 사용한다.
- `SlipNumberService.extractSeqNo(slipNo)`: 채번 결과 문자열에서 trailing 순번 정수 파싱 (Slip.seqNo 컬럼 채움용).

#### SlipService (orchestrator + Inventory 연계)
- `SlipService.create(req, requesterId)`: ProductClient.lookup 으로 라인 productId 일괄 검증 → SlipNumberService 채번 → slipType 분기로 createOutbound/createInbound → 라인 추가 (snapshot 명칭 보강) → applyDeliveryTagAutoMemo → 영속화. 라인 productId 미존재 시 INVALID_INPUT/NOT_FOUND.
- `SlipService.editHeader(id, req, callerId)`: DRAFT/SAVED 가드 (도메인 메서드 위임). null 필드는 보존.
- `SlipService.addLine(id, req, callerId)`: requireEditable + ProductClient.requireExists → addLine.
- `SlipService.removeLine(id, lineId, callerId)`: requireEditable + 라인 검색 → orphanRemoval.
- `SlipService.save/send/process/ship/deliver/confirm`: 도메인 메서드 단순 위임. 상태 전이 위반은 도메인이 CONFLICT 던짐.
- `SlipService.accept(id, acceptor)`: domain.accept → **OUTBOUND 면 라인별 inventoryClient.reserve(productId, sourceWarehouseId, quantity, "SLIP", slipId)** 호출.
- `SlipService.complete(id)`: domain.complete → OUTBOUND 면 라인별 deduct(fromReservation=true), INBOUND 면 라인별 inbound(unitCost=line.unitPrice).
- `SlipService.reject(id, callerId, reason)`: 직전 status 캐싱 → domain.reject → **이전 상태가 ACCEPTED 였고 OUTBOUND 면** 라인별 release.
- `SlipService.cancel(id, callerId)`: 도메인이 ACCEPTED 단계 cancel 을 거부하므로 OUTBOUND release 분기는 사실상 reject 경로로만 트리거됨. spec 일관성을 위해 release 분기 유지.
- `SlipService.getOne(id)` / `list(slipType, status, pageable)`: read-only 페이지 조회. 두 필터 조합 모두 지원.
- 트랜잭션 경계: 클래스 레벨 `@Transactional` (mutation), readOnly 만 명시. `applyMutation` 헬퍼가 OptimisticLock → CONFLICT, IllegalState → CONFLICT, IllegalArgument → INVALID_INPUT 매핑.

#### Client (RestClient + LoadBalanced + X-Internal-Token)
- `ProductClient.lookup(ids)` / `requireExists(id)`: inventory-service 의 ProductClient 패턴 그대로 (4xx → INVALID_INPUT, 5xx → INTERNAL_ERROR, 응답 부족 → NOT_FOUND).
- `InventoryClient.reserve(productId, warehouseId, quantity, refType, refId)`: POST `/inventory/reserve`. 4xx → CONFLICT, 5xx → INTERNAL_ERROR.
- `InventoryClient.release(...)`: POST `/inventory/release`. 동일 매핑.
- `InventoryClient.deduct(productId, warehouseId, quantity, fromReservation, refType, refId)`: POST `/inventory/deduct`. fromReservation 플래그를 body 에 포함.
- `InventoryClient.inbound(productId, warehouseId, quantity, lotNo, unitCost)`: POST `/inventory/lots/inbound` (입고전표 complete 시 호출). lotNo 는 보통 slipNo, unitCost 는 line.unitPrice.

### Controller (16 endpoint, `@RequestMapping("/slips")`)

| Method | Path | 권한 | Status |
|---|---|---|---|
| GET | `/slips` (status?, slipType?, page, size) | 인증 | 200, Page<SlipResponse> |
| GET | `/slips/{id}` | 인증 | 200, SlipDetailResponse |
| POST | `/slips` | SALES, MANAGER, MASTER | 201, SlipDetailResponse |
| PATCH | `/slips/{id}/header` | SALES, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/lines` | SALES, MANAGER, MASTER | 201 |
| DELETE | `/slips/{id}/lines/{lineId}` | SALES, MANAGER, MASTER | 204 |
| POST | `/slips/{id}/save` | SALES, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/send` | SALES, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/accept` | WAREHOUSE, INVENTORY, MANAGER, MASTER | 200 (Inventory reserve) |
| POST | `/slips/{id}/process` | WAREHOUSE, INVENTORY, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/complete` | WAREHOUSE, INVENTORY, MANAGER, MASTER | 200 (Inventory deduct/inbound) |
| POST | `/slips/{id}/ship` | WAREHOUSE, INVENTORY, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/deliver` | WAREHOUSE, INVENTORY, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/confirm` | ACCOUNTANT, MANAGER, MASTER | 200 |
| POST | `/slips/{id}/reject` (RejectRequest body) | MANAGER, MASTER | 200 (release if ACCEPTED) |
| POST | `/slips/{id}/cancel` | SALES, MANAGER, MASTER | 200 |

ApiResponse 래핑 → jsonPath `$.data.*`. 권한 부족 = 403, 잘못된 상태 전이 = 409, 미존재 = 404, validation 실패 = 400.

### 보안 + Internal Token
- `SecurityConfig`: HeaderAuthenticationFilter + permitAll(actuator, v3/api-docs, swagger-ui) + anyRequest authenticated.
- `InternalTokenGuard`: `@PostConstruct` 부팅 검증 — prod + dev 기본값이면 IllegalStateException.
- `InternalAuthProperties` (`app.security.internal.token`, env `INTERNAL_AUTH_TOKEN`).

### Flyway
- `V1__init_slip_service.sql`: slips, slip_lines, slip_number_sequences 3 테이블. partial unique on `slips(slip_no) WHERE is_deleted=FALSE`. version BIGINT NOT NULL DEFAULT 0. VARCHAR only. (시드 SQL 없음.)

### 단위 테스트 (51건 PASS)
- `SlipDomainTest` (22 tests): 출고/입고 생성 가드, 11 단계 전체 lifecycle, 잘못된 전이 CONFLICT, deliveryTag direction 호환, applyDeliveryTagAutoMemo 검증, SlipLine lineTotal 계산, SlipNumberSequence next() 증가.
- `SlipNumberServiceTest` (5 tests): 첫 호출 시 시퀀스 자동 생성, 동일 날짜 다중 호출 순번 증가, 다른 날짜 독립, extractSeqNo 파싱.
- `SlipServiceTest` (15 tests): create OUTBOUND/INBOUND, accept OUTBOUND 라인별 reserve / INBOUND no-reserve, complete deduct/inbound, reject from ACCEPTED → release / from SENT → no release, cancel from SAVED 성공 / from ACCEPTED CONFLICT, editHeader DRAFT 적용 / SENT CONFLICT, getOne NOT_FOUND.
- `InventoryClientTest` (9 tests): X-Internal-Token 헤더 송신, reserve/release/deduct/inbound 4종 path 검증, 4xx → CONFLICT, 5xx → INTERNAL_ERROR, 토큰 미설정 → INTERNAL_ERROR.

### 검증 결과
- `./gradlew :services:slip-service:test --no-daemon` → BUILD SUCCESSFUL, 51 tests PASS
- `./gradlew :services:slip-service:assemble :services:product-service:assemble :services:inventory-service:assemble --no-daemon` → BUILD SUCCESSFUL
- `./gradlew :services:slip-service:compileTestJava --no-daemon` → BUILD SUCCESSFUL

## FE (Team-Slip FE)

### 신규 컴포넌트 (3)

- **SlipStatusBadge** — 9단계 + 분기 2종 = 11종 상태 시각 구분 Badge.
  색상 규약: 편집 가능(DRAFT/SAVED/SENT) 회색~파란색 / 처리(ACCEPTED/PROCESSING/COMPLETED) 주황색 / 완료(SHIPPING/DELIVERED/CONFIRMED) 녹색 / REJECTED 빨강 / CANCELED 회색 + 취소선.
  같은 그룹 내에서도 진행도에 따라 채도 강도(tier-1/2/3) 점증.
  `showStep` prop 으로 단계 번호(1~9) 노출. 분기는 단계 번호 없음.
  **Storybook 7 stories** (AllStatuses / WithStep / DraftEditable / AcceptedLocked / RejectedFlow / Confirmed / Canceled).
- **DeliveryTagSelector** — 11종 배송태그 single-select.
  `direction` (OUTBOUND/INBOUND) 으로 옵션 자동 필터링 (출고 8종 / 입고 3종).
  autoMemo 태그 (야적/지방) 선택 시 inline `📝 자동 메모: {출고일} 상차 → {다음날} 하차` 미리보기 chip 표시.
  `slipDate` 제공 시 실제 일자, 미제공 시 placeholder 형태로 메모 렌더.
  `disabled` 로 수락 이후 단계 잠금 시뮬 가능. controlled (`value` + `onChange`).
  **Storybook 6 stories** (OutboundOptions / InboundOptions / StackSelected_AutoMemoPreview / WithError / Disabled / RegionWithoutDate).
- **SlipNumberDisplay** — `YYYY/MM/DD - {seq}` 표시 (Plan §3.1 표시 형식).
  monospace + tabular-nums 로 목록 자릿수 정렬 보장. `size` 3종 (sm 목록 / md 본문 / lg 헤더).
  optional `uuid` prop 제공 시 호버 tooltip 으로 디버깅용 UUID 노출 (일반 사용자 비공개 원칙).
  **Storybook 6 stories** (Default / SmallMediumLarge / WithUUID / LongSequence / TodaysSlip / TableColumnAlignment).

### 디자인 시스템 export

`@samhan/design-system` 루트 (`src/index.ts`) 에 추가 export:
- 컴포넌트: `SlipStatusBadge`, `DeliveryTagSelector`, `SlipNumberDisplay`
- 타입: `SlipStatus`, `SlipStatusBadgeProps`, `DeliveryTagCode`, `DeliveryTagOption`, `SlipDirection`, `DeliveryTagSelectorProps`, `SlipNumberDisplayProps`

### 의존성 변경

- 신규 NPM 의존성 0건. 기존 react 18 / vite 5 / typescript 5.6 / storybook 8 그대로 사용.

### 빌드 검증

| 명령 | 결과 |
| --- | --- |
| `npm run build` (tsc + vite + dts) | PASS — 50 modules, 30.31 kB JS / 19.69 kB CSS |
| `npm run lint` | PASS — 0 error 0 warning |
| `npm run build-storybook` | PASS — 신규 3개 스토리 산출 확인 |

## QA (Team-Slip QA)

### IT 클래스
- `AbstractPostgresIT` — 싱글턴 컨테이너 (`slip_db`, postgres:16-alpine), `DockerAvailableCondition` 으로 Docker 미가용 시 skip
- `SlipNumberServiceIT` — 같은 일자 atomic seq + 다른 일자 독립 시퀀스 (시나리오 2건)
- `SlipDomainIT` — 출고/입고 happy path + 잘못된 전이 4건 + applyDeliveryTagAutoMemo (시나리오 8건)
- `SlipControllerIT` — 권한 매트릭스 + Inventory mock verify (reserve / deduct / release) (시나리오 9건)
- `SlipLifecycleControllerIT` — 출고 풀 9단계 + 입고 ship 스킵 409 + slipNo 정규식 검증 (시나리오 3건)

총 IT 시나리오 22건. SlipControllerIT 가 직접 검증하는 핵심 권한 셀 7개. 나머지는 fixtures.http + 수동 시연으로 PM 통합 시 검증.

### 시나리오 fixtures
- `services/slip-service/src/test/resources/fixtures.http` — VS Code REST Client / IntelliJ HTTP Client 시나리오 8건 (출고 풀 라이프사이클 / 입고 라이프사이클 + ship 스킵 / reject_after_accept release / DRAFT accept 잘못된 전이 / 야적 자동 메모 등)

### QA 산출물
- `docs/qa/slip-service-report.md` — 권한 매트릭스 16 endpoint × 7-tier 전수 표 + 핵심 시나리오 8건 + 종합 판정 (PM 통합 시 PASS/FAIL 채움)
- `docs/qa/slip-first-slice/screenshots/README.md` — 캡처 예정 파일 10건 가이드

### 알려진 제약
- Docker 미가용 시 IT 자동 skip (`DockerAvailableCondition`)
- `InventoryClient` 는 `@MockBean` 으로 격리. **모든 메서드 void** 라 `Mockito.verify(...)` 호출 검증만 (PR #16 회고: void 가 아닌 메서드에 `doNothing()` 금지)

## DevOps (Team-Slip DevOps)

### 인프라 점검 결과 (사전 등록 확인 — 직접 수정 0건)

본 슬라이스의 모든 인프라 자원이 사전 등록돼 있어 **DevOps 영역 인프라 파일 직접 수정 불필요**:
- `infrastructure/postgres/init/01-create-databases.sql:9` — `slip_db` 등재
- `infrastructure/postgres/init/02-extensions.sql:21-23` — `slip_db` 의 uuid-ossp / pgcrypto 등재
- `services/api-gateway/src/main/resources/application.yml:38-44` — `/api/slips/**` → `lb://slip-service` 라우트 등재 (StripPrefix=1 + JwtAuthentication)
- `infrastructure/.env.example:31, 36` — `INTERNAL_AUTH_TOKEN` / `JWT_SECRET` 존재. `SLIP_SERVICE_PORT` 같은 신규 항목 불필요

### 검토 산출물
- `docs/devops/slip-service-review.md` — 인프라 점검 + 보안 가드 + CI 영향 + 모니터링/운영 + Phase 4 Accounting + Phase 5 Notification 권고 (전 7장)

### 핵심 발견 — inventory-service InternalTokenFilter 부재 (PM 통합 단계에서 보강)
- product-service / auth-service 에는 `InternalTokenFilter` 존재. inventory-service 에는 **부재** (`InternalTokenGuard` + `InternalAuthProperties` 만 존재)
- slip-service 가 `/inventory/reserve` 등 4 endpoint 를 gateway 우회 직접 호출하므로 servlet filter 보강 필수
- **PM 이 본 슬라이스 통합 시 보강** — product-service `InternalTokenFilter.java` 패턴 그대로 inventory-service 에 추가 + `SecurityConfig` filter chain 등록

### 후속 슬라이스 권고
1. ~~inventory-service InternalTokenFilter 추가~~ — PM 통합 단계에서 본 슬라이스 동봉 처리
2. HISTORY snapshot 테이블 + 출고일 변경 + UUID 추적 복원 (Slip 2nd slice — Plan §3.1)
3. 긴급 수정 요청 워크플로우 + 30분 타임아웃 락 해제 (Slip 3rd slice)
4. 입금/출금 전표 + AR 자동 생성 + 홈택스/오픈뱅킹 API (Phase 4 Accounting Service)
5. WebSocket 실시간 동기화 + Dashboard 위젯 (Phase 5 Notification + Dashboard)
6. JWT_SECRET 가드 (전 서비스 공통 — prod 부팅 시 dev default 거부 패턴)

## Plan 대비 의도적 변경

- **Q1=A STI** (Slip 1 테이블) — 첫 슬라이스 단순함 우선. 후속에서 sub 도메인 분리 검토 가능
- **Q2=A 라이프사이클별 Inventory 연계** — accept→reserve / complete→deduct(fromReservation) / reject_after_accept→release. cancel 분기는 도메인이 ACCEPTED 단계 거부하므로 release 분기는 reject 경로로만 트리거 (spec 일관성 유지를 위해 코드 분기는 보존, dead 코드 명시)
- **Q3=B 출고+입고만** — 입금/출금은 Phase 4 Accounting 함께. 이동전표는 inventory-service StockTransfer 가 이미 담당
- **Q4=A basic audit + @Version** — HISTORY 복원 + snapshot 은 Slip 2nd slice
- **Q5=B 낙관적 락 + 상태 전이 가드** — Redis 분산 락은 다중 인스턴스 운영 시점에
- **inventory-service InternalTokenFilter 보강** — DevOps 검토 단계에서 발견된 보안 갭 (slip→inventory gateway 우회 호출 시 servlet filter 부재). PM 이 본 슬라이스 통합 단계에서 product-service 패턴 그대로 추가
