import { beforeEach, describe, expect, it, vi } from 'vitest'
import { apiClient } from './client'
import { updateProductFixedDiscount } from './productCatalogApi'

vi.mock('./client', () => ({
  apiClient: {
    patch: vi.fn(),
  },
}))

describe('productCatalogApi fixed discount contract', () => {
  beforeEach(() => {
    vi.mocked(apiClient.patch).mockReset()
  })

  it('PATCH /fixed-discount body에 null 고정DC를 그대로 전송한다', async () => {
    const row = { modelCode: 'AC100' }
    vi.mocked(apiClient.patch).mockResolvedValueOnce({ data: row })

    await expect(updateProductFixedDiscount('AC100', null)).resolves.toBe(row)

    expect(apiClient.patch).toHaveBeenCalledWith(
      '/api/v1/products/AC100/fixed-discount',
      { fixedDiscountRate: null },
    )
  })

  it('PATCH /fixed-discount body에 0~100 문자열 고정DC를 전송한다', async () => {
    const row = { modelCode: 'AC/100' }
    vi.mocked(apiClient.patch).mockResolvedValueOnce({ data: row })

    await expect(updateProductFixedDiscount('AC/100', '12.50')).resolves.toBe(row)

    expect(apiClient.patch).toHaveBeenCalledWith(
      '/api/v1/products/AC%2F100/fixed-discount',
      { fixedDiscountRate: '12.50' },
    )
  })
})
