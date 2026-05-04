# Slip Output Format Slice — Dev Report

본 문서는 본 슬라이스의 4-team (BE / FE / QA / DevOps) 결과물을 누적 기록한다.

---

## BE (Team-Slip / Team-Inventory)

> _BE 팀이 본 섹션을 작성합니다 (모델명 lookup endpoint, 라이프사이클 endpoint 검증)._

---

## FE (Team-Desktop FE)

### 사이드바 IA 변경 (Q1=A 새 슬라이스 / Q5=A 1 큰 슬라이스 2주)

기존 `/slips` 단일 라우트 폐기. 영업원/회계원/창고원 사용 흐름을 자연스럽게 분리:

| 메뉴 | 라우트 | 메인 사용자 |
|---|---|---|
| 대시보드 | `/` | 전체 |
| 창고 | `/warehouses` | MASTER/MANAGER |
| 판매조회 (출고전표) | `/sales` | SALES |
| 구매조회 (입고전표) | `/purchases` | ACCOUNTANT |
| 재고이동 | `/transfers` | WAREHOUSE/INVENTORY |

### 11 화면 + 2 인쇄 view

| 라우트 | 화면 | 주 endpoint |
|---|---|---|
| `/sales` | 판매조회 목록 | `GET /slips?slipType=OUTBOUND` |
| `/sales/new` | 출고전표 작성 (모델명 onBlur) | `POST /slips` |
| `/sales/:id` | 출고전표 상세 + lifecycle | `GET /slips/{id}` + `POST /slips/{id}/{action}` |
| `/sales/:id/print/invoice` | 거래명세서 인쇄 | (캐시된 데이터) |
| `/sales/:id/print/dispatch` | 작업지시서 인쇄 | (캐시된 데이터) |
| `/purchases` | 구매조회 목록 | `GET /slips?slipType=INBOUND` |
| `/purchases/new` | 입고전표 작성 | `POST /slips` (slipType=INBOUND) |
| `/purchases/:id` | 입고전표 상세 + lifecycle | `GET /slips/{id}` |
| `/transfers` | 재고이동 목록 | `GET /inventory/transfers` |
| `/transfers/new` | 재고이동 작성 | `POST /inventory/transfers` |
| `/transfers/:id` | 재고이동 상세 + lifecycle | `GET /inventory/transfers/{id}` |

### UUID 전면 제거 (Q6=A — `feedback_uuid_no_user_visibility.md`)

- `SlipNumberDisplay` 의 `uuid` prop 제거 (디자인 시스템 breaking change — 호출자 영향 없음, 옵셔널이었음)
- 모든 라인 입력 placeholder 의 "UUID" 문구 → "예: AJ040RXH4BC1" 모델명으로 교체
- `DataTable` column 정의에 ID 컬럼 미포함 (전표번호/창고 코드/모델명 등 비즈니스 식별자만)
- `WarehouseSelector` 옵션 라벨은 `code · name (type)` 만 — id 미사용
- 검증: `grep -r "uuid\|UUID" src/renderer` 결과 화면 표시 영역 0건 (모두 JSDoc / mock 패턴 매칭 주석)

허용 (사용자 비공개):
- React `key` prop / `axios` body 안 / URL path param (`/sales/:id`)
- `productId` 는 onBlur lookup 으로 내부 state 보유 (전송 시 body 에 포함, 화면 미노출)

### 모델명 onBlur lookup (Q3=B)

- 라인 입력: 모델명 → onBlur → `GET /slips/lookup-product?modelName=...`
- 200: `productName` / `sellingPrice` 자동 fill (사용자가 단가 수정 가능)
- 404: 빨간 경고 메시지 "해당 모델명을 찾을 수 없습니다" (FormField error prop)
- 라인별 `lookupLoading` flag 로 "조회중..." placeholder 표시

### 인쇄 양식 2종 (Q4=A — window.print + @media print CSS)

- `/sales/:id/print/invoice` 거래명세서 — 사용자 제공 이미지 1 양식 충실 반영
- `/sales/:id/print/dispatch` 출고전표 작업지시서 — 사용자 제공 이미지 2 양식 충실 반영
- 별도 라이브러리 추가 X — `window.print()` + `@media print` CSS 만 사용
- `.no-print` 클래스 적용으로 사이드바/헤더/버튼 인쇄 시 숨김
- `@page { size: A4; margin: 12mm; }` 으로 A4 자동 맞춤
- 인쇄 view 는 `src/renderer/print/` 디렉토리 (디자인 시스템 외부)

### 라이프사이클 transition (Q8=A)

