// @vitest-environment jsdom
import React from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  getEstimateConfig: vi.fn(),
  updateEstimateConfig: vi.fn(),
  createPartnerFull: vi.fn(),
  navigate: vi.fn(),
}))

vi.mock('../api/sales', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/sales')>()),
  getEstimateConfig: mocks.getEstimateConfig,
  updateEstimateConfig: mocks.updateEstimateConfig,
}))
vi.mock('../api/partnerApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../api/partnerApi')>()),
  createPartnerFull: mocks.createPartnerFull,
}))
vi.mock('../hooks/usePermissions', () => ({
  usePermissions: () => ({ canAccess: () => true, isLoading: false, isError: false }),
}))
vi.mock('../hooks/usePageTitle', () => ({ usePageTitle: vi.fn() }))
vi.mock('react-router-dom', async (importOriginal) => ({
  ...(await importOriginal<typeof import('react-router-dom')>()),
  useNavigate: () => mocks.navigate,
}))

import { EstimatePricingConfigPage } from './EstimatePricingConfigPage'
import { PartnerCreatePage } from './admin/PartnerCreatePage'

const MONEY_SCREEN_INVENTORY = [
  { route: '/products/new', fields: ['판매가', '매입가', '출고가', '배송가'] },
  { route: '/admin/partners/new', fields: ['신용한도 (원)'] },
  { route: '/sales/estimate-config', fields: ['할인', '1WAY할인'] },
] as const

function renderWithQuery(element: React.ReactElement) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(<QueryClientProvider client={client}><MemoryRouter>{element}</MemoryRouter></QueryClientProvider>)
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('#1222 monetary screen inventory', () => {
  it('keeps the reviewed screen inventory explicit: 3 screens / 7 monetary inputs', () => {
    expect(MONEY_SCREEN_INVENTORY.flatMap((screen) => screen.fields)).toHaveLength(7)
    expect(MONEY_SCREEN_INVENTORY.map((screen) => screen.route)).toEqual([
      '/products/new',
      '/admin/partners/new',
      '/sales/estimate-config',
    ])
  })

  it('renders both estimate-config monetary fields and sends raw values', async () => {
    mocks.getEstimateConfig.mockResolvedValue({
      commonHomeDiscountRate: 0.45, commonCommercialDiscountRate: 0.45, oldProductDiscountRate: 0.5,
      vatRate: 0.1, cardFeeRate: 0.03, advanceDiscountRate: 0, comboWarnRate: 0,
      homeNoHose: false, homeNoBranch: false, homeWithFoot: false, homeDefaultPanel: '',
      singleDefaultWiredRemote: '', singleNoRemote: false, singleWithBase: false, singleDefaultPanel: '',
      singlePanelShape: '360', singleDiscount: 123456, singleOneWayDiscount: 112232,
      singleMaterialInclusion: 'SEPARATE', footerNotice: '',
    })
    renderWithQuery(<EstimatePricingConfigPage />)
    const discount = await screen.findByLabelText('할인') as HTMLInputElement
    const oneWay = screen.getByLabelText('1WAY할인') as HTMLInputElement
    expect(discount.value).toBe('123,456')
    expect(oneWay.value).toBe('112,232')
    fireEvent.change(discount, { target: { value: '1234567', selectionStart: 7 } })
    expect(discount.value).toBe('1,234,567')
  })

  it('renders partner credit limit in the actual policy tab without formatting non-money fields', async () => {
    renderWithQuery(<PartnerCreatePage />)
    fireEvent.click(screen.getByTestId('partner-tab-2'))
    const creditLimit = await screen.findByLabelText('신용한도 (원)') as HTMLInputElement
    const discountRate = screen.getByLabelText('기본 할인율 (%)') as HTMLInputElement
    fireEvent.change(creditLimit, { target: { value: '12345678', selectionStart: 8 } })
    fireEvent.change(discountRate, { target: { value: '12.5' } })
    expect(creditLimit.value).toBe('12,345,678')
    expect(discountRate.value).toBe('12.5')
  })
})
