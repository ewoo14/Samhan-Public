import { useCallback, useEffect, useRef, useState, type CSSProperties } from 'react'
import { useMutation, useQuery, useQueryClient, type QueryClient, type QueryKey } from '@tanstack/react-query'
import {
  DndContext,
  PointerSensor,
  KeyboardSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import { Card, DragHandle, Select, Spinner } from '@samhan/design-system'
import {
  DOC_TYPES,
  fetchApprovalLineGroups,
  fetchApprovalLineRoles,
  reorderApprovalLineRoles,
  renameApprovalLineRole,
  updateApprovalLineRole,
  type ApprovalLineGroupOption,
  type ApprovalLineRole,
} from '../api/approvalLineConfigApi'
import { usePageTitle } from '../hooks/usePageTitle'

/** 결재라인 설정 — 전표 종류별 역할에 권한 그룹/필수 지정, 드래그 순서변경, 라벨 인라인 편집. */
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

  // ── 그룹/필수 업데이트 뮤테이션 (기존 A2-1 패턴 유지) ──
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

  // ── 라벨 rename 뮤테이션 (Task 3) ──
  const renameMutation = useMutation({
    mutationFn: (value: { id: string; label: string }) =>
      renameApprovalLineRole(value.id, value.label),
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
        (current) => current?.map((role) =>
          role.id === value.id ? { ...role, label: value.label } : role,
        ),
      )
      return { prev }
    },
    onSuccess: () => {
      setToast({ type: 'success', message: '역할 라벨을 변경했습니다.' })
    },
    onError: (_error, _value, context) => {
      restoreApprovalLineRolesSnapshot(queryClient, rolesQueryKey, context?.prev)
      setToast({ type: 'error', message: '라벨 변경 중 오류가 발생했습니다.' })
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

  // ── 드래그 순서 변경 뮤테이션 (Task 4) ──
  const reorderMutation = useMutation({
    mutationFn: (value: { orderedIds: string[] }) =>
      reorderApprovalLineRoles(docType, value.orderedIds),
    onMutate: async (value) => {
      await queryClient.cancelQueries({ queryKey: rolesQueryKey })
      const prev = queryClient.getQueryData<ApprovalLineRole[]>(rolesQueryKey)
      // 낙관적 재정렬: orderedIds 순서대로 sequence 재할당
      queryClient.setQueryData<ApprovalLineRole[]>(
        rolesQueryKey,
        (current) => {
          if (!current) return current
          return value.orderedIds
            .map((id, index) => {
              const role = current.find((r) => r.id === id)
              return role ? { ...role, sequence: index } : null
            })
            .filter((r): r is ApprovalLineRole => r !== null)
        },
      )
      return { prev }
    },
    onSuccess: () => {
      setToast({ type: 'success', message: '결재 역할 순서를 변경했습니다.' })
    },
    onError: (_error, _value, context) => {
      restoreApprovalLineRolesSnapshot(queryClient, rolesQueryKey, context?.prev)
      setToast({ type: 'error', message: '순서 변경 중 오류가 발생했습니다.' })
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: rolesQueryKey })
    },
  })

  // ── 드래그 센서 ──
  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, { coordinateGetter: sortableKeyboardCoordinates }),
  )

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    const roles = rolesQuery.data ?? []
    const orderedIds = computeApprovalRoleReorder(roles, String(active.id), String(over.id))
    if (areApprovalRoleOrdersEqual(orderedIds, getOrderedApprovalRoleIds(roles))) return
    reorderMutation.mutate({ orderedIds })
  }, [rolesQuery.data, reorderMutation])

  useEffect(() => {
    if (!toast) return undefined
    const timer = window.setTimeout(() => setToast(null), 3000)
    return () => window.clearTimeout(timer)
  }, [toast])

  const groups = groupsQuery.data ?? []
  const roles = rolesQuery.data ?? []

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
            <DndContext
              sensors={sensors}
              collisionDetection={closestCenter}
              onDragEnd={handleDragEnd}
            >
              <SortableContext
                items={roles.map((r) => r.id)}
                strategy={verticalListSortingStrategy}
              >
                <table data-testid="approval-line-role-table" style={tableStyle}>
                  <colgroup>
                    <col style={dragColumnStyle} />
                    <col style={sequenceColumnStyle} />
                    <col style={roleColumnStyle} />
                    <col style={groupColumnStyle} />
                    <col style={requiredColumnStyle} />
                  </colgroup>
                  <thead>
                    <tr>
                      <th style={dragHeadCellStyle} aria-label="드래그 핸들" />
                      <th style={sequenceHeadCellStyle}>순서</th>
                      <th style={roleHeadCellStyle}>역할</th>
                      <th style={groupHeadCellStyle}>권한 그룹</th>
                      <th style={requiredHeadCellStyle}>필수</th>
                    </tr>
                  </thead>
                  <tbody>
                    {roles.map((role) => (
                      <SortableApprovalRoleRow
                        key={role.id}
                        role={role}
                        groups={groups}
                        saving={pendingRoleIds.has(role.id)}
                        onSave={(approverGroupId, required) =>
                          updateMutation.mutate({ id: role.id, approverGroupId, required })}
                        onRename={(label) =>
                          renameMutation.mutate({ id: role.id, label })}
                      />
                    ))}
                  </tbody>
                </table>
              </SortableContext>
            </DndContext>
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

