/**
 * 계정명세서 화면 (`/accounting/reports/account-statement`).
 *
 * 특정 기준일의 계정×거래처 잔액 스냅샷을 표시한다.
 */
import { useMemo, useState } from 'react'
import type React from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Button,
  Card,
  DataTable,
  Spinner,
  type DataTableColumn,
} from '@samhan/design-system'
import {
  getAccountStatement,
  type AccountStatementAccountSection,
  type AccountStatementLine,
} from '../api/accounting'
import { usePageTitle } from '../hooks/usePageTitle'

function isoToday(): string {
  return new Date().toISOString().slice(0, 10)
}

function amountNumber(raw: string | number | null | undefined): number {
  if (raw == null || raw === '') return 0
  const parsed = typeof raw === 'number' ? raw : Number.parseFloat(raw)
  return Number.isFinite(parsed) ? parsed : 0
}

function isNegativeAmount(raw: string | number | null | undefined): boolean {
  return amountNumber(raw) < 0
}

function fmtAmount(raw: string | number | null | undefined): string {
  const value = amountNumber(raw)
  if (value === 0) return '—'
  const rounded = Math.round(Math.abs(value))
  const text = rounded.toLocaleString('ko-KR')
  return value < 0 ? `-${text}` : text
}

function amountStyle(raw: string | number | null | undefined): React.CSSProperties {
  return {
    fontVariantNumeric: 'tabular-nums',
    color: isNegativeAmount(raw) ? 'var(--state-danger)' : undefined,
    fontWeight: isNegativeAmount(raw) ? 700 : undefined,
  }
}

function AmountText({ value }: { value: string }) {
  return <span style={amountStyle(value)}>{fmtAmount(value)}</span>
}

function sectionRowKey(row: AccountStatementLine): string {
  return `${row.accountCode}:${row.partnerName}`
}

function TotalBand({
  label,
  balance,
}: {
  label: string
  balance: string
}) {
  return (
    <Card data-testid="accounting-account-statement-total" style={{ marginTop: 16 }}>
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: 16,
          fontSize: 14,
          fontWeight: 700,
        }}
      >
        <span>{label}</span>
        <span style={amountStyle(balance)}>{fmtAmount(balance)} 원</span>
      </div>
    </Card>
  )
}

