/**
 * 라우트 정의 — `HashRouter` 기반.
 *
 * Electron 의 `file://` 프로토콜에서는 `BrowserRouter` 의 history mode 가
 * 새로고침 시 404 를 일으키므로 `createHashRouter` 를 사용한다.
 *
 * IA 재편 (slip-output-format 슬라이스 — Q1=A 새 슬라이스):
 * - `/login` → LoginPage (보호 X)
 * - `/`             대시보드
 * - `/warehouses`   창고
 * - `/sales`        판매조회 (출고전표 목록)
 * - `/sales/new`    출고전표 작성
 * - `/sales/link-dispatch`  링크발송 (배송 묶음 + e-sign URL SMS) — link-dispatch-slice
 * - `/sales/:id`    출고전표 상세 + lifecycle
 * - `/sales/:id/print/invoice`   거래명세서 인쇄 미리보기
 * - `/sales/:id/print/dispatch`  출고전표 작업지시서 인쇄
 * - `/purchases`    구매조회 (입고전표 목록)
 * - `/purchases/new` 입고전표 작성
 * - `/purchases/:id` 입고전표 상세 + lifecycle
 * - `/transfers`     재고이동 목록
 * - `/transfers/new` 재고이동 작성
 * - `/transfers/:id` 재고이동 상세 + lifecycle
 *
 * accounting-slice-A 신규 라우트 (ACCOUNTANT/MASTER 만 — RoleGuard):
 * - `/accounting/accounts`              계정과목 트리
 * - `/accounting/journals`              분개장 목록
 * - `/accounting/journals/new`          분개 작성
 * - `/accounting/journals/:id/edit`     분개 편집 (DRAFT 만)
 * - `/accounting/journals/:id`          분개 상세 + 확정/역분개
 * - `/accounting/balances`              시산표 (월별)
 *
 * 기존 PR #18 의 `/slips`, `/slips/new` 라우트는 폐기.
 */
