/**
 * 아로로지스 현재 사용자 page-code 권한 API.
 *
 * BE `GET /admin/arologis/permissions/my` 응답을 메인 desktop 과 같은
 * `MyPermission[]` 형태로 정규화한다. 권한 데이터가 없거나 page/action 이 없으면
 * fail-closed 로 false 를 반환한다.
 */
import { apiClient, type ApiEnvelope } from './client'

export type PageCode =
  | 'arologis.hr.employees'
  | 'arologis.hr.departments'
  | 'arologis.accounting.cashbook'
  | 'arologis.accounting.accounts'
  | 'arologis.admin.permissions'
  | string

export const PERMISSION_ACTIONS = [
  'view',
  'create',
  'update',
  'delete',
  'restore',
  'download',
  'print',
] as const

export type PermissionAction = (typeof PERMISSION_ACTIONS)[number]
export type PermissionLookupAction = PermissionAction | 'edit' | string

export interface MyPermission {
  pageCode: PageCode
  actions: PermissionAction[]
}

/** 기존 edit 액션은 BE PermissionAction.UPDATE 와 동일하게 취급한다. */
export function normalizePermissionAction(action: PermissionLookupAction): PermissionAction {
  const normalized = String(action).toLowerCase()
  return (normalized === 'edit' ? 'update' : normalized) as PermissionAction
}

function actionsFromRaw(value: unknown): PermissionAction[] {
  if (!Array.isArray(value)) return []
  return value
    .map((raw) => normalizePermissionAction(String(raw)))
    .filter((raw): raw is PermissionAction =>
      (PERMISSION_ACTIONS as readonly string[]).includes(raw),
    )
}

export async function fetchMyPermissions(): Promise<MyPermission[]> {
  const res = await apiClient.get<ApiEnvelope<Record<string, string[]>>>(
    '/admin/arologis/permissions/my',
  )
  return Object.entries(res.data.data ?? {}).map(([pageCode, rawActions]) => ({
    pageCode,
    actions: actionsFromRaw(rawActions),
  }))
}

/** 주어진 권한 데이터에서 page-code/action 접근 가능 여부를 fail-closed 로 판정한다. */
export function canAccess(
  permissions: MyPermission[] | null | undefined,
  pageCode: string,
  action: PermissionLookupAction = 'view',
): boolean {
  if (!permissions) return false
  const entry = permissions.find((permission) => permission.pageCode === pageCode)
  if (!entry) return false
  return entry.actions.includes(normalizePermissionAction(action))
}
