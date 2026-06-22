/**
 * 현재 로그인 사용자의 아로로지스 page-code 권한 조회 hook.
 *
 * TanStack Query 5분 캐시를 사용하고, 조회 결과를 module-level 캐시에 반영해
 * 동기 `canAccess()` 판정도 같은 데이터로 동작하게 한다.
 */
import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  fetchMyPermissions,
  normalizePermissionAction,
  setPermissionsCache,
  type MyPermission,
  type PageCode,
  type PermissionLookupAction,
} from '../api/permissions'

export interface UsePermissionsResult {
  canAccess: (pageCode: PageCode, action?: PermissionLookupAction) => boolean
  permissions: MyPermission[] | undefined
  isLoading: boolean
  isError: boolean
}

export function usePermissions(): UsePermissionsResult {
  const query = useQuery({
    queryKey: ['permissions', 'my'],
    queryFn: fetchMyPermissions,
    staleTime: 5 * 60 * 1000,
    retry: 1,
  })

  useEffect(() => {
    if (query.data) {
      setPermissionsCache(query.data)
    }
  }, [query.data])

  function canAccess(
    pageCode: PageCode,
    action: PermissionLookupAction = 'view',
  ): boolean {
    if (!query.data) return false
    const entry = query.data.find((permission) => permission.pageCode === pageCode)
    if (!entry) return false
    return entry.actions.includes(normalizePermissionAction(action))
  }

  return {
    canAccess,
    permissions: query.data,
    isLoading: query.isLoading,
    isError: query.isError,
  }
}
