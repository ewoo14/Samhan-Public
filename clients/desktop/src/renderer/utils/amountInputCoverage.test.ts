import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const rendererRoot = resolve(__dirname, '..')

function source(route: string): string {
  return readFileSync(resolve(rendererRoot, 'routes', route), 'utf8')
}

describe('#1222 금액 입력 전수 연결 계약', () => {
  it.each([
    ['견적서 작성·편집', 'EstimateFormPage.tsx'],
    ['세금계산서 작성·편집', 'TaxInvoiceFormPage.tsx'],
    ['입금보고서 작성·편집', 'CashReceiptFormPage.tsx'],
    ['받을어음 등록', 'NotesReceivablePage.tsx'],
    ['수금계획 등록', 'CollectionPlanPage.tsx'],
    ['출고전표 모바일 LineRow', 'SlipFormPage.tsx'],
  ])('%s는 표시 formatter와 서버 parser를 모두 연결한다', (_label, route) => {
    const content = source(route)
    if (route === 'NotesReceivablePage.tsx' || route === 'CollectionPlanPage.tsx') {
      expect(content).toContain('EditableAmountInput')
    } else {
      expect(content).toContain('formatEditableAmountInput')
      expect(content).toContain('parseEditableAmountForServer')
    }
  })

  it('전표 상세 단가는 formatted input에서 키보드 증감을 명시적으로 켠다', () => {
    const content = source('SlipDetailPage.tsx')
    expect(content).toContain('enableAmountKeyboardStep')
  })
})
