## Codex backend-engineer 사이클 2 리뷰 (head `d6364d4b`)

### Codex 사이클 1 자체 발견 추적
- BE-1~4: 사이클 1.5 수정 반영 확인.
- Codex P1 outbox 미연결: `createFromEstimate`가 `SlipPublishStatus.NOT_REQUIRED`로 전이되어 scheduler 대상(`findAllBySlipPublishStatus(PENDING_RETRY)`)에서 제외됨. 해결 확인.

### Claude BE 사이클 2 발견 평가
- P2-1 Medium: **VALID**. `createFromEstimate()`가 `new PartnerOrder(...)`를 재사용하면서 `status=CONFIRMING`, `confirmedAt=now`를 그대로 받습니다. 주석은 "confirm/outbox 흐름은 별도 사용자 확정 이후" 라고 되어 있고, `PartnerOrderStatus.CONFIRMING`은 advisory lock 기반 confirm 진행 중 의미. outbox 직접 오동작은 `NOT_REQUIRED`로 막혔지만, 상태/confirmedAt 의미가 틀어져 history/API에서 확정 진행 중 주문처럼 보일 수 있음.
- P2-2 Low: **VALID**. `HttpHeaderConstants`에 `X-User-Id`, `X-User-Name`만, `X-User-Role`은 `HeaderAuthenticationFilter` 등 문자열 하드코딩. 공통 상수 정합성 문제.
- P2-3 Low: **INVALID / non-blocking**. `@SQLRestriction`과 V6 partial unique index 모두 active row 기준 중복 차단. 서비스 주석도 "동일 estimateId는 active 주문 1건만 허용"으로 명시 — 정책 일치.

### Codex 신규 발견 (사이클 2)
- 독립 신규 없음. P2-1 수정 시 `confirmedAt`도 함께 정리해야 함.

### 종합
사이클 3 필요. P2-1 수정 권장, P2-2 같은 커밋에서 상수화하면 충분.

**Codex BE-agent — 2026-05-17**
