// @vitest-environment jsdom
import React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'

const harness = vi.hoisted(() => ({
  getPriceMemory: vi.fn(),
  lookupPartnerForAutoFill: vi.fn(),
  createSlip: vi.fn(),
  listWarehouses: vi.fn(),
  searchProducts: vi.fn(),
  searchPartners: vi.fn(),
  usePageTitle: vi.fn(),
  partnerA: {
    id: '11111111-1111-1111-1111-111111111111',
    partnerCode: 'P-A',
    name: 'Partner A',
    phone: '010-1111-1111',
  },
  partnerB: {
    id: '22222222-2222-2222-2222-222222222222',
    partnerCode: 'P-B',
    name: 'Partner B',
    phone: '010-2222-2222',
  },
  productA: {
    id: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    modelName: 'MODEL-A',
    productName: 'Product A',
    productType: 'SINGLE',
    sellingPrice: '1000',
    modelCode: 'A',
  },
  productB: {
    id: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    modelName: 'MODEL-B',
    productName: 'Product B',
    productType: 'SINGLE',
    sellingPrice: '2000',
    modelCode: 'B',
  },
  productC: {
    id: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
    modelName: 'MODEL-C',
    productName: 'Product C',
    productType: 'SINGLE',
    sellingPrice: '3000',
    modelCode: 'C',
  },
}))

vi.mock('@samhan/design-system', () => ({
  Button: ({ children, variant: _variant, size: _size, loading: _loading, ...props }: any) => {
    const text = Array.isArray(children) ? children.join('') : String(children ?? '')
    const testId = props['data-testid'] ?? (text.includes('+') ? 'add-line-button' : undefined)
    return (
      <button {...props} data-testid={testId} type="button">
        {children}
      </button>
    )
  },
  Card: ({ children }: { children: React.ReactNode }) => <section>{children}</section>,
  DeliveryTagSelector: () => <div data-testid="delivery-tag-selector" />,
  FormField: ({ label, render }: { label: string; render: (args: { id: string }) => React.ReactNode }) => (
    <label>
      <span>{label}</span>
      {render({ id: `field-${label}` })}
    </label>
  ),
  Input: React.forwardRef<HTMLInputElement, any>(function Input(props, ref) {
    return <input ref={ref} {...props} />
  }),
  KOREAN_MOBILE_PHONE_PATTERN: /^010-/,
  LineRow: (props: any) => {
    const lineNo = props.lineNumber
    return (
      <div
        data-testid={`line-${lineNo}`}
        data-product-id={props.line.productId ?? ''}
        data-price-source={props.line.priceSource ?? ''}
      >
        {props.modelCell}
        <span data-testid={`product-name-${lineNo}`}>{props.line.productName}</span>
        <input
          aria-label={`line-${lineNo}-unit-price`}
          value={props.line.unitPrice}
          onChange={(event) => props.onUnitPriceChange(event.target.value)}
        />
        {props.line.priceSource === 'REMEMBERED' ? (
          <span
            role="note"
            title={`최근 단가 · ${props.line.priceMemoryUpdatedAt?.slice(0, 10) ?? ''} 저장`}
          >
            최근가
          </span>
        ) : null}
        <button
          type="button"
          data-testid={`delete-line-${lineNo}`}
          disabled={!props.canDelete}
          onClick={props.onDelete}
        >
          delete
        </button>
      </div>
    )
  },
  LineTableHeader: () => <div data-testid="line-table-header" />,
  PartnerAutocomplete: ({ onChange, disabled }: any) => (
    <div>
      <button type="button" data-testid="select-partner-a" disabled={disabled} onClick={() => onChange(harness.partnerA)}>
        partner-a
      </button>
      <button type="button" data-testid="select-partner-b" disabled={disabled} onClick={() => onChange(harness.partnerB)}>
        partner-b
      </button>
      <button type="button" data-testid="clear-partner" disabled={disabled} onClick={() => onChange(null)}>
        clear-partner
      </button>
    </div>
  ),
  PhoneInput: ({ helperText: _helperText, ...props }: any) => <input {...props} />,
  ProductAutocomplete: ({ ariaLabel, onChange }: any) => {
    const lineNo = /(\d+)/.exec(String(ariaLabel ?? ''))?.[1] ?? '1'
    return (
      <div data-testid={`product-autocomplete-${lineNo}`}>
        <button type="button" data-testid={`select-product-a-${lineNo}`} onClick={() => onChange(harness.productA)}>
          product-a
        </button>
        <button type="button" data-testid={`select-product-b-${lineNo}`} onClick={() => onChange(harness.productB)}>
          product-b
        </button>
        <button type="button" data-testid={`select-product-c-${lineNo}`} onClick={() => onChange(harness.productC)}>
          product-c
        </button>
      </div>
    )
  },
  WarehouseAutocomplete: ({ onChange }: any) => (
    <button type="button" data-testid="select-warehouse" onClick={() => onChange('warehouse-1')}>
      warehouse
    </button>
  ),
}))

