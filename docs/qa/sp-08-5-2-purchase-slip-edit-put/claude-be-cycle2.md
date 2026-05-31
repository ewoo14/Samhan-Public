## backend-engineer 사이클 2 재리뷰 (head `2dbc84c3`)

### Codex 2c fix 평가

| 항목 | Claude 평가 |
|---|---|
| C1 LineRequest Bean Validation 제거 | OK — `validateLines()` `BusinessException(SLIP_UPDATE_INVALID_LINE)` 422 계약 보존. IT `testUpdateInvalidLineReturns422` quantity=0 422 정합 |
| C2 INBOUND check 선행 ordering | OK — OUTBOUND DRAFT 슬립도 즉시 403. `requireEditable()` 는 INBOUND + 비편집 시에만 CONFLICT. 의미 분리 명확. IT `testUpdateNonInboundForbidden` 정합 |
| C2 회귀 risk (다른 도메인 메서드 경로) | OK — `updateHeader`/`replaceLines` direct PUT 전용. `editHeader`/`addLine`/`removeLine` 미변경. 회귀 없음 |
| IT `testUpdateNonInboundForbidden` 403 정합 | OK — OUTBOUND DRAFT, C2 후 INBOUND guard 선행 FORBIDDEN 확정 |
| IT `testUpdateInvalidLineReturns422` 422 계약 | OK — Bean Validation 제거 후 `validateLines` 동등 제약 |

### Claude 재리뷰 신규 발견

없음. 점검 포인트:
- `SlipUpdateController` `@Valid @RequestBody` 유지 — top-level `updatedAt @NotNull`, `lines @NotEmpty` 정상 동작
- `SlipUpdateService.update()` `validateLines` try 외부 — `BusinessException` 낙관적 잠금 catch 삼킴 차단
- IT mock 격리 `InventoryClient/ProductClient/NotificationClient/PartnerInternalClient/PartnerBlockClient` 5종 `@MockBean` lenient
- `summarizeLines` `replaceLines` clear() 후 saveAndFlush 결과 기준 `after` 캡처 — audit diff 정확성 유지

### 종합

**APPROVE** — P0/P1 잔존 없음. C1+C2 의도 부합 + IT 정합. 사이클 3 불필요. 머지 가능.

**backend-engineer agent — 2026-05-18**
