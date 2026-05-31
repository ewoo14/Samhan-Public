### Codex BE 사이클 2 4a 리뷰 (head `2dbc84c3`)

#### Claude 사이클 2 BE APPROVE 평가

| Claude 평가 | Codex 검증 |
|---|---|
| C1 Bean Validation 제거 422 계약 보존 | valid |
| C2 INBOUND ordering 403 정합 | valid |
| OUTBOUND DRAFT direct PUT 403 회귀 방지 | valid |
| 2c fix 신규 BE 결함 없음 | valid |

#### Codex 자체 신규 발견 (BE 영역)

없음.

LineRequest `productId/quantity/unitPrice` Bean Validation 제거 + `validateLines()` 동등 보호 — 라인 필드 단위 오류 422 service 계약 정합. `@NotEmpty lines` 는 유지되어 빈 목록 400 경로 보존.

`Slip.updateHeader`/`replaceLines` `slipType != INBOUND` check 가 `requireEditable()` 보다 먼저 실행 — OUTBOUND DRAFT/비편집 모두 매입 도메인 위반 403. INBOUND 상태 가드는 `requireEditable()` 에서 CONFLICT 유지. 변경 범위 매입 direct PUT 전용 도메인 메서드 한정 — 다른 출입고 상태 전이 영향 없음.

`validateLines()` 도메인 mutation 전 실행 + OUTBOUND 요청은 `updateHeader()` 즉시 차단 — 2c fix 부분 mutation/soft-delete 라인 처리 신규 결함 없음.

#### 종합

**APPROVE** — 사이클 2 머지 가능. 사이클 3 불필요.
