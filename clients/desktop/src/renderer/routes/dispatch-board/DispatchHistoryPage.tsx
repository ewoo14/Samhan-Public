import { useMemo, useState } from 'react'
import {
  Badge,
  Button,
  DataTable,
  Input,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import { usePageTitle } from '../../hooks/usePageTitle'
import {
  DISPATCH_TASK_STATUS_LABEL,
  type DispatchTaskStatus,
  type DispatchTaskSummaryResponse,
} from '../../api/dispatchTask'
import { offsetIsoSeoul, todayIsoSeoul } from '../../api/dispatchBoard'
import {
  useDispatchTaskQuery,
  useDispatchTasksQuery,
} from './hooks/useDispatchTask'
import { DispatchTaskDetailModal } from './components/DispatchTaskDetailModal'

const PAGE_SIZE = 20

// "완료배차 이력" = 완료(DISPATCHED) 전용.
// FAILED(배차 불가)·CANCELLED(배차 취소)는 추후 "배차현황" 화면으로 분리한다.
const HISTORY_STATUS_OPTIONS: DispatchTaskStatus[] = ['DISPATCHED']

function statusBadgeVariant(status: DispatchTaskStatus): 'success' | 'danger' | 'neutral' {
  if (status === 'DISPATCHED') return 'success'
  if (status === 'FAILED') return 'danger'
  return 'neutral'
}

export function DispatchHistoryPage() {
  usePageTitle('완료 배차 내역')

  const today = useMemo(() => todayIsoSeoul(), [])
  const [from, setFrom] = useState(() => offsetIsoSeoul(today, -30))
  const [to, setTo] = useState(today)
  const [status, setStatus] = useState<DispatchTaskStatus>('DISPATCHED')
  const [page, setPage] = useState(0)
  const [selectedDetailKey, setSelectedDetailKey] = useState<string | null>(null)

  const listQuery = useDispatchTasksQuery({
    from,
    to,
    status: [status],
    page,
    size: PAGE_SIZE,
  })
  const detailQuery = useDispatchTaskQuery(selectedDetailKey)

  const columns: DataTableColumn<DispatchTaskSummaryResponse>[] = useMemo(
    () => [
      {
        key: 'taskCode',
        header: '배차 작업번호',
        width: '160px',
        render: (row) => (
          <span data-testid={`dispatch-history-row-${row.taskCode}`}>
            {row.taskCode}
          </span>
        ),
      },
      {
        key: 'dispatchDate',
        header: '배차일',
        width: '120px',
      },
      {
        key: 'status',
        header: '상태',
        width: '150px',
        render: (row) => (
          <Badge variant={statusBadgeVariant(row.status)}>
            {DISPATCH_TASK_STATUS_LABEL[row.status]}
          </Badge>
        ),
      },
      {
        key: 'vehicleGroupCount',
        header: '차량',
        width: '80px',
        render: (row) => `${row.vehicleGroupCount}대`,
      },
      {
        key: 'slipCount',
        header: '전표',
        width: '80px',
        render: (row) => `${row.slipCount}건`,
      },
      {
        key: 'partnerNames',
        header: '거래처',
        render: (row) => row.partnerNames || '-',
      },
      {
        key: 'driverCount',
        header: '기사',
        width: '80px',
        render: (row) => `${row.driverCount}명`,
      },
    ],
    [],
  )

  const rows = listQuery.data?.content ?? []
  const totalElements = listQuery.data?.totalElements ?? 0
  const totalPages = listQuery.data?.totalPages ?? 1
  const isFirst = listQuery.data?.first ?? true
  const isLast = listQuery.data?.last ?? true

  const handleApplyFilters = () => {
    setPage(0)
    void listQuery.refetch()
  }

  const handleRowClick = (row: DispatchTaskSummaryResponse) => {
    if (!row.arologisDispatchId) return
    setSelectedDetailKey(row.arologisDispatchId)
  }

  return (
    <div
      data-testid="dispatch-history-page"
      style={{ padding: 16, display: 'flex', flexDirection: 'column', gap: 16 }}
    >
      <section
        aria-label="완료 배차 내역 필터"
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 12,
          flexWrap: 'wrap',
        }}
      >
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          시작일
          <Input
            type="date"
            value={from}
            onChange={(e) => setFrom(e.currentTarget.value)}
            data-testid="dispatch-history-from"
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          종료일
          <Input
            type="date"
            value={to}
            onChange={(e) => setTo(e.currentTarget.value)}
            data-testid="dispatch-history-to"
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          상태
          <select
            value={status}
            onChange={(e) => setStatus(e.currentTarget.value as DispatchTaskStatus)}
            data-testid="dispatch-history-status"
            style={{
              height: 36,
              minWidth: 160,
              border: '1px solid var(--color-neutral-300)',
              borderRadius: 6,
              padding: '0 10px',
              background: 'var(--color-neutral-0)',
            }}
          >
            {HISTORY_STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {DISPATCH_TASK_STATUS_LABEL[option]}
              </option>
            ))}
          </select>
        </label>
        <Button
          type="button"
          variant="primary"
          onClick={handleApplyFilters}
          data-testid="dispatch-history-filter-submit"
        >
          조회
        </Button>
        <span style={{ marginLeft: 'auto', fontSize: 12, color: 'var(--color-neutral-500)' }}>
          총 {totalElements}건
        </span>
      </section>

      {listQuery.isError ? (
        <div
          role="alert"
          style={{
            padding: 12,
            border: '1px solid var(--color-danger-200)',
            borderRadius: 6,
            background: 'var(--color-danger-50)',
            color: 'var(--color-danger-700)',
            fontSize: 13,
          }}
        >
          완료 배차 내역을 불러오지 못했습니다.
        </div>
      ) : null}

      <div data-testid="dispatch-history-table">
        <DataTable
          columns={columns}
          rows={rows}
          loading={listQuery.isLoading}
          rowKey={(row) => row.taskCode}
          emptyMessage="조회 조건에 맞는 완료 배차 내역이 없습니다."
          onRowClick={handleRowClick}
        />
      </div>

      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          gap: 12,
          fontSize: 13,
        }}
      >
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={isFirst}
          onClick={() => setPage((current) => Math.max(0, current - 1))}
          data-testid="dispatch-history-prev"
        >
          이전
        </Button>
        <span>
          {page + 1} / {Math.max(totalPages, 1)}
        </span>
        <Button
          type="button"
          variant="secondary"
          size="sm"
          disabled={isLast}
          onClick={() => setPage((current) => current + 1)}
          data-testid="dispatch-history-next"
        >
          다음
        </Button>
      </div>

      {selectedDetailKey && detailQuery.isLoading ? (
        <div
          data-testid="dispatch-history-detail-loading"
          style={{
            position: 'fixed',
            inset: 0,
            display: 'grid',
            placeItems: 'center',
            background: 'rgba(255,255,255,0.5)',
            zIndex: 999,
          }}
        >
          <Spinner size="md" />
        </div>
      ) : null}

      {selectedDetailKey && detailQuery.data ? (
        <DispatchTaskDetailModal
          task={detailQuery.data}
          readOnly
          onClose={() => setSelectedDetailKey(null)}
        />
      ) : null}
    </div>
  )
}
