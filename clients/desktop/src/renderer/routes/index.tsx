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
 * 기존 PR #18 의 `/slips`, `/slips/new` 라우트는 폐기.
 */
import { createHashRouter, RouterProvider } from 'react-router-dom'
import { AuthGuard } from '../components/AuthGuard'
import { AppLayout } from '../components/AppLayout'
import { LoginPage } from './LoginPage'
import { DashboardPage } from './DashboardPage'
import { WarehousesPage } from './WarehousesPage'
import { SlipListPage } from './SlipListPage'
import { SlipFormPage } from './SlipFormPage'
import { SlipDetailPage } from './SlipDetailPage'
import { TransferListPage } from './TransferListPage'
import { TransferFormPage } from './TransferFormPage'
import { TransferDetailPage } from './TransferDetailPage'
import { InvoiceView } from '../print/InvoiceView'
import { DispatchView } from '../print/DispatchView'

const router = createHashRouter([
  { path: '/login', element: <LoginPage /> },
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
      { path: '/sales/:id', element: <SlipDetailPage mode="OUTBOUND" /> },
      { path: '/sales/:id/print/invoice', element: <InvoiceView /> },
      { path: '/sales/:id/print/dispatch', element: <DispatchView /> },

      // 구매조회 (입고전표)
      { path: '/purchases', element: <SlipListPage mode="INBOUND" /> },
      { path: '/purchases/new', element: <SlipFormPage mode="INBOUND" /> },
      { path: '/purchases/:id', element: <SlipDetailPage mode="INBOUND" /> },

      // 재고이동
      { path: '/transfers', element: <TransferListPage /> },
      { path: '/transfers/new', element: <TransferFormPage /> },
      { path: '/transfers/:id', element: <TransferDetailPage /> },
    ],
  },
])

/**
 * 앱 루트가 import 하는 RouterProvider wrapper.
 */
export function AppRouter() {
  return <RouterProvider router={router} />
}
