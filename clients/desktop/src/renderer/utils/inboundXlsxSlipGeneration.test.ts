import { describe, expect, it } from 'vitest'
import { buildInboundSlipBatches, type InboundSlipGenerationRow } from './inboundXlsxSlipGeneration'

const row = (overrides: Partial<InboundSlipGenerationRow> = {}): InboundSlipGenerationRow => ({
  sourceSheet: '입고',
  sourceRow: 8,
  cleanModel: 'AC-100',
  productName: 'AC-100',
  productId: 'product-1',
  status: '품목일치',
  warehouseCode: '00003',
  quantity: 2,
  ...overrides,
})

describe('buildInboundSlipBatches', () => {
  it('검색실패와 0수량을 제외하고 창고별 100라인 청크와 추적 note를 만든다', () => {
    const rows = Array.from({ length: 101 }, (_, index) => row({
      sourceRow: index + 8,
      productId: `product-${index}`,
    }))
    rows.push(row({ status: '검색실패', sourceRow: 999 }))
    rows.push(row({ quantity: 0, sourceRow: 1000 }))
    rows.push(row({ warehouseCode: '2', sourceRow: 1001 }))

    const batches = buildInboundSlipBatches(rows, 'FILE-HASH', {
      '00003': 'warehouse- 초월',
      '2': 'warehouse- 상일',
    })

    expect(batches).toHaveLength(3)
    expect(batches[0].warehouseCode).toBe('00003')
    expect(batches[0].request.lines).toHaveLength(100)
    expect(batches[1].request.lines).toHaveLength(1)
    expect(batches[2].warehouseCode).toBe('2')
    expect(batches[0].request.lines[0].unitPrice).toBe('0')
    expect(batches[0].request.partnerCode).toBe('1248100998')
    expect(batches[0].request.partnerName).toBe('삼성전자(주)')
    expect(batches[0].request.sourceType).toBe('INBOUND_XLSX')
    expect(batches[0].request.idempotencyKey).toBe('inbound-xlsx:FILE-HASH:00003:1')
    expect(batches[0].request.lines[0].note).toContain('FILE-HASH')
    expect(batches[0].request.lines[0].note).toContain('입고/8')
  })

  it('유효 행이 없으면 빈 배열을 반환한다', () => {
    expect(buildInboundSlipBatches([
      row({ status: '검색실패' }),
      row({ quantity: 0, sourceRow: 9 }),
    ], 'HASH', { '00003': 'warehouse-1' })).toEqual([])
  })
})
