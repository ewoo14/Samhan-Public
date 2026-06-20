import type {
  FundsAmountSummary,
  FundsIncreaseDetailResponse,
  FundsStatusAccountSection,
  FundsStatusLine,
} from '../api/accounting'

export interface FundsStatusTableRow extends FundsStatusLine {
  rowKind: 'line' | 'subtotal'
  rowKey: string
}

/** KRW 정수 string → "5,000" 형식. 음수는 빨간색 표시를 위해 부호를 유지한다. */
export function fmtFundsKrw(raw: string | number): string {
  const n = typeof raw === 'string' ? Number.parseInt(raw, 10) : raw
  if (!Number.isFinite(n)) return String(raw)
  if (n === 0) return '-'
  return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 음수 금액 여부. */
export function isNegativeAmount(raw: string | number): boolean {
  const n = typeof raw === 'string' ? Number.parseInt(raw, 10) : raw
  return Number.isFinite(n) && n < 0
}

/**
 * 계정 섹션의 거래처 라인 + 소계 라인을 DataTable row 로 변환한다.
 */
export function buildFundsStatusRows(section: FundsStatusAccountSection): FundsStatusTableRow[] {
  const rows: FundsStatusTableRow[] = section.lines.map((line, index) => ({
    ...line,
    rowKind: 'line',
    rowKey: `${section.accountCode}:${line.partnerName}:${index}`,
  }))
  rows.push({
    accountCode: section.accountCode,
    accountName: section.accountName,
    partnerName: '소계',
    openingBalance: section.subtotal.openingBalance,
    increase: section.subtotal.increase,
    decrease: section.subtotal.decrease,
    closingBalance: section.subtotal.closingBalance,
    rowKind: 'subtotal',
    rowKey: `${section.accountCode}:subtotal`,
  })
  return rows
}

/** 금액 요약을 총합 행 표시용 라인으로 변환한다. */
export function summaryToLine(label: string, summary: FundsAmountSummary): FundsStatusLine {
  return {
    accountCode: '',
    accountName: '',
    partnerName: label,
    openingBalance: summary.openingBalance,
    increase: summary.increase,
    decrease: summary.decrease,
    closingBalance: summary.closingBalance,
  }
}

/** 증가 상세 modal 제목. */
export function fundsIncreaseDetailTitle(detail: FundsIncreaseDetailResponse | null): string {
  if (!detail) return '자금 증가 상세'
  const partner = detail.partnerName ? ` / ${detail.partnerName}` : ''
  return `${detail.accountCode} ${detail.accountName}${partner} 증가 상세`
}
