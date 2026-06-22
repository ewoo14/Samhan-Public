/**
 * 합계잔액시산표 화면 (`/accounting/balances`).
 *
 * 이월잔액(from-1 누적) + 임의기간 차변/대변 합계 + eCount 4컬럼
 * (차변 잔액/합계, 대변 합계/잔액)을 조회한다.
 */
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Badge,
  Button,
  Card,
  DataTable,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  getTrialBalanceSummary,
  type TrialBalanceGranularity,
  type TrialBalanceSummaryLine,
} from '../api/accounting'
import { usePageTitle } from '../hooks/usePageTitle'

const today = (): string => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const currentMonth = (): string => today().slice(0, 7)

const monthRange = (month: string): { from: string; to: string } => {
  const [year, monthText] = month.split('-')
  const lastDay = new Date(Number(year), Number(monthText), 0).getDate()
  return {
    from: `${year}-${monthText}-01`,
    to: `${year}-${monthText}-${String(lastDay).padStart(2, '0')}`,
  }
}

const fmtKrw = (raw: string): string => {
  const n = Number(raw)
  if (!Number.isFinite(n)) return raw
  if (n === 0) return '—'
  return n.toLocaleString('ko-KR', { maximumFractionDigits: 0 })
}

const amountColor = (raw: string): string => {
  const n = Number(raw)
  if (!Number.isFinite(n) || n >= 0) return 'var(--color-neutral-900)'
  return 'var(--state-danger)'
}

const dateLabel = (from: string, to: string): string =>
  from === to ? from : `${from} ~ ${to}`

