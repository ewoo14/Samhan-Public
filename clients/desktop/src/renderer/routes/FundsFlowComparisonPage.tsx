/**
 * 자금 입출금내역 2기간 비교 화면 (`/accounting/reports/funds-flow-comparison`).
 *
 * 공식 현금흐름표(`/accounting/reports/cash-flow`)와 별개인 자금관리 보고서다.
 * 현금성 계정의 증가/감소를 상대계정별로 분해하고, 당기와 직전 동일길이 기간을 나란히 표시한다.
 */
import { useMemo, useState } from 'react'
import type React from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, Card, Spinner } from '@samhan/design-system'
import {
  getFundsFlowComparison,
  type FundsFlowComparisonResponse,
  type FundsFlowPeriod,
} from '../api/accounting'
import { usePageTitle } from '../hooks/usePageTitle'
import { buildRows } from './fundsFlowComparisonPageModel'
import { fmtFundsKrw, isNegativeAmount } from './fundsStatusPageModel'

function isoToday(): string {
  return new Date().toISOString().slice(0, 10)
}

function isoMonthStart(): string {
  const d = new Date()
  d.setDate(1)
  return d.toISOString().slice(0, 10)
}

function amountStyle(raw: string | number): React.CSSProperties {
  return {
    fontVariantNumeric: 'tabular-nums',
    color: isNegativeAmount(raw) ? 'var(--state-danger)' : undefined,
    fontWeight: isNegativeAmount(raw) ? 700 : undefined,
  }
}

function AmountCell({ value, strong = false }: { value: string; strong?: boolean }) {
  return (
    <span style={{ ...amountStyle(value), fontWeight: strong ? 700 : amountStyle(value).fontWeight }}>
      {fmtFundsKrw(value)}
    </span>
  )
}

function PeriodMeta({ title, period }: { title: string; period: FundsFlowPeriod }) {
  return (
    <div
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: 4,
        padding: '8px 10px',
        border: '1px solid var(--color-border)',
        borderRadius: 6,
        minWidth: 220,
      }}
    >
      <span style={{ fontSize: 12, color: 'var(--color-neutral-500)' }}>{title}</span>
      <strong style={{ fontSize: 13 }}>{period.fromDate} ~ {period.toDate}</strong>
    </div>
  )
}

export function FundsFlowComparisonPage() {
  const [from, setFrom] = useState<string>(isoMonthStart())
  const [to, setTo] = useState<string>(isoToday())
  const [queryRange, setQueryRange] = useState(() => ({ from: isoMonthStart(), to: isoToday() }))

  usePageTitle('자금 입출금내역', `${queryRange.from} ~ ${queryRange.to}`)

  const query = useQuery<FundsFlowComparisonResponse>({
    queryKey: ['accounting', 'reports', 'funds-flow-comparison', queryRange.from, queryRange.to],
    queryFn: () => getFundsFlowComparison(queryRange.from, queryRange.to),
  })

  const rows = useMemo(() => buildRows(query.data), [query.data])

  const handleSearch = () => {
    setQueryRange({ from, to })
  }

  return (
    <>
      <div
        className="no-print"
        style={{
          display: 'flex',
          alignItems: 'flex-end',
          gap: 12,
          marginBottom: 16,
          flexWrap: 'wrap',
        }}
      >
        <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>자금 입출금내역</h3>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          시작일
          <input
            type="date"
            value={from}
            onChange={(event) => setFrom(event.target.value)}
            style={{
              height: 32,
              padding: '0 8px',
              borderRadius: 6,
              border: '1px solid var(--color-border)',
            }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          종료일
          <input
            type="date"
            value={to}
            onChange={(event) => setTo(event.target.value)}
            style={{
              height: 32,
              padding: '0 8px',
              borderRadius: 6,
              border: '1px solid var(--color-border)',
            }}
          />
        </label>
        <Button
          variant="primary"
          size="sm"
          onClick={handleSearch}
          disabled={query.isFetching || !from || !to}
        >
          조회
        </Button>
      </div>

      {query.isLoading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 220 }}>
          <Spinner size="lg" label="자금 입출금내역 불러오는 중" />
        </div>
      ) : query.isError ? (
        <div className="error-banner" role="alert">
          자금 입출금내역을 불러오지 못했습니다. 백엔드 연결을 확인하세요.
        </div>
      ) : query.data ? (
        <Card data-testid="accounting-funds-flow-comparison">
          <div
            style={{
              display: 'flex',
              gap: 12,
              flexWrap: 'wrap',
              justifyContent: 'space-between',
              marginBottom: 14,
            }}
          >
            <PeriodMeta title="당기" period={query.data.current} />
            <PeriodMeta title="직전 동일기간" period={query.data.prior} />
          </div>

          {!query.data.current.reconciled || !query.data.prior.reconciled ? (
            <div className="error-banner" role="alert" style={{ marginBottom: 12 }}>
              자금 잔액 검산이 불일치합니다. 기초잔액 + 증가소계 - 감소소계와 기말잔액을 확인하세요.
            </div>
          ) : null}

          <div
            style={{
              overflowX: 'auto',
              opacity: query.isFetching && !query.isLoading ? 0.55 : 1,
              transition: 'opacity 120ms ease',
            }}
          >
            <table
              data-testid="accounting-funds-flow-comparison-table"
              style={{
                width: '100%',
                minWidth: 720,
                borderCollapse: 'collapse',
                fontSize: 13,
              }}
            >
              <thead>
                <tr>
                  <th style={headerStyle}>구분</th>
                  <th style={headerStyle}>상대계정</th>
                  <th style={{ ...headerStyle, textAlign: 'right' }}>당기</th>
                  <th style={{ ...headerStyle, textAlign: 'right' }}>직전기간</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => {
                  const isSection = row.kind === 'section'
                  const isStrong = row.kind === 'balance' || row.kind === 'subtotal'
                  return (
                    <tr
                      key={row.key}
                      className={row.kind === 'balance' ? 'report-grand-total-row' : row.kind === 'subtotal' ? 'report-total-row' : undefined}
                    >
                      <td style={{ ...cellStyle, fontWeight: isStrong || isSection ? 700 : 500 }}>
                        {row.label}
                      </td>
                      <td style={{ ...cellStyle, color: isSection ? 'var(--color-neutral-700)' : undefined }}>
                        {isSection ? '' : row.accountName}
                      </td>
                      <td style={{ ...cellStyle, textAlign: 'right' }}>
                        {isSection ? '' : <AmountCell value={row.current} strong={isStrong} />}
                      </td>
                      <td style={{ ...cellStyle, textAlign: 'right' }}>
                        {isSection ? '' : <AmountCell value={row.prior} strong={isStrong} />}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        </Card>
      ) : null}
    </>
  )
}

const headerStyle: React.CSSProperties = {
  padding: '8px 10px',
  borderBottom: '1px solid var(--color-border)',
  color: 'var(--color-neutral-600)',
  fontWeight: 700,
  textAlign: 'left',
  background: 'var(--color-bg-muted)',
}

const cellStyle: React.CSSProperties = {
  padding: '7px 10px',
  borderBottom: '1px solid var(--color-border)',
  verticalAlign: 'middle',
}
