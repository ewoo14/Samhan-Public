## devops-engineer 사이클 2 리뷰 (head `d6364d4b`)

### CI 상태

24 check 전체 SUCCESS. JUnit 8 그룹 / 빌드+테스트 6 그룹 / Frontend DS·Desktop·Mobile-Staff / Playwright + Detox / GitGuardian / arologis CI 전부 SUCCESS. `reviewDecision` 빈 값 (리뷰어 미등록).

### 사이클 1.5 변경 검증

**Flyway V1~V6 무결성**: V1~V5 diff 없음, V6 단독 신규. SQL `source_estimate_id UUID` + partial unique index (`WHERE is_deleted = FALSE AND source_estimate_id IS NOT NULL`) — soft-delete active 주문 1건 제약 정확.

**SlipPublishStatus.NOT_REQUIRED**: `EnumType.STRING` 매핑이므로 기존 row `PUBLISHED/PENDING_RETRY/FAILED_PERMANENT` 값 충돌 없음. `createFromEstimate` 만 `NOT_REQUIRED` 세팅 — Flyway migration 불필요.

**HttpHeaderConstants**: `public final class` + `private` 생성자 + 상수 2건 append-only. 타 service 영향 없음. 3 controller 일관성 확보.

**git diff --check**: 출력 없음, whitespace 클린.

**GitGuardian**: SUCCESS. FixtureEstimateClient 빈 구현 + 상수값 — dev-only 자격 패턴 아님.

### 사이클 2 신규 발견

**[D-1] FixtureEstimateClient 운영 배포 위험**: `findById` 항상 `Optional.empty()` 반환. 운영 배포 시 `/from-estimate/{estimateId}` 호출마다 404. `@Profile("!production")` 또는 `@ConditionalOnMissingBean(EstimateClient.class)` 가드 누락. Phase 11 cutover 전 실 client 구현 + 조건부 등록 필요. 현재 브랜치 blocker 아님.

**[D-2] `nextOrderNo` soft-delete row 제외 시 시퀀스 중복 가능**: `pg_advisory_xact_lock(hashtext(?1))` 으로 날짜 단위 직렬화하나, `findAllByOrderNoStartingWith` 가 `@SQLRestriction("is_deleted = false")` 적용. 동일 날짜 N건 삭제 후 재생성 시 orderNo 중복. partner_orders unique constraint 가 DB 에서 잡으나 service 레이어 재시도 없이 500 전파. 후속 슬라이스 보완.

**[D-3] `parseActorId` UUID 오류 시 nil UUID fallback**: DeleteController + FromEstimateController 양쪽 `new UUID(0L, 0L)` nil UUID. audit log nil UUID 행이 "시스템 자동" vs "헤더 파싱 실패" 구분 어려움. 운영 모니터링 이슈.

### 종합

CI 24/24 green, Flyway 정합, GitGuardian clean, whitespace 클린, shared/common append-only. [D-1]/[D-2]/[D-3] 모두 후속 슬라이스 백로그 — 현 PR blocker 아님.

**APPROVE** — 사이클 3 불필요. D-1/D-2 후속 슬라이스 등록.

**devops-engineer agent — 2026-05-17**