function AmountCell({ value }: { value: string }) {
  return (
    <span
      style={{
        color: amountColor(value),
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {fmtKrw(value)}
    </span>
  )
}

export function TrialBalancePage() {
  const [granularity, setGranularity] = useState<TrialBalanceGranularity>('MONTH')
  const [date, setDate] = useState<string>(today())
  const [month, setMonth] = useState<string>(currentMonth())
  const initialMonthRange = monthRange(currentMonth())
  const [from, setFrom] = useState<string>(initialMonthRange.from)
  const [to, setTo] = useState<string>(initialMonthRange.to)

  const queryRange = useMemo(() => {
    if (granularity === 'DAY') {
      return { from: date, to: date }
    }
    if (granularity === 'MONTH') {
      return monthRange(month)
    }
    return { from, to }
  }, [date, from, granularity, month, to])

  usePageTitle('합계잔액시산표', dateLabel(queryRange.from, queryRange.to))

  const query = useQuery({
    queryKey: ['accounting', 'trial-balance-summary', queryRange.from, queryRange.to, granularity],
    queryFn: () => getTrialBalanceSummary(queryRange.from, queryRange.to, granularity),
  })

  const grouped = useMemo(() => {
    const rows = query.data?.rows ?? []
    const map = new Map<string, TrialBalanceSummaryLine[]>()
    for (const row of rows) {
      const key = row.categoryDisplayName || row.category
      const list = map.get(key) ?? []
      list.push(row)
      map.set(key, list)
    }
    return Array.from(map.entries())
  }, [query.data])

  const columns: DataTableColumn<TrialBalanceSummaryLine>[] = [
    { key: 'accountCode', header: '코드', width: '72px' },
    {
      key: 'openingBalance',
      header: '이월잔액',
      width: '130px',
      align: 'right',
      render: (row) => <AmountCell value={row.openingBalance} />,
    },
    {
      key: 'debitBalance',
      header: '차변 잔액',
      width: '130px',
      align: 'right',
      render: (row) => <AmountCell value={row.debitBalance} />,
    },
    {
      key: 'debitTotal',
      header: '차변 합계',
      width: '130px',
      align: 'right',
      render: (row) => <AmountCell value={row.debitTotal} />,
    },
    { key: 'accountName', header: '계정명', width: '180px' },
    {
      key: 'creditTotal',
      header: '대변 합계',
      width: '130px',
      align: 'right',
      render: (row) => <AmountCell value={row.creditTotal} />,
    },
    {
      key: 'creditBalance',
      header: '대변 잔액',
      width: '130px',
      align: 'right',
      render: (row) => <AmountCell value={row.creditBalance} />,
    },
  ]

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-start',
          gap: 16,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <div>
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>합계잔액시산표</h3>
          <div style={{ marginTop: 4, fontSize: 13, color: 'var(--color-neutral-500)' }}>
            {dateLabel(queryRange.from, queryRange.to)}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          {(['DAY', 'MONTH', 'RANGE'] as TrialBalanceGranularity[]).map((unit) => (
            <Button
              key={unit}
              variant={granularity === unit ? 'primary' : 'ghost'}
              size="sm"
              onClick={() => setGranularity(unit)}
            >
              {unit === 'DAY' ? '일' : unit === 'MONTH' ? '월' : '기간'}
            </Button>
          ))}
          {granularity === 'DAY' ? (
            <input
              type="date"
              value={date}
              onChange={(event) => setDate(event.target.value)}
              style={{
                height: 32,
                padding: '0 8px',
                borderRadius: 6,
                border: '1px solid var(--color-border)',
                fontSize: 13,
              }}
            />
          ) : null}
          {granularity === 'MONTH' ? (
            <input
              type="month"
              value={month}
              onChange={(event) => setMonth(event.target.value)}
              style={{
                height: 32,
                padding: '0 8px',
                borderRadius: 6,
                border: '1px solid var(--color-border)',
                fontSize: 13,
              }}
            />
          ) : null}
          {granularity === 'RANGE' ? (
            <>
              <input
                type="date"
                value={from}
                onChange={(event) => setFrom(event.target.value)}
                style={{
                  height: 32,
                  padding: '0 8px',
                  borderRadius: 6,
                  border: '1px solid var(--color-border)',
                  fontSize: 13,
                }}
              />
              <input
                type="date"
                value={to}
                onChange={(event) => setTo(event.target.value)}
                style={{
                  height: 32,
                  padding: '0 8px',
                  borderRadius: 6,
                  border: '1px solid var(--color-border)',
                  fontSize: 13,
                }}
              />
            </>
          ) : null}
        </div>
      </div>

      {query.data ? (
        <Card
          data-testid="accounting-trial-balance-summary"
          style={{ marginBottom: 16 }}
        >
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
              gap: 12,
              alignItems: 'center',
            }}
          >
            <SummaryItem label="이월잔액" value={query.data.totals.openingBalanceTotal} />
            <SummaryItem label="차변 합계" value={query.data.totals.debitTotal} />
            <SummaryItem label="대변 합계" value={query.data.totals.creditTotal} />
            <SummaryItem label="차변 잔액" value={query.data.totals.debitBalanceTotal} />
            <SummaryItem label="대변 잔액" value={query.data.totals.creditBalanceTotal} />
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 13, fontWeight: 600, color: 'var(--color-neutral-700)' }}>
                균형
              </span>
              <Badge variant={query.data.totals.balanced ? 'success' : 'danger'}>
                {query.data.totals.balanced ? '일치' : '불일치'}
              </Badge>
            </div>
          </div>
        </Card>
      ) : null}

      {query.isLoading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 200 }}>
          <Spinner size="lg" label="합계잔액시산표 불러오는 중" />
        </div>
      ) : query.isError ? (
        <div className="error-banner" role="alert">
          합계잔액시산표를 불러오지 못했습니다. 기간과 백엔드 연결을 확인하세요.
        </div>
      ) : (
        <>
          {grouped.map(([category, rows]) => (
            <Card key={category} style={{ marginBottom: 16 }}>
              <div
                style={{
                  marginBottom: 8,
                  fontSize: 14,
                  fontWeight: 600,
                  color: 'var(--color-neutral-900)',
                }}
              >
                {category}
              </div>
              <DataTable
                columns={columns}
                rows={rows}
                rowKey={(row) => row.accountCode}
                emptyMessage="해당 구분에 데이터가 없습니다."
              />
            </Card>
          ))}

          <Card>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '72px 130px 130px 130px 180px 130px 130px',
                gap: 8,
                padding: '8px 0',
                fontSize: 14,
                fontWeight: 600,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              <div />
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totals.openingBalanceTotal ?? '0')}
              </div>
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totals.debitBalanceTotal ?? '0')}
              </div>
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totals.debitTotal ?? '0')}
              </div>
              <div>총합</div>
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totals.creditTotal ?? '0')}
              </div>
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totals.creditBalanceTotal ?? '0')}
              </div>
            </div>
          </Card>
        </>
      )}
    </>
  )
}

function SummaryItem({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ fontSize: 13, color: 'var(--color-neutral-600)' }}>
      <div style={{ marginBottom: 2 }}>{label}</div>
      <div
        style={{
          color: amountColor(value),
          fontSize: 16,
          fontWeight: 700,
          fontVariantNumeric: 'tabular-nums',
        }}
      >
        {fmtKrw(value)}
      </div>
    </div>
  )
}
