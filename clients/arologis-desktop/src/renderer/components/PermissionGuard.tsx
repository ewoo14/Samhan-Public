/**
 * PermissionGuard — 아로로지스 page-code 기반 라우트 가드.
 *
 * 권한 조회 중에는 보호 화면을 렌더하지 않고, 조회 완료 후 권한이 없으면 홈으로 이동한다.
 */
import type { ReactNode } from 'react'
import { Navigate } from 'react-router-dom'
import { MascotLoader } from '@samhan/design-system'
import { usePermissions } from '../hooks/usePermissions'
import type { PageCode, PermissionLookupAction } from '../api/permissions'

export interface PermissionGuardProps {
  pageCode: PageCode
  action?: PermissionLookupAction
  children: ReactNode
}

export function PermissionGuard({
  pageCode,
  action = 'view',
  children,
}: PermissionGuardProps): JSX.Element {
  const { canAccess, isLoading, isError } = usePermissions()

  if (isLoading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 'calc(100vh - 120px)' }}>
        <MascotLoader size="md" label="권한 확인 중" />
      </div>
    )
  }

  if (isError) {
    return <Navigate to="/" replace />
  }

  if (!canAccess(pageCode, action)) {
    return <Navigate to="/" replace />
  }

  return <>{children}</>
}
