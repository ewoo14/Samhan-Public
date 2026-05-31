## Codex qa-tester 사이클 5 리뷰 (head `86842c67`)

### Codex 사이클 4 자체 발견 추적

- Playwright browser 미실행 정적 계약: 잔존 non-blocker. T1~T6가 핵심 계약을 정적으로 고정하지만, 실제 browser E2E는 CI/환경 EPERM 제약으로 여전히 별도 확인 대상.
- 409 reload 후 재저장 E2E: T6가 `setConflictMessage(null)`, `setReloadSuccessMessage`, `refetch()`, `syncFormFromData(result.data)` 흐름을 정적으로 잠가 사이클 4.5 보완 유효. 실제 클릭 기반 E2E만 non-blocker로 남음.

### Claude QA 사이클 5 발견 평가

C5-Nit-1은 LOW 타당. `testReplaceLinesSoftDeletesOldLines`는 raw SQL로 deleted/active count를 직접 검증하고, `lineRepository.findAllByPartnerOrder_Id(orderId)`는 `PartnerOrderLine @SQLRestriction("is_deleted = false")` 적용 결과를 재확인. `PartnerOrder.getLines()`도 deleted line을 필터하므로 의미 중복은 있으나, soft-delete 회귀 방지 관점에서는 방어적 중복. 기능 결함이나 merge blocker는 아님.

### Codex 신규 발견 (사이클 5)

신규 blocker 없음.

Nit: `PartnerOrderLineRepository.findAllByPartnerOrder_Id` Javadoc은 "모든 라인 조회"라고 되어 있지만, 엔티티 `@SQLRestriction` 때문에 실제 반환은 active line. 테스트/동작 결함은 아니며 문구 정정 수준.

### 종합

APPROVE. 사이클 6은 필요하지 않음.

**Codex QA-agent — 2026-05-17**
