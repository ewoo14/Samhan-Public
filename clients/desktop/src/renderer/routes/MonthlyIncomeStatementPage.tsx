/**
 * 월별손익분석 화면 (`/accounting/reports/income-statement/monthly`).
 *
 * 손익계정 × 1~12월 매트릭스와 당기/전기 연간 비교 컬럼을 표시한다.
 * UUID 비공개 가드: 응답/화면에 UUID 필드를 사용하지 않는다.
 */
import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Button,
  Card,
  DataTable,
  Input,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  getMonthlyIncomeStatement,
  type MonthlyIncomeStatementLine,
  type MonthlyIncomeStatementResponse,
} from '../api/accounting'
import { usePageTitle } from '../hooks/usePageTitle'
import {
  fmtMonthlyKrw,
  isNegativeMonthlyAmount,
  isStrongMonthlyRow,
  monthlyAmountAt,
  rowLabel,
  sectionLabel,
} from './monthlyIncomeStatementPageModel'

function currentYear(): number {
  return new Date().getFullYear()
}

function AmountCell({
  value,
  strong,
}: {
  value: string | number
  strong: boolean
}) {
  return (
    <span
      style={{
        color: isNegativeMonthlyAmount(value)
          ? 'var(--state-danger)'
          : 'var(--color-neutral-900)',
        fontWeight: strong ? 700 : 400,
        fontVariantNumeric: 'tabular-nums',
      }}
    >
      {fmtMonthlyKrw(value)}
    </span>
  )
}

export function MonthlyIncomeStatementPage() {
  const [year, setYear] = useState<number>(currentYear())
  const [queryYear, setQueryYear] = useState<number>(currentYear())

  usePageTitle('월별손익분석', `${queryYear}년`)

  const query = useQuery<MonthlyIncomeStatementResponse>({
    queryKey: ['accounting', 'reports', 'income-statement', 'monthly', queryYear],
    queryFn: () => getMonthlyIncomeStatement(queryYear),
  })

  const columns = useMemo<DataTableColumn<MonthlyIncomeStatementLine>[]>(() => {
    const monthColumns: DataTableColumn<MonthlyIncomeStatementLine>[] = (query.data?.months ?? [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12])
      .map((month) => ({
        key: `month-${month}`,
        header: `${month}월`,
        width: '96px',
        align: 'right',
        render: (row) => (
          <AmountCell
            value={monthlyAmountAt(row, month)}
            strong={isStrongMonthlyRow(row)}
          />
        ),
      }))

    return [
      {
        key: 'section',
        header: '구분',
        width: '150px',
        render: (row) => (
          <span style={{ color: 'var(--color-neutral-600)' }}>
            {sectionLabel(row.section)}
          </span>
        ),
      },
      {
        key: 'accountName',
        header: '계정명',
        width: '220px',
        render: (row) => (
          <span
            style={{
              fontWeight: isStrongMonthlyRow(row) ? 700 : 500,
              color: row.rowKind === 'TOTAL'
                ? 'var(--color-neutral-900)'
                : 'var(--color-neutral-800)',
            }}
          >
            {rowLabel(row)}
          </span>
        ),
      },
      ...monthColumns,
      {
        key: 'annualTotal',
        header: '당기 합계',
        width: '120px',
        align: 'right',
        render: (row) => <AmountCell value={row.annualTotal} strong />,
      },
      {
        key: 'priorYearTotal',
        header: '전기 합계',
        width: '120px',
        align: 'right',
        render: (row) => <AmountCell value={row.priorYearTotal} strong={isStrongMonthlyRow(row)} />,
      },
      {
        key: 'difference',
        header: '증감',
        width: '120px',
        align: 'right',
        render: (row) => <AmountCell value={row.difference} strong={isStrongMonthlyRow(row)} />,
      },
    ]
  }, [query.data?.months])

  return (
    <>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          gap: 16,
          flexWrap: 'wrap',
          marginBottom: 16,
        }}
      >
        <div>
          <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>월별손익분석</h3>
          <div style={{ marginTop: 4, fontSize: 13, color: 'var(--color-neutral-500)' }}>
            손익계정 × 월 매트릭스 · 전기 연간 비교
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <label
              htmlFor="monthly-income-statement-year"
              style={{ fontSize: 12, color: 'var(--color-neutral-700)', fontWeight: 500 }}
            >
              회계연도
            </label>
            <Input
              id="monthly-income-statement-year"
              type="number"
              inputSize="sm"
              min={1900}
              max={2100}
              fullWidth={false}
              value={year}
              onChange={(event) => setYear(Number(event.target.value))}
              style={{ width: 120 }}
            />
          </div>
          <Button
            variant="primary"
            size="sm"
            onClick={() => setQueryYear(year)}
            disabled={query.isFetching || year < 1900 || year > 2100}
          >
            조회
          </Button>
        </div>
      </div>

      {query.data ? (
        <Card style={{ marginBottom: 16 }}>
          <div
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(150px, 1fr))',
              gap: 12,
            }}
          >
            <Summary label="당기" value={`${query.data.fiscalYear}년`} />
            <Summary label="전기" value={`${query.data.priorYear}년`} />
            <Summary label="기간" value={`${query.data.fromDate} ~ ${query.data.toDate}`} />
            <Summary
              label="생성시각"
              value={new Date(query.data.generatedAt).toLocaleString('ko-KR')}
            />
          </div>
        </Card>
      ) : null}

      {query.isLoading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 200 }}>
          <Spinner size="lg" label="월별손익분석 불러오는 중" />
        </div>
      ) : query.isError ? (
        <div className="error-banner" role="alert">
          월별손익분석을 불러오지 못했습니다. 연도와 백엔드 연결을 확인하세요.
        </div>
      ) : query.data ? (
        <Card data-testid="accounting-monthly-income-statement">
          <div style={{ overflowX: 'auto' }}>
            <div style={{ minWidth: 1760 }}>
              <DataTable
                columns={columns}
                rows={query.data.rows}
                rowKey={(row) => `${row.section}:${row.accountCode ?? row.accountName}:${row.sortOrder}`}
                tableLayout="fixed"
                emptyMessage="해당 연도 손익 데이터가 없습니다."
              />
            </div>
          </div>
        </Card>
      ) : null}
    </>
  )
}

function Summary({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ fontSize: 13, color: 'var(--color-neutral-600)' }}>
      <div style={{ marginBottom: 2 }}>{label}</div>
      <div style={{ color: 'var(--color-neutral-900)', fontSize: 15, fontWeight: 700 }}>
        {value}
      </div>
    </div>
  )
}