// ── Sortable 행 컴포넌트 (dnd-kit) ──
function SortableApprovalRoleRow({
  role,
  groups,
  saving,
  onSave,
  onRename,
}: {
  role: ApprovalLineRole
  groups: ApprovalLineGroupOption[]
  saving: boolean
  onSave: (approverGroupId: string | null, required: boolean) => void
  onRename: (label: string) => void
}) {
  const isCreator = role.stepType === 'CREATOR'

  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: role.id, disabled: isCreator })

  const rowStyle: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
    position: 'relative',
    zIndex: isDragging ? 2 : 'auto',
  }

  return (
    <tr
      ref={setNodeRef}
      style={rowStyle}
      data-testid={`approval-role-${role.label}`}
    >
      <td style={dragBodyCellStyle}>
        {isCreator ? (
          // CREATOR 는 드래그 핸들 없음 — 잠금 아이콘으로 고정 표시
          <span
            aria-label="작성자는 순서 고정"
            title="작성자는 항상 첫 순서입니다"
            style={{ display: 'inline-block', width: 28, textAlign: 'center', color: 'var(--color-neutral-400)' }}
          >
            🔒
          </span>
        ) : (
          <DragHandle
            label={`${role.label} 드래그`}
            listeners={listeners as Record<string, unknown> | undefined}
            attributes={attributes as unknown as Record<string, unknown>}
            setActivatorNodeRef={setActivatorNodeRef}
            dragging={isDragging}
          />
        )}
      </td>
      <td style={sequenceBodyCellStyle}>{role.sequence + 1}</td>
      <td style={roleBodyCellStyle}>
        {isCreator ? (
          // CREATOR 라벨은 정적 텍스트 (편집 불가)
          <strong data-testid={`approval-role-label-static-${role.id}`}>{role.label}</strong>
        ) : (
          <ApprovalRoleLabelInput
            role={role}
            saving={saving}
            onRename={onRename}
          />
        )}
      </td>
      <td style={groupBodyCellStyle}>
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
      <td style={requiredBodyCellStyle}>
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

// ── 라벨 인라인 편집 컴포넌트 (Task 3) ──
function ApprovalRoleLabelInput({
  role,
  saving,
  onRename,
}: {
  role: ApprovalLineRole
  saving: boolean
  onRename: (label: string) => void
}) {
  const [editing, setEditing] = useState(false)
  const [inputValue, setInputValue] = useState(role.label)
  const inputRef = useRef<HTMLInputElement>(null)

  // role.label 외부 변경(낙관/롤백) 시 동기화 (editing 중에는 덮지 않음)
  useEffect(() => {
    if (!editing) {
      setInputValue(role.label)
    }
  }, [role.label, editing])

  useEffect(() => {
    if (!editing) return
    inputRef.current?.focus()
    inputRef.current?.select?.()
  }, [editing])

  function commitEdit() {
    setEditing(false)
    notifyApprovalRoleLabelChange(inputValue, role, onRename)
    setInputValue(inputValue.trim() || role.label)
  }

  if (editing) {
    return (
      <input
        ref={inputRef}
        type="text"
        value={inputValue}
        onChange={(event) => setInputValue(event.target.value)}
        onBlur={commitEdit}
        onKeyDown={(event) => {
          if (event.key === 'Enter') {
            event.preventDefault()
            commitEdit()
          } else if (event.key === 'Escape') {
            setEditing(false)
            setInputValue(role.label)
          }
        }}
        aria-label={`${role.label} 라벨 편집`}
        data-testid={`approval-role-label-input-${role.id}`}
        disabled={saving}
        style={{
          fontSize: 13,
          fontWeight: 700,
          border: '1px solid var(--color-primary-400)',
          borderRadius: 4,
          padding: '2px 6px',
          outline: 'none',
          minWidth: 80,
        }}
      />
    )
  }

  return (
    <button
      type="button"
      onClick={() => {
        setEditing(true)
        setInputValue(role.label)
      }}
      aria-label={`${role.label} 라벨 편집`}
      data-testid={`approval-role-label-btn-${role.id}`}
      disabled={saving}
      title="클릭하여 라벨 편집"
      style={{
        background: 'none',
        border: 'none',
        cursor: 'pointer',
        padding: '2px 4px',
        borderRadius: 4,
        fontSize: 13,
        fontWeight: 700,
        display: 'inline-flex',
        alignItems: 'center',
        gap: 4,
      }}
    >
      <strong>{role.label}</strong>
      <span aria-hidden="true" style={{ fontSize: 11, color: 'var(--color-neutral-400)' }}>✎</span>
    </button>
  )
}

// ── 기존 ApprovalRoleRow (단위테스트·SSR 호환용, CREATOR SSR 테스트 대상) ──
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

// ── 순수 핸들러 / 헬퍼 (단위테스트 대상) ──

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

/**
 * 라벨 인라인 편집 계약 (Task 3).
 * - blank 입력 무시
 * - 동일 값 무시
 * - CREATOR 는 호출 안 함 (호출자가 보장하지만 방어적 체크)
 */
export function notifyApprovalRoleLabelChange(
  label: string,
  role: ApprovalLineRole,
  onRename: (label: string) => void,
) {
  if (role.stepType === 'CREATOR') return
  const trimmed = label.trim()
  if (!trimmed) return
  if (trimmed === role.label) return
  onRename(trimmed)
}

/**
 * 드래그 순서 변경 계약 (Task 4).
 * 작성자(CREATOR) 는 항상 index 0 고정. 비-CREATOR 만 재배치.
 * 작성자 행이 active/over 인 경우 현재 순서 그대로 반환.
 *
 * @param roles 현재 sequence 순으로 정렬된 역할 목록
 * @param activeId 드래그된 행 id
 * @param overId 드롭 대상 행 id
 * @returns orderedIds (id 배열, index 0 = CREATOR 강제)
 */
export function computeApprovalRoleReorder(
  roles: ApprovalLineRole[],
  activeId: string,
  overId: string,
): string[] {
  const sorted = [...roles].sort((a, b) => a.sequence - b.sequence)

  const activeRole = sorted.find((r) => r.id === activeId)
  const overRole = sorted.find((r) => r.id === overId)

  // 작성자가 active/over 인 경우 → 현재 순서 그대로
  if (!activeRole || !overRole) return sorted.map((r) => r.id)
  if (activeRole.stepType === 'CREATOR' || overRole.stepType === 'CREATOR') {
    return sorted.map((r) => r.id)
  }

  // 비-CREATOR 만 arrayMove
  const creator = sorted.find((r) => r.stepType === 'CREATOR')
  const nonCreators = sorted.filter((r) => r.stepType !== 'CREATOR')

  const activeIndex = nonCreators.findIndex((r) => r.id === activeId)
  const overIndex = nonCreators.findIndex((r) => r.id === overId)

  if (activeIndex < 0 || overIndex < 0) return sorted.map((r) => r.id)

  // arrayMove: 같은 배열 내 이동
  const reordered = [...nonCreators]
  reordered.splice(activeIndex, 1)
  reordered.splice(overIndex, 0, nonCreators[activeIndex]!)

  const result = creator ? [creator, ...reordered] : reordered
  return result.map((r) => r.id)
}

export function getOrderedApprovalRoleIds(roles: ApprovalLineRole[]): string[] {
  return [...roles].sort((a, b) => a.sequence - b.sequence).map((role) => role.id)
}

export function areApprovalRoleOrdersEqual(left: string[], right: string[]): boolean {
  return left.length === right.length && left.every((id, index) => id === right[index])
}

const tableStyle: React.CSSProperties = {
  width: '100%',
  tableLayout: 'fixed',
  borderCollapse: 'collapse',
}

const dragColumnStyle: React.CSSProperties = { width: 48 }
const sequenceColumnStyle: React.CSSProperties = { width: 72 }
const roleColumnStyle: React.CSSProperties = { width: '24%' }
const groupColumnStyle: React.CSSProperties = { width: '42%' }
const requiredColumnStyle: React.CSSProperties = { width: 88 }

const headCellStyle: React.CSSProperties = {
  padding: '10px 12px',
  borderBottom: '1px solid var(--color-neutral-200)',
  background: 'var(--color-neutral-50)',
  color: 'var(--color-neutral-600)',
  fontSize: 12,
  textAlign: 'left',
  whiteSpace: 'nowrap',
}

const dragHeadCellStyle: React.CSSProperties = { ...headCellStyle, ...dragColumnStyle }
const sequenceHeadCellStyle: React.CSSProperties = { ...headCellStyle, ...sequenceColumnStyle }
const roleHeadCellStyle: React.CSSProperties = { ...headCellStyle, ...roleColumnStyle }
const groupHeadCellStyle: React.CSSProperties = { ...headCellStyle, ...groupColumnStyle }
const requiredHeadCellStyle: React.CSSProperties = { ...headCellStyle, ...requiredColumnStyle }

const bodyCellStyle: React.CSSProperties = {
  padding: '11px 12px',
  borderBottom: '1px solid var(--color-neutral-200)',
  fontSize: 13,
  verticalAlign: 'middle',
}

const dragBodyCellStyle: React.CSSProperties = { ...bodyCellStyle, ...dragColumnStyle }
const sequenceBodyCellStyle: React.CSSProperties = { ...bodyCellStyle, ...sequenceColumnStyle }
const roleBodyCellStyle: React.CSSProperties = { ...bodyCellStyle, ...roleColumnStyle }
const groupBodyCellStyle: React.CSSProperties = { ...bodyCellStyle, ...groupColumnStyle }
const requiredBodyCellStyle: React.CSSProperties = { ...bodyCellStyle, ...requiredColumnStyle }
