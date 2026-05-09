/**
 * 관리자 통합 admin 셸 — Phase 10 P0-5 슬라이스 4.
 *
 * <p>AppLayout 의 Outlet 안에 mount 되며, MASTER 만 접근 허용 (RoleGuard).
 * 좌측 5 entry 사이드바 + 우측 본문 (Outlet).
 *
 * 사이드바 entry:
 * - 사용자 (`/admin/users`)
 * - 권한    (`/admin/roles`)
 * - 거래처 (`/admin/partners`)
 * - 창고    (`/admin/warehouses`)
 * - 부서    (`/admin/departments`)
 *
 * memory feedback_uuid_no_user_visibility — admin 화면도 비즈니스 식별자만 노출.
 * memory feedback_role_naming_full — entry 라벨/가드 표기 풀네임 사용.
 */
import { NavLink, Outlet } from 'react-router-dom'
import { RoleGuard } from './RoleGuard'

const ADMIN_ROLES = ['MASTER'] as const

export function AdminLayout() {
  return (
    <RoleGuard allow={ADMIN_ROLES}>
      <div
        className="admin-shell"
        style={{
          display: 'grid',
          gridTemplateColumns: '200px 1fr',
          gap: 16,
          minHeight: 'calc(100vh - 120px)',
        }}
        data-testid="admin-shell"
      >
        <aside
          className="admin-sidebar"
          style={{
            background: 'var(--color-neutral-0)',
            border: '1px solid var(--color-neutral-200)',
            borderRadius: 6,
            padding: 12,
            display: 'flex',
            flexDirection: 'column',
            gap: 4,
            height: 'fit-content',
          }}
        >
          <div
            style={{
              padding: '8px 12px 12px',
              fontSize: 12,
              fontWeight: 700,
              color: 'var(--color-brand-700)',
              borderBottom: '1px solid var(--color-neutral-100)',
              marginBottom: 8,
            }}
          >
            관리자 (MASTER 전용)
          </div>
          <AdminNav to="/admin/users" testId="admin-nav-users">
            사용자
          </AdminNav>
          <AdminNav to="/admin/roles" testId="admin-nav-roles">
            권한
          </AdminNav>
          <AdminNav to="/admin/partners" testId="admin-nav-partners">
            거래처
          </AdminNav>
          <AdminNav to="/admin/warehouses" testId="admin-nav-warehouses">
            창고
          </AdminNav>
          <AdminNav to="/admin/departments" testId="admin-nav-departments">
            부서
          </AdminNav>
        </aside>
        <section className="admin-main">
          <Outlet />
        </section>
      </div>
    </RoleGuard>
  )
}

interface AdminNavProps {
  to: string
  testId: string
  children: React.ReactNode
}

function AdminNav({ to, testId, children }: AdminNavProps) {
  return (
    <NavLink
      to={to}
      data-testid={testId}
      style={({ isActive }) => ({
        display: 'block',
        padding: '8px 12px',
        borderRadius: 6,
        fontSize: 13,
        color: isActive
          ? 'var(--color-brand-700)'
          : 'var(--color-neutral-700)',
        background: isActive ? 'var(--color-brand-50)' : 'transparent',
        textDecoration: 'none',
        fontWeight: isActive ? 600 : 400,
      })}
    >
      {children}
    </NavLink>
  )
}
