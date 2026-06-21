import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
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

  const rolesQuery = useQuery({
    queryKey: ['admin', 'approval-line-config', docType],
    queryFn: () => fetchApprovalLineRoles(docType),
  })

  const groupsQuery = useQuery({
    queryKey: ['admin', 'approval-line-config', 'groups'],
    queryFn: fetchApprovalLineGroups,
  })

  const updateMutation = useMutation({
    mutationFn: (value: { id: string; approverGroupId: string | null; required: boolean }) =>
      updateApprovalLineRole(value.id, { approverGroupId: value.approverGroupId, required: value.required }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'approval-line-config', docType] })
      setToast({ type: 'success', message: '결재라인 설정을 저장했습니다.' })
    },
    onError: () => setToast({ type: 'error', message: '저장 중 오류가 발생했습니다.' }),
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
                    saving={updateMutation.isPending}
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
  const [groupId, setGroupId] = useState(role.approverGroupId ?? '')
  const [required, setRequired] = useState(role.required)

  useEffect(() => {
    setGroupId(role.approverGroupId ?? '')
    setRequired(role.required)
  }, [role.approverGroupId, role.required])

  function save(nextGroupId: string, nextRequired: boolean) {
    onSave(nextGroupId === '' ? null : nextGroupId, nextRequired)
  }

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
            value={groupId}
            onChange={(event) => {
              const nextGroupId = event.target.value
              setGroupId(nextGroupId)
              save(nextGroupId, required)
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
          checked={required}
          disabled={isCreator || saving}
          onChange={(event) => {
            const nextRequired = event.target.checked
            setRequired(nextRequired)
            save(groupId, nextRequired)
          }}
          aria-label={`${role.label} 필수`}
          data-testid={`approval-role-required-${role.label}`}
        />
      </td>
    </tr>
  )
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
