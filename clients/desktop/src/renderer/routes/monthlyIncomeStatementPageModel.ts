import type { MonthlyIncomeStatementLine } from '../api/accounting'

export type AmountValue = string | number

export function numericAmount(raw: AmountValue | null | undefined): number {
  if (raw == null) return 0
  const value = typeof raw === 'number' ? raw : Number(raw)
  return Number.isFinite(value) ? value : 0
}

/** KRW 정수/소수 string → "5,000" 형식. 0은 eCount 표처럼 대시로 표시한다. */
export function fmtMonthlyKrw(raw: AmountValue): string {
  const value = numericAmount(raw)
  if (value === 0) return '-'
  const abs = Math.abs(Math.round(value)).toLocaleString('ko-KR')
  return value < 0 ? `(${abs})` : abs
}

export function isNegativeMonthlyAmount(raw: AmountValue): boolean {
  return numericAmount(raw) < 0
}

export function isStrongMonthlyRow(row: MonthlyIncomeStatementLine): boolean {
  return row.rowKind === 'SUBTOTAL' || row.rowKind === 'TOTAL'
}

export function monthlyAmountAt(row: MonthlyIncomeStatementLine, month: number): AmountValue {
  return row.monthlyAmounts[month - 1] ?? 0
}

export function rowLabel(row: MonthlyIncomeStatementLine): string {
  return row.accountCode ? `${row.accountCode} ${row.accountName}` : row.accountName
}

export function sectionLabel(section: string): string {
  switch (section) {
    case 'REVENUE':
      return '매출'
    case 'COST_OF_SALES':
      return '매출원가'
    case 'GROSS_PROFIT':
      return '매출총이익'
    case 'SGA':
      return '판매비와관리비'
    case 'OPERATING_PROFIT':
      return '영업이익'
    case 'NON_OPERATING':
      return '영업외손익'
    case 'INCOME_BEFORE_TAX':
      return '법인세차감전순이익'
    case 'INCOME_TAX':
      return '법인세비용'
    case 'NET_INCOME':
      return '당기순이익'
    default:
      return section
  }
}
