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
})
