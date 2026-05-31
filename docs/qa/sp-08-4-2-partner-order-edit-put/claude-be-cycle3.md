## backend-engineer 사이클 3 리뷰 (head `232c5637`)

### 사이클 2 BE 결함 해소 표

| # | 사이클 2 발견 | 해소 여부 | 근거 |
|---|---|---|---|
| BE-1 | @Version 없이 modifiedAt 수동 비교만 → race | **FIXED** | `PartnerOrder.java:94-96` `@Version Long lockVersion` 선언 확인. `V5__add_partner_order_lock_version.sql` `BIGINT NOT NULL DEFAULT 0` backfill 정합. `PartnerOrderUpdateService.java:68` `OptimisticLockException | OptimisticLockingFailureException` 양쪽 catch → 409 매핑 확인. |
| BE-2 | replaceLines lines.clear() 물리 삭제 → Soft Delete 위반 | **FIXED** | `PartnerOrder.java:202-205` 기존 active line 에 `line.markDeleted("system-partner-order-update")` 호출. `BaseEntity.markDeleted` 가 `isDeleted=true`, `deletedAt`, `deletedBy` 모두 세팅. orphanRemoval=true 는 유지되나 실제 삭제 대상이 없어 DB 에 물리 delete 발생하지 않음. |
| BE-3 | IdResolver RuntimeException catch 범위 과다 | **PARTIAL** | `PartnerOrderIdResolver.java:58` `catch (RuntimeException ignored)` — UUID.fromString 실패 시 `IllegalArgumentException` 만 발생하므로 실질 위험은 낮음. 그러나 Repository `findById` 내부에서 DB 예외가 발생해도 묻힐 수 있어 범위 축소 권고는 여전히 유효. |
| BE-4 | 동시 stale + soft delete IT 부재 | **FIXED** | `PartnerOrderUpdateIT.java:208` `testConcurrentUpdateRejectsStaleVersion` — detach 후 copyA 저장 → copyB flush 시 `ObjectOptimisticLockingFailureException` 기대. `:232` `testReplaceLinesSoftDeletesOldLines` — PUT 후 JDBC 직접 조회로 `is_deleted=TRUE && deleted_at IS NOT NULL` count 검증. |

### 사이클 3 신규 발견

| 우선순위 | 위치 | 내용 |
|---|---|---|
| P1 | `PartnerOrder.java:102`, `replaceLines:208-210` | `@OneToMany(orphanRemoval = true)` + `@SQLRestriction("is_deleted = false")` 조합: `replaceLines` 에서 markDeleted 처리한 기존 라인은 `getLines()` 필터 덕분에 외부에서 보이지 않지만, `this.lines` 내부 컬렉션에는 여전히 잔류함. `orphanRemoval` 은 컬렉션에서 remove 되었을 때 삭제를 수행하는데, markDeleted 된 행은 컬렉션에서 제거되지 않으므로 orphanRemoval 이 실질적으로 작동하지 않는다. 의도 자체는 soft-delete 이므로 비정합은 아니지만, `orphanRemoval = true` 선언이 오해를 유발한다. soft-delete 전략이라면 `orphanRemoval = false` 로 변경하고 Javadoc 에 이유를 명시해야 한다. |
| P2 | `PartnerOrderUpdateService.java:53`, `verifyVersion:80-84` | `verifyVersion` 에서 `modifiedAt == null` 이면 무조건 409 를 반환한다. 신규 주문은 첫 수정 전까지 `modifiedAt` 이 null 일 수 있어 합법적 수정이 거부될 가능성이 있다. `createdAt` 기준으로 fallback 하거나, `modifiedAt` null 일 때 `requestUpdatedAt` 도 null 이어야 통과하는 명시적 정책이 필요하다. |
| Nit | `PartnerOrderUpdateService.java:63-66` | `saveAndFlush` 후 `auditLogService.recordBatch`, 이어서 `partnerOrderRepository.flush()` 재호출. audit log INSERT 전후로 불필요한 flush 가 중복 발생한다. `saveAndFlush` 한 번이면 충분하고 두 번째 `flush()` 는 제거 가능. |

### 긍정 사항

- `@Version` + `modifiedAt` 이중 검증 레이어가 의도적으로 분리 배치되어 낙관적 락 실패 시 사용자 메시지(modifiedAt 불일치)와 DB 레벨 lock(version 충돌) 양쪽에서 race 를 방어한다.
- `recomputeTotal()` 이 `deletedAt != null` 라인을 명시적으로 skip 하여 soft-delete 후 합계 오염이 없다.
- IT 에서 `@MockBean` 외부 클라이언트 7종 전체 격리, JDBC 직접 조회로 soft-delete 컬럼 값까지 검증하는 방식이 컨벤션을 충실히 따른다.
- Flyway V5 migration 이 기존 row 에 `DEFAULT 0` 을 주어 legacy 호환 조건(NULLable 또는 default)을 만족한다.

### 종합

P1 (orphanRemoval 의미 불일치) 과 P2 (modifiedAt null → 409 오진) 수정 후 재검토 권고.

**사이클 4 필요**

**backend-engineer agent — 2026-05-17**
