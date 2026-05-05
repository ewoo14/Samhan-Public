/**
 * 시산표 화면 (`/accounting/balances`).
 *
 * 회계월(YYYYMM) 선택 → BE 가 해당 월 분개 합산 + 카테고리 그룹별 표시.
 * 본 슬라이스는 read-only. PDF 출력 등은 추후 슬라이스에서 추가.
 *
 * 권한: ACCOUNTANT / MASTER 만 진입 (RouteGuard).
 */
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Card,
  DataTable,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import { getTrialBalance, type TrialBalanceRow } from '../api/accounting'
import { usePageTitle } from '../hooks/usePageTitle'

const CATEGORY_LABEL: Record<string, string> = {
  '100': '자산',
  '200': '부채',
  '300': '자본',
  '400': '매출',
  '500': '매출원가',
  '800': '판매관리비',
  '900': '영업외',
}

const fmtKrw = (raw: string): string => {
  const n = Number.parseInt(raw, 10)
  if (!Number.isFinite(n)) return raw
  if (n === 0) return '—'
  return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** YYYYMM 현재 월 (한국 시간 클라이언트 local). */
const currentPeriod = (): string => {
  const d = new Date()
  return `${d.getFullYear()}${String(d.getMonth() + 1).padStart(2, '0')}`
}

/** YYYYMM → "2026-05" 표시용. */
const formatPeriod = (period: string): string =>
  `${period.slice(0, 4)}-${period.slice(4, 6)}`

export function TrialBalancePage() {
  const [period, setPeriod] = useState<string>(currentPeriod())

  usePageTitle('시산표', formatPeriod(period))

  const query = useQuery({
    queryKey: ['accounting', 'balances', period],
    queryFn: () => getTrialBalance(period),
  })

  const grouped = useMemo(() => {
    const rows = query.data?.rows ?? []
    const map = new Map<string, TrialBalanceRow[]>()
    for (const r of rows) {
      const list = map.get(r.category) ?? []
      list.push(r)
      map.set(r.category, list)
    }
    return Array.from(map.entries()).sort(([a], [b]) => a.localeCompare(b))
  }, [query.data])

  const columns: DataTableColumn<TrialBalanceRow>[] = [
    { key: 'accountCode', header: '코드', width: '80px' },
    { key: 'accountName', header: '계정명', width: '160px' },
    {
      key: 'openingBalance',
      header: '기초잔액',
      width: '140px',
      align: 'right',
      render: (r) => fmtKrw(r.openingBalance),
    },
    {
      key: 'periodDebit',
      header: '당월 차변',
      width: '140px',
      align: 'right',
      render: (r) => fmtKrw(r.periodDebit),
    },
    {
      key: 'periodCredit',
      header: '당월 대변',
      width: '140px',
      align: 'right',
      render: (r) => fmtKrw(r.periodCredit),
    },
    {
      key: 'closingBalance',
      header: '기말잔액',
      width: '140px',
      align: 'right',
      render: (r) => fmtKrw(r.closingBalance),
    },
  ]

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          marginBottom: 16,
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <h3 style={{ margin: 0 }}>시산표 — {formatPeriod(period)}</h3>
        <label style={{ fontSize: 13, color: '#374151' }}>
          회계월:&nbsp;
          <input
            type="month"
            value={`${period.slice(0, 4)}-${period.slice(4, 6)}`}
            onChange={(e) => {
              const v = e.target.value.replace('-', '')
              if (/^\d{6}$/.test(v)) setPeriod(v)
            }}
            style={{
              height: 32,
              padding: '0 8px',
              borderRadius: 6,
              border: '1px solid #D1D5DB',
              fontSize: 13,
            }}
          />
        </label>
      </div>

      {query.isLoading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 200 }}>
          <Spinner size="lg" label="시산표 불러오는 중" />
        </div>
      ) : query.isError ? (
        <div className="error-banner" role="alert">
          시산표를 불러오지 못했습니다. 백엔드 연결을 확인하세요.
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
                  color: '#111827',
                }}
              >
                {CATEGORY_LABEL[category] ?? category} ({category})
              </div>
              <DataTable
                columns={columns}
                rows={rows}
                rowKey={(r) => r.accountCode}
                emptyMessage="해당 카테고리에 데이터가 없습니다."
              />
            </Card>
          ))}

          {/* 총합 */}
          <Card>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: '80px 160px 140px 140px 140px 140px',
                gap: 8,
                padding: '8px 0',
                fontSize: 14,
                fontWeight: 600,
                fontVariantNumeric: 'tabular-nums',
              }}
            >
              <div />
              <div>총합</div>
              <div />
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totalDebit ?? '0')}
              </div>
              <div style={{ textAlign: 'right' }}>
                {fmtKrw(query.data?.totalCredit ?? '0')}
              </div>
              <div
                style={{
                  textAlign: 'right',
                  color:
                    query.data?.totalDebit === query.data?.totalCredit
                      ? '#059669'
                      : '#DC2626',
                }}
              >
                {query.data?.totalDebit === query.data?.totalCredit
                  ? '균형 ✓'
                  : '불균형'}
              </div>
            </div>
          </Card>
        </>
      )}
    </>
  )
}
