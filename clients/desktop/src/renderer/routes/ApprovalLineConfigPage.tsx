import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query'
import { Card, Select, Spinner } from '@samhan/design-system'
import {
  DOC_TYPES,
  fetchApprovalLineGroups,
  fetchApprovalLineRoles,
  updateApprovalLineRole,
  type ApprovalLineGroupOption,
  type ApprovalLineRole,
} from '../api/approvalLineConfigApi'
import { usePageTitle } from '../hooks/usePageTitle'

/** 결재라인 설정 — 전표 종류별 역할에 권한 그룹/필수 지정(선언적, enforcement=A2-2). */
export function ApprovalLineConfigPage() {
  usePageTitle('결재라인 설정')

  const queryClient = useQueryClient()
  const [docType, setDocType] = useState(DOC_TYPES[0]!.value)
  const [toast, setToast] = useState<{ type: 'success' | 'error'; message: string } | null>(null)
  const [pendingRoleIds, setPendingRoleIds] = useState<Set<string>>(() => new Set())
  const rolesQueryKey = approvalLineRolesQueryKey(docType)

  const rolesQuery = useQuery({
    queryKey: rolesQueryKey,
    queryFn: () => fetchApprovalLineRoles(docType),
  })

  const groupsQuery = useQuery({
    queryKey: ['admin', 'approval-line-config', 'groups'],
    queryFn: fetchApprovalLineGroups,
  })

  const updateMutation = useMutation({
    mutationFn: (value: { id: string; approverGroupId: string | null; required: boolean }) =>
      updateApprovalLineRole(value.id, { approverGroupId: value.approverGroupId, required: value.required }),
    onMutate: async (value) => {
      setPendingRoleIds((prev) => {
        const next = new Set(prev)
        next.add(value.id)
        return next
      })
      await queryClient.cancelQueries({ queryKey: rolesQueryKey })
      const prev = queryClient.getQueryData<ApprovalLineRole[]>(rolesQueryKey)
      queryClient.setQueryData<ApprovalLineRole[]>(
        rolesQueryKey,
        (current) => optimisticallyUpdateApprovalLineRoles(current, value),
      )
      return { prev }
    },
    onSuccess: () => {
      setToast({ type: 'success', message: '결재라인 설정을 저장했습니다.' })
    },
    onError: (_error, _value, context) => {
      restoreApprovalLineRolesSnapshot(queryClient, rolesQueryKey, context?.prev)
      setToast({ type: 'error', message: '저장 중 오류가 발생했습니다.' })
    },
    onSettled: (_data, _error, value) => {
      setPendingRoleIds((prev) => {
        const next = new Set(prev)
        next.delete(value.id)
        return next
      })
      void queryClient.invalidateQueries({ queryKey: rolesQueryKey })
    },
  })

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(null), 3000)
    return () => window.clearTimeout(timer)
  }, [toast])

  const groups = groupsQuery.data ?? []

  return (
    <div data-testid="approval-line-config-page" style={{ maxWidth: 1120 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 16, marginBottom: 12 }}>
        <div>
          <h3 style={{ margin: 0 }}>결재라인 설정</h3>
          <p style={{ margin: '4px 0 0', color: 'var(--color-neutral-500)', fontSize: 13 }}>
            전표 종류별 결재 역할에 권한 그룹과 필수여부를 지정합니다.
          </p>
        </div>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 180 }}>
          <span style={{ fontSize: 13, fontWeight: 700 }}>전표 종류</span>
          <Select
            value={docType}
            onChange={(event) => setDocType(event.target.value)}
            aria-label="전표 종류"
            data-testid="approval-line-doc-type-select"
          >
            {DOC_TYPES.map((type) => (
              <option key={type.value} value={type.value}>{type.label}</option>
            ))}
          </Select>
        </label>
      </div>

      <Card style={{ padding: 0, overflow: 'hidden' }}>
        {rolesQuery.isLoading ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: 48 }}><Spinner /></div>
        ) : null}

        {rolesQuery.isError ? (
          <div style={{ padding: 24, color: 'var(--color-danger-600)' }}>
            결재라인 설정 정보를 불러오지 못했습니다.
          </div>
        ) : null}

        {!rolesQuery.isLoading && !rolesQuery.isError ? (
          <>
            {groupsQuery.isError ? (
              <div style={{ padding: '10px 12px', color: 'var(--color-warning-700)', fontSize: 13 }}>
                권한 그룹 목록을 불러오지 못했습니다. 역할 목록은 계속 표시됩니다.
              </div>
            ) : null}
            <table data-testid="approval-line-role-table" style={tableStyle}>
              <thead>
                <tr>
                  <th style={headCellStyle}>순서</th>
                  <th style={headCellStyle}>역할</th>
                  <th style={headCellStyle}>권한 그룹</th>
                  <th style={headCellStyle}>필수</th>
                </tr>
              </thead>
              <tbody>
                {(rolesQuery.data ?? []).map((role) => (
                  <ApprovalRoleRow
                    key={role.id}
                    role={role}
                    groups={groups}
                    saving={pendingRoleIds.has(role.id)}
                    onSave={(approverGroupId, required) =>
                      updateMutation.mutate({ id: role.id, approverGroupId, required })}
                  />
                ))}
              </tbody>
            </table>
          </>
        ) : null}
      </Card>

      {toast ? (
        <div
          role="status"
          aria-live="polite"
          data-testid="approval-line-toast"
          style={{
            position: 'fixed',
            right: 24,
            bottom: 24,
            zIndex: 100,
            borderRadius: 8,
            padding: '10px 14px',
            background: toast.type === 'success' ? 'var(--color-success-600)' : 'var(--color-danger-600)',
            color: 'var(--color-neutral-0)',
            boxShadow: 'var(--shadow-lg)',
            fontSize: 13,
          }}
        >
          {toast.message}
        </div>
      ) : null}
    </div>
  )
}