vi.mock('@dnd-kit/core', () => ({
  DndContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  KeyboardSensor: vi.fn(),
  PointerSensor: vi.fn(),
  closestCenter: vi.fn(),
  useSensor: vi.fn(() => ({})),
  useSensors: vi.fn(() => []),
}))

vi.mock('@dnd-kit/sortable', () => ({
  SortableContext: ({ children }: { children: React.ReactNode }) => <>{children}</>,
  arrayMove: (items: unknown[]) => items,
  sortableKeyboardCoordinates: vi.fn(),
  useSortable: () => ({
    attributes: {},
    listeners: {},
    setNodeRef: vi.fn(),
    setActivatorNodeRef: vi.fn(),
    transform: null,
    transition: undefined,
    isDragging: false,
  }),
  verticalListSortingStrategy: {},
}))

vi.mock('@dnd-kit/utilities', () => ({
  CSS: { Transform: { toString: () => undefined } },
}))

vi.mock('../api/slip', () => ({
  createSlip: harness.createSlip,
  getPriceMemory: harness.getPriceMemory,
  lookupPartnerForAutoFill: harness.lookupPartnerForAutoFill,
  emptyBundleSetOptions: () => ({
    outdoorUnits: 1,
    indoorUnits: 1,
    installationHours: 0,
    commissioningHours: 0,
  }),
  toApiBundleSetOptions: () => undefined,
}))

vi.mock('../api/inventory', () => ({
  listWarehouses: harness.listWarehouses,
}))

vi.mock('../api/productApi', () => ({
  searchProducts: harness.searchProducts,
}))

vi.mock('../api/partnerApi', () => ({
  searchPartners: harness.searchPartners,
}))

vi.mock('../hooks/useIsMobile', () => ({ useIsMobile: () => false }))
vi.mock('../hooks/usePageTitle', () => ({ usePageTitle: harness.usePageTitle }))
vi.mock('./components/InventoryLookupModal', () => ({ InventoryLookupModal: () => null }))
vi.mock('./components/BundleOptionRow', () => ({ BundleOptionRow: () => null }))

import { SlipFormPage } from './SlipFormPage'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function renderPage() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <SlipFormPage mode="OUTBOUND" />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function selectPartnerA() {
  fireEvent.click(screen.getByTestId('select-partner-a'))
  await waitFor(() => expect(harness.lookupPartnerForAutoFill).toHaveBeenCalledWith('P-A'))
}

async function selectPartnerB() {
  fireEvent.click(screen.getByTestId('select-partner-b'))
  await waitFor(() => expect(harness.lookupPartnerForAutoFill).toHaveBeenCalledWith('P-B'))
}

function unitPrice(lineNo = 1) {
  return screen.getByLabelText(`line-${lineNo}-unit-price`) as HTMLInputElement
}

afterEach(() => {
  cleanup()
})

