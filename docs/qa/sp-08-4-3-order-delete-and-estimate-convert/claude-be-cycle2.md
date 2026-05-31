## backend-engineer 사이클 2 리뷰 (head `d6364d4b`)

### 사이클 1 BE 결함 해소 표

| ID | 항목 | 판정 |
|---|---|---|
| P1-1 | `softDeleteCascade(actorName)` caller 전달 + resolveActorName fallback | 해소 |
| P1-2 | FromEstimateService L47 dead code 제거 | 해소 |
| P1-3 | `pg_advisory_xact_lock(hashtext(?1))` confirm/from-estimate 양쪽 | 해소 |
| P1-4 | DeleteIT @MockBean EstimateClient | 해소 |
| Codex P1 신규 | SlipPublishStatus.NOT_REQUIRED + createFromEstimate 사용 | 해소 |
| P2-6 | recomputeTotal Javadoc 보강 | 해소 |
| P2-7 | FromEstimate IT source_estimate_id + due_date jdbcTemplate 단언 | 해소 |
| Nit-1 | HttpHeaderConstants 추출 + 3 controller | 해소 |
| Nit-2 | FromEstimateService import 정렬 | 해소 |

사이클 1 BE 9건 전원 해소.

### 사이클 2 신규 발견

**P2-1 (Medium) — `createFromEstimate` status 의미 오류**

`PartnerOrder.createFromEstimate` 가 private 생성자 경유로 `status = PartnerOrderStatus.CONFIRMING` 적용. `CONFIRMING` 은 설계 §3.6 상 "advisory lock 보유, DC/reserve/slip 발행 진행 중". 견적 변환은 slip 발행 예약 전 단계이므로 `DRAFT` 가 의미상 정확. IT `testFromEstimateSuccess` L95 도 `"CONFIRMING"` expect 하여 잘못된 상태 통과. outbox scheduler 가 `CONFIRMING` 필터 시 오동작 가능.

**P2-2 (Low) — `HttpHeaderConstants` X-User-Role 상수 누락**

`HeaderAuthenticationFilter` 가 `X-User-Id` + `X-User-Role` 쌍을 파싱하나, `HttpHeaderConstants` 에는 `CALLER_ID_HEADER` + `CALLER_NAME_HEADER` 만 선언. `X-User-Role` 이 Filter L25 리터럴 문자열로 남음. `CALLER_ROLE_HEADER` 추가 권장.

**P2-3 (Low) — `findBySourceEstimateId` soft-delete 중복 정책 미명시**

`PartnerOrderRepository.findBySourceEstimateId` 가 `@SQLRestriction("is_deleted = false")` 적용 엔티티 대상이므로, 동일 `estimateId` 변환 주문이 soft-delete 후 재호출 시 `ALREADY_CONVERTED` 통과하여 중복 주문 생성. 정책 문서 미명시. 의도된 동작이면 Javadoc 명시, 아니면 `isDeleted` 포함 native query 교체 필요.

### 종합

사이클 1 9건 전원 해소. P2-1 status 의미 오류 (Medium) + P2-2/P2-3 Low 2건. P2-1 운영 영향 가능 — **사이클 3 fix 필수**.

**사이클 3 필요.**

**backend-engineer agent — 2026-05-17**
