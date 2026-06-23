import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { PermissionGuard } from './PermissionGuard'
import { usePermissions } from '../hooks/usePermissions'
import { useAuthStore } from '../stores/authStore'

vi.mock('../hooks/usePermissions', () => ({
  usePermissions: vi.fn(),
}))

const mockedUsePermissions = vi.mocked(usePermissions)

describe('PermissionGuard', () => {
  afterEach(() => {
    cleanup()
    vi.resetAllMocks()
    useAuthStore.setState({ auth: null, bootstrapped: true })
  })

  it('비허용 page-code 는 보호 라우트에서 홈으로 리다이렉트한다', () => {
    mockedUsePermissions.mockReturnValue({
      canAccess: () => false,
      permissions: [],
      isLoading: false,
      isError: false,
    })

    renderGuard()

    expect(screen.queryByTestId('home-page')).not.toBeNull()
    expect(screen.queryByTestId('secure-page')).toBeNull()
  })

  it('MASTER 전용 라우트는 page-code 권한이 있어도 비-MASTER role 을 차단한다', () => {
    useAuthStore.setState({
      auth: authSnapshot('AROLOGIS_MANAGER'),
      bootstrapped: true,
    })
    mockedUsePermissions.mockReturnValue({
      canAccess: () => true,
      permissions: [{ pageCode: 'arologis.admin.permissions', actions: ['view'] }],
      isLoading: false,
      isError: false,
    })

    renderGuard({ requireMaster: true })

    expect(screen.queryByTestId('home-page')).not.toBeNull()
    expect(screen.queryByTestId('secure-page')).toBeNull()
  })
})

function renderGuard({ requireMaster = false }: { requireMaster?: boolean } = {}): void {
  render(
    <MemoryRouter initialEntries={['/secure']}>
      <Routes>
        <Route path="/" element={<div data-testid="home-page">home</div>} />
        <Route
          path="/secure"
          element={(
            <PermissionGuard
              pageCode="arologis.admin.permissions"
              action="view"
              requireMaster={requireMaster}
            >
              <div data-testid="secure-page">secure</div>
            </PermissionGuard>
          )}
        />
      </Routes>
    </MemoryRouter>,
  )
}

function authSnapshot(role: string) {
  return {
    accessToken: 'access',
    refreshToken: 'refresh',
    userId: 'user-1',
    role,
    loginId: 'tester',
    fullName: '테스터',
    expiresAt: '2026-06-23T00:00:00Z',
  }
}
