/**
 * 관리자 — 거래처 관리 (`/admin/partners`).
 *
 * Phase 10 P0-5 슬라이스 4. BE `GET /admin/partners/search?q=&type=` backing.
 *
 * UUID 비공개 — 모든 식별자는 partnerCode (사업자번호 / 상호 / 전화번호 표시 가능).
 *
 * <h2>PR-H4c FE-C 보강 — 실시간 동기화</h2>
 * <ul>
 *   <li>30초 polling — 거래처 신규/상태 변경/발송금지 등록 결과 자동 반영.</li>
 *   <li>partner-service SSE (PR-H4b BE-A): {@code GET /admin/partners/{entityId}/realtime}
 *       (entity-id 단위). admin list 화면은 broadcast endpoint 합류 전까지 polling fallback.</li>
 *   <li>BlockedPartnersPage 와 cache 공유 — 양쪽 화면 변경이 서로 반영됨.</li>
 * </ul>
 *
 * data-testid:
 * - admin-partners-table
 * - admin-partners-search-input
 * - admin-partners-status-filter
 * - admin-partners-row-{partnerCode}
 * - admin-partners-realtime-indicator
 */
import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Badge,
  DataTable,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  listAdminPartners,
  PARTNER_STATUS_LABEL,
  type PartnerStatus,
  type PartnerSummary,
} from '../../api/adminApi'
import { usePageTitle } from '../../hooks/usePageTitle'

const STATUS_VARIANT: Record<
  PartnerStatus,
  'brand' | 'neutral' | 'success' | 'warning' | 'danger'
> = {
  ACTIVE: 'success',
  SUSPENDED: 'warning',
  TERMINATED: 'danger',
}

/** KRW 정수 (string 또는 number) → "₩1,234,567" 표시. */
function formatKrw(raw: string | number | null | undefined): string {
  if (raw === null || raw === undefined) return '—'
  const n = typeof raw === 'string' ? Number.parseFloat(raw) : raw
  if (!Number.isFinite(n)) return '—'
  return '₩' + Math.round(n).toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

export function PartnersPage() {
  usePageTitle('거래처 관리')

  const [q, setQ] = useState('')
  const [type, setType] = useState<PartnerStatus | ''>('')
  const [page, setPage] = useState(0)

  const query = useQuery({
    queryKey: ['admin', 'partners', q, type, page],
    queryFn: () =>
      listAdminPartners({
        q: q || undefined,
        type: type || undefined,
        page,
        size: 20,
      }),
    // PR-H4c FE-C: 30초 polling — 멀티 워크스테이션 동기화 안전망 (BE broadcast SSE 합류 전 단계).
    refetchInterval: 30_000,
  })

  const totalPages = query.data
    ? Math.max(1, Math.ceil(query.data.total / query.data.size))
    : 1

  const columns: DataTableColumn<PartnerSummary>[] = [
    { key: 'partnerCode', header: '거래처 코드', width: '140px' },
    {
      key: 'name',
      header: '상호',
      render: (p) => (
        <span data-testid={`admin-partners-row-${p.partnerCode}`}>
          {p.name}
        </span>
      ),
    },
    { key: 'bizNo', header: '사업자번호', width: '140px' },
    {
      key: 'phone',
      header: '전화',
      width: '140px',
      render: (p) => p.phone ?? '—',
    },
    {
      key: 'status',
      header: '상태',
      width: '110px',
      render: (p) => (
        <Badge variant={STATUS_VARIANT[p.status]}>
          {PARTNER_STATUS_LABEL[p.status]}
        </Badge>
      ),
    },
    {
      key: 'creditLimit',
      header: '신용한도',
      width: '140px',
      align: 'right',
      render: (p) => formatKrw(p.creditLimit),
    },
    {
      key: 'outstandingBalance',
      header: '미수금',
      width: '140px',
      align: 'right',
      render: (p) => formatKrw(p.outstandingBalance),
    },
  ]

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
        <h3 style={{ margin: 0 }}>거래처 관리</h3>
        <span
          data-testid="admin-partners-realtime-indicator"
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
          placeholder="코드 / 상호 / 사업자번호 / 전화 검색"
          value={q}
          onChange={(e) => {
            setQ(e.target.value)
            setPage(0)
          }}
          data-testid="admin-partners-search-input"
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
        <select
          value={type}
          onChange={(e) => {
            setType(e.target.value as PartnerStatus | '')
            setPage(0)
          }}
          data-testid="admin-partners-status-filter"
          style={{
            height: 32,
            padding: '0 10px',
            border: '1px solid #D1D5DB',
            borderRadius: 6,
            fontSize: 13,
          }}
        >
          <option value="">상태 전체</option>
          <option value="ACTIVE">{PARTNER_STATUS_LABEL.ACTIVE}</option>
          <option value="SUSPENDED">{PARTNER_STATUS_LABEL.SUSPENDED}</option>
          <option value="TERMINATED">{PARTNER_STATUS_LABEL.TERMINATED}</option>
        </select>
      </div>

      <div data-testid="admin-partners-table">
        <DataTable
          columns={columns}
          rows={query.data?.items ?? []}
          loading={query.isLoading}
          rowKey={(p) => p.partnerCode}
          emptyMessage="조건에 맞는 거래처가 없습니다."
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
