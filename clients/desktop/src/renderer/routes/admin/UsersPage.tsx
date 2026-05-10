/**
 * 관리자 — 사용자 관리 (`/admin/users`).
 *
 * Phase 10 P0-5 슬라이스 4. BE `GET /admin/users` (q/role/dept 필터) backing.
 *
 * 표시 컬럼 (UUID 비공개): 로그인ID / 이름 / 부서 / 권한 (한국어 라벨) / 상태 (활성/잠금).
 * 액션 (MASTER 전용 — RoleGuard 가드 우선, 컬럼 버튼은 disable=false 의 안전장치):
 * - 비활성화 / 재활성화 (terminationDate toggle)
 * - 권한 변경 (Modal — Role select + reason)
 * - 권한 변경 이력 조회 (Dialog)
 *
 * <h2>PR-H4c FE-C 보강 — 실시간 동기화</h2>
 * <ul>
 *   <li>30초 polling refetchInterval — 멀티 워크스테이션 동기화 안전망 (SlipEditRequestsPage 패턴).</li>
 *   <li>BE user-service 가 PR-H4b BE-D 로 user:edit / user:edit-request:* SSE 채널 노출 (entity-id 단위).
 *       admin list 화면은 단일 entityId 가 없으므로 broadcast endpoint 합류 전까지 polling fallback 유지.</li>
 *   <li>헤더 우측 "실시간 자동 갱신" 안내 — 사용자에게 cache 갱신 주기 명시.</li>
 * </ul>
 *
 * data-testid:
 * - admin-users-table
 * - admin-user-disable-button
 * - admin-user-enable-button
 * - admin-user-role-change
 * - admin-user-role-history
 * - admin-user-search-input
 * - admin-user-role-filter
 * - admin-user-dept-filter
 * - admin-user-role-change-modal / admin-user-role-history-modal
 *
 * memory feedback_role_naming_full — role label 풀네임 (BE Role.displayName 사용).
 */
import { useMemo, useState, type FormEvent } from 'react'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  Badge,
  Button,
  DataTable,
  FormField,
  Modal,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  ADMIN_ROLE_LABEL,
  disableAdminUser,
  enableAdminUser,
  listAdminRoles,
  listAdminUsers,
  listDepartments,
  listRoleHistory,
  updateAdminUserRole,
  type AdminRole,
  type AdminUser,
  type RoleHistoryEntry,
} from '../../api/adminApi'
import { usePageTitle } from '../../hooks/usePageTitle'

