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
 * - 시트 동기화 (`/admin/sheet-sync`) — PR-D Phase B FE-A
 * - 지역 분류 (`/admin/regions`) — PR-D Phase B FE-B (MASTER/MANAGER, 본 entry 는 MASTER 만 가시)
 * - 발송금지 거래처 (`/admin/blocked-partners`) — PR-D Phase B FE-E (MASTER, 민감)
 * - 단톡방 매핑 (`/admin/chat-rooms`) — PR-D Phase B FE-D (MASTER/MANAGER, 본 entry 는 MASTER 만 가시)
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
          <AdminNav to="/admin/sheet-sync" testId="admin-nav-sheet-sync">
            시트 동기화
          </AdminNav>
          {/*
            [PR-D Phase B FE-B] arologis 지역 분류 — 라우트 자체는 MASTER/MANAGER (DISPATCH backlog)
            허용. AdminLayout 은 MASTER 전용이므로 본 entry 는 MASTER 시점에서만 노출되며,
            MANAGER 는 직접 URL (/admin/regions) 또는 AppLayout 좌측 arologis 그룹으로 접근.
          */}
          <AdminNav to="/admin/regions" testId="admin-nav-regions">
            지역 분류
          </AdminNav>
          {/*
            [PR-D Phase B FE-E] 발송금지 거래처 — partner-service /api/v1/partners/admin/blocks.
            BE 가 MASTER 강제 (delete/import) + read 도 MANAGER 까지지만 AdminLayout 자체가
            MASTER 전용이므로 본 entry 는 MASTER 만 노출. UUID 비공개 (사용자 노출 = partnerCode + 상호).
          */}
          <AdminNav
            to="/admin/blocked-partners"
            testId="admin-nav-blocked-partners"
          >
            발송금지 거래처
          </AdminNav>
          {/*
            [PR-D Phase B FE-D] 단톡방 매핑 — 라우트 자체는 MASTER/MANAGER 허용.
            AdminLayout 은 MASTER 전용이므로 본 entry 는 MASTER 시점만 노출되며,
            MANAGER 는 직접 URL (/admin/chat-rooms) 또는 AppLayout 좌측 메뉴로 접근.
          */}
          <AdminNav to="/admin/chat-rooms" testId="admin-nav-chat-rooms">
            단톡방 매핑
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
