## backend-engineer 사이클 1 리뷰 (head `97afca70`)

### 결함 표 (P0/P1/P2/Nit)

| # | 심각도 | 파일 | 위치 | 설명 |
|---|---|---|---|---|
| BE-1 | P1 | `PartnerOrderDeleteService.java` | L49 | `softDeleteCascade` 에서 `markDeleted(DELETE_ACTOR)` 가 hard-coded `"system-partner-order-delete"` 로 저장되어 `deleted_by` 컬럼은 system 값. 그러나 audit log 의 `actorName` 은 caller (`X-User-Name`) 기반. 두 채널 간 일관성 없음 — 운영 추적 시 누가 삭제했는지 알기 어려움. `markDeleted(actorName)` 으로 caller 전달 필요. |
| BE-2 | P1 | `PartnerOrderFromEstimateService.java` | L40–48 | 중복 변환 방지 2중 체크 — `findBySourceEstimateId(estimateId)` (L40) + `findBySourceEstimateId(snapshot.estimateId())` (L47) 동일 path 2번 호출. 두 UUID 가 동일하다고 가정하나 EstimateClient 가 alias/redirect 처리 시 다를 가능성. 명시적 `Objects.requireNonNull` + `estimateId.equals(snapshot.estimateId())` 검증 또는 L47 dead code 제거 (QA P2-02 와 동일 결함). |
| BE-3 | P1 | `PartnerOrderFromEstimateService.java` | L83–89 | `nextOrderNo()` race condition — pessimistic lock 없는 SELECT 기반 채번. 고트래픽 동시 요청 시 같은 max seq 읽고 같은 orderNo 생성 → unique constraint 위반(500). SP-08-4-2 confirm 흐름과 동일 패턴이면 기존 결함 복제. DB sequence 또는 `SELECT ... FOR UPDATE SKIP LOCKED` 필요. |
| BE-4 | P1 | `PartnerOrderDeleteIT.java` | L57–69 | `EstimateClient` `@MockBean` 누락. 다른 외부 client (DcConfigClient, ProductClient 등) 모두 `@MockBean` 처리됐는데 EstimateClient 만 빠짐. 향후 RestClient 구현 교체 시 즉시 깨짐. 일관성 결함. |
| BE-5 | P2 | `PartnerOrderFromEstimateService.java` | L51–58 | `idempotency_key = "PO-EST-" + estimateId` 인데 `idempotency_key` 컬럼은 **full unique** (partial 아님). soft-delete 후 재변환 시도 시 `source_estimate_id` partial index 는 통과해도 `idempotency_key` 전체 unique 충돌 500. V6 migration 미고려. |
| BE-6 | P2 | `PartnerOrderFromEstimateService.java` | L70 | `recomputeTotal()` 이 `addLine()` 이후 호출. `addLine()` 이 이미 `totalAmount += subtotal` 누적 → `recomputeTotal()` 이 다시 전체 합산. 결과는 정확하나 이중 책임. 주석 또는 내부 초기화 전용 메서드 분리 필요. |
| BE-7 | P2 | `PartnerOrderFromEstimateIT.java` | `testFromEstimateSuccess` | `$.data.orderNumber.exists()` 만 검증, `source_estimate_id` DB 저장 + audit log `FROM_ESTIMATE` 필드 직접 단언 없음. delete IT 의 jdbcTemplate 직접 조회 패턴 적용 권장. |
| BE-Nit-1 | Nit | `PartnerOrderDeleteController.java` / `PartnerOrderFromEstimateController.java` | L26 | `CALLER_NAME_HEADER = "X-User-Name"` 상수 양쪽 controller 에 별도 선언. 공통 `HttpHeaderConstants` 추출 권장. |
| BE-Nit-2 | Nit | `PartnerOrderFromEstimateService.java` | L14-15 | `DateTimeParseException` / `DateTimeFormatter` import 순서 알파벳 역순. Checkstyle 확인. |

### 긍정 사항

- `softDeleteCascade` markDeleted + `deletedAt == null` 가드로 이중 처리 방지 (SP-08-4-2 패턴 계승)
- PartnerOrderLine `@SQLRestriction("is_deleted = false")` cascade soft-delete 후 자동 필터
- V6 partial unique index (`WHERE is_deleted = FALSE AND source_estimate_id IS NOT NULL`) 정합
- `EstimateClient` @MockBean 격리 (FromEstimateIT)
- FixtureEstimateClient `Optional.empty()` no-op fallback 안전
- ErrorCode → HTTP status 매핑 정확 (422/409/404/403)

### 종합

사이클 2 필요. P1 4건 + P2 3건 + Nit 2건 사이클 1.5 일괄 fix.

**backend-engineer agent — 2026-05-17**
