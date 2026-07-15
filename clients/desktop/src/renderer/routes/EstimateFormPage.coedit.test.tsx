// @vitest-environment jsdom
import React from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import type { EstimateDetail } from '../api/estimateApi'

const mocks = vi.hoisted(() => ({
  getEstimate: vi.fn(),
  updateEstimate: vi.fn(),
  createEstimate: vi.fn(),
  sendEstimate: vi.fn(),
  searchPartners: vi.fn(),
  lookupProductByModelName: vi.fn(),
  getPriceMemory: vi.fn(),
  getPriceMemories: vi.fn(),
  createDocCoeditProvider: vi.fn(),
  partnerA: {
    id: '11111111-1111-1111-1111-111111111111',
    partnerCode: 'P-A',
    name: 'Partner A',
    bizNo: '111-11-11111',
  },
  partnerB: {
    id: '22222222-2222-2222-2222-222222222222',
    partnerCode: 'P-B',
    name: 'Partner B',
    bizNo: '222-22-22222',
  },
}))

vi.mock('@samhan/design-system', () => ({
  Button: ({ children, variant: _variant, size: _size, ...props }: any) => (
    <button {...props}>{children}</button>
  ),
  Card: ({ children }: { children: React.ReactNode }) => <section>{children}</section>,
  Input: React.forwardRef<HTMLInputElement, any>(function Input(
    { label, inputSize: _inputSize, ...props },
    ref,
  ) {
    return (
      <label>
        {label ? <span>{label}</span> : null}
        <input ref={ref} {...props} />
      </label>
    )
  }),
  PartnerAutocomplete: ({ label, disabled, onChange }: { label?: string; disabled?: boolean; onChange: (value: unknown) => void }) => (
    <label>
      {label ? <span>{label}</span> : null}
      <input data-testid="estimate-partner-autocomplete" disabled={disabled} />
      <button type="button" data-testid="estimate-select-partner-a" disabled={disabled} onClick={() => onChange(mocks.partnerA)}>
        partner-a
      </button>
      <button type="button" data-testid="estimate-select-partner-b" disabled={disabled} onClick={() => onChange(mocks.partnerB)}>
        partner-b
      </button>
      <button type="button" data-testid="estimate-clear-partner" disabled={disabled} onClick={() => onChange(null)}>
        clear-partner
      </button>
    </label>
  ),
  Spinner: ({ label }: { label?: string }) => <div role="status">{label}</div>,
}))

vi.mock('../components/collab/CollaborativeSlipInput', () => ({
  CollaborativeSlipInput: (props: {
    provider: any
    fieldPath: string
    value: string
    onValueChange?: (value: string) => void
    onDocSyncValueChange?: (value: string) => void
    coeditPending?: boolean
    readOnly?: boolean
    onBlur?: () => void
    'aria-label': string
    'aria-describedby'?: string
  }) => (
    <input
      aria-label={props['aria-label']}
      aria-describedby={props['aria-describedby']}
      data-testid={`estimate-coedit-${props.fieldPath.replace(/\./g, '-')}`}
      data-field-path={props.fieldPath}
      data-provider-present={String(!!props.provider)}
      data-coedit-pending={String(!!props.coeditPending)}
      data-doc-sync={String(!!props.onDocSyncValueChange)}
      value={props.value}
      disabled={!!props.coeditPending || !!props.readOnly}
      onBlur={props.onBlur}
      onChange={(event) => {
        const nextValue = event.target.value
        props.onValueChange?.(nextValue)
        const [scope, rowKey, cellName] = props.fieldPath.split('.')
        if (props.provider && scope === 'header') props.provider.setHeaderValue(rowKey, nextValue)
        if (props.provider && scope === 'items') props.provider.setItemValue(Number(rowKey), cellName, nextValue)
      }}
    />
  ),
}))

vi.mock('../realtime/createCoeditProvider', () => ({
  createDocCoeditProvider: mocks.createDocCoeditProvider,
}))

vi.mock('../api/estimateApi', () => ({
  getEstimate: mocks.getEstimate,
  updateEstimate: mocks.updateEstimate,
  createEstimate: mocks.createEstimate,
  sendEstimate: mocks.sendEstimate,
}))

vi.mock('../api/sales', () => ({
  searchPartners: mocks.searchPartners,
}))

vi.mock('../api/slip', () => ({
  lookupProductByModelName: mocks.lookupProductByModelName,
  getPriceMemories: mocks.getPriceMemories,
  getPriceMemory: mocks.getPriceMemory,
  emptyBundleSetOptions: () => ({
    outdoorUnits: 1,
    indoorUnits: 1,
    installationHours: 0,
    commissioningHours: 0,
  }),
  toApiBundleSetOptions: () => undefined,
}))

