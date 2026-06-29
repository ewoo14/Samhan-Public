---
name: feedback_aborted_tx_after_div_catch
description: catch(DataIntegrityViolationException) 후 같은 @Transactional 안에서 재조회/추가 SQL = PostgreSQL aborted-tx 로 무효. REQUIRES_NEW 격리 또는 (직렬화 시) catch 제거.
metadata:
  type: feedback
---

PostgreSQL 은 제약 위반(unique/check 등) 발생 시 **현재 트랜잭션 전체를 abort** 한다. 그 뒤 같은 트랜잭션에서 실행하는 SQL 은 `current transaction is aborted, commands ignored until end of transaction block` 로 실패한다. 따라서 **`catch (DataIntegrityViolationException)` 안에서 같은 `@Transactional` 의 재조회·복구 쿼리를 돌리는 패턴은 실 충돌에서 동작하지 않는다**(겉보기엔 복구 코드지만 무효).

**Why:** #668(CODEF `CodefConnectionService.saveConnection`)에서 적발. `saveAndFlush` 위반을 잡고 같은 tx 에서 `activeConnectionOptional()` 재조회 → aborted-tx 로 실패. Codex 순차 듀얼리뷰가 Opus 미적발 BLOCKING 으로 단독 적발했고, CI green·단위테스트로는 안 드러났다(실 충돌 경로 미발생 = dead 코드).

**How to apply:**
- 충돌을 복구/재시도해야 하면 **별도 트랜잭션으로 격리**: `@Transactional(propagation = REQUIRES_NEW)` 메서드 또는 `TransactionTemplate(REQUIRES_NEW)` 로 save 를 감싸고, 실패 시 바깥(미-abort) 트랜잭션에서 재조회/재시도. (accounting-service 정상 사례: `UserCodefImportScopeService.upsert`, `CodefImportService.saveInNewTransaction`, `Purchase/SalesAccountingSlipService.createDraft`.)
- 동시성을 **다른 수단으로 직렬화**(예: `pg_advisory_xact_lock`)해서 위반 자체가 안 나는 구조면, catch-복구는 **도달 불가 dead 코드** → 제거하고 위반은 그대로 전파(정직한 에러)가 옳다. (#668 채택안.)
- catch 후 같은 tx 에서 **추가 SQL 없이 즉시 rethrow** 는 안전. 금지되는 건 **catch 후 같은 tx 재조회/재기록**.
- 리뷰 시 `catch (DataIntegrityViolationException` 전수 grep → 각 catch 가 같은 tx 에서 추가 SQL 을 돌리는지 점검(family sweep). [[feedback_defect_family_sweep_fix]] [[feedback_self_invocation_transactional_bypass]] [[feedback_qa_docker_real_test]]
