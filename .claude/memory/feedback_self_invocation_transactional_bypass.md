---
name: self-invocation-transactional-bypass
description: 같은 빈 내 self-invocation 은 @Transactional 프록시 우회 → pessimistic 락/flush 가 트랜잭션 에러. @Lazy self-reference 로 프록시 경유
metadata:
  type: feedback
---
2026-06-16 PR #489(combo kind) 회고. **HTTP 트리거 시에만** 터지는 구조적 버그를 라이브가 적발.

`ProductSheetSyncService.syncAll()`(@Transactional 아님)이 `this.syncComponentTab(cm)` 으로 **self-invocation** 호출 → Spring AOP 프록시 우회로 `syncComponentTab` 의 `@Transactional` **미적용** → 내부 `findByIdForUpdate`(PESSIMISTIC_WRITE 락)가 활성 트랜잭션 요구 → `TransactionRequiredException: Query requires transaction be in progress` → 구성품 sync 전체 실패.

**왜 IT 가 못 잡았나**: IT 는 클래스 레벨 `@Transactional` 이라 호출 시 항상 ambient 트랜잭션 존재 → self-invocation 이어도 락이 통과(버그 미발현). 즉 **IT green ≠ 운영 정상**. HTTP 요청(OSIV=EntityManager 있으나 트랜잭션 없음)에서만 발현. 라이브 재sync(POST /sync) 가 단독 적발.

**How to apply**:
- 같은 빈 안에서 `@Transactional`(특히 pessimistic 락/`flush`/`@Modifying` 쓰는) 메서드를 self-invocation 하지 말 것. `@Lazy ProductSheetSyncService self` 자기주입 후 `self.method()` 로 **프록시 경유** 호출(순환의존은 @Lazy 로 해소, 표준 패턴).
- pessimistic 락/flush 없는 self-invoked @Transactional(예 syncTab — save 만)은 각 repo save 자체 트랜잭션 auto-commit 이라 동작은 하나(원자성만 손실), 락 쓰는 메서드는 **반드시** 트랜잭션 필요.
- 트랜잭션 의존 코드 검증은 **HTTP 트리거 실 경로**로(IT 클래스-@Transactional 가림 주의). 관련 [[spec-sync-full-db-distribution-check]] [[standalone-boot-real-qa]].