beforeEach(() => {
  vi.resetAllMocks()
  harness.listWarehouses.mockResolvedValue([])
  harness.lookupPartnerForAutoFill.mockResolvedValue({})
  harness.createSlip.mockResolvedValue({})
  harness.getPriceMemory.mockResolvedValue(null)
})

describe('SlipFormPage price memory autofill', () => {
  it('uses remembered unit price when price memory exists', async () => {
    harness.getPriceMemory.mockResolvedValueOnce({
      unitPrice: 999000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    await waitFor(() =>
      expect(harness.getPriceMemory).toHaveBeenCalledWith(
        harness.partnerA.id,
        harness.productA.id,
      ),
    )
    await waitFor(() => expect(unitPrice().value).toBe('999000'))
    expect(unitPrice().value).not.toBe(harness.productA.sellingPrice)
  })

  it('falls back to catalog selling price when price memory misses', async () => {
    harness.getPriceMemory.mockResolvedValueOnce(null)
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    await waitFor(() =>
      expect(harness.getPriceMemory).toHaveBeenCalledWith(
        harness.partnerA.id,
        harness.productA.id,
      ),
    )
    await waitFor(() => expect(unitPrice().value).toBe(harness.productA.sellingPrice))
    expect(screen.queryByRole('note')).toBeNull()
  })

  it('preserves user override and skips price memory lookup', async () => {
    renderPage()
    await selectPartnerA()
    fireEvent.change(unitPrice(), { target: { value: '7777' } })

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    await waitFor(() => expect(screen.getByTestId('product-name-1').textContent).toBe(harness.productA.productName))
    expect(harness.getPriceMemory).not.toHaveBeenCalled()
    expect(unitPrice().value).toBe('7777')
    expect(screen.queryByRole('note')).toBeNull()
  })

  it('ignores a late response when partner changes during lookup', async () => {
    const first = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    const second = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    harness.getPriceMemory
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))
    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerA.id, harness.productA.id))
    await waitFor(() => expect(screen.getByTestId('product-name-1').textContent).toBe(harness.productA.productName))
    await selectPartnerB()
    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerB.id, harness.productA.id))

    await act(async () => {
      second.resolve({ unitPrice: 222000, source: 'LINE_SAVE', updatedAt: '2026-07-11T09:00:00' })
      await second.promise
    })
    await waitFor(() => expect(unitPrice().value).toBe('222000'))

    await act(async () => {
      first.resolve({ unitPrice: 111000, source: 'LINE_SAVE', updatedAt: '2026-07-10T09:00:00' })
      await first.promise
    })
    expect(unitPrice().value).toBe('222000')
  })

  it('ignores a late response when the same line changes to another product', async () => {
    const first = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    const second = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    harness.getPriceMemory
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))
    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerA.id, harness.productA.id))
    await waitFor(() => expect(screen.getByTestId('product-name-1').textContent).toBe(harness.productA.productName))
    fireEvent.click(screen.getByTestId('select-product-b-1'))
    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerA.id, harness.productB.id))

    await act(async () => {
      second.resolve({ unitPrice: 202000, source: 'LINE_SAVE', updatedAt: '2026-07-12T09:00:00' })
      await second.promise
    })
    await waitFor(() => expect(unitPrice().value).toBe('202000'))

    await act(async () => {
      first.resolve({ unitPrice: 101000, source: 'LINE_SAVE', updatedAt: '2026-07-10T09:00:00' })
      await first.promise
    })
    expect(screen.getByTestId('product-name-1').textContent).toBe(harness.productB.productName)
    expect(unitPrice().value).toBe('202000')
  })

  it('ignores a late response when the line is deleted during lookup', async () => {
    const pending = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    harness.getPriceMemory.mockReturnValueOnce(pending.promise)
    renderPage()
    await selectPartnerA()
    fireEvent.click(screen.getByTestId('add-line-button'))

    fireEvent.click(screen.getByTestId('select-product-a-2'))
    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerA.id, harness.productA.id))
    fireEvent.click(screen.getByTestId('delete-line-2'))
    expect(screen.queryByTestId('line-2')).toBeNull()

    await act(async () => {
      pending.resolve({ unitPrice: 999000, source: 'LINE_SAVE', updatedAt: '2026-07-10T09:00:00' })
      await pending.promise
    })
    expect(screen.queryByTestId('line-2')).toBeNull()
    expect(unitPrice(1).value).toBe('0')
  })

  it('refreshes autofilled lines on partner change and preserves user override lines', async () => {
    harness.getPriceMemory
      .mockResolvedValueOnce({ unitPrice: 100000, source: 'LINE_SAVE', updatedAt: '2026-07-10T09:00:00' })
      .mockResolvedValueOnce({ unitPrice: 200000, source: 'LINE_SAVE', updatedAt: '2026-07-11T09:00:00' })
    renderPage()
    await selectPartnerA()
    fireEvent.click(screen.getByTestId('select-product-a-1'))
    await waitFor(() => expect(unitPrice(1).value).toBe('100000'))

    fireEvent.click(screen.getByTestId('add-line-button'))
    fireEvent.change(unitPrice(2), { target: { value: '7777' } })
    fireEvent.click(screen.getByTestId('select-product-b-2'))
    await waitFor(() => expect(screen.getByTestId('product-name-2').textContent).toBe(harness.productB.productName))
    expect(unitPrice(2).value).toBe('7777')

    await selectPartnerB()

    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerB.id, harness.productA.id))
    expect(harness.getPriceMemory).not.toHaveBeenCalledWith(harness.partnerB.id, harness.productB.id)
    await waitFor(() => expect(unitPrice(1).value).toBe('200000'))
    expect(unitPrice(2).value).toBe('7777')
  })

  it('treats zero remembered unit price as a valid hit', async () => {
    harness.getPriceMemory.mockResolvedValueOnce({
      unitPrice: 0,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerA.id, harness.productA.id))
    await waitFor(() => expect(unitPrice().value).toBe('0'))
    expect(screen.getByRole('note').textContent).toBe('최근가')
  })

  it('renders recent-price marker only for remembered hits with updatedAt tooltip', async () => {
    harness.getPriceMemory.mockResolvedValueOnce({
      unitPrice: 123000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    const note = await screen.findByRole('note')
    expect(note.getAttribute('title')).toContain('2026-07-10')
    expect(note.textContent).toBe('최근가')

    fireEvent.click(screen.getByTestId('add-line-button'))
    fireEvent.change(unitPrice(2), { target: { value: '7777' } })
    fireEvent.click(screen.getByTestId('select-product-b-2'))
    await waitFor(() => expect(screen.getByTestId('product-name-2').textContent).toBe(harness.productB.productName))
    expect(screen.getAllByRole('note')).toHaveLength(1)
  })

  it('skips price memory lookup when partnerId is not selected', async () => {
    renderPage()

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    await waitFor(() => expect(screen.getByTestId('product-name-1').textContent).toBe(harness.productA.productName))
    expect(harness.getPriceMemory).not.toHaveBeenCalled()
    expect(unitPrice().value).toBe(harness.productA.sellingPrice)
  })

  it('keeps catalog fallback when price memory lookup rejects', async () => {
    harness.getPriceMemory.mockRejectedValueOnce(new Error('forbidden'))
    renderPage()
    await selectPartnerA()

    fireEvent.click(screen.getByTestId('select-product-a-1'))

    await waitFor(() => expect(harness.getPriceMemory).toHaveBeenCalledWith(harness.partnerA.id, harness.productA.id))
    await waitFor(() => expect(screen.getByTestId('product-name-1').textContent).toBe(harness.productA.productName))
    await waitFor(() => expect(unitPrice().value).toBe(harness.productA.sellingPrice))
    expect(screen.queryByRole('note')).toBeNull()
  })
})
