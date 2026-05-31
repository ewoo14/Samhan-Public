## frontend-engineer 사이클 1 리뷰 (head `7cbbd13b`)

### 결함 표

| # | 심각도 | 위치 | 내용 |
|---|---|---|---|
| F-01 | Major | `SlipDetailPage.tsx:2024` | 409 배너 `className="error-banner"` — global.css 에 SP-08-5-3 신규 `.danger-banner` 정의 별도. JSX 와 CSS 불일치. `.danger-banner` 로 통일 또는 정의 제거 |
| F-02 | Minor | `SlipDetailPage.tsx:592-595` | `canDirectDeletePurchase` SAVED/DRAFT — `canDirectEditPurchase` 와 동일. BE 가 SENT 이후 삭제 허용 시 가드 과도. 계약 재확인 |
| F-03 | Minor | `SlipDetailPage.tsx:1997` | 확인 버튼 `onClick={() => mutate()}` `isPending` 가드 disabled 만. onClose isPending 가드와 일관 위해 early-return 방어 권장 |

### 긍정 사항

- `deletePurchaseSlip` `{ data: { updatedAt } }` body 낙관적 잠금 + JSDoc 409/422/403 명세
- `purchaseDeleteConflict` boolean → 배너 → "최신 내용 불러오기" refetch — SP-08-5-2 `purchaseIsConflict` 패턴 일관
- data-testid 4종 (`purchase-slip-delete-button/confirm/confirm-yes/confirm-no`)
- UUID 비공개 — modal `slip.slipNo` 만, `id` 미노출
- onSuccess invalidate `['slips','query','INBOUND']` + `['slips']` + navigate `/purchases`

### 종합

F-01 CSS 이중화 사이클 2 fix 필수. F-02 BE 계약 재확인. **사이클 2 필요**.

**frontend-engineer agent — 2026-05-18**