vi.mock('./components/LineLookupReferenceModal', () => ({
  LineLookupReferenceModal: () => <div data-testid="line-lookup-reference-modal" />,
}))

vi.mock('./components/BundleOptionRow', () => ({
  BundleOptionRow: () => <div data-testid="bundle-option-row" />,
}))

vi.mock('../hooks/useIsMobile', () => ({ useIsMobile: () => false }))
vi.mock('../hooks/usePageTitle', () => ({ usePageTitle: vi.fn() }))
vi.mock('../hooks/usePermissions', () => ({
  usePermissions: () => ({ canAccess: () => true }),
}))

import { EstimateFormPage } from './EstimateFormPage'

function makeEstimate(overrides: Partial<EstimateDetail> = {}): EstimateDetail {
  const estimate: EstimateDetail = {
    id: 'estimate-1',
    estimateNo: '2099/07/01-1',
    estimateDate: '2099-07-01',
    seqNo: 1,
    status: 'QUOTE_DRAFT',
    partnerId: '11111111-1111-1111-1111-111111111111',
    partnerName: '테스트 거래처',
    partnerBusinessNo: '123-45-67890',
    partnerAddress: '서울시 중구',
    validUntil: '2099-07-31',
    totalSupply: '10000',
    totalVat: '1000',
    totalAmount: '11000',
    convertedSlipId: null,
    sentAt: null,
    acceptedAt: null,
    convertedAt: null,
    requesterId: null,
    version: 1,
    isDeleted: false,
    deletedAt: null,
    deletedByName: null,
    rejectedAt: null,
    memo: '초기 메모',
    lines: [
      {
        id: 'line-1',
        lineNo: 0,
        productId: 'product-1',
        productName: '제품 1',
        modelName: 'MODEL-1',
        specification: '스펙 1',
        quantity: 2,
        unitPrice: '10000',
        unitPriceWithVat: '11000',
        supplyAmount: '20000',
        vatAmount: '2000',
        lineTotal: '22000',
        note: '라인 메모',
      },
    ],
  }
  return { ...estimate, ...overrides }
}

function makeProvider() {
  const header = new Map<string, string>()
  let rows: Record<string, string>[] = []
  const subscribers = new Set<() => void>()
  const provider = {
    items: {
      toArray: () => rows,
    },
    setHeaderValue: vi.fn((fieldName: string, value: string) => {
      header.set(fieldName, value)
    }),
    getHeaderValue: vi.fn((fieldName: string) => header.get(fieldName) ?? ''),
    replaceItems: vi.fn((nextRows: Record<string, string>[]) => {
      rows = nextRows.map((row) => ({ ...row }))
    }),
    getItemValue: vi.fn((index: number, cellName: string) => rows[index]?.[cellName] ?? ''),
    setItemValue: vi.fn((index: number, cellName: string, value: string) => {
      rows[index] = { ...(rows[index] ?? {}), [cellName]: value }
    }),
    isEmpty: vi.fn(() => true),
    subscribeDoc: vi.fn((listener: () => void) => {
      subscribers.add(listener)
      return () => subscribers.delete(listener)
    }),
    subscribeAwareness: vi.fn(() => () => undefined),
    getRemoteCursors: vi.fn(() => []),
    getRemoteEdits: vi.fn(() => []),
    setLocalCursor: vi.fn(),
    setLocalLastEdit: vi.fn(),
    destroy: vi.fn(),
    __setRows: (nextRows: Record<string, string>[]) => {
      rows = nextRows.map((row) => ({ ...row }))
    },
    __emit: () => {
      for (const subscriber of subscribers) subscriber()
    },
  }
  return provider
}

