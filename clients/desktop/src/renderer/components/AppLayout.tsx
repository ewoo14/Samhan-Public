/**
 * 인증된 사용자용 앱 셸 레이아웃 — 좌측 사이드바 + 우측 본문 (Outlet).
 *
 * 사이드바 메뉴 (slip-output-format 슬라이스 IA 재편 — Q1=A 새 슬라이스):
 * - 대시보드 (`/`)
 * - 창고 (`/warehouses`)
 * - 판매조회 (`/sales`)     — 출고전표, 영업원 메인
 * - 구매조회 (`/purchases`) — 입고전표, 회계원 메인
 * - 재고이동 (`/transfers`) — 창고 간 이동, 창고원/재고원
 *
 * 기존 PR #18 의 `/slips` IA 는 폐기. 영업/회계/창고 흐름 분리.
 *
 * 우상단에는 현재 사용자명 + 역할 + 로그아웃 버튼을 표시한다.
 * 인쇄 화면 (`/print/...`) 에서는 @media print CSS 가 사이드바/헤더를 숨긴다.
 */
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Button } from '@samhan/design-system'
import { useSessionStore } from '../stores/session'

export function AppLayout() {
  const auth = useSessionStore((s) => s.auth)
  const logout = useSessionStore((s) => s.logout)
  const navigate = useNavigate()

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app-shell">
      <aside className="app-sidebar no-print">
        <h1>삼한로지스</h1>
        <nav>
          <NavLink to="/" end>
            대시보드
          </NavLink>
          <NavLink to="/warehouses">창고</NavLink>
          <NavLink to="/sales">판매조회</NavLink>
          <NavLink to="/purchases">구매조회</NavLink>
          <NavLink to="/transfers">재고이동</NavLink>
        </nav>
        <div style={{ marginTop: 'auto', fontSize: 12, color: '#6B7280' }}>
          v0.1.0 · 사내 전용
        </div>
      </aside>
      <main className="app-main">
        <header className="app-header no-print">
          <h2>업무 화면</h2>
          <div className="app-header-actions">
            <span className="app-user-chip">
              {auth?.fullName ?? '사용자'} · {auth?.role ?? '-'}
            </span>
            <Button variant="ghost" size="sm" onClick={handleLogout}>
              로그아웃
            </Button>
          </div>
        </header>
        <Outlet />
      </main>
    </div>
  )
}
