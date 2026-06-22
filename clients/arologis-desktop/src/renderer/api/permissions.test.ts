import { beforeEach, describe, expect, it, vi } from 'vitest'
import { canAccess, fetchMyPermissions, type MyPermission } from './permissions'
import { apiClient } from './client'

vi.mock('./client', () => ({
  apiClient: {
    get: vi.fn(),
  },
}))

const mockedGet = vi.mocked(apiClient.get)

describe('arologis permissions', () => {
  beforeEach(() => {
    mockedGet.mockReset()
  })

  it('권한 캐시나 데이터가 없으면 fail-closed 로 거부한다', () => {
    expect(canAccess(null, 'arologis.hr.employees', 'view')).toBe(false)
    expect(canAccess(undefined, 'arologis.hr.employees', 'view')).toBe(false)
  })

  it('edit lookup action 은 update 권한으로 정규화한다', () => {
    const permissions: MyPermission[] = [
      { pageCode: 'arologis.hr.employees', actions: ['update'] },
    ]

    expect(canAccess(permissions, 'arologis.hr.employees', 'edit')).toBe(true)
  })

  it('BE 대문자 PermissionAction enum 응답을 소문자 action 으로 정규화한다', async () => {
    mockedGet.mockResolvedValueOnce({
      data: {
        data: {
          'arologis.hr.employees': ['VIEW', 'EDIT', 'DELETE', 'UNKNOWN'],
        },
      },
    })

    await expect(fetchMyPermissions()).resolves.toEqual([
      {
        pageCode: 'arologis.hr.employees',
        actions: ['view', 'update', 'delete'],
      },
    ])
  })

  it('page-code 는 정확히 일치해야 하며 다른 page 는 거부한다', () => {
    const permissions: MyPermission[] = [
      { pageCode: 'arologis.hr.employees', actions: ['view'] },
    ]

    expect(canAccess(permissions, 'arologis.hr.employees', 'view')).toBe(true)
    expect(canAccess(permissions, 'arologis.hr.departments', 'view')).toBe(false)
  })

  it('권한 entry 의 actions 가 비어 있으면 fail-closed 로 거부한다', () => {
    const permissions: MyPermission[] = [
      { pageCode: 'arologis.hr.employees', actions: [] },
    ]

    expect(canAccess(permissions, 'arologis.hr.employees', 'view')).toBe(false)
  })
})