function renderPage(initialPath = '/sales/estimates/estimate-1/edit') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[initialPath]}>
        <Routes>
          <Route path="/sales/estimates/:id/edit" element={<EstimateFormPage />} />
          <Route path="/sales/estimates/new" element={<EstimateFormPage />} />
          <Route path="/sales/estimates/:id" element={<div data-testid="estimate-detail" />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

function estimateUnitPrice(index = 0): HTMLInputElement {
  return screen.getByTestId(`estimate-coedit-items-${index}-unitPrice`) as HTMLInputElement
}

function estimateModel(index = 0): HTMLInputElement {
  return screen.getByTestId(`estimate-coedit-items-${index}-modelName`) as HTMLInputElement
}

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

beforeEach(() => {
  mocks.getPriceMemory.mockResolvedValue(null)
  mocks.getPriceMemories.mockResolvedValue([])
  mocks.createEstimate.mockResolvedValue({ id: 'estimate-created' })
  mocks.updateEstimate.mockResolvedValue({ id: 'estimate-1' })
})

describe('EstimateFormPage 견적 편집 full-form coedit 배선', () => {
  it('provider 생성 옵션과 헤더/라인 CollaborativeSlipInput fieldPath 를 slip 패턴으로 배선한다', async () => {
    const provider = makeProvider()
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockResolvedValue(provider)

    renderPage()

    await waitFor(() => expect(mocks.createDocCoeditProvider).toHaveBeenCalledTimes(1))
    expect(mocks.createDocCoeditProvider).toHaveBeenCalledWith({
      documentId: 'estimate-1',
      basePath: '/slips/estimates/estimate-1',
      headerTextFields: new Set(['memo']),
    })
    expect(provider.setHeaderValue).toHaveBeenCalledWith('partnerName', '테스트 거래처')
    expect(provider.setHeaderValue).toHaveBeenCalledWith('partnerBusinessNo', '123-45-67890')
    expect(provider.setHeaderValue).toHaveBeenCalledWith('partnerAddress', '서울시 중구')
    expect(provider.setHeaderValue).toHaveBeenCalledWith('estimateDate', '2099-07-01')
    expect(provider.setHeaderValue).toHaveBeenCalledWith('validUntil', '2099-07-31')
    expect(provider.setHeaderValue).toHaveBeenCalledWith('memo', '초기 메모')
    expect(provider.replaceItems).toHaveBeenCalledWith([
      expect.objectContaining({
        modelName: 'MODEL-1',
        productName: '제품 1',
        specification: '스펙 1',
        quantity: '2',
        unitPrice: '11000',
        productId: 'product-1',
      }),
    ])

    for (const fieldPath of [
      'header.partnerName',
      'header.partnerBusinessNo',
      'header.partnerAddress',
      'header.estimateDate',
      'header.validUntil',
      'header.memo',
      'items.0.modelName',
      'items.0.productName',
      'items.0.specification',
      'items.0.quantity',
      'items.0.unitPrice',
    ]) {
      const field = await screen.findByTestId(`estimate-coedit-${fieldPath.replace(/\./g, '-')}`)
      expect(field.getAttribute('data-field-path')).toBe(fieldPath)
      expect(field.getAttribute('data-provider-present')).toBe('true')
    }
    expect((screen.getByTestId('estimate-partner-autocomplete') as HTMLInputElement).disabled).toBe(true)
    // R4-F6: 단가 필드만 doc-sync 전용 콜백 배선 — 자동채움 provider write 의 doc-sync 가
    // pending REMEMBERED/CATALOG 분류를 USER 로 재분류하지 않게 분리한다(타 필드는 기존 경로).
    expect(screen.getByTestId('estimate-coedit-items-0-unitPrice').getAttribute('data-doc-sync')).toBe('true')
    expect(screen.getByTestId('estimate-coedit-items-0-modelName').getAttribute('data-doc-sync')).toBe('false')
  })

  it('subscribeDoc 원격 업데이트를 React form state 에 반영한다', async () => {
    const provider = makeProvider()
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockResolvedValue(provider)

    renderPage()
    await waitFor(() => expect(provider.subscribeDoc).toHaveBeenCalledTimes(1))

    provider.setHeaderValue('partnerName', '원격 거래처')
    provider.setHeaderValue('partnerBusinessNo', '999-88-77777')
    provider.setHeaderValue('partnerAddress', '원격 주소')
    provider.setHeaderValue('estimateDate', '2099-08-01')
    provider.setHeaderValue('validUntil', '2099-08-31')
    provider.setHeaderValue('memo', '원격 메모')
    provider.__setRows([
      {
        modelName: 'REMOTE-1',
        productName: '원격 제품',
        specification: '원격 스펙',
        quantity: '5',
        unitPrice: '7000',
        productId: 'product-remote',
      },
    ])
    act(() => {
      provider.__emit()
    })

    await waitFor(() => expect((screen.getByTestId('estimate-coedit-header-memo') as HTMLInputElement).value).toBe('원격 메모'))
    expect((screen.getByTestId('estimate-coedit-header-partnerName') as HTMLInputElement).value).toBe('원격 거래처')
    expect((screen.getByTestId('estimate-coedit-items-0-modelName') as HTMLInputElement).value).toBe('REMOTE-1')
    expect((screen.getByTestId('estimate-coedit-items-0-productName') as HTMLInputElement).value).toBe('원격 제품')
    expect((screen.getByTestId('estimate-coedit-items-0-quantity') as HTMLInputElement).value).toBe('5')
  })

  it('provider 생성 실패 시 pending 을 해제하고 평문 입력 가능한 폼으로 폴백한다', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockRejectedValue(new Error('coedit unavailable'))

    renderPage()

    const memoInput = await screen.findByTestId('estimate-coedit-header-memo')
    await waitFor(() => expect(memoInput.getAttribute('data-provider-present')).toBe('false'))
    await waitFor(() => expect(memoInput.getAttribute('data-coedit-pending')).toBe('false'))
    expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(false)

    fireEvent.change(memoInput, { target: { value: '평문 수정' } })
    expect((memoInput as HTMLInputElement).value).toBe('평문 수정')
  })

  it('coedit 연결 중에는 안내 문구를 표시하고 입력/저장을 잠근다', async () => {
    let resolveProvider!: (provider: ReturnType<typeof makeProvider>) => void
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockReturnValue(
      new Promise((resolve) => {
        resolveProvider = resolve
      }),
    )

    renderPage()

    expect(await screen.findByText('협업 연결 중…')).not.toBeNull()
    expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(true)
    expect((await screen.findByTestId('estimate-coedit-header-memo')).getAttribute('data-coedit-pending')).toBe('true')

    resolveProvider(makeProvider())
  })

  it('편집불가 status 에서는 coedit provider 를 생성하지 않는다', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate({ status: 'QUOTE_CONVERTED' }))

    renderPage()

    await screen.findByText('이 견적서는 수락/거절/변환되어 더 이상 수정할 수 없습니다.')
    expect(mocks.createDocCoeditProvider).not.toHaveBeenCalled()
  })

  it('모델 lookup 성공 시 productName/unitPrice/productId 를 provider 에도 동기화한다', async () => {
    const provider = makeProvider()
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockResolvedValue(provider)
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-lookup',
      productName: '조회 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })

    renderPage()
    await screen.findByTestId('estimate-coedit-items-0-modelName')
    provider.setItemValue(0, 'modelName', 'LOOKUP-1')
    act(() => {
      provider.__emit()
    })
    await waitFor(() => expect((screen.getByTestId('estimate-coedit-items-0-modelName') as HTMLInputElement).value).toBe('LOOKUP-1'))
    fireEvent.blur(screen.getByTestId('estimate-coedit-items-0-modelName'))

    await waitFor(() => expect(mocks.lookupProductByModelName).toHaveBeenCalledWith('LOOKUP-1'))
    expect(provider.setItemValue).toHaveBeenCalledWith(0, 'productName', '조회 제품')
    // 이미 입력된 단가는 사용자 override 로 보고 provider 에 재전송하지 않는다.
    expect(provider.setItemValue).not.toHaveBeenCalledWith(0, 'unitPrice', '11000')
    expect(provider.setItemValue).toHaveBeenCalledWith(0, 'productId', 'product-lookup')
  })

  it('provider 라인 수가 서버 라인 수와 다르면 server-wins 로 재시드한다', async () => {
    const provider = makeProvider()
    provider.isEmpty.mockReturnValue(false)
    provider.__setRows([
      {
        modelName: 'STALE',
        productName: '스테일 제품',
        specification: '스테일',
        quantity: '1',
        unitPrice: '1',
        productId: 'stale-product',
      },
    ])
    mocks.getEstimate.mockResolvedValue(makeEstimate({
      lines: [
        ...makeEstimate().lines,
        {
          ...makeEstimate().lines[0],
          id: 'line-2',
          productId: 'product-2',
          modelName: 'MODEL-2',
          productName: '제품 2',
          quantity: 3,
          unitPriceWithVat: '12000',
        },
      ],
    }))
    mocks.createDocCoeditProvider.mockResolvedValue(provider)

    renderPage()

    await waitFor(() => expect(provider.replaceItems).toHaveBeenCalledTimes(1))
    expect(provider.replaceItems).toHaveBeenCalledWith([
      expect.objectContaining({ modelName: 'MODEL-1', productName: '제품 1' }),
      expect.objectContaining({ modelName: 'MODEL-2', productName: '제품 2' }),
    ])
  })

  it('newEstimate_autofillsRememberedPrice', async () => {
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-new',
      productName: '신규 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockResolvedValue({
      unitPrice: 88000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    renderPage('/sales/estimates/new')

    fireEvent.click(screen.getByTestId('estimate-select-partner-a'))
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-NEW' } })
    fireEvent.blur(estimateModel())

    await waitFor(() => expect(mocks.getPriceMemory).toHaveBeenCalledWith(mocks.partnerA.id, 'product-new'))
    await waitFor(() => expect(estimateUnitPrice().value).toBe('88000'))
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('REMEMBERED')
    const note = screen.getByRole('note', { name: /이 거래처에 마지막으로 저장된 단가/ })
    expect(note.textContent).toBe('거래처 최근단가')
    expect(estimateUnitPrice().getAttribute('aria-describedby')).toBe(note.id)
    // R4-D2: 라인 칩에 aria-live 금지 — 라인 N개 flip 시 N회 낭독 폭주(전역 고지는 배너 1곳).
    expect(note.hasAttribute('aria-live')).toBe(false)
  })

  it('CB-1 edit hydrate preserves the persisted price when partner changes', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockRejectedValue(new Error('coedit unavailable'))
    renderPage()

    await waitFor(() => expect((screen.getByTestId('estimate-select-partner-b') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByTestId('estimate-select-partner-b'))

    expect(estimateUnitPrice().value).toBe('11000')
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('USER')
    expect(mocks.getPriceMemories).not.toHaveBeenCalled()
  })

  it('editEstimate_refreshesRememberedPriceForSelectedPartner_onlyForSessionAutoLines', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockRejectedValue(new Error('coedit unavailable'))
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-session',
      productName: '세션 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockResolvedValue({
      unitPrice: 44000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    mocks.getPriceMemories.mockResolvedValue([{
      productId: 'product-session',
      unitPrice: 99000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-11T09:00:00',
    }])
    renderPage()

    await waitFor(() => expect((screen.getByTestId('estimate-form-add-line') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.click(screen.getByTestId('estimate-form-add-line'))
    fireEvent.change(estimateModel(1), { target: { value: 'MODEL-SESSION' } })
    fireEvent.blur(estimateModel(1))
    await waitFor(() => expect(estimateUnitPrice(1).value).toBe('44000'))

    // R4-D9: 배너 live region 은 활성 전에도 빈 컨테이너로 선존재해야 SR 낭독이 신뢰된다.
    const banner = screen.getByTestId('estimate-price-refresh-banner')
    expect(banner.getAttribute('role')).toBe('status')
    expect(banner.textContent).toBe('')

    fireEvent.click(screen.getByTestId('estimate-select-partner-b'))

    await waitFor(() => expect(mocks.getPriceMemories).toHaveBeenCalledWith(mocks.partnerB.id, ['product-session']))
    await waitFor(() => expect(estimateUnitPrice(1).value).toBe('99000'))
    expect(estimateUnitPrice(0).value).toBe('11000')
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('USER')
    expect(screen.getByText(/거래처 변경으로 최근단가 재적용/)).not.toBeNull()
    // R4-D9: 동일 DOM 노드 유지(재마운트 아님) + 텍스트만 토글.
    expect(screen.getByTestId('estimate-price-refresh-banner')).toBe(banner)
    expect(banner.textContent).toContain('거래처 변경으로 최근단가 재적용')
  })

  it('estimate_ignoresStaleMemoryResponse', async () => {
    const pending = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-stale',
      productName: '스테일 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockReturnValueOnce(pending.promise)
    renderPage('/sales/estimates/new')

    fireEvent.click(screen.getByTestId('estimate-select-partner-a'))
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-STALE' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(mocks.getPriceMemory).toHaveBeenCalledWith(mocks.partnerA.id, 'product-stale'))
    fireEvent.click(screen.getByTestId('estimate-select-partner-b'))
    await act(async () => {
      pending.resolve({ unitPrice: 77000, source: 'LINE_SAVE', updatedAt: '2026-07-10T09:00:00' })
      await pending.promise
    })

    // 가격 3필드는 stale 게이트로 폐기(기존 단언 유지) — 이전 거래처(A) 기억단가 미적용.
    expect(estimateUnitPrice().value).toBe('0')
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('')
    // R4-F3: 품목 바인딩(lookup 결과)은 보존 — 기존에는 통째 폐기돼 productId 미설정으로
    // 저장이 사유 없이 차단됐다.
    expect((screen.getByTestId('estimate-coedit-items-0-productName') as HTMLInputElement).value).toBe('스테일 제품')

    fireEvent.click(screen.getByTestId('estimate-form-save-button'))
    await waitFor(() => expect(mocks.createEstimate).toHaveBeenCalledTimes(1))
    expect(mocks.createEstimate).toHaveBeenCalledWith(expect.objectContaining({
      partnerId: mocks.partnerB.id,
      lines: [expect.objectContaining({ productId: 'product-stale', unitPrice: '0' })],
    }))
  })

  // R4-F1: 견적도 전표와 동일 semantics — 자동채움(REMEMBERED/CATALOG) 라인의 품목 교체 시
  // 이전 품목 단가·마커를 승계하지 않고 새 품목 기준 재채움(판매가 + 가격기억 재조회).
  it('estimate_productSwap_refillsAutoPriceAndMarker', async () => {
    mocks.lookupProductByModelName
      .mockResolvedValueOnce({
        productId: 'product-x',
        productName: '제품 X',
        productType: 'SINGLE',
        sellingPrice: '30000',
      })
      .mockResolvedValueOnce({
        productId: 'product-y',
        productName: '제품 Y',
        productType: 'SINGLE',
        sellingPrice: '55000',
      })
    mocks.getPriceMemory
      .mockResolvedValueOnce({ unitPrice: 88000, source: 'LINE_SAVE', updatedAt: '2026-07-01T09:00:00' })
      .mockResolvedValueOnce(null)
    renderPage('/sales/estimates/new')

    fireEvent.click(screen.getByTestId('estimate-select-partner-a'))
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-X' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(estimateUnitPrice().value).toBe('88000'))
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('REMEMBERED')

    fireEvent.change(estimateModel(), { target: { value: 'MODEL-Y' } })
    fireEvent.blur(estimateModel())

    // 품목 교체 시 새 품목으로 가격기억 재조회 — X의 88000/저장일이 Y로 승계되지 않는다.
    await waitFor(() => expect(mocks.getPriceMemory).toHaveBeenCalledWith(mocks.partnerA.id, 'product-y'))
    await waitFor(() => expect(estimateUnitPrice().value).toBe('55000'))
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('CATALOG')
    expect((screen.getByTestId('estimate-coedit-items-0-productName') as HTMLInputElement).value).toBe('제품 Y')
    // D-R4-1: miss 마커 라벨 = '판매가'(제품 등록 화면 sellingPrice 라벨) — '정가' 금지.
    expect(screen.getByRole('note', { name: /판매가를 적용했습니다/ }).textContent).toBe('판매가')
    expect(screen.queryByRole('note', { name: /마지막으로 저장된 단가/ })).toBeNull()
  })

  // R4-D4(a): 거래처 미선택 CATALOG 마커는 거래처를 단정하지 않는 카피로 분기 —
  // "이 거래처에 저장된 최근단가가 없어 …" 는 거래처 선택 상태 전용.
  it('estimate_noPartner_catalogMarkerDoesNotClaimPartnerCopy', async () => {
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-new',
      productName: '신규 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    renderPage('/sales/estimates/new')

    fireEvent.change(estimateModel(), { target: { value: 'MODEL-NEW' } })
    fireEvent.blur(estimateModel())

    await waitFor(() => expect(estimateUnitPrice().value).toBe('33000'))
    expect(mocks.getPriceMemory).not.toHaveBeenCalled()
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('CATALOG')
    // getByRole name(string) = 접근명 전체 일치 — 거래처 단정 카피였다면 매칭되지 않는다.
    const note = screen.getByRole('note', { name: '판매가를 적용했습니다' })
    expect(note.textContent).toBe('판매가')
    expect(note.getAttribute('aria-label')).not.toContain('거래처')
    expect(estimateUnitPrice().getAttribute('aria-describedby')).toBe(note.id)
  })

  // R4-D4(b)·D-R4-4: 거래처 해제 시 단가값 유지 + 마커(저장일 포함)만 해제. priceSource state 는
  // 유지해 거래처 재선택 시 재조회 대상 자격을 보존한다.
  it('estimate_partnerCleared_keepsPriceAndReleasesMarkerOnly', async () => {
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-new',
      productName: '신규 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockResolvedValue({
      unitPrice: 88000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    renderPage('/sales/estimates/new')

    fireEvent.click(screen.getByTestId('estimate-select-partner-a'))
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-NEW' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(estimateUnitPrice().value).toBe('88000'))
    expect(screen.getByRole('note', { name: /이 거래처에 마지막으로 저장된 단가/ })).not.toBeNull()

    fireEvent.click(screen.getByTestId('estimate-clear-partner'))

    // 단가값 유지(판매가 33000 으로 되돌리지 않음) + 마커/저장일 해제 + 상태 보존
    expect(estimateUnitPrice().value).toBe('88000')
    expect(screen.queryByRole('note')).toBeNull()
    expect(estimateUnitPrice().hasAttribute('aria-describedby')).toBe(false)
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('REMEMBERED')

    // 거래처 재선택 시 자동 라인 재조회 자격 보존 — miss 면 판매가 마커로 격리
    mocks.getPriceMemories.mockResolvedValueOnce([])
    fireEvent.click(screen.getByTestId('estimate-select-partner-b'))
    await waitFor(() => expect(mocks.getPriceMemories).toHaveBeenCalledWith(mocks.partnerB.id, ['product-new']))
    await waitFor(() => expect(estimateUnitPrice().value).toBe('33000'))
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('CATALOG')
    expect(screen.getByRole('note', { name: '이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다' }).textContent).toBe('판매가')
  })

  // R4-F2: legacy(unitPriceWithVat=null) 라인 편집-저장 시 원 공급단가 + priceVatInclusive=false —
  // BE 의 /1.1 재분리로 인한 약 9.1% 단가 하락·가격기억 오염 방지(전표 복사와 동일 semantics).
  it('estimate_legacyLine_unmodifiedSave_keepsSupplyPriceVatExclusive', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate({
      lines: [{ ...makeEstimate().lines[0], unitPriceWithVat: null, unitPrice: '10000' }],
    }))
    mocks.createDocCoeditProvider.mockRejectedValue(new Error('coedit unavailable'))
    renderPage()
    await waitFor(() => expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(false))
    expect(estimateUnitPrice().value).toBe('10000')

    fireEvent.click(screen.getByTestId('estimate-form-save-button'))

    await waitFor(() => expect(mocks.updateEstimate).toHaveBeenCalledTimes(1))
    expect(mocks.updateEstimate).toHaveBeenCalledWith('estimate-1', expect.objectContaining({
      lines: [expect.objectContaining({
        unitPrice: '10000',
        priceVatInclusive: false,
      })],
    }))
  })

  it('estimate_legacyLine_userEditedSave_sendsVatInclusive', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate({
      lines: [{ ...makeEstimate().lines[0], unitPriceWithVat: null, unitPrice: '10000' }],
    }))
    mocks.createDocCoeditProvider.mockRejectedValue(new Error('coedit unavailable'))
    renderPage()
    await waitFor(() => expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(false))

    // 사용자가 단가를 수정하면 '단가(VAT포함)' 입력 semantics — 기존대로 VAT 포함 전송.
    fireEvent.change(estimateUnitPrice(), { target: { value: '99000' } })
    fireEvent.click(screen.getByTestId('estimate-form-save-button'))

    await waitFor(() => expect(mocks.updateEstimate).toHaveBeenCalledTimes(1))
    expect(mocks.updateEstimate).toHaveBeenCalledWith('estimate-1', expect.objectContaining({
      lines: [expect.objectContaining({
        unitPrice: '99000',
        priceVatInclusive: true,
      })],
    }))
  })

  // R4-F4: 거래처 변경 최근단가 재조회 in-flight 동안 저장/발송 차단 + busy 단서 —
  // 이전 거래처 단가가 새 partnerId 로 저장돼 가격기억이 교차 오염되는 것을 방지.
  it('estimate_partnerSwitch_blocksSaveWhileRefreshInFlight', async () => {
    const pendingBulk = deferred<Array<{ productId: string; unitPrice: number; source: string; updatedAt: string }>>()
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-busy',
      productName: 'busy 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockResolvedValue({
      unitPrice: 44000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    mocks.getPriceMemories.mockReturnValueOnce(pendingBulk.promise)
    renderPage('/sales/estimates/new')

    fireEvent.click(screen.getByTestId('estimate-select-partner-a'))
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-BUSY' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(estimateUnitPrice().value).toBe('44000'))

    // R4-D9 계열 sweep: busy live region 도 배너와 동일하게 활성 전부터 빈 컨테이너로
    // 선존재해야 SR 낭독이 신뢰된다(조건부 마운트 금지). 저장 버튼 enabled 대기로
    // lookupLoading 완전 해제(= priceResolutionBusy false)를 보장한 뒤 단언한다.
    await waitFor(() =>
      expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(false),
    )
    const busyNote = screen.getByTestId('estimate-form-price-refresh-busy')
    expect(busyNote.getAttribute('role')).toBe('status')
    expect(busyNote.getAttribute('aria-live')).toBe('polite')
    expect(busyNote.textContent).toBe('')

    fireEvent.click(screen.getByTestId('estimate-select-partner-b'))
    await waitFor(() => expect(mocks.getPriceMemories).toHaveBeenCalledWith(mocks.partnerB.id, ['product-busy']))

    // 동일 DOM 노드 유지(재마운트 아님) + 텍스트만 토글.
    expect(screen.getByTestId('estimate-form-price-refresh-busy')).toBe(busyNote)
    expect(busyNote.textContent).toContain('최근단가 확인 중')
    const saveButton = screen.getByTestId('estimate-form-save-button') as HTMLButtonElement
    expect(saveButton.disabled).toBe(true)
    fireEvent.click(saveButton)
    expect(mocks.createEstimate).not.toHaveBeenCalled()

    await act(async () => {
      pendingBulk.resolve([{
        productId: 'product-busy',
        unitPrice: 99000,
        source: 'LINE_SAVE',
        updatedAt: '2026-07-11T09:00:00',
      }])
      await pendingBulk.promise
    })

    await waitFor(() => expect(estimateUnitPrice().value).toBe('99000'))
    // 완료 후에도 live region 은 상시 마운트 유지 — 텍스트만 소거된다.
    expect(screen.getByTestId('estimate-form-price-refresh-busy')).toBe(busyNote)
    expect(busyNote.textContent).toBe('')
    expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(false)
  })

  it('estimate_preservesUserOverride in both provider and UI', async () => {
    const pending = deferred<{ unitPrice: number; source: string; updatedAt: string } | null>()
    const provider = makeProvider()
    mocks.getEstimate.mockResolvedValue(makeEstimate({ lines: [] }))
    mocks.createDocCoeditProvider.mockResolvedValue(provider)
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-pending',
      productName: '대기 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockReturnValueOnce(pending.promise)
    renderPage()

    await waitFor(() => expect(provider.subscribeDoc).toHaveBeenCalled())
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-PENDING' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(mocks.getPriceMemory).toHaveBeenCalled())
    fireEvent.change(estimateUnitPrice(), { target: { value: '7777' } })
    await act(async () => {
      pending.resolve({ unitPrice: 88000, source: 'LINE_SAVE', updatedAt: '2026-07-10T09:00:00' })
      await pending.promise
    })

    expect(estimateUnitPrice().value).toBe('7777')
    expect(provider.setItemValue).toHaveBeenCalledWith(0, 'unitPrice', '7777')
    expect(provider.setItemValue).not.toHaveBeenCalledWith(0, 'unitPrice', '88000')
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('USER')
  })

  it('remote coedit unit price change promotes REMEMBERED to USER', async () => {
    const provider = makeProvider()
    mocks.getEstimate.mockResolvedValue(makeEstimate({ lines: [] }))
    mocks.createDocCoeditProvider.mockResolvedValue(provider)
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-remote-price',
      productName: '원격 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    mocks.getPriceMemory.mockResolvedValue({
      unitPrice: 88000,
      source: 'LINE_SAVE',
      updatedAt: '2026-07-10T09:00:00',
    })
    renderPage()

    await waitFor(() => expect(provider.subscribeDoc).toHaveBeenCalled())
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-REMOTE' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('REMEMBERED'))

    provider.setItemValue(0, 'unitPrice', '7777')
    act(() => provider.__emit())

    await waitFor(() => expect(estimateUnitPrice().value).toBe('7777'))
    expect(screen.getByTestId('estimate-form-line-0').getAttribute('data-price-source')).toBe('USER')
    expect(screen.queryByRole('note')).toBeNull()
  })

  it('EstimateFormPage_create_sendsPriceVatInclusiveTrue', async () => {
    mocks.lookupProductByModelName.mockResolvedValue({
      productId: 'product-create',
      productName: '생성 제품',
      productType: 'SINGLE',
      sellingPrice: '33000',
    })
    renderPage('/sales/estimates/new')
    fireEvent.click(screen.getByTestId('estimate-select-partner-a'))
    fireEvent.change(estimateModel(), { target: { value: 'MODEL-CREATE' } })
    fireEvent.blur(estimateModel())
    await waitFor(() => expect(estimateUnitPrice().value).toBe('33000'))
    fireEvent.change(estimateUnitPrice(), { target: { value: '100000' } })
    fireEvent.click(screen.getByTestId('estimate-form-save-button'))

    await waitFor(() => expect(mocks.createEstimate).toHaveBeenCalledTimes(1))
    expect(mocks.createEstimate).toHaveBeenCalledWith(expect.objectContaining({
      partnerId: mocks.partnerA.id,
      lines: [expect.objectContaining({
        productId: 'product-create',
        unitPrice: '100000',
        priceVatInclusive: true,
      })],
    }))
  })

  it('EstimateFormPage_update_sendsPriceVatInclusiveTrue', async () => {
    mocks.getEstimate.mockResolvedValue(makeEstimate())
    mocks.createDocCoeditProvider.mockRejectedValue(new Error('coedit unavailable'))
    renderPage()
    await waitFor(() => expect((screen.getByTestId('estimate-form-save-button') as HTMLButtonElement).disabled).toBe(false))
    fireEvent.change(estimateUnitPrice(), { target: { value: '100000' } })
    fireEvent.click(screen.getByTestId('estimate-form-save-button'))

    await waitFor(() => expect(mocks.updateEstimate).toHaveBeenCalledTimes(1))
    expect(mocks.updateEstimate).toHaveBeenCalledWith('estimate-1', expect.objectContaining({
      partnerId: '11111111-1111-1111-1111-111111111111',
      lines: [expect.objectContaining({
        productId: 'product-1',
        unitPrice: '100000',
        priceVatInclusive: true,
      })],
    }))
  })
})
