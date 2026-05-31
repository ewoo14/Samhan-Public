## Codex backend-engineer 사이클 3 리뷰 (head `232c5637`)

### Codex 사이클 2 자체 발견 추적

| Codex 사이클 2 발견 | 사이클 2.5 fix 결과 |
|---|---|
| P2-1: `@Version` 부재 race | FIXED: `PartnerOrder.lockVersion` + `V5__add_partner_order_lock_version.sql` 추가, stale detached save IT 포함 |
| P2-2: `replaceLines` hard delete 위험 | FIXED: 기존 active line은 `markDeleted(...)`, 신규 snapshot만 append. soft delete row 검증 IT 포함 |

### Claude BE 사이클 3 발견 평가

| Claude 발견 | Codex 평가 | 사유 |
|---|---|---|
| P1: `orphanRemoval=true`와 soft delete 의미 충돌 | invalid | 현재 `replaceLines`는 컬렉션에서 기존 line을 제거하지 않고 `markDeleted`만 호출한다. orphan removal은 현 경로에서 hard delete를 유발하지 않는다. 다만 주석의 "orphanRemoval로 제거" 표현은 혼동 소지 있음 |
| P2: `verifyVersion`의 `modifiedAt == null` 거짓 양성 | valid | `modified_at` 컬럼은 nullable이고, null row는 클라이언트가 유효한 `updatedAt`을 만들 수 없어 첫 수정이 409로 막힌다. `modifiedAt != null` invariant를 migration/backfill로 보장하거나 `createdAt` fallback 필요 |
| Nit: `findByUuid`의 `RuntimeException` catch | valid | UUID parse 실패만 삼켜야 한다. 현재는 `repository.findById`의 런타임 DB/infra 예외까지 404로 숨길 수 있음 |

### Codex 신규 발견 (사이클 3)

결함 0건. Claude P2/Nit 외 추가 backend blocker는 없음.

### 긍정 사항

`@Version`과 `saveAndFlush`/flush 경계가 추가되어 실제 동시 갱신은 DB 레벨에서도 방어된다. 라인 교체도 active 조회와 soft delete 검증이 맞물려 기존 사용자 노출 조건을 보존한다.

### 종합

사이클 4 필요

**Codex BE-agent — 2026-05-17**