**Slip 10 transition button** (status 별 disable/enable):
- DRAFT → save / cancel
- SAVED → send / cancel
- SENT → accept / reject / cancel
- ACCEPTED → process / reject
- PROCESSING → complete
- COMPLETED → ship (출고) / confirm (입고 즉시)
- SHIPPING → deliver
- DELIVERED → confirm (출고)
- 권한 부족 button label 에 `(권한 부족)` 표기 + disable

**StockTransfer 6 transition button**:
- REQUESTED/PENDING_APPROVAL → approve / reject / cancel
- APPROVED → ship / cancel
- SHIPPED/IN_TRANSIT → receive
- RECEIVED → confirm

각 버튼 click → `useMutation` → 성공 시 `queryClient.invalidateQueries({ queryKey: ['slip', id] })` → status 즉시 업데이트.

### 디자인 시스템 영향

- `SlipNumberDisplay.uuid` prop 제거 (breaking change — 옵셔널이었으므로 호출자 영향 없음)
- 신규 컴포넌트 추가 없음 — 인쇄 view 는 `src/renderer/print/` 안 React component 로만 관리

### 권한 헬퍼 추가 (`stores/session.ts`)

- `canCreateTransfer(role)` — MASTER/MANAGER/WAREHOUSE/INVENTORY
- `canTransitionSlip(action, role)` — BE `@PreAuthorize` 와 동일 매핑
- `canTransitionTransfer(action, role)` — BE `@PreAuthorize` 와 동일 매핑

### dev-only mock 갱신 (`api/mock.ts`)

- `GET /slips/lookup-product?modelName=...` — 5종 mock product (AJ040RXH4BC1 등)
- `GET /slips/{id}` — 상세 응답 + 샘플 라인 2건
- `POST /slips/{id}/{action}` — 10종 transition mock (status 자동 진행)
- `GET /inventory/transfers` — mock 5건
- `GET /inventory/transfers/{id}` — 상세 응답 + 샘플 라인 2건
- `POST /inventory/transfers` — 신규 응답
- `POST /inventory/transfers/{id}/{action}` — 6종 transition mock

### 검증 결과

```
$ npm run typecheck   → PASS (0 errors)
$ npm run lint        → PASS (0 violations)
$ npm run build       → out/main + out/preload + out/renderer 정상
   - main: 3.05 kB
   - renderer: 675.39 kB (index.js) + 13.41 kB (index.css)
```

### 신규/변경 파일 (FE)

신규 (8):
- `src/renderer/routes/SlipDetailPage.tsx`
- `src/renderer/routes/TransferListPage.tsx`
- `src/renderer/routes/TransferFormPage.tsx`
- `src/renderer/routes/TransferDetailPage.tsx`
- `src/renderer/print/InvoiceView.tsx`
- `src/renderer/print/DispatchView.tsx`
- `docs/dev-reports/slip-output-format-slice.md` (본 문서)

변경 (10):
- `clients/web/design-system/src/components/SlipNumberDisplay/SlipNumberDisplay.tsx` — uuid prop 제거
- `clients/web/design-system/src/components/SlipNumberDisplay/SlipNumberDisplay.stories.tsx` — WithUUID story 제거
- `src/renderer/routes/index.tsx` — IA 재편 (11 라우트 + 2 print)
- `src/renderer/routes/SlipFormPage.tsx` — 모델명 onBlur lookup, mode prop, OUTBOUND/INBOUND 공용
- `src/renderer/routes/SlipListPage.tsx` — mode prop, navigate 상세
- `src/renderer/routes/DashboardPage.tsx` — 빠른 액션 버튼 갱신
- `src/renderer/components/AppLayout.tsx` — 사이드바 IA 변경 + no-print
- `src/renderer/api/slip.ts` — getSlip / lookupProductByModelName / transitionSlip 추가
- `src/renderer/api/inventory.ts` — Transfer 도메인 (list/get/create/transition) 추가
- `src/renderer/api/mock.ts` — 신규 endpoint mock + lifecycle transition mock
- `src/renderer/stores/session.ts` — canCreateTransfer / canTransitionSlip / canTransitionTransfer 추가
- `src/renderer/styles/global.css` — line-row v2/transfer + detail-grid + 인쇄 양식 + @media print

---

## QA (Team-Slip-Output-Format QA)

### 산출물

