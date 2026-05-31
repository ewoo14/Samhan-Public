## Codex backend-engineer 사이클 2 리뷰 (head `67791758`)

### Claude BE 사이클 2 발견 평가

| Claude 발견 | 우선순위 | Codex 평가 | 사유 |
|---|---|---|---|
| `verifyVersion` null 분기 | P2 | invalid | `PartnerOrderUpdateRequest.updatedAt`는 `@NotNull`이고, persisted `PartnerOrder.modifiedAt` 기준 비교 계약입니다. null이면 stale/비정상 데이터로 409 처리하는 현재 방어가 더 보수적입니다. |
| `recordBatch` propagation 주석 | P3 | over-engineering | `update()`와 `recordBatch()` 모두 기본 `@Transactional(REQUIRED)`라 같은 트랜잭션에 참여합니다. overload Javadoc도 같은 트랜잭션 호출자 의도를 이미 설명합니다. |
| `IdResolver` catch 범위 | P3 | valid | `findByUuid()`가 `RuntimeException` 전체를 삼켜 valid UUID의 repository/DB 예외까지 404로 위장할 수 있습니다. `UUID.fromString`의 `IllegalArgumentException`만 잡는 편이 맞습니다. |

### Codex 신규 발견

| # | 우선순위 | 위치 | 내용 |
|---|---|---|---|
| 1 | P2 | `PartnerOrderUpdateService.java:51` | `modifiedAt` 수동 비교만 있고 `@Version`/조건부 update가 없어, 동일 `updatedAt`으로 동시에 진입한 두 PUT이 모두 통과할 수 있습니다. 마지막 커밋이 앞선 변경을 덮어써 "optimistic lock" 계약이 실제 동시성에서는 깨집니다. |
| 2 | P2 | `PartnerOrder.java:97`, `PartnerOrder.java:190` | `replaceLines()`가 `orphanRemoval=true` 컬렉션을 `clear()`하여 기존 `PartnerOrderLine`을 물리 삭제합니다. 라인도 `BaseEntity`/`@SQLRestriction` 대상이므로 repo 컨벤션의 Soft Delete only와 충돌합니다. |

### 긍정 사항
- ErrorCode 매핑은 404/409/422 계약과 IT 기대값이 일치합니다.
- `saveAndFlush` 후 audit 기록, audit 후 repository flush 흐름은 dirty checking 관점에서 revision_count 반영 가능성이 확보됩니다.
- audit log FK 미강제는 soft-deleted 주문의 감사 보존 의도와 일치합니다.
- IT가 외부 client `@MockBean`과 outbox 선 cleanup을 포함해 Windows/Testcontainers 회귀 포인트를 잘 막고 있습니다.

### 종합
사이클 3 필요 — direct PUT의 핵심 계약이 "낙관적 잠금 + 감사 보존"인데, 실제 동시성 경합과 라인 hard delete가 그 계약을 약화합니다. 두 P2 수정 후 `PartnerOrderUpdateIT`에 동시 stale 방어 또는 라인 보존 검증을 보강하면 승인 가능해 보입니다.

**Codex BE-agent — 2026-05-17**
