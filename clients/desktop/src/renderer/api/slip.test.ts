import { beforeEach, describe, expect, it, vi } from 'vitest'

const apiClientMock = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('./client', () => ({ apiClient: apiClientMock }))

import { duplicateSlip, getPriceMemories, type SlipDetail } from './slip'

function sourceWithPrice(unitPriceWithVat: string | null): SlipDetail {
  return {
    id: 'slip-source',
    slipType: 'OUTBOUND',
    slipNo: '2099/01/01-1',
    slipDate: '2099-01-01',
    seqNo: 1,
    status: 'DRAFT',
    partnerId: '11111111-1111-1111-1111-111111111111',
    partnerName: '거래처',
    sourceWarehouseId: 'warehouse-1',
    destinationWarehouseId: null,
    deliveryTag: null,
    requesterId: null,
    acceptedBy: null,
    acceptedAt: null,
    completedAt: null,
    confirmedAt: null,
    updatedAt: '2099-01-01T09:00:00',
    version: 0,
    memo: null,
    lines: [{
      id: 'line-1',
      productId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      productName: '품목',
      modelName: 'MODEL-1',
      specification: null,
      quantity: 1,
      unitPrice: '100000',
      unitPriceWithVat,
      lineTotal: '100000',
      note: null,
    }],
  }
}

describe('slip price contract', () => {
  beforeEach(() => {
    vi.resetAllMocks()
    apiClientMock.post.mockResolvedValue({ data: { data: {} } })
  })

  it('legacy null VAT-inclusive price copy preserves the supply unit price exactly', async () => {
    await duplicateSlip(sourceWithPrice(null))

    expect(apiClientMock.post).toHaveBeenCalledWith('/slips', expect.objectContaining({
      lines: [expect.objectContaining({
        unitPrice: '100000',
        priceVatInclusive: false,
      })],
    }))
  })

  it('copy uses the stored VAT-inclusive price when present', async () => {
    await duplicateSlip(sourceWithPrice('110000'))

    expect(apiClientMock.post).toHaveBeenCalledWith('/slips', expect.objectContaining({
      lines: [expect.objectContaining({
        unitPrice: '110000',
        priceVatInclusive: true,
      })],
    }))
  })

  it('bulk lookup posts unique productIds and returns partial hits unchanged', async () => {
    const hit = {
      productId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
      unitPrice: 123000,
      source: 'LINE_SAVE',
      updatedAt: '2099-01-01T09:00:00',
    }
    apiClientMock.post.mockResolvedValueOnce({ data: { data: [hit] } })

    await expect(getPriceMemories(
      '11111111-1111-1111-1111-111111111111',
      [hit.productId, hit.productId, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'],
    )).resolves.toEqual([hit])
    expect(apiClientMock.post).toHaveBeenCalledWith('/slips/price-memory/bulk', {
      partnerId: '11111111-1111-1111-1111-111111111111',
      productIds: [hit.productId, 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb'],
    })
  })

  // R4-F5: 고유 품목 101개↑ 에서 throw → 전 라인 조용히 CATALOG 강등되던 결함의 회귀 가드.
  it('bulk lookup chunks 101+ unique productIds into sequential 100-size calls', async () => {
    const ids = Array.from({ length: 150 }, (_, i) => `product-${String(i).padStart(3, '0')}`)
    const hitFirstChunk = {
      productId: 'product-000',
      unitPrice: 1000,
      source: 'LINE_SAVE',
      updatedAt: '2099-01-01T09:00:00',
    }
    const hitSecondChunk = {
      productId: 'product-149',
      unitPrice: 2000,
      source: 'LINE_SAVE',
      updatedAt: '2099-01-02T09:00:00',
    }
    apiClientMock.post
      .mockResolvedValueOnce({ data: { data: [hitFirstChunk] } })
      .mockResolvedValueOnce({ data: { data: [hitSecondChunk] } })

    await expect(getPriceMemories('partner-1', ids)).resolves.toEqual([
      hitFirstChunk,
      hitSecondChunk,
    ])

    expect(apiClientMock.post).toHaveBeenCalledTimes(2)
    expect(apiClientMock.post).toHaveBeenNthCalledWith(1, '/slips/price-memory/bulk', {
      partnerId: 'partner-1',
      productIds: ids.slice(0, 100),
    })
    expect(apiClientMock.post).toHaveBeenNthCalledWith(2, '/slips/price-memory/bulk', {
      partnerId: 'partner-1',
      productIds: ids.slice(100),
    })
  })

  it('bulk lookup rejects when any chunk call fails so callers fall back uniformly', async () => {
    // 부분 실패 시 반쪽 결과를 돌려주지 않고 throw — 호출자 catch(판매가 fallback) 일관 처리.
    // 사용자(USER) 단가 라인은 재조회 후보에서 제외되므로 어떤 경우에도 불가침(R4-F5).
    const ids = Array.from({ length: 101 }, (_, i) => `product-${i}`)
    apiClientMock.post
      .mockResolvedValueOnce({ data: { data: [] } })
      .mockRejectedValueOnce(new Error('bulk chunk failed'))

    await expect(getPriceMemories('partner-1', ids)).rejects.toThrow('bulk chunk failed')
    expect(apiClientMock.post).toHaveBeenCalledTimes(2)
  })

  it('bulk lookup still rejects an empty productIds list', async () => {
    await expect(getPriceMemories('partner-1', [])).rejects.toThrow(/at least 1 unique/)
    expect(apiClientMock.post).not.toHaveBeenCalled()
  })
})