import { createHashRouter, RouterProvider } from 'react-router-dom'
import { AuthGuard } from '../components/AuthGuard'
import { AppLayout } from '../components/AppLayout'
import { RoleGuard } from '../components/RoleGuard'
import { LoginPage } from './LoginPage'
import { DashboardPage } from './DashboardPage'
import { WarehousesPage } from './WarehousesPage'
import { SlipListPage } from './SlipListPage'
import { SlipFormPage } from './SlipFormPage'
import { SlipDetailPage } from './SlipDetailPage'
import { TransferListPage } from './TransferListPage'
import { TransferFormPage } from './TransferFormPage'
import { TransferDetailPage } from './TransferDetailPage'
import { LinkDispatchListPage } from './LinkDispatchListPage'
import { InvoiceView } from '../print/InvoiceView'
import { DispatchView } from '../print/DispatchView'
// P0-4 인쇄 양식 5건 1차 mock — Designer 단계 신규 (출고/입고/견적/세금계산서)
import { OutboundView } from '../print/OutboundView'
import { InboundView } from '../print/InboundView'
import { QuoteView } from '../print/QuoteView'
import { TaxInvoiceView } from '../print/TaxInvoiceView'
// signature-slice-C 모바일 mock 라우트 (Phase 5 nginx 분리 전 시뮬레이션 — AuthGuard 외부)
import { MobileSignaturePage } from './MobileSignaturePage'
import { MobileRecipientPage } from './MobileRecipientPage'
// accounting-slice-A 회계 라우트 5종 (ACCOUNTANT/MASTER 만 — RoleGuard 적용)
import { AccountTreePage } from './AccountTreePage'
import { JournalListPage } from './JournalListPage'
import { JournalFormPage } from './JournalFormPage'
import { JournalDetailPage } from './JournalDetailPage'
import { TrialBalancePage } from './TrialBalancePage'
// P0-4 세금계산서 라우트 3종 (ACCOUNTANT/MASTER — RoleGuard).
// BE: accounting-service `/accounting/tax-invoices/*` (commit f8b8b49).
import { TaxInvoiceListPage } from './TaxInvoiceListPage'
import { TaxInvoiceFormPage } from './TaxInvoiceFormPage'
import { TaxInvoiceDetailPage } from './TaxInvoiceDetailPage'
// P2-1 견적서 라우트 3종 — slip-service `/slips/estimates/*` (commit 59232bd) 신규 BE 연결.
// legacy webview (EstimateLegacyWebviewPage) 폐기 후 SamhanLogis 도메인 견적 화면으로 교체.
import { EstimateListPage } from './EstimateListPage'
import { EstimateFormPage } from './EstimateFormPage'
import { EstimateDetailPage } from './EstimateDetailPage'
// [Phase 6 v4] 판매 sub-route 4종 (견적은 신규 EstimateListPage — legacy webview 폐기)
import { SalesPartnerOrderListPage } from './SalesPartnerOrderListPage'
import { SalesPartnerOrderDetailPage } from './SalesPartnerOrderDetailPage'
import { SalesOrderApprovalsPage } from './SalesOrderApprovalsPage'
import { SalesPartnerDcConfigPage } from './SalesPartnerDcConfigPage'
// Phase 10 P0-2 — 본인 비밀번호 변경 페이지 (재로그인 강제)
import { PasswordChangePage } from './PasswordChangePage'
// [Phase 10 P1-5] arologis 수동 배차 admin UI (DISPATCH/MASTER 가드 — backlog DISPATCH role 부재로 MASTER/MANAGER 매핑)
import { ArologisManualDispatchPage } from './ArologisManualDispatchPage'
import { ARO_MANUAL_DISPATCH_ROLES } from '../api/arologisManualApi'
// [Phase 10 P2-4 / slice 8] 매출 마감 — 일별/월별 (ACCOUNTANT/MASTER 진입, 역마감은 MASTER 만)
import { MonthEndClosingPage } from './MonthEndClosingPage'
// [Phase 10 P0-5 / slice 4] 관리자 통합 admin (MASTER 전용 5 페이지)
import { AdminLayout } from '../components/AdminLayout'
import { UsersPage as AdminUsersPage } from './admin/UsersPage'
import { RolesPage as AdminRolesPage } from './admin/RolesPage'
import { PartnersPage as AdminPartnersPage } from './admin/PartnersPage'
import { WarehousesPage as AdminWarehousesPage } from './admin/WarehousesPage'
import { DepartmentsPage as AdminDepartmentsPage } from './admin/DepartmentsPage'
// [PR-D Phase B FE-A] 구글 시트 동기화 admin (MASTER 전용 — AdminLayout 가드)
import { SheetSyncPage as AdminSheetSyncPage } from './admin/SheetSyncPage'
// [PR-D Phase B FE-B] arologis 가배차 지역 분류 admin UI — MASTER/MANAGER (DISPATCH backlog)
import { RegionsPage as AdminRegionsPage } from './admin/RegionsPage'
import { ARO_REGIONS_ADMIN_ROLES } from '../api/regionApi'
// [Phase 10 P2-6 / slice 9] 재고 실사 3 페이지 (WAREHOUSE/MASTER)
import { InventoryAuditListPage } from './InventoryAuditListPage'
import { InventoryAuditFormPage } from './InventoryAuditFormPage'
import { InventoryAuditDetailPage } from './InventoryAuditDetailPage'

/** 회계 권한 풀네임 화이트리스트 (feedback_role_naming_full.md). */
const ACCOUNTING_ROLES = ['ACCOUNTANT', 'MASTER'] as const

/** 재고 실사 권한 — WAREHOUSE / MASTER (사용자 요구). */
const AUDIT_ROLES = ['WAREHOUSE', 'MASTER'] as const

