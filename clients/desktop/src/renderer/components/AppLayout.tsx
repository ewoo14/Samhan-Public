/**
 * 인증된 사용자용 앱 셸 레이아웃 — 좌측 사이드바 + 우측 본문 (Outlet).
 *
 * 사이드바 메뉴:
 * - 대시보드 (`/`)
 * - 창고 (`/warehouses`)
 * - 출고전표 (`/slips`)
 *
 * 우상단에는 현재 사용자명 + 역할 + 로그아웃 버튼을 표시한다.
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
      <aside className="app-sidebar">
        <h1>삼한로지스</h1>
        <nav>
          <NavLink to="/" end>
            대시보드
          </NavLink>
          <NavLink to="/warehouses">창고</NavLink>
          <NavLink to="/slips">출고전표</NavLink>
        </nav>
        <div style={{ marginTop: 'auto', fontSize: 12, color: '#6B7280' }}>
          v0.1.0 · 사내 전용
        </div>
      </aside>
      <main className="app-main">
        <header className="app-header">
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
