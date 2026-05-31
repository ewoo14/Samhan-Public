# SP-D4 FE Cycle 1 리뷰 — claude-fe

> 작성일: 2026-05-18
> 검토자: FE (Claude)
> PR: #244 `feat/sp-d4-remaining-pages-permission-migration`
> HEAD: `6d141002`
> 검토 범위: permissionsApi.ts / mock.ts / AppLayout.tsx / routes/index.tsx / PermissionMatrixPage.tsx

---

## 종합 판정

**CONDITIONAL APPROVE (cycle 2 fix 필요)**

| 검토 항목 | 판정 | 요약 |
|---|---|---|
| permissionsApi.ts PageCode +22 정합 | PASS | 41개 정확, BE enum 일치 |
| mock.ts SP_D1_PAGES 22 코드 추가 | PASS | 41개 배열, SP-D4 전부 포함 |
| mock.ts VIEW 매트릭스 ↔ V10 seed | FAIL | MANAGER admin.users view 불일치 |
| mock.ts EDIT 매트릭스 ↔ V10 seed | FAIL | 3셀 불일치 (SALES products.list / WAREHOUSE sales.vendor-order / INVENTORY products.list) |
| AppLayout.tsx 22 dynamicCanAccess 변수 | PASS | 22개 변수 정확 선언 |
| AppLayout.tsx 사이드바 hidden 정합 | FAIL | _showInventoryStock 기반 빈 창고운영 그룹 헤더 노출 가능 |
| routes/index.tsx PermissionGuard 추가 | PASS (조건부) | 14+ 라우트 PermissionGuard 정상. /sales/estimates RoleGuard 미적용 P2 |
| /admin/blocked-partners 이중 가드 | PASS | RoleGuard + PermissionGuard 정상 |
| PermissionMatrixPage.tsx 13 카테고리 | PASS (조건부) | PAGES_WITH_EDIT inventory.audit 누락 P2 |
| design-system import 의무 | PASS | Button/Badge/Spinner @samhan/design-system 정상 |
| typecheck | PASS | 오류 0건 |
| lint | PASS (경고) | 오류 0건, 경고 3건 (P2) |

---

## 결함 목록

### F1 (P1) — mock.ts MANAGER VIEW: admin.users 불일치

**위치**: `clients/desktop/src/renderer/api/mock.ts` — `SP_D1_DEFAULT_VIEW.MANAGER` 배열 (5659라인)

**현상**:
MANAGER VIEW 배열에 `'admin.users'`가 포함되어 있다.

```
// mock.ts 5659라인
'inventory.dps', 'inventory.audit', 'admin.employees', 'admin.users',
```

V10 seed 기준 MANAGER admin.users는 `FALSE/FALSE`이다.

```sql
-- V10 seed 179라인
('MANAGER', 'admin.users', FALSE, FALSE, ...)
```

**영향**: 개발 mock 환경에서 MANAGER 역할이 `/admin/permission-matrix` (admin.users 가드) 에 진입 가능해 보이는 오해를 준다. 운영 환경(DB 기반)에서는 실제 V10 seed 값으로 올바르게 차단되므로 서버 측 보안 영향은 없으나, mock ↔ seed 불일치는 FE 개발/테스트 신뢰도를 저하시킨다.

**수정 방향**: `SP_D1_DEFAULT_VIEW.MANAGER` 배열에서 `'admin.users'` 제거.

---

### F2 (P1) — mock.ts SALES EDIT: products.list 불일치

**위치**: `clients/desktop/src/renderer/api/mock.ts` — `SP_D1_DEFAULT_EDIT.SALES` 배열 (5761라인)

**현상**:
SALES EDIT 배열에 `'products.list'`가 포함되어 있다.

```
// mock.ts 5761라인
'partners.list', 'partners.detail',
'products.list', 'products.admin',
```

V10 seed 기준 SALES products.list는 `TRUE/FALSE` (view 전용, edit=FALSE)이다.

```sql
-- V10 seed 241라인
('SALES', 'products.list', TRUE, FALSE, ...)
```

Plan §2 표에서도 products.list SALES = `V` (edit 미부여)로 명시되어 있다.

**영향**: mock 환경에서 SALES 가 상품 목록 edit 권한을 가진 것으로 오동작. E2E Playwright 결과 신뢰도 저하.

**수정 방향**: `SP_D1_DEFAULT_EDIT.SALES` 배열에서 `'products.list'` 제거.

---