export function UsersPage() {
  usePageTitle('사용자 관리')
  const queryClient = useQueryClient()

  const [q, setQ] = useState('')
  const [role, setRole] = useState<AdminRole | ''>('')
  const [departmentId, setDepartmentId] = useState('')
  const [page, setPage] = useState(0)

  const [roleModal, setRoleModal] = useState<AdminUser | null>(null)
  const [historyModal, setHistoryModal] = useState<AdminUser | null>(null)

  const usersQuery = useQuery({
    queryKey: ['admin', 'users', q, role, departmentId, page],
    queryFn: () =>
      listAdminUsers({
        q: q || undefined,
        role: role || undefined,
        departmentId: departmentId || undefined,
        page,
        size: 20,
      }),
    // PR-H4c FE-C: 30초 polling — 멀티 워크스테이션 동기화 안전망 (BE broadcast SSE 합류 전 단계).
    refetchInterval: 30_000,
  })

  const rolesQuery = useQuery({
    queryKey: ['admin', 'roles'],
    queryFn: listAdminRoles,
  })

  const departmentsQuery = useQuery({
    queryKey: ['admin', 'departments'],
    queryFn: listDepartments,
  })

  const disableMutation = useMutation({
    mutationFn: disableAdminUser,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })

  const enableMutation = useMutation({
    mutationFn: enableAdminUser,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'users'] })
    },
  })

  const columns: DataTableColumn<AdminUser>[] = useMemo(
    () => [
      { key: 'loginId', header: '로그인ID', width: '140px' },
      { key: 'fullName', header: '이름', width: '120px' },
      {
        key: 'departmentName',
        header: '부서',
        width: '140px',
        render: (u) => u.departmentName,
      },
      {
        key: 'role',
        header: '권한',
        width: '110px',
        render: (u) => ADMIN_ROLE_LABEL[u.role],
      },
      {
        key: 'terminationDate',
        header: '상태',
        width: '90px',
        render: (u) =>
          u.terminationDate ? (
            <Badge variant="danger">잠금</Badge>
          ) : (
            <Badge variant="success">활성</Badge>
          ),
      },
      {
        key: 'id',
        header: '관리',
        render: (u) => (
          <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
            {u.terminationDate ? (
              <Button
                variant="ghost"
                size="sm"
                data-testid="admin-user-enable-button"
                onClick={(e) => {
                  e.stopPropagation()
                  enableMutation.mutate(u.id)
                }}
              >
                재활성화
              </Button>
            ) : (
              <Button
                variant="ghost"
                size="sm"
                data-testid="admin-user-disable-button"
                onClick={(e) => {
                  e.stopPropagation()
                  if (window.confirm(`${u.fullName} 사용자를 비활성화합니다.`)) {
                    disableMutation.mutate(u.id)
                  }
                }}
              >
                비활성화
              </Button>
            )}
            <Button
              variant="ghost"
              size="sm"
              data-testid="admin-user-role-change"
              onClick={(e) => {
                e.stopPropagation()
                setRoleModal(u)
              }}
            >
              권한 변경
            </Button>
            <Button
              variant="ghost"
              size="sm"
              data-testid="admin-user-role-history"
              onClick={(e) => {
                e.stopPropagation()
                setHistoryModal(u)
              }}
            >
              이력
            </Button>
          </div>
        ),
      },
    ],
    [disableMutation, enableMutation],
  )

  const totalPages = usersQuery.data
    ? Math.max(1, Math.ceil(usersQuery.data.total / usersQuery.data.size))
    : 1

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'baseline',
          marginBottom: 16,
        }}
      >
        <h3 style={{ margin: 0 }}>사용자 관리</h3>
        <span
          data-testid="admin-users-realtime-indicator"
          style={{ fontSize: 12, color: 'var(--color-neutral-500)' }}
        >
          실시간 자동 갱신 · 30초
        </span>
      </div>

      <div
        style={{
          display: 'flex',
          gap: 12,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <input
          type="search"
          placeholder="로그인ID / 이름 / 이메일 검색"
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
            setPage(0)
          }}
          data-testid="admin-user-search-input"
          style={{
            flex: '1 1 240px',
            minWidth: 200,
            height: 32,
            padding: '0 10px',
            border: '1px solid #D1D5DB',
            borderRadius: 6,
            fontSize: 13,
          }}
        />
        <select
          value={role}
          onChange={(e) => {
            setRole(e.target.value as AdminRole | '')
            setPage(0)
          }}
          data-testid="admin-user-role-filter"
          style={selectStyle}
        >
          <option value="">권한 전체</option>
          {(rolesQuery.data ?? []).map((r) => (
            <option key={r} value={r}>
              {ADMIN_ROLE_LABEL[r]}
            </option>
          ))}
        </select>
        <select
          value={departmentId}
          onChange={(e) => {
            setDepartmentId(e.target.value)
            setPage(0)
          }}
          data-testid="admin-user-dept-filter"
          style={selectStyle}
        >
          <option value="">부서 전체</option>
          {(departmentsQuery.data ?? []).map((d) => (
            <option key={d.id} value={d.id}>
              {d.name}
            </option>
          ))}
        </select>
      </div>

      <div data-testid="admin-users-table">
        <DataTable
          columns={columns}
          rows={usersQuery.data?.items ?? []}
          loading={usersQuery.isLoading}
          rowKey={(u) => u.id}
          emptyMessage="조건에 맞는 사용자가 없습니다."
        />
      </div>

      {usersQuery.data && totalPages > 1 ? (
        <Pagination
          page={page}
          totalPages={totalPages}
          onChange={setPage}
        />
      ) : null}

      {roleModal ? (
        <div data-testid="admin-user-role-change-modal">
          <RoleChangeModal
            user={roleModal}
            roles={rolesQuery.data ?? []}
            onClose={() => setRoleModal(null)}
            onCommitted={() => {
              setRoleModal(null)
              void queryClient.invalidateQueries({
                queryKey: ['admin', 'users'],
              })
            }}
          />
        </div>
      ) : null}

      {historyModal ? (
        <div data-testid="admin-user-role-history-modal">
          <RoleHistoryModal
            user={historyModal}
            onClose={() => setHistoryModal(null)}
          />
        </div>
      ) : null}
    </>
  )
}

const selectStyle = {
  height: 32,
  padding: '0 10px',
  border: '1px solid #D1D5DB',
  borderRadius: 6,
  fontSize: 13,
} as const

interface PaginationProps {
  page: number
  totalPages: number
  onChange: (page: number) => void
}

