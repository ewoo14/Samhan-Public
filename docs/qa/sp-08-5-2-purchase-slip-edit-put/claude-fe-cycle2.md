## frontend-engineer 사이클 2 재리뷰 (head `2dbc84c3`)

### Codex 2c fix 평가

| 항목 | Claude 평가 |
|---|---|
| C3 addPurchaseLine 제거 | OK — 함수 + "행 추가" 버튼 양쪽 완전 제거. `removePurchaseLine` + `×` 정상 잔존. Playwright `not.toContain('addPurchaseLine')` 추가 |
| C4 TS 토큰 mirror | OK — success/warning/danger 3 scale. `semantic.*` 단일 alias 중복 없음. 기존 소비자 (`colors.semantic.warning`) breaking 없음. desktop/mobile-staff `colors.warning/danger` 직접 참조 없음 |
| C-N2 setPurchaseIsConflict reset | OK — onSuccess + modal open onClick 양쪽 reset |
| C-N3 inline style 이동 | OK — `purchase-edit-memo`/`purchase-edit-lines` CSS 클래스 이동. 레이아웃 동등 |
| C-N4 .td-right scope | OK — `.slip-line-table .td-right` scope 강화. 단일 사용처 정상 적용 |

### Claude 재리뷰 신규 발견

- **Nit-1**: submit 버튼 `disabled` 조건 `purchaseEditLines.length === 0` — 사용자가 모든 `×` 제거 시 저장 비활성화. 인라인 안내 문구 "라인을 1개 이상 입력해 주세요" 부재. UX 권고, 머지 blocker 아님.
- **Nit-2**: `success` 토큰 `100/300/400/600` step 누락. `warning/danger` `100/400/600` 누락. `brand/neutral` 전 step 완비 대비 sparse. Tailwind 연동 시 utility 누락 risk — 별도 design-system 정비 이슈 추적 권장.

### 종합

**APPROVE** — 5 fix 의도대로 구현, 회귀 없음. Nit 2건만, 머지 가능.

**frontend-engineer agent — 2026-05-18**
