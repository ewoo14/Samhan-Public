## Codex frontend-engineer 사이클 1 리뷰 (head `97afca70`)

### Claude FE 발견 평가

- P1-1 valid. `ButtonVariant`에 `danger`가 존재하므로 삭제 진입 버튼은 `secondary`보다 `danger`가 맞습니다. `SalesPartnerOrderDetailPage.tsx:209`, `Button.tsx:5`
- P1-2 valid. 삭제 확정 버튼도 destructive action이라 `primary`는 부적절. `SalesPartnerOrderDetailPage.tsx:526`
- P1-3 valid. `mock.ts` DELETE는 현재 무조건 성공만 반환해 404/422 에러 UI를 QA할 수 없음. `mock.ts:3295`
- P1-4 valid. 삭제 문구에서 `orderNumber ?? ''`는 로딩/데이터 소실 시 빈 주문서 문구. `조회 중`이 기존 title/badge fallback과 일관. `SalesPartnerOrderDetailPage.tsx:541`
- P2-5 invalid. 현재 list/detail/audit regex는 각각 anchored 또는 하위 path 조건이 있어 실제 경합으로 보이지 않음.
- P2-6 valid-low. `revisionNo-field-changedAt` key는 batch audit에서 같은 revision/field/timestamp가 생기면 충돌 여지. `SalesPartnerOrderDetailPage.tsx:345`
- P2-7 over-engineering. `query.data && canEdit` 중복은 정리 가능하지만 동작 결함은 아님.
- Nit-1 over-engineering. `Modal` 자체는 `role="dialog"`와 backdrop testid가 있고, 본문 wrapper에도 `partner-order-delete-confirm-dialog`가 있어 테스트 타깃은 충분.
- Nit-2 invalid. mock이 `fieldName`을 반환해도 `createAuditApi`가 `field`로 정규화. `createAuditApi.ts:70`

### Codex 신규 발견

- **P1**. 삭제 API 호출이 상세 route id가 아니라 `query.data.orderNumber`를 사용. 목록 route는 `/`를 `-`로 바꾼 id를 만들고, BE IT도 `2026-05-17-31` 형태를 delete path로 검증. 그런데 상세 응답의 `orderNumber`는 `2026/05/04-1`이고 `deletePartnerOrder()`가 이를 `encodeURIComponent`하면 `%2F`가 포함된 path가 됨. 서버/프록시에서 encoded slash가 막히면 실제 삭제가 404/400으로 깨질 수 있고 mock은 이를 가림. 삭제/수정/audit path 인자는 route `id` 또는 동일한 hyphen id로 통일 필요. `SalesPartnerOrderDetailPage.tsx:109`, `sales.ts:397`, `SalesPartnerOrderListPage.tsx:32`

### 종합

Claude FE 9건 중 valid 5건, invalid/over-engineering 4건. 추가로 path id 불일치 1건은 실제 API 연동 리스크가 있어 P1.

**Codex FE-agent — 2026-05-17**
