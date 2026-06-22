/**
 * AppLayout — 상단 네비게이션 + Outlet.
 *
 * Samhan Public desktop 의 AppLayout 패턴을 단순화. 디자인 토큰 적용은
 * Designer (D1~D5) 작업 결과로 후속 PR 에서 확장.
 */
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useQueryClient } from '@tanstack/react-query'
import { usePermissions } from '../hooks/usePermissions'
import { useAuthStore } from '../stores/authStore'

const navStyle: React.CSSProperties = {
  display: 'flex',
  gap: 16,
  padding: '12px 24px',
  borderBottom: '1px solid var(--color-border)',
  background: 'var(--color-surface)',
  alignItems: 'center',
}

const linkStyle: React.CSSProperties = {
  textDecoration: 'none',
  color: 'var(--color-text-muted)',
  padding: '6px 10px',
  borderRadius: 4,
}

const activeLinkStyle: React.CSSProperties = {
  ...linkStyle,
  color: 'var(--color-primary)',
  fontWeight: 600,
}

const adminNavStyle: React.CSSProperties = {
  display: 'flex',
  gap: 16,
  alignItems: 'center',
  minWidth: 280,
  minHeight: 32,
}

const adminNavPlaceholderStyle: React.CSSProperties = {
  width: 280,
  height: 32,
}

export function AppLayout(): JSX.Element {
  const auth = useAuthStore((s) => s.auth)
  const logout = useAuthStore((s) => s.logout)
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { canAccess, isLoading } = usePermissions()
  const canViewEmployees = canAccess('arologis.hr.employees', 'view')
  const canViewDepartments = canAccess('arologis.hr.departments', 'view')
  const canViewCashbook = canAccess('arologis.accounting.cashbook', 'view')
  const canViewAccounts = canAccess('arologis.accounting.accounts', 'view')
  const canViewPermissions = canAccess('arologis.admin.permissions', 'view')

  const handleLogout = async (): Promise<void> => {
    queryClient.removeQueries({ queryKey: ['permissions', 'my'] })
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
      <nav style={navStyle} aria-label="주 메뉴">
        <strong style={{ fontSize: 'var(--font-size-lg)' }}>아로로지스</strong>
        <NavLink
          to="/dispatches"
          style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
        >
          배차
        </NavLink>
        <NavLink
          to="/drivers"
          style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
        >
          기사 관리
        </NavLink>
        <div style={adminNavStyle} aria-busy={isLoading}>
          {isLoading ? <div style={adminNavPlaceholderStyle} aria-hidden="true" /> : null}
          {!isLoading && canViewEmployees ? (
            <NavLink
              to="/admin/employees"
              style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
            >
              인사
            </NavLink>
          ) : null}
          {!isLoading && canViewDepartments ? (
            <NavLink
              to="/admin/departments"
              style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
            >
              부서
            </NavLink>
          ) : null}
          {!isLoading && canViewCashbook ? (
            <NavLink
              to="/admin/cashbook"
              style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
            >
              회계
            </NavLink>
          ) : null}
          {!isLoading && canViewAccounts ? (
            <NavLink
              to="/admin/accounts"
              style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
            >
              계정과목
            </NavLink>
          ) : null}
          {!isLoading && canViewPermissions ? (
            <NavLink
              to="/admin/permissions"
              style={({ isActive }) => (isActive ? activeLinkStyle : linkStyle)}
            >
              권한
            </NavLink>
          ) : null}
        </div>
        <div style={{ marginLeft: 'auto', display: 'flex', gap: 12, alignItems: 'center' }}>
          {auth && (
            <span style={{ color: 'var(--color-text-muted)' }}>
              {auth.fullName} ({auth.loginId})
            </span>
          )}
          <button
            type="button"
            onClick={handleLogout}
            style={{
              padding: '6px 12px',
              border: '1px solid var(--color-border)',
              background: 'var(--color-surface)',
              borderRadius: 4,
            }}
          >
            로그아웃
          </button>
        </div>
      </nav>
      <main style={{ flex: 1, overflow: 'auto', padding: 24 }}>
        <Outlet />
      </main>
    </div>
  )
}