### F3 (P1) — mock.ts WAREHOUSE EDIT: sales.vendor-order 불일치

**위치**: `clients/desktop/src/renderer/api/mock.ts` — `SP_D1_DEFAULT_EDIT.WAREHOUSE` 배열 (5778라인)

**현상**:
WAREHOUSE EDIT 배열에 `'sales.vendor-order'`가 포함되어 있다.

```
// mock.ts 5778라인
'sales.vendor-order', 'inventory.warehouse', 'inventory.stock',
```

V10 seed 기준 WAREHOUSE sales.vendor-order는 `TRUE/FALSE` (view 전용, edit=FALSE)이다.

```sql
-- V10 seed 98라인
('WAREHOUSE', 'sales.vendor-order', TRUE, FALSE, ...)
```

mock.ts EDIT 주석 (5722라인)에는 `sales.vendor-order: MASTER/MANAGER/SALES/WAREHOUSE (BE EP 에 따라 WAREHOUSE 포함)` 이라고 표기되어 있으나 V10 seed는 WAREHOUSE edit=FALSE이므로 주석 자체도 오도적이다.

**영향**: WAREHOUSE 역할이 벤더 주문 edit 권한을 가진 것으로 오동작.

**수정 방향**: `SP_D1_DEFAULT_EDIT.WAREHOUSE` 배열에서 `'sales.vendor-order'` 제거. EDIT 주석도 `sales.vendor-order: MASTER/MANAGER/SALES` 로 정정.

---

### F4 (P1) — mock.ts INVENTORY EDIT: products.list 불일치

**위치**: `clients/desktop/src/renderer/api/mock.ts` — `SP_D1_DEFAULT_EDIT.INVENTORY` 배열 (5785라인)

**현상**:
INVENTORY EDIT 배열에 `'products.list'`가 포함되어 있다.

```
// mock.ts 5785라인
'inventory.dps',
'products.list', 'products.admin',
```

V10 seed 기준 INVENTORY products.list는 `TRUE/FALSE` (view 전용, edit=FALSE)이다.

```sql
-- V10 seed 244라인
('INVENTORY', 'products.list', TRUE, FALSE, ...)
```

INVENTORY products.admin은 V10에서 `TRUE/TRUE` (edit 허용)이므로 products.admin은 정상이다.

**영향**: INVENTORY 역할이 상품 목록 edit 권한을 가진 것으로 오동작.

**수정 방향**: `SP_D1_DEFAULT_EDIT.INVENTORY` 배열에서 `'products.list'` 제거.

---

### F5 (P1) — AppLayout.tsx 사이드바: SALES/ACCOUNTANT/DISPATCH 역할에 "창고 운영" 빈 그룹 헤더 노출

**위치**: `clients/desktop/src/renderer/components/AppLayout.tsx` — 805라인

**현상**:
`showInventoryGroup` 조건이 `_showInventoryStock` 변수 (inventory.stock view) 기반으로 계산된다.

```tsx
// AppLayout.tsx 252-254라인
const showInventoryGroup =
  showInventoryWarehouse || _showInventoryStock || showInventoryStockTransfer
  || showInventoryDps || showInventoryAuditPage
```

V10 seed 기준 inventory.stock view는 MASTER/MANAGER/ACCOUNTANT/SALES/WAREHOUSE/DISPATCH/INVENTORY 7개 역할 모두 `TRUE`이다. 따라서 `_showInventoryStock`이 항상 `true`가 되어 `showInventoryGroup=true`가 된다.

창고 운영 그룹 표시 조건이 `(showWarehouseOps || showInventoryGroup)`이므로, SALES/ACCOUNTANT/DISPATCH 역할에서 `showInventoryGroup=true`로 그룹 헤더가 렌더된다.

그러나 그룹 내부의 6개 `SidebarLink` (`입고검수` / `재고실사` / `DPS입고비교` / `품목별DPS분석` / `전표수정요청` / `사진감사` / `안전재고알림`)는 각각 기존 정적 역할 체크(`showInboundInspection`, `showAudit`, `showDpsCompare` 등)로 제어되어 SALES/ACCOUNTANT/DISPATCH에서 모두 `show=false` → DOM 렌더 없음 → **"창고 운영" 헤더 텍스트만 노출되고 내부 항목이 없는 빈 그룹 헤더 UX 발생**.

