/**
 * 관리자 — 창고 관리 (`/admin/warehouses`).
 *
 * Phase 10 P0-5 슬라이스 4. BE `GET /inventory/warehouses/search?q=` backing.
 * 신규 창고 등록은 기존 `/warehouses` 화면 유지 — 본 화면은 관리자용 검색/조회.
 *
 * UUID 비공개 — 화면에는 code / name / type / address / displayOrder 만 표시.
 *
 * data-testid:
 * - admin-warehouses-table
 * - admin-warehouses-search-input
 * - admin-warehouses-row-{code}
 */
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Badge,
  DataTable,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  listAdminWarehouses,
  type AdminWarehouse,
} from '../../api/adminApi'
import { usePageTitle } from '../../hooks/usePageTitle'

const TYPE_LABEL: Record<AdminWarehouse['type'], string> = {
  HEADQUARTERS: '본사',
  VEHICLE: '차량',
  CONSIGNMENT: '위탁',
  VIRTUAL: '가상',
}

const TYPE_VARIANT: Record<
  AdminWarehouse['type'],
  'brand' | 'neutral' | 'success' | 'warning' | 'danger'
> = {
  HEADQUARTERS: 'brand',
  VEHICLE: 'success',
  CONSIGNMENT: 'neutral',
  VIRTUAL: 'warning',
}

export function WarehousesPage() {
  usePageTitle('창고 관리')

  const [q, setQ] = useState('')
  const [page, setPage] = useState(0)

  const query = useQuery({
    queryKey: ['admin', 'warehouses', q, page],
    queryFn: () =>
      listAdminWarehouses({
        q: q || undefined,
        page,
        size: 20,
      }),
  })

  const totalPages = query.data
    ? Math.max(1, Math.ceil(query.data.total / query.data.size))
    : 1

  const columns: DataTableColumn<AdminWarehouse>[] = [
    {
      key: 'code',
      header: '코드',
      width: '120px',
      render: (w) => (
        <span data-testid={`admin-warehouses-row-${w.code}`}>{w.code}</span>
      ),
    },
    { key: 'name', header: '창고명' },
    {
      key: 'type',
      header: '분류',
      width: '110px',
      render: (w) => (
        <Badge variant={TYPE_VARIANT[w.type]}>{TYPE_LABEL[w.type]}</Badge>
      ),
    },
    {
      key: 'displayOrder',
      header: '표시 순서',
      width: '110px',
      align: 'right',
    },
    {
      key: 'address',
      header: '주소',
      render: (w) => w.address ?? '—',
    },
  ]

  return (
    <>
      <h3 style={{ margin: '0 0 16px' }}>창고 관리</h3>

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
          placeholder="코드 / 창고명 / 주소 검색"
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
            setPage(0)
          }}
          data-testid="admin-warehouses-search-input"
          style={{
            flex: '1 1 280px',
            minWidth: 240,
            height: 32,
            padding: '0 10px',
            border: '1px solid #D1D5DB',
            borderRadius: 6,
            fontSize: 13,
          }}
        />
        <a
          href="#/warehouses"
          style={{
            display: 'inline-flex',
            alignItems: 'center',
            padding: '0 14px',
            height: 32,
            border: '1px solid var(--color-brand-300)',
            borderRadius: 6,
            color: 'var(--color-brand-700)',
            background: 'var(--color-brand-50)',
            fontSize: 13,
          }}
        >
          신규 등록 (창고 화면)
        </a>
      </div>

      <div data-testid="admin-warehouses-table">
        <DataTable
          columns={columns}
          rows={query.data?.items ?? []}
          loading={query.isLoading}
          rowKey={(w) => w.id}
          emptyMessage="조건에 맞는 창고가 없습니다."
        />
      </div>

      {query.data && totalPages > 1 ? (
        <div
          style={{
            display: 'flex',
            justifyContent: 'center',
            alignItems: 'center',
            gap: 12,
            marginTop: 16,
            fontSize: 13,
          }}
        >
          <button
            type="button"
            disabled={page <= 0}
            onClick={() => setPage((p) => p - 1)}
            style={pagerBtnStyle}
          >
            이전
          </button>
          <span>
            {page + 1} / {totalPages}
          </span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => setPage((p) => p + 1)}
            style={pagerBtnStyle}
          >
            다음
          </button>
        </div>
      ) : null}
    </>
  )
}

const pagerBtnStyle: React.CSSProperties = {
  height: 28,
  padding: '0 12px',
  border: '1px solid #D1D5DB',
  borderRadius: 4,
  background: '#fff',
  cursor: 'pointer',
  fontSize: 13,
}
