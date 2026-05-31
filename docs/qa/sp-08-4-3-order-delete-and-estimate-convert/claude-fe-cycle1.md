## frontend-engineer 사이클 1 리뷰 (head `97afca70`)

### 결함 표 (P0/P1/P2/Nit)

| # | 등급 | 위치 | 내용 |
|---|---|---|---|
| FE-1 | P1 | `SalesPartnerOrderDetailPage.tsx:209` | 삭제 버튼 `variant="secondary"` — design-system `Button` `variant="danger"` 존재. 파괴적 액션에 secondary 사용 → 일반 보조 작업과 시각 구분 없음. UX 혼선. `danger` variant 필요. |
| FE-2 | P1 | `SalesPartnerOrderDetailPage.tsx:524–534` | 삭제 확인 Modal footer 확정 버튼도 `variant="primary"` (파란색 CTA). 파괴적 확인 동작은 red/danger 계열 필수 (이카운트 reference + design-system 가드). `danger` variant 변경. |
| FE-3 | P1 | `mock.ts:3295–3296` | DELETE mock 이 `envelope(null)` 단일 시나리오만. 404 (존재하지 않는 주문) / 422 (CONFIRMED/SLIP_PUBLISHED) 시나리오 mock 누락. `mockDelete404` / `mockDelete422` query param 분기 추가 필요 — QA 에러 경로 검증 불가. |
| FE-4 | P1 | `SalesPartnerOrderDetailPage.tsx:541` | 확인 Modal 본문 `주문서 {query.data?.orderNumber ?? ''}를` — orderNumber 미로드 시 빈 문자열 → "주문서 를 삭제하시겠습니까?". `?? '조회 중'` 또는 `?? '(알 수 없음)'` fallback. |
| FE-5 | P2 | `mock.ts` | DELETE mock URL pattern 과 audit URL pattern 경합 가능성 — 현재 순서로는 안전하나 향후 변경 시 위험. 주석 명시 또는 regex `$` anchor 강화. |
| FE-6 | P2 | `SalesPartnerOrderDetailPage.tsx:345` | audit row key `${entry.revisionNo}-${entry.field}-${entry.changedAt}` — `entry.field` 빈 문자열 + 동일 changedAt 시 key 충돌. revisionNo + index 보조 권장. |
| FE-7 | P2 | `SalesPartnerOrderDetailPage.tsx:192-218` | 수정/삭제 버튼 별도 `{query.data && canEdit ? ...}` 블록 — 동일 조건이면 단일 Fragment 로 묶기 (가독성). |
| FE-Nit-1 | Nit | `SalesPartnerOrderDetailPage.tsx:539` | `data-testid="partner-order-delete-confirm-dialog"` Modal 내부 div 부여. Modal 컴포넌트 root testid prop 일관성 권장. |
| FE-Nit-2 | Nit | `mock.ts:3307` | mock audit log 필드 `fieldName: '요청사항'` — `RawAuditLogEntry` 가 `fieldName` / `field` 양쪽 normalize. 통일 권장. |

### 긍정 사항

- `Button`/`Input`/`Modal`/`Select` 전량 `@samhan/design-system`. native button 0건.
- `deletePartnerOrder` orderNumber path param (UUID 미노출, `feedback_uuid_no_user_visibility` 준수).
- `EDIT_ROLES = ['SALES', 'MANAGER', 'MASTER']` PARTNER 자동 배제.
- `deleteMutation.onError` 422 분기 → 한국어 안내. 일반 fallback 존재.
- 삭제 성공 후 `queryClient.invalidateQueries` + `navigate('/sales/partner-orders')` 캐시 무효화 + redirect.
- 사이클 2.5/4.5 패턴 유지 — syncFormFromData, reloadSuccessMessage, '조회 중' fallback 회귀 없음.
- Modal 재오픈 시 `setDeleteErrorMessage(null)` 초기화.
- `encodeURIComponent` URL 안전성.

### 종합

**사이클 2 필요** — P1 4건 (variant danger 2건, mock 누락, fallback 누락) 일괄 fix 후 재검토.

**frontend-engineer agent — 2026-05-17**