**영향**: 역할 SALES, ACCOUNTANT, DISPATCH 로그인 시 사이드바에 "창고 운영" 카테고리 헤더만 보이고 하위 메뉴가 없음. UX 혼란 + Playwright 사이드바 스크린샷 7역할 비교 시 이슈로 검출될 수 있음.

**수정 방향**:
- `showInventoryGroup` 조건에서 `_showInventoryStock` 제거하고 실제 사이드바 항목(`showInventoryWarehouse`, `showInventoryStockTransfer`, `showInventoryDps`, `showInventoryAuditPage`)만으로 구성.
- 또는 `showWarehouseOps || showInventoryGroup` 대신 개별 항목 OR 조건으로 교체.
- 수정 후 SALES/ACCOUNTANT/DISPATCH 역할에서 창고 운영 그룹이 미노출되어야 한다.

---

### F6 (P2) — /sales/estimates 라우트 RoleGuard 미적용

**위치**: `clients/desktop/src/renderer/routes/index.tsx` — 432~437라인

**현상**:
`/sales/estimates` 라우트에 `PermissionGuard` 만 적용되고 `RoleGuard` 가 없다.

```tsx
// routes/index.tsx 432~437라인
{
  path: '/sales/estimates',
  element: (
    <PermissionGuard pageCode="estimates.list" action="view">
      <EstimateListPage />
    </PermissionGuard>
  ),
},
```

SP-D4 Plan §1 가이드라인 "RoleGuard `@PreAuthorize` **보존**(회귀 차단)" 및 기존 `/sales/partner-orders` 패턴(`RoleGuard` + `PermissionGuard` 이중 가드)과 일관성이 어긋난다.

**영향**: 운영 환경에서는 PermissionGuard가 V10 seed 기반으로 WAREHOUSE/DISPATCH/INVENTORY를 차단하므로 보안 영향 없음. 그러나 mock 환경 오류 시 이중 가드 미적용으로 예기치 않은 접근이 가능해질 수 있다. 개선 권장(P2).

**수정 방향**: `SALES_PARTNER_ORDER_ROLES`와 유사하게 `estimates.list` 접근 가능 역할 상수 (`ESTIMATES_ROLES = ['MASTER', 'MANAGER', 'ACCOUNTANT', 'SALES']`)를 정의하고 RoleGuard + PermissionGuard 이중 가드 적용.

---

### F7 (P2) — PermissionMatrixPage.tsx PAGES_WITH_EDIT에 inventory.audit 누락

**위치**: `clients/desktop/src/renderer/routes/PermissionMatrixPage.tsx` — 260~298라인 (PAGES_WITH_EDIT Set)

**현상**:
`PAGES_WITH_EDIT` Set에 SP-D4 신규 22개 중 `inventory.audit`가 포함되지 않았다.

V10 seed 기준 `inventory.audit`는 MASTER에 대해 `edit=TRUE`가 부여되어 있다 (149라인: `('MASTER', 'inventory.audit', TRUE, TRUE, ...)`). 나머지 역할은 view 전용이지만 MASTER 에게 edit 이 의미 있으므로 `PAGES_WITH_EDIT` 포함이 적합하다.

**영향**: 권한 매트릭스 관리 화면에서 `재고 감사` 열에 edit 체크박스가 표시되지 않아 MASTER가 edit 권한을 토글할 수 없다. UI 기능 누락.

**수정 방향**: `PAGES_WITH_EDIT` Set에 `'inventory.audit'` 추가.

---

### F8 (P2) — AppLayout.tsx lint 경고: DISPATCH_BOARD_SIDEBAR_ROLES 미사용

**위치**: `clients/desktop/src/renderer/components/AppLayout.tsx` — 134라인

**현상**:
`DISPATCH_BOARD_SIDEBAR_ROLES` 상수가 선언되어 있으나 어디에도 사용되지 않는다 (lint warning `@typescript-eslint/no-unused-vars`). SP-D1 cycle 2에서 `showDispatchBoard = dynamicCanAccess('dispatch.board', 'view')` 로 전환된 이후 정적 역할 배열이 잔류한 것으로 보인다.

**영향**: lint 경고 (`warning`) — 빌드 실패는 아니나 코드 노이즈.

**수정 방향**: `DISPATCH_BOARD_SIDEBAR_ROLES` 상수 제거 또는 `_DISPATCH_BOARD_SIDEBAR_ROLES` 로 rename하여 eslint 규칙 우회.

---

## 매트릭스 cross-check 결과 요약

### V10 seed ↔ mock.ts VIEW 불일치

