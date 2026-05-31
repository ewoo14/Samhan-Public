## Codex backend-engineer 사이클 1 리뷰 (head `97afca70`)

### Claude BE 발견 평가

- **P1-1 유효**: `PartnerOrderDeleteService`가 `actorName`을 받아 audit에는 실제 호출자명을 남기지만, `softDeleteCascade(DELETE_ACTOR)`로 `deleted_by`는 항상 `system-partner-order-delete`가 됩니다. `BaseEntity.markDeleted()`가 곧 `deleted_by`를 쓰므로 감사 추적 기준이 둘로 갈립니다.
- **P1-2 유효**: `createFromEstimate(UUID estimateId, ...)`는 path `estimateId`와 `snapshot.estimateId()`가 같다는 계약을 검증하지 않습니다. 잘못된 `EstimateClient` 구현이 다른 snapshot을 반환하면 `/from-estimate/A` 호출로 B 주문이 생성됩니다.
- **P1-3 유효**: `nextOrderNo()`는 `findAllByOrderNoStartingWith()` 후 max+1 계산만 합니다. 동시 요청이면 같은 주문번호가 생성되고 DB unique 충돌이 500으로 새어 나갈 수 있습니다.
- **P1-4 부분 유효, P2 권장**: `PartnerOrderDeleteIT`의 `EstimateClient @MockBean` 누락은 현재 fixture가 empty라 즉시 장애는 아닙니다. 다만 외부 client 격리 컨벤션 위반이므로 보강은 맞습니다.
- **P2-5 무효/과장**: Flyway V1의 `ux_partner_orders_idempotency_active`는 partial unique입니다. `@Column(unique=true)`는 매핑 오해를 만들 수 있으나, "full unique 때문에 soft-delete 후 재변환 500"은 현 migration 기준 재현되지 않습니다.
- **P2-6 유효**: `addLine()`이 total을 누적하고 `recomputeTotal()`을 다시 호출합니다. 현재 값은 맞지만 도메인 책임이 중복되어 후속 수정 시 회귀 위험이 큽니다.
- **P2-7 유효**: `FromEstimateIT`가 response만 보고 `source_estimate_id`, `due_date`, audit `FROM_ESTIMATE` 저장을 DB에서 직접 검증하지 않습니다.
- **Nit-1 유효**: `CALLER_*_HEADER`가 두 controller에 중복입니다.
- **Nit-2 유효**: `PartnerOrderFromEstimateService` import 순서가 정렬 규칙과 어긋납니다.

### Codex 신규 발견

- **P1 신규**: `PartnerOrderFromEstimateService`는 `PartnerOrder.createFromEstimate()`를 사용해 기본 `slipPublishStatus=PENDING_RETRY` 주문을 만들지만, `SlipPublishOutbox` row를 생성하지 않고 slip-service 호출도 하지 않습니다. `SlipPublishStatus.PENDING_RETRY`의 도메인 의미는 "5xx → outbox row INSERT"인데, 변환 주문은 retry 대상 없이 영구 pending 상태가 됩니다. 별도 "전표 발행 전" 상태가 필요하거나 confirm 흐름처럼 outbox/발행 경로를 연결해야 합니다.

### 종합

**사이클 2 필요**. Claude BE 9건 중 7건 유효, 1건 severity 조정, 1건 무효/과장입니다. Codex 신규 P1 1건 포함해 BE 차단 이슈는 actor audit 정합성, estimate snapshot 동일성 검증, 주문번호 동시성, from-estimate slip 상태 정합성입니다.

**Codex BE-agent — 2026-05-17**