export function ApprovalRoleRow({
  role,
  groups,
  saving,
  onSave,
}: {
  role: ApprovalLineRole
  groups: ApprovalLineGroupOption[]
  saving: boolean
  onSave: (approverGroupId: string | null, required: boolean) => void
}) {
  const isCreator = role.stepType === 'CREATOR'

  return (
    <tr data-testid={`approval-role-${role.label}`}>
      <td style={bodyCellStyle}>{role.sequence + 1}</td>
      <td style={bodyCellStyle}>
        <strong>{role.label}</strong>
      </td>
      <td style={bodyCellStyle}>
        {isCreator ? (
          <span data-testid="approval-line-creator-auto" style={{ color: 'var(--color-neutral-500)' }}>
            전표 작성자 자동
          </span>
        ) : (
          <Select
            value={role.approverGroupId ?? ''}
            onChange={(event) => {
              const nextGroupId = event.target.value
              notifyApprovalRoleGroupChange(onSave, nextGroupId, role.required)
            }}
            aria-label={`${role.label} 권한 그룹`}
            data-testid={`approval-role-group-${role.label}`}
            disabled={saving}
            style={{ minWidth: 220 }}
          >
            <option value="">(미지정)</option>
            {groups.map((group) => (
              <option key={group.id} value={group.id}>{group.name}</option>
            ))}
          </Select>
        )}
      </td>
      <td style={bodyCellStyle}>
        <input
          type="checkbox"
          checked={role.required}
          disabled={isCreator || saving}
          onChange={(event) => {
            const nextRequired = event.target.checked
            notifyApprovalRoleRequiredChange(onSave, role.approverGroupId ?? '', nextRequired)
          }}
          aria-label={`${role.label} 필수`}
          data-testid={`approval-role-required-${role.label}`}
        />
      </td>
    </tr>
  )
}

type ApprovalRoleSaveHandler = (approverGroupId: string | null, required: boolean) => void
type ApprovalRoleUpdateValue = { id: string; approverGroupId: string | null; required: boolean }

export function approvalLineRolesQueryKey(documentType: string) {
  return ['admin', 'approval-line-config', documentType] as const
}

export function optimisticallyUpdateApprovalLineRoles(
  current: ApprovalLineRole[] | undefined,
  value: ApprovalRoleUpdateValue,
) {
  return current?.map((role) => role.id === value.id
    ? {
        ...role,
        approverGroupId: value.approverGroupId,
        approverGroupName: value.approverGroupId == null ? null : role.approverGroupName,
        required: value.required,
      }
    : role)
}

export function restoreApprovalLineRolesSnapshot(
  queryClient: QueryClient,
  queryKey: QueryKey,
  prev: ApprovalLineRole[] | undefined,
) {
  if (prev) {
    queryClient.setQueryData(queryKey, prev)
  }
}

/** 권한그룹 Select 자동저장 계약. 빈 문자열은 그룹 해제(null)로 전송한다. */
export function notifyApprovalRoleGroupChange(
  onSave: ApprovalRoleSaveHandler,
  nextGroupId: string,
  currentRequired: boolean,
) {
  onSave(nextGroupId === '' ? null : nextGroupId, currentRequired)
}

/** 필수 여부 checkbox 자동저장 계약. 현재 권한그룹 선택값과 다음 required 값을 전송한다. */
export function notifyApprovalRoleRequiredChange(
  onSave: ApprovalRoleSaveHandler,
  currentGroupId: string,
  nextRequired: boolean,
) {
  onSave(currentGroupId === '' ? null : currentGroupId, nextRequired)
}

const tableStyle: React.CSSProperties = {
  width: '100%',
  borderCollapse: 'collapse',
}

const headCellStyle: React.CSSProperties = {
  padding: '10px 12px',
  borderBottom: '1px solid var(--color-neutral-200)',
  background: 'var(--color-neutral-50)',
  color: 'var(--color-neutral-600)',
  fontSize: 12,
  textAlign: 'left',
  whiteSpace: 'nowrap',
}

const bodyCellStyle: React.CSSProperties = {
  padding: '11px 12px',
  borderBottom: '1px solid var(--color-neutral-200)',
  fontSize: 13,
  verticalAlign: 'middle',
}