| 역할 | PageCode | V10 seed VIEW | mock DEFAULT_VIEW | 판정 |
|---|---|---|---|---|
| MANAGER | admin.users | FALSE | TRUE (포함) | 불일치 (F1) |
| 기타 21 코드 × 6 역할 | — | — | — | 일치 |

### V10 seed ↔ mock.ts EDIT 불일치

| 역할 | PageCode | V10 seed EDIT | mock DEFAULT_EDIT | 판정 |
|---|---|---|---|---|
| SALES | products.list | FALSE | TRUE (포함) | 불일치 (F2) |
| WAREHOUSE | sales.vendor-order | FALSE | TRUE (포함) | 불일치 (F3) |
| INVENTORY | products.list | FALSE | TRUE (포함) | 불일치 (F4) |
| 기타 | — | — | — | 일치 |

---

## 정상 확인 항목

1. **permissionsApi.ts PageCode 타입**: SP-D4 22개 코드 (`estimates.list` ~ `arologis.region`) 정확히 추가. BE `PageCode.java` enum dot-separated code와 1:1 일치 확인.

2. **SP_D1_PAGES 배열**: 41개 (SP-D1 12 + SP-D2 7 + SP-D4 22) 정확. `as const` 타입 보존.

3. **V10 seed 행 수**: 154 row (22 × 7) 정확. BaseEntity 7 audit 필드 + `is_deleted=FALSE` 모두 명시. `ON CONFLICT DO NOTHING` 멱등성 보장.

4. **AppLayout.tsx 22 dynamicCanAccess 변수**: `showEstimatesList` ~ `showArologisRegionPage` 22개 변수 선언 정상. `_showInventoryStock` / `_showProductsList` / `_showProductsAdmin` 3개는 사이드바 미노출 주석 처리 (라우트 가드 전용) — 의도적.

5. **/admin/blocked-partners 이중 가드**: `RoleGuard allow={BLOCKED_PARTNER_ROLES}` + `PermissionGuard pageCode="partners.block"` 이중 가드 TM cross-check fix 포함 확인.

6. **AppLayout 인사 그룹**: `(showAdmin || showAdminHrGroup)` 조건으로 `admin.employees` / `admin.users` 동적 연동 정상. `showAdminEmployees` (MASTER/MANAGER) + `showAdminUsersMgmt` (MASTER 전용) 분리 구현 일치.

7. **PermissionMatrixPage.tsx**: `@samhan/design-system` Button/Badge/Spinner import 정상. 13 카테고리 그룹 + 41 PageCode 전수 포함. `PAGE_LABEL` 한국어 22개 정상.

8. **typecheck**: 오류 0건. strict mode TypeScript 이상 없음.

9. **routes PermissionGuard 적용 범위**: `/warehouse/audit`, `/warehouse/audit/new` (inventory.audit), `/admin/regions` (arologis.region), `/admin/permission-matrix` (admin.users), `/admin/users` / `/admin/users/new` (admin.employees), `/arologis/admin/*` 3개 (arologis.admin), `/sales/estimates` (estimates.list), `/sales/partner-orders` / `/sales/partner-orders/:id` (sales.partner-order.list) 모두 정상 적용.

---

## cycle 2 fix 요청 우선순위

| 우선순위 | 결함 | 수정 위치 |
|---|---|---|
| P1 (필수) | F1: mock MANAGER VIEW admin.users | mock.ts SP_D1_DEFAULT_VIEW.MANAGER |
| P1 (필수) | F2: mock SALES EDIT products.list | mock.ts SP_D1_DEFAULT_EDIT.SALES |
| P1 (필수) | F3: mock WAREHOUSE EDIT sales.vendor-order | mock.ts SP_D1_DEFAULT_EDIT.WAREHOUSE |
| P1 (필수) | F4: mock INVENTORY EDIT products.list | mock.ts SP_D1_DEFAULT_EDIT.INVENTORY |
| P1 (필수) | F5: 빈 창고운영 그룹 헤더 노출 | AppLayout.tsx showInventoryGroup 조건 |
| P2 (권장) | F6: /sales/estimates RoleGuard 미적용 | routes/index.tsx |
| P2 (권장) | F7: PAGES_WITH_EDIT inventory.audit 누락 | PermissionMatrixPage.tsx |
| P2 (권장) | F8: DISPATCH_BOARD_SIDEBAR_ROLES lint 경고 | AppLayout.tsx |
