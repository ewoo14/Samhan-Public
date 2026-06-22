/**
 * arologis-desktop 개발용 in-process mock.
 *
 * 현재 패키지는 별도 Playwright/MSW mock 인프라가 없으므로, 메인 desktop 의
 * VITE_MOCK_MODE axios adapter 패턴을 최소 복제한다. 처리하지 않는 URL 은 null 을
 * 반환해 기존 실 API 흐름을 유지한다.
 */
import type { AxiosRequestConfig } from 'axios'
import { useAuthStore } from '../stores/authStore'

type PermissionMap = Record<string, string[]>

interface MockEnvelope<T> {
  success: true
  code: 'SUCCESS'
  message: string
  data: T
  timestamp: string
}

const ALL_ADMIN_PERMISSIONS: PermissionMap = {
  'arologis.hr.employees': fullActions(),
  'arologis.hr.departments': fullActions(),
  'arologis.accounting.cashbook': fullActions(),
  'arologis.accounting.accounts': fullActions(),
  'arologis.admin.permissions': fullActions(),
}

const ROLE_PERMISSION_FIXTURES: Record<string, PermissionMap> = {
  AROLOGIS_MASTER: ALL_ADMIN_PERMISSIONS,
  AROLOGIS_MANAGER: {
    'arologis.hr.employees': fullActions(),
    'arologis.hr.departments': fullActions(),
    'arologis.accounting.cashbook': fullActions(),
  },
  AROLOGIS_DEVELOPER: {
    'arologis.accounting.cashbook': fullActions(),
  },
  AROLOGIS_ACCOUNTANT: {
    'arologis.accounting.cashbook': fullActions(),
    'arologis.accounting.accounts': fullActions(),
  },
  AROLOGIS_SALES: {},
  AROLOGIS_DRIVER: {},
}

let permissionFixtures: Record<string, PermissionMap> = clonePermissionFixtures(ROLE_PERMISSION_FIXTURES)

export function isMockMode(): boolean {
  return import.meta.env['VITE_MOCK_MODE'] === '1'
}

export function resetArologisPermissionMock(): void {
  permissionFixtures = clonePermissionFixtures(ROLE_PERMISSION_FIXTURES)
}

export function setArologisPermissionMock(role: string, permissions: PermissionMap): void {
  permissionFixtures[role] = clonePermissionMap(permissions)
}

export function getMockResponse(config: AxiosRequestConfig): unknown | null {
  const method = (config.method ?? 'get').toLowerCase()
  const url = String(config.url ?? '')

  if (method === 'get' && url.endsWith('/admin/arologis/permissions/my')) {
    const role = useAuthStore.getState().auth?.role ?? ''
    return envelope(clonePermissionMap(permissionFixtures[role] ?? {}))
  }

  return null
}

function envelope<T>(data: T): MockEnvelope<T> {
  return {
    success: true,
    code: 'SUCCESS',
    message: 'OK',
    data,
    timestamp: new Date().toISOString(),
  }
}

function fullActions(): string[] {
  return ['VIEW', 'CREATE', 'UPDATE', 'DELETE', 'RESTORE', 'DOWNLOAD', 'PRINT']
}

function clonePermissionFixtures(fixtures: Record<string, PermissionMap>): Record<string, PermissionMap> {
  return Object.fromEntries(
    Object.entries(fixtures).map(([role, permissions]) => [role, clonePermissionMap(permissions)]),
  )
}

function clonePermissionMap(permissions: PermissionMap): PermissionMap {
  return Object.fromEntries(
    Object.entries(permissions).map(([pageCode, actions]) => [pageCode, [...actions]]),
  )
}
