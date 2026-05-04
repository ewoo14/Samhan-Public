/**
 * 출고전표 목록 화면 — `DataTable` + `SlipNumberDisplay` + `SlipStatusBadge`.
 *
 * - 1 페이지 (size=20) 만 조회 — 페이지네이션은 후속 슬라이스
 * - 행 클릭 시 alert 안내 (상세 페이지 미구현)
 * - 우상단 "새 출고전표" 버튼 → `/slips/new`
 */
import { useNavigate } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  Badge,
  Button,
  DataTable,
  SlipNumberDisplay,
  SlipStatusBadge,
  type DataTableColumn,
} from '@samhan/design-system'
import { listSlips, type SlipSummary } from '../api/slip'
import { useSessionStore, canCreateSlip } from '../stores/session'

export function SlipListPage() {
  const navigate = useNavigate()
  const role = useSessionStore((s) => s.auth?.role)

  const query = useQuery({
    queryKey: ['slips', 'list'],
    queryFn: () => listSlips({ page: 0, size: 20 }),
  })

  const columns: DataTableColumn<SlipSummary>[] = [
    {
      key: 'slipNo',
      header: '전표번호',
      width: '180px',
      render: (row) => (
        <SlipNumberDisplay
          slipDate={row.slipDate}
          seqNo={row.seqNo}
          size="sm"
          uuid={row.id}
        />
      ),
    },
    {
      key: 'slipType',
      header: '구분',
      width: '90px',
      render: (row) => (
        <Badge variant={row.slipType === 'OUTBOUND' ? 'brand' : 'success'}>
          {row.slipType === 'OUTBOUND' ? '출고' : '입고'}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: '상태',
      width: '120px',
      render: (row) => <SlipStatusBadge status={row.status} />,
    },
    { key: 'partnerName', header: '거래처' },
  ]

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
        }}
      >
        <h3 style={{ margin: 0 }}>출고전표 목록</h3>
        {canCreateSlip(role) ? (
          <Button variant="primary" onClick={() => navigate('/slips/new')}>
            새 출고전표
          </Button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={query.data?.content ?? []}
        loading={query.isLoading}
        rowKey={(slip) => slip.id}
        onRowClick={(slip) =>
          alert(`상세 화면은 후속 슬라이스에서 제공됩니다.\n전표 ID: ${slip.id}`)
        }
        emptyMessage="등록된 전표가 없습니다."
      />

      {query.isError ? (
        <div className="error-banner" role="alert" style={{ marginTop: 16 }}>
          전표 목록을 불러오지 못했습니다. 백엔드 연결을 확인하세요.
        </div>
      ) : null}
    </>
  )
}
