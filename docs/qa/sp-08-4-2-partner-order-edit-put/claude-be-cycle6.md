## backend-engineer 사이클 6 리뷰 (head `bb28b2e6`)

### 사이클 5 BE 결함 해소 표

| 항목 | 내용 | 상태 |
|---|---|---|
| Nit-1 `replaceLines` Javadoc 보강 | `deletedAt == null` 가드 의도 + `markDeleted` 가 `isDeleted=true` / `deletedAt` 동시 세팅 명시 | **RESOLVED** — `PartnerOrder.java` L193-203 확인. orphanRemoval=false 설계 근거·SQLRestriction 정합·재처리 방지 가드 세 항목 모두 Javadoc에 기재됨 |
| Codex QA Nit `findAllByPartnerOrder_Id` Javadoc | "soft-deleted 포함 전체" → "active 라인만" 정정 | **RESOLVED** — `PartnerOrderLineRepository.java` L11: `@SQLRestriction("is_deleted = false") 자동 적용` 명시 확인 |
| skip — BE Nit-2 `@WithMockUser` (Codex invalid) | 검증 대상 외 | — |
| skip — QA C5-Nit-1 방어적 중복 | BE Nit-1 Javadoc 보강으로 자연 해소 | — |

### 사이클 6 신규 발견

IT 9 case 전수 점검 결과 회귀 없음.

- `testReplaceLinesSoftDeletesOldLines` — `deleted_at IS NOT NULL` AND `is_deleted = TRUE` 동시 쿼리로 `markDeleted` 양쪽 필드 세팅 검증.
- `testConcurrentUpdateRejectsStaleVersion` — `@Version` optimistic lock 직접 DB-level 검증.
- `testVerifyVersionAllowsFirstUpdateWhenModifiedAtIsNull` — `modifiedAt=NULL` 첫 수정 허용 + 오래된 타임스탬프 409.

신규 BE 결함 없음.

### 종합

**APPROVE** — 사이클 5.5 fix 두 항목 모두 설계 의도와 정합. Javadoc-only 변경, IT 9 case 회귀 없음. 사이클 7 불필요.

**backend-engineer agent — 2026-05-17**