export function AccountStatementPage() {
  const [asOfDate, setAsOfDate] = useState<string>(isoToday())
  const [accountCode, setAccountCode] = useState<string>('')
  const [query, setQuery] = useState(() => ({ asOfDate: isoToday(), accountCode: '' }))

  usePageTitle('계정명세서', query.asOfDate)

  const statementQuery = useQuery({
    queryKey: ['accounting', 'reports', 'account-statement', query.asOfDate, query.accountCode],
    queryFn: () => getAccountStatement(query.asOfDate, query.accountCode || undefined),
  })

  const columns = useMemo<DataTableColumn<AccountStatementLine>[]>(() => [
    {
      key: 'partnerName',
      header: '거래처명',
      width: '220px',
      render: (row) => (
        <span style={{ fontWeight: 600 }}>
          {row.partnerName}
        </span>
      ),
    },
    {
      key: 'increase',
      header: '증가누계',
      width: '130px',
      align: 'right',
      render: (row) => <AmountText value={row.increase} />,
    },
    {
      key: 'decrease',
      header: '감소누계',
      width: '130px',
      align: 'right',
      render: (row) => <AmountText value={row.decrease} />,
    },
    {
      key: 'debitTotal',
      header: '차변누계',
      width: '130px',
      align: 'right',
      render: (row) => <AmountText value={row.debitTotal} />,
    },
    {
      key: 'creditTotal',
      header: '대변누계',
      width: '130px',
      align: 'right',
      render: (row) => <AmountText value={row.creditTotal} />,
    },
    {
      key: 'balance',
      header: '잔액',
      width: '140px',
      align: 'right',
      render: (row) => <AmountText value={row.balance} />,
    },
  ], [])

  const handleSearch = () => {
    setQuery({ asOfDate, accountCode: accountCode.trim() })
  }

  const groups = statementQuery.data?.groups ?? []
  const totalBalance = statementQuery.data?.total.balance ?? '0'

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
        <h3 style={{ margin: 0, fontSize: 18, fontWeight: 700 }}>계정명세서</h3>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          기준일
          <input
            type="date"
            value={asOfDate}
            onChange={(event) => setAsOfDate(event.target.value)}
            style={{
              height: 32,
              padding: '0 8px',
              borderRadius: 6,
              border: '1px solid var(--color-border)',
            }}
          />
        </label>
        <label style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 12 }}>
          계정코드
          <input
            type="text"
            inputMode="numeric"
            value={accountCode}
            onChange={(event) => setAccountCode(event.target.value)}
            style={{
              height: 32,
              width: 120,
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
          disabled={statementQuery.isFetching || !asOfDate}
        >
          조회
        </Button>
      </div>

      {statementQuery.isLoading ? (
        <div style={{ display: 'grid', placeItems: 'center', minHeight: 220 }}>
          <Spinner size="lg" label="계정명세서 불러오는 중" />
        </div>
      ) : statementQuery.isError ? (
        <div className="error-banner" role="alert">
          계정명세서를 불러오지 못했습니다. 백엔드 연결을 확인하세요.
        </div>
      ) : (
        <>
          {groups.map((group) => (
            <Card
              key={group.groupCode}
              data-testid={`accounting-account-statement-group-${group.groupCode}`}
              style={{ marginBottom: 16 }}
            >
              <div
                style={{
                  marginBottom: 12,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'space-between',
                  gap: 12,
                }}
              >
                <div>
                  <div style={{ fontSize: 15, fontWeight: 700 }}>{group.groupName}</div>
                  <div style={{ marginTop: 2, fontSize: 12, color: 'var(--color-neutral-500)' }}>
                    {group.balanceDirection === 'DEBIT' ? '차변잔액' : '대변잔액'}
                  </div>
                </div>
                <div style={{ fontSize: 12, color: 'var(--color-neutral-500)' }}>
                  소계 잔액 <strong style={amountStyle(group.subtotal.balance)}>
                    {fmtAmount(group.subtotal.balance)}
                  </strong>
                </div>
              </div>

              {group.accounts.map((section: AccountStatementAccountSection) => (
                <div key={section.accountCode} style={{ marginBottom: 14 }}>
                  <div
                    style={{
                      marginBottom: 8,
                      display: 'flex',
                      alignItems: 'center',
                      gap: 8,
                      flexWrap: 'wrap',
                    }}
                  >
                    <span
                      style={{
                        padding: '1px 6px',
                        borderRadius: 6,
                        background: 'var(--color-bg-muted)',
                        color: 'var(--color-neutral-600)',
                        fontSize: 12,
                        fontWeight: 700,
                        fontVariantNumeric: 'tabular-nums',
                      }}
                    >
                      {section.accountCode}
                    </span>
                    <span style={{ fontSize: 13, fontWeight: 700 }}>
                      {section.accountName}
                    </span>
                    <span style={{ fontSize: 12, color: 'var(--color-neutral-500)' }}>
                      {section.categoryDisplayName} · {section.balanceDirectionDisplayName}
                    </span>
                  </div>
                  <DataTable<AccountStatementLine>
                    columns={columns}
                    rows={section.lines}
                    rowKey={sectionRowKey}
                    emptyMessage="계정명세서 라인이 없습니다."
                  />
                  <div
                    style={{
                      display: 'flex',
                      justifyContent: 'flex-end',
                      marginTop: 8,
                      fontSize: 12,
                      color: 'var(--color-neutral-600)',
                    }}
                  >
                    계정 소계 <strong style={{ marginLeft: 8, ...amountStyle(section.subtotal.balance) }}>
                      {fmtAmount(section.subtotal.balance)}
                    </strong>
                  </div>
                </div>
              ))}
            </Card>
          ))}

          {groups.length === 0 ? (
            <Card>
              <div style={{ padding: 24, color: 'var(--color-neutral-500)', textAlign: 'center' }}>
                조회 기준에 해당하는 계정명세서 라인이 없습니다.
              </div>
            </Card>
          ) : null}

          <TotalBand label="합계 잔액" balance={totalBalance} />
        </>
      )}
    </>
  )
}