function Pagination({ page, totalPages, onChange }: PaginationProps) {
  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        gap: 12,
        marginTop: 16,
      }}
    >
      <Button
        variant="ghost"
        size="sm"
        disabled={page <= 0}
        onClick={() => onChange(page - 1)}
      >
        이전
      </Button>
      <span style={{ fontSize: 13 }}>
        {page + 1} / {totalPages}
      </span>
      <Button
        variant="ghost"
        size="sm"
        disabled={page + 1 >= totalPages}
        onClick={() => onChange(page + 1)}
      >
        다음
      </Button>
    </div>
  )
}

interface RoleChangeModalProps {
  user: AdminUser
  roles: AdminRole[]
  onClose: () => void
  onCommitted: () => void
}

function RoleChangeModal({
  user,
  roles,
  onClose,
  onCommitted,
}: RoleChangeModalProps) {
  const [role, setRole] = useState<AdminRole>(user.role)
  const [reason, setReason] = useState('')

  const mutation = useMutation({
    mutationFn: () =>
      updateAdminUserRole(user.id, {
        role,
        reason: reason.trim() || undefined,
      }),
    onSuccess: () => onCommitted(),
  })

  const handleSubmit = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (mutation.isPending) return
    mutation.mutate()
  }

  return (
    <Modal
      open
      onClose={onClose}
      title={`권한 변경 — ${user.fullName} (${user.loginId})`}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button
            variant="primary"
            onClick={() => mutation.mutate()}
            loading={mutation.isPending}
            disabled={role === user.role}
          >
            적용
          </Button>
        </>
      }
    >
      <form
        onSubmit={handleSubmit}
        style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
      >
        <FormField
          label="현재 권한"
          render={() => (
            <div style={{ fontSize: 13 }}>
              {ADMIN_ROLE_LABEL[user.role]}
            </div>
          )}
        />
        <FormField
          label="신규 권한"
          required
          render={({ id }) => (
            <select
              id={id}
              value={role}
              onChange={(e) => setRole(e.target.value as AdminRole)}
              style={selectStyle}
            >
              {roles.map((r) => (
                <option key={r} value={r}>
                  {ADMIN_ROLE_LABEL[r]}
                </option>
              ))}
            </select>
          )}
        />
        <FormField
          label="변경 사유"
          render={({ id }) => (
            <textarea
              id={id}
              value={reason}
              onChange={(e) => setReason(e.target.value)}
              maxLength={500}
              rows={3}
              style={{
                padding: 8,
                border: '1px solid #D1D5DB',
                borderRadius: 6,
                fontSize: 13,
                fontFamily: 'inherit',
                resize: 'vertical',
              }}
            />
          )}
        />
        {mutation.isError ? (
          <div className="error-banner" role="alert">
            권한 변경에 실패했습니다.
          </div>
        ) : null}
      </form>
    </Modal>
  )
}

interface RoleHistoryModalProps {
  user: AdminUser
  onClose: () => void
}

function RoleHistoryModal({ user, onClose }: RoleHistoryModalProps) {
  const query = useQuery({
    queryKey: ['admin', 'role-history', user.id],
    queryFn: () => listRoleHistory(user.id),
  })

  const columns: DataTableColumn<RoleHistoryEntry>[] = [
    {
      key: 'changedAt',
      header: '변경 시각',
      width: '180px',
      render: (h) => h.changedAt.replace('T', ' ').slice(0, 19),
    },
    {
      key: 'previousRole',
      header: '이전',
      width: '100px',
      render: (h) =>
        h.previousRole ? ADMIN_ROLE_LABEL[h.previousRole] : '(신규)',
    },
    {
      key: 'newRole',
      header: '변경 후',
      width: '100px',
      render: (h) => ADMIN_ROLE_LABEL[h.newRole],
    },
    {
      key: 'reason',
      header: '사유',
      render: (h) => h.reason ?? '—',
    },
    {
      key: 'changedBy',
      header: '변경자',
      width: '120px',
      render: (h) => h.changedBy ?? '—',
    },
  ]

  return (
    <Modal
      open
      onClose={onClose}
      title={`권한 변경 이력 — ${user.fullName} (${user.loginId})`}
      footer={
        <Button variant="primary" onClick={onClose}>
          닫기
        </Button>
      }
    >
      <DataTable
        columns={columns}
        rows={query.data ?? []}
        loading={query.isLoading}
        rowKey={(h) => h.id}
        emptyMessage="변경 이력이 없습니다."
      />
    </Modal>
  )
}