const router = createHashRouter([
  { path: '/login', element: <LoginPage /> },
  // signature-slice-C 모바일 mock — AuthGuard / AppLayout 미적용 (NO AUTH 공개 endpoint 시뮬레이션)
  { path: '/mobile/d/:token/s/:slipNo', element: <MobileSignaturePage /> },
  { path: '/mobile/share/:shareToken', element: <MobileRecipientPage /> },
  {
    element: (
      <AuthGuard>
        <AppLayout />
      </AuthGuard>
    ),
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/warehouses', element: <WarehousesPage /> },

      // 판매조회 (출고전표)
      { path: '/sales', element: <SlipListPage mode="OUTBOUND" /> },
      { path: '/sales/new', element: <SlipFormPage mode="OUTBOUND" /> },
      // link-dispatch-slice: 링크발송 (배송 묶음) — `/sales/:id` 보다 먼저 매칭되어야 함
      { path: '/sales/link-dispatch', element: <LinkDispatchListPage /> },

      // P2-1 견적서 SamhanLogis 도메인 (slip-service `/slips/estimates`).
      // legacy webview (EstimateLegacyWebviewPage) 폐기. 정적 path 우선 매칭 의무.
      { path: '/sales/estimates', element: <EstimateListPage /> },
      { path: '/sales/estimates/new', element: <EstimateFormPage /> },
      { path: '/sales/partner-orders', element: <SalesPartnerOrderListPage /> },
      { path: '/sales/partner-orders/:id', element: <SalesPartnerOrderDetailPage /> },
      { path: '/sales/order-approvals', element: <SalesOrderApprovalsPage /> },
      { path: '/sales/partner-dc-config', element: <SalesPartnerDcConfigPage /> },

      // P0-4 견적서 인쇄 (estimateNumber path param) — Designer commit 5dcbbef QuoteView 재사용.
      // P2-1 견적서 상세/편집 (id UUID path param) — `/sales/:id` 보다 먼저 매칭되어야 함.
      { path: '/sales/estimates/:estimateNumber/print', element: <QuoteView /> },
      { path: '/sales/estimates/:id/edit', element: <EstimateFormPage /> },
      { path: '/sales/estimates/:id', element: <EstimateDetailPage /> },

      { path: '/sales/:id', element: <SlipDetailPage mode="OUTBOUND" /> },
      { path: '/sales/:id/print/invoice', element: <InvoiceView /> },
      { path: '/sales/:id/print/dispatch', element: <DispatchView /> },
      // P0-4 신규 — 출고전표 (88mm/A4 분기). 세금계산서는 별도 accounting-service id 라우트로 이전.
      { path: '/sales/:id/print/outbound', element: <OutboundView /> },

      // 구매조회 (입고전표)
      { path: '/purchases', element: <SlipListPage mode="INBOUND" /> },
      { path: '/purchases/new', element: <SlipFormPage mode="INBOUND" /> },
      { path: '/purchases/:id', element: <SlipDetailPage mode="INBOUND" /> },
      // P0-4 신규 — 입고전표 (A4/88mm 분기)
      { path: '/purchases/:id/print/inbound', element: <InboundView /> },

      // 재고이동
      { path: '/transfers', element: <TransferListPage /> },
      { path: '/transfers/new', element: <TransferFormPage /> },
      { path: '/transfers/:id', element: <TransferDetailPage /> },

      // Phase 10 P0-2 — 본인 비밀번호 변경 (모든 인증 사용자 접근 가능)
      { path: '/password/change', element: <PasswordChangePage /> },

      // accounting-slice-A — 회계 라우트 5종 (ACCOUNTANT/MASTER 만)
      {
        path: '/accounting/accounts',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <AccountTreePage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/journals',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <JournalListPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/journals/new',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <JournalFormPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/journals/:id/edit',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <JournalFormPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/journals/:id',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <JournalDetailPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/balances',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <TrialBalancePage />
          </RoleGuard>
        ),
      },

      // [Phase 10 P1-5] arologis 수동 배차 admin UI — MASTER / MANAGER (backlog DISPATCH).
      {
        path: '/arologis/manual',
        element: (
          <RoleGuard allow={ARO_MANUAL_DISPATCH_ROLES}>
            <ArologisManualDispatchPage />
          </RoleGuard>
        ),
      },

      // [Phase 10 P2-4 / slice 8] 매출 마감 — 매뉴얼 docs/manual/02-창고/04-매출-마감.md 경로 일치.
      // 진입 가드 ACCOUNTANT/MASTER (역마감 버튼은 페이지 내부에서 MASTER 만 노출).
      {
        path: '/warehouse/closing',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <MonthEndClosingPage />
          </RoleGuard>
        ),
      },

      // P0-4 세금계산서 — accounting-service `/accounting/tax-invoices/*` (commit f8b8b49).
      // ACCOUNTANT / MASTER 만. 정적 path (`/new`) 우선, 다음 print, 마지막 `:id`.
      {
        path: '/accounting/tax-invoices',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <TaxInvoiceListPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/tax-invoices/new',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <TaxInvoiceFormPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/tax-invoices/:id/print',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <TaxInvoiceView />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/tax-invoices/:id/edit',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <TaxInvoiceFormPage />
          </RoleGuard>
        ),
      },
      {
        path: '/accounting/tax-invoices/:id',
        element: (
          <RoleGuard allow={ACCOUNTING_ROLES}>
            <TaxInvoiceDetailPage />
          </RoleGuard>
        ),
      },

      // [Phase 10 P0-5 / slice 4] 관리자 통합 admin — MASTER 전용 5 페이지.
      // AdminLayout 자체에 RoleGuard(MASTER) 가 있으므로 outlet children 은 별도 가드 불필요.
      {
        path: '/admin',
        element: <AdminLayout />,
        children: [
          { path: 'users', element: <AdminUsersPage /> },
          { path: 'roles', element: <AdminRolesPage /> },
          { path: 'partners', element: <AdminPartnersPage /> },
          { path: 'warehouses', element: <AdminWarehousesPage /> },
          { path: 'departments', element: <AdminDepartmentsPage /> },
          // [PR-D Phase B FE-A] 구글 시트 동기화
          { path: 'sheet-sync', element: <AdminSheetSyncPage /> },
        ],
      },

      // [PR-D Phase B FE-B] arologis 가배차 지역 분류 — MASTER / MANAGER (DISPATCH backlog).
      // AdminLayout (MASTER 전용) 외부에 배치하여 MANAGER 도 접근 가능 — 자체 RoleGuard 적용.
      {
        path: '/admin/regions',
        element: (
          <RoleGuard allow={ARO_REGIONS_ADMIN_ROLES}>
            <AdminRegionsPage />
          </RoleGuard>
        ),
      },

      // [Phase 10 P2-6 / slice 9] 재고 실사 — WAREHOUSE / MASTER 만.
      // 매뉴얼 docs/manual/02-창고/05-재고-실사.md 와 경로 일치.
      {
        path: '/warehouse/audit',
        element: (
          <RoleGuard allow={AUDIT_ROLES}>
            <InventoryAuditListPage />
          </RoleGuard>
        ),
      },
      {
        path: '/warehouse/audit/new',
        element: (
          <RoleGuard allow={AUDIT_ROLES}>
            <InventoryAuditFormPage />
          </RoleGuard>
        ),
      },
      {
        path: '/warehouse/audit/:id',
        element: (
          <RoleGuard allow={AUDIT_ROLES}>
            <InventoryAuditDetailPage />
          </RoleGuard>
        ),
      },
    ],
  },
])

/**
 * 앱 루트가 import 하는 RouterProvider wrapper.
 */
export function AppRouter() {
  return <RouterProvider router={router} />
}