| 항목 | 파일 | 시나리오 수 |
|------|------|------------|
| product-service IT (internal endpoint) | `services/product-service/.../web/ProductInternalLookupByModelTest.java` | 4 |
| product-service IT (public endpoint, 7-tier 권한 매트릭스) | `services/product-service/.../it/ProductByModelControllerIT.java` | 9 |
| slip-service IT (ProductClient 단위) | `services/slip-service/.../client/ProductClientLookupByModelTest.java` | 6 |
| slip-service IT (facade endpoint, 7-tier 권한 매트릭스) | `services/slip-service/.../it/SlipLookupControllerIT.java` | 9 |
| product-service fixtures.http (신규) | `services/product-service/src/test/resources/fixtures.http` | 9 |
| slip-service fixtures.http (lookup-product 4건 추가) | `services/slip-service/src/test/resources/fixtures.http` | 4 |
| qa-report | `docs/qa/slip-output-format-slice/qa-report.md` | — |
| FE 시연 캡처 (mock + Edge headless, PM 통합 후 재캡처) | `docs/qa/slip-output-format-slice/screenshots/*.png` | 5 → 11+ |

총 신규 IT **28건** + fixtures **13건**.

### 권한 매트릭스 검증 (7-tier × 신규 endpoint 2종 전수)
- MASTER / MANAGER / DEVELOPER / SALES / WAREHOUSE / INVENTORY / ACCOUNTANT 모두 200
- 미인증 403, 미존재 404
- 가드 분기: 모델명 미존재 = `BusinessException(NOT_FOUND)` (CONFLICT 아님 — mutation 없음)

### 회고 가드 적용 (PR #16/17/18)
- 외부 RestClient `@MockBean` 의무 (`SlipLookupControllerIT` ProductClient + InventoryClient)
- void 메서드만 `doNothing()` — `lookupByModel` 은 ProductSummary 반환 → `thenReturn`/`thenThrow`
- BusinessException ErrorCode 분기 NOT_FOUND vs CONFLICT vs INTERNAL_ERROR 정확
- 한국어 메시지 substring 검증만
- ApiResponse 래핑 → `$.data.*` jsonPath
- 싱글턴 Testcontainers (`extends AbstractPostgresIT`, `@Testcontainers` 미사용)

### FE 11 화면 시연 (PM 통합 후 재캡처)
- QA worktree 캡처 시점에는 FE 신규 7 화면 미머지 상태 → React Router 404 만 캡처됨
- **PM 통합 단계에서 FE 변경 머지 후 11 화면 재캡처** (mock 모드 + Vite + Edge headless 패턴, PR #18 정상화)

---

## DevOps (Team-Slip-Output-Format DevOps)

### 인프라 변경
- **변경 없음** — 신규 모듈 / DB 스키마 / gateway 라우트 / docker-compose / CI workflow 모두 0건
- 기존 7 마이크로서비스 재활용 (`/api/products/**`, `/api/slips/**` 라우트 하위 신규 endpoint 자동 노출)

### 점검 결과
- `services/api-gateway/.../application.yml:38-52` 라우트 기존 등재 ✓
- `services/api-gateway/.../CorsConfig.java:44-51` Electron `app://`, `file://*`, `localhost:*` origin 등록 완료 ✓
- `infrastructure/docker-compose.yml` 변경 불요 ✓
- `.github/workflows/ci.yml` 변경 불요 ✓

### 인쇄 보안 핵심 위험 (review.md §2 참조)
1. 거래명세서 인쇄 시 거래처 정보 + 단가 종이/PDF 외부 유출
2. 인쇄 권한 미분리 (모든 인증 사용자 인쇄 가능)
3. Electron `window.print()` → OS PDF 다이얼로그 → 외부 공유 가능
4. 인쇄 감사 추적 불가

### 후속 슬라이스 권고 (우선순위)
1. **Slip 2nd HISTORY** (수정 사유 + 팀장 승인 + 시점별 복원)
2. **인쇄 보안 강화** (권한 분리 + 워터마크 + 감사 로그 + PDF 차단)
3. **Partner Service Q9** (전잔/후잔/할인율/감리주소/입금예정일 BE 도메인 확장)
4. **Admin UUID 화면** (`/admin/system/objects` MASTER/DEVELOPER 한정)
5. **전자서명 링크 자동 발행** (Phase 5 Notification 연계)
6. **WebSocket 실시간 동기화** (Phase 5 Notification + Dashboard)
7. **모바일 듀얼 앱** (Phase 6, 창고원/거래처 분리)

### 검토 산출물
- `docs/devops/slip-output-format-review.md` — 8장 (인프라 + 인쇄 보안 + UUID admin + CI + 모니터링 + 후속 7항목 + Plan Q1~Q9 + 결론)
