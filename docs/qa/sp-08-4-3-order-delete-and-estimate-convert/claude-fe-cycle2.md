## frontend-engineer 사이클 2 리뷰 (head `d6364d4b`)

### 사이클 1 FE 결함 해소 표

| # | 항목 | 결과 |
|---|---|---|
| 1 | 삭제 진입 버튼 variant="danger" | 해소 (L209) |
| 2 | 삭제 Modal 확정 버튼 variant="danger" | 해소 (L527) |
| 3 | mock.ts DELETE 404 query param | 해소 (mockDelete404 L3297) |
| 4 | mock.ts DELETE 422 query param | 해소 (mockDelete422 L3300) |
| 5 | Modal orderNumber fallback "조회 중" | 해소 (L542) |
| 6 | <strong> 강조 + 을(를) 조사 처리 | 해소 (L542-543) |
| 7 | route id useParams 통일 | 해소 (orderId = useParams id L63) |
| 8 | audit row key +index 보조 | 해소 (L346) |

typecheck: 0 error / lint: 0 error / 기존 warning 2건 (타 파일).

### 사이클 2 신규 발견

**FE-C2-01 (경고) — 거래처 코드 수정 필드 노출 범위 불명확**

수정 Modal `거래처 코드` Input (L426-430) 편집 가능 상태. `partnerCode` 는 비즈니스 식별자이자 외부 거래처 계약 연결 키. 본사 직원 임의 변경 시 적합성 문제. BE `PartnerOrderUpdateService` 가 `partnerCode` 변경 허용 여부 contract 확인 필요. 미허용이면 `readOnly` + payload 제외.

**FE-C2-02 (경고) — `toOrderPathId` 역변환 누락 — 실 API 404 위험**

`SalesPartnerOrderListPage.toOrderPathId` 가 슬립번호 `2026/05/04-1` → `2026-05-04-1` 로 `/` → `-` 치환 후 `encodeURIComponent` route push. Detail page 가 `useParams.id` (변환된 값) 를 `orderId` 로 받아 `getPartnerOrder(id!)` / `updatePartnerOrder(orderId, ...)` / `deletePartnerOrder(orderId)` 전달. BE 는 원래 슬립번호 `2026/05/04-1` 로 조회 — `-` → `/` 역변환 없음. mock regex 가 가리지만 실 API 호출 시 404. **사이클 1.5 route id 통일 fix 의 부작용**. `useParams.id` 를 `id.replace(/-/g, '/')` 역변환 또는 목록 navigate 시 변환 없이 `encodeURIComponent(o.orderNumber)` 만 사용으로 통일 필요.

**FE-C2-03 (정보) — queryKey 영향 범위**

`updateMutation.onSuccess` `queryClient.setQueryData(['partner-order', id], updated)` (L97) 의 `id` 가 경로 변환 문자열. `getPartnerOrder` queryKey 와 동일하므로 캐시 일치. FE-C2-02 fix 시 함께 재검토.

### 종합

사이클 1 FE 8건 전원 해소. 사이클 2 **FE-C2-02 기능 결함** (실 API 404 발생) + **FE-C2-01 BE contract 확인 필요**.

**사이클 3 필요** — FE-C2-02 경로 역변환 + FE-C2-01 BE contract 확인 후 readOnly 결정.

**frontend-engineer agent — 2026-05-17**
