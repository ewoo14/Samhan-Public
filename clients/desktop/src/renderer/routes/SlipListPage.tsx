/**
 * 전표 목록 화면 (출고/입고 공용) — slip-output-format 슬라이스 v2.
 *
 * 변경사항 (PR #18 → 본 슬라이스):
 * - 단일 `/slips` 라우트 폐기, mode prop 으로 OUTBOUND (`/sales`) / INBOUND (`/purchases`) 분리
 * - DataTable 컬럼에서 ID 컬럼 미포함 (UUID 비공개 가드)
 * - 행 클릭 시 alert 가 아닌 상세 페이지로 navigate (`/sales/:id` 또는 `/purchases/:id`)
 *
 * 사용 컴포넌트:
 * - `DataTable` (rows + columns)
 * - `SlipNumberDisplay` (uuid prop 제거됨 — 비즈니스 식별자만)
 * - `SlipStatusBadge`
 * - `Badge` (구분: 출고/입고)
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
import { listSlips, type SlipSummary, type SlipType } from '../api/slip'
import { useSessionStore, canCreateSlip } from '../stores/session'

export interface SlipListPageProps {
  /** OUTBOUND (판매조회) 또는 INBOUND (구매조회). */
  mode: SlipType
}

export function SlipListPage({ mode }: SlipListPageProps) {
  const navigate = useNavigate()
  const role = useSessionStore((s) => s.auth?.role)
  const isOutbound = mode === 'OUTBOUND'
  const basePath = isOutbound ? '/sales' : '/purchases'
  const titleLabel = isOutbound ? '판매조회 (출고전표)' : '구매조회 (입고전표)'
  const newButtonLabel = isOutbound ? '새 출고전표' : '새 입고전표'

  const query = useQuery({
    queryKey: ['slips', 'list', mode],
    queryFn: () => listSlips({ slipType: mode, page: 0, size: 20 }),
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
        <h3 style={{ margin: 0 }}>{titleLabel}</h3>
        {canCreateSlip(role) ? (
          <Button variant="primary" onClick={() => navigate(`${basePath}/new`)}>
            {newButtonLabel}
          </Button>
        ) : null}
      </div>

      <DataTable
        columns={columns}
        rows={query.data?.content ?? []}
        loading={query.isLoading}
        rowKey={(slip) => slip.id}
        onRowClick={(slip) => navigate(`${basePath}/${slip.id}`)}
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
