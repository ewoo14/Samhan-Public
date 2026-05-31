## designer 사이클 1 리뷰 (head `1248cdc1`)

### 결함 표

| # | 등급 | 위치 | 내용 |
|---|---|---|---|
| D-C1-1 | Major | `SlipDetailPage.tsx` L1704 | 409 conflict 배너 raw `className="error-banner"` 사용. locked 배너(L970) inline style 로 `var(--color-warning-300)` 직접 인용 — tokens.css 에 미등록 (`--color-warning: #E9A53D` 단일). 런타임 fallback `#FCD34D` 의존. warning scale (50/200/300/800) 정식 등록 필요. |
| D-C1-2 | Major | `SlipDetailPage.tsx` L1726 | 매입 수정 모달 폼 필드 래퍼 `className="driver-edit-field"` 재사용 — 의미 불일치. `purchase-edit-field` 또는 `formField` 분리 권고. |
| D-C1-3 | Minor | `SlipDetailPage.tsx` L1865 | 라인 합계 셀 `style={{ textAlign: 'right', whiteSpace: 'nowrap' }}` inline (2항목). SP-08-4-2 사이클 5/6 inline 제거 패턴 미적용. `.tdRight`/`.tdNoWrap` 권고. |
| D-C1-4 | Minor | QA PNG 02-reload.png | "다른 사용자가 먼저 수정했습니다. 최신 내용으로 다시 불러온 뒤 다시 저장해 주세요." — "다시" 반복 + 코드(L394) "최신 내용 불러오기 후 다시 저장" 과 불일치 가능. 재확인. |
| D-C1-5 | Nit | `SlipDetailPage.tsx` L514 | 로딩 fallback `<p>불러오는 중...</p>` plain. `<Spinner>` 또는 `loading-fallback` 통일 권고. |

### 긍정 사항

- design-system `<Modal>/<Button>/<Input>/<Badge>/<Card>/<AuditOverlay>` 일관 사용
- 한국어 라벨 완비 (영문 enum 노출 0건)
- 409 reload UX 흐름 (배너 → 버튼 → reload → 3초 자동 소거) 완결
- `AuditOverlay` actorName + 색상 hash — UUID 비공개 100%
- `PURCHASE_EDIT_ROLES = ['WAREHOUSE','MANAGER','MASTER']` — INVENTORY 미포함
- QA PNG 4장 UUID 미노출 + 한국어
- `--color-success-*` 4종 토큰 SP-08-4-2 D-C5-3 회고 반영 확인

### 종합

design-system, 한국어 라벨, UUID 비공개, 409 처리 견고. Major 2건 (D-C1-1 warning scale, D-C1-2 className) 사이클 1c fix 필수. Minor SP-08-4-2 패턴 적용 가능.

**CHANGES REQUESTED**

**designer agent — 2026-05-18**
