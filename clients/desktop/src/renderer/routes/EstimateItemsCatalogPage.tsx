/**
 * 견적품목 관리 페이지 (`/products/estimate-items`) — 기초품목 master 참조 기반 판매 노출 카탈로그.
 *
 * BE/데이터 모델은 변경하지 않고 기존 products endpoint 를 재사용한다.
 * - 목록: usageScope != NONE 품목, 견적 카테고리별 필터
 * - 노출: PATCH /api/v1/products/{modelCode}/usage
 * - 순서: PUT /api/v1/products/display-orders
 * - 추가: ProductAutocomplete 로 기초품목 선택 후 현재 견적 카테고리에 append
 */
import {
  useCallback,
  useEffect,
  useRef,
  useState,
  type CSSProperties,
} from 'react'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  DndContext,
  PointerSensor,
  KeyboardSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
  arrayMove,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  Badge,
  Button,
  DataTable,
  DragHandle,
  Input,
  ProductAutocomplete,
  Select,
  TagChip,
  type DataTableColumn,
  type ProductOption,
} from '@samhan/design-system'
import { isMockMode } from '../api/mock'
import { ProductRealtimeClient } from '../realtime/ProductRealtimeClient'
import {
  listProducts,
  updateDisplayOrders,
  updateProductUsage,
  type EstimateCategory,
  type ProductCatalogRow,
  type ProductCategory,
  type UsageScope,
} from '../api/productCatalogApi'
import { searchProducts as searchProductsApi } from '../api/productApi'
import { usePermissions } from '../hooks/usePermissions'
import { usePageTitleStore } from '../stores/pageTitle'
import {
  buildCategoryDisplayOrderInputs,
  estimateCategoryValues,
  exposureDisplayOrder,
  normalizeEstimateCategoryExposures,
} from './ProductCatalogPageModel'

const DISPLAY_ORDER_FULL_SIZE = 999
const PAGE_SIZE = 50

const ESTIMATE_CATEGORY_LABEL: Record<EstimateCategory, string> = {
  HOME_MULTI: '홈멀티',
  SINGLE_SET: '싱글중대형',
  COMMERCIAL_MULTI: '상업멀티',
  LEGACY: '구형',
  OTHER: '기타',
}

const PRODUCT_CATEGORY_LABEL: Record<ProductCategory, string> = {
  HOME_MULTI: '홈멀티',
  SINGLE_SET: '싱글중대형',
  SINGLE_PART: '싱글 구성품',
  COMMERCIAL_MULTI: '상업 멀티',
  COMMERCIAL_PART: '상업 구성품',
  OLD: '구형',
  MATERIAL: '자재',
}

const ESTIMATE_CATEGORY_OPTIONS: Array<{ value: EstimateCategory; label: string }> = [
  { value: 'HOME_MULTI', label: '홈멀티' },
  { value: 'SINGLE_SET', label: '싱글중대형' },
  { value: 'COMMERCIAL_MULTI', label: '상업멀티' },
  { value: 'LEGACY', label: '구형' },
  { value: 'OTHER', label: '기타' },
]

function toUsageScope(estimate: boolean, order: boolean): UsageScope {
  if (estimate && order) return 'BOTH'
  if (estimate) return 'ESTIMATE'
  if (order) return 'PARTNER_ORDER'
  return 'NONE'
}

function fromUsageScope(scope: UsageScope): { estimate: boolean; order: boolean } {
  return {
    estimate: scope === 'ESTIMATE' || scope === 'BOTH',
    order: scope === 'PARTNER_ORDER' || scope === 'BOTH',
  }
}

function errorMsg(err: unknown): string {
  if (
    typeof err === 'object' &&
    err !== null &&
    'response' in err &&
    typeof (err as { response?: unknown }).response === 'object' &&
    (err as { response?: { data?: { message?: unknown } } }).response?.data?.message
  ) {
    const msg = (err as { response: { data: { message: unknown } } }).response.data.message
    if (typeof msg === 'string' && msg.length > 0) return msg
  }
  if (err instanceof Error) return err.message
  return '처리 중 오류가 발생했습니다. 다시 시도해 주세요.'
}

function nextScopeForEstimateAppend(scope: UsageScope): UsageScope {
  return scope === 'PARTNER_ORDER' || scope === 'BOTH' ? 'BOTH' : 'ESTIMATE'
}

interface ToggleCellProps {
  row: ProductCatalogRow
  canEdit: boolean
  onPatch: (modelCode: string, scope: UsageScope, estimateCategories: EstimateCategory[]) => void
  patchLoading: boolean
}

function ToggleCell({ row, canEdit, onPatch, patchLoading }: ToggleCellProps) {
  const { estimate, order } = fromUsageScope(row.usageScope)
  const selectedCategories = estimateCategoryValues(row)
  const remainingOptions = ESTIMATE_CATEGORY_OPTIONS.filter(
    (opt) => !selectedCategories.includes(opt.value),
  )

  const handleEstimateChange = (checked: boolean) => {
    const newScope = toUsageScope(checked, order)
    onPatch(row.modelCode, newScope, checked ? selectedCategories : [])
  }

  const handleOrderChange = (checked: boolean) => {
    const newScope = toUsageScope(estimate, checked)
    const nextCategories = newScope === 'ESTIMATE' || newScope === 'BOTH'
      ? selectedCategories
      : []
    onPatch(row.modelCode, newScope, nextCategories)
  }

  const handleCategoryAdd = (value: string) => {
    if (!value) return
    const category = value as EstimateCategory
    if (selectedCategories.includes(category)) return
    onPatch(row.modelCode, row.usageScope, [...selectedCategories, category])
  }

  const handleCategoryRemove = (category: EstimateCategory) => {
    onPatch(
      row.modelCode,
      row.usageScope,
      selectedCategories.filter((current) => current !== category),
    )
  }

  const showEstimateCategory = estimate && (row.usageScope === 'ESTIMATE' || row.usageScope === 'BOTH')

  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'center', flexWrap: 'wrap' }}>
      <label style={checkboxLabelStyle}>
        <input
          type="checkbox"
          checked={estimate}
          disabled={!canEdit || patchLoading}
          onChange={(e) => handleEstimateChange(e.target.checked)}
          data-testid={`estimate-items-estimate-toggle-${row.modelCode}`}
          aria-label="견적 노출"
        />
        견적 노출
      </label>
      <label style={checkboxLabelStyle}>
        <input
          type="checkbox"
          checked={order}
          disabled={!canEdit || patchLoading}
          onChange={(e) => handleOrderChange(e.target.checked)}
          data-testid={`estimate-items-order-toggle-${row.modelCode}`}
          aria-label="주문 노출"
        />
        주문 노출
      </label>
      {showEstimateCategory ? (
        <div
          data-testid={`estimate-items-estimate-category-${row.modelCode}`}
          style={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}
        >
          {normalizeEstimateCategoryExposures(row).map((exposure) => (
            <TagChip
              key={exposure.category}
              label={ESTIMATE_CATEGORY_LABEL[exposure.category]}
              value={exposure.displayOrder != null ? String(exposure.displayOrder) : '—'}
              removeLabel={ESTIMATE_CATEGORY_LABEL[exposure.category]}
              onRemove={canEdit && !patchLoading ? () => handleCategoryRemove(exposure.category) : undefined}
              data-testid={`estimate-items-estimate-category-${row.modelCode}-chip-${exposure.category}`}
            />
          ))}
          {remainingOptions.length > 0 ? (
            <Select
              value=""
              disabled={!canEdit || patchLoading}
              onChange={(e) => handleCategoryAdd(e.target.value)}
              data-testid={`estimate-items-estimate-category-${row.modelCode}-add`}
              selectSize="sm"
              fullWidth={false}
              aria-label="견적 카테고리 추가"
              style={{ minWidth: 112 }}
            >
              <option value="">카테고리 추가</option>
              {remainingOptions.map((opt) => (
                <option key={opt.value} value={opt.value}>{opt.label}</option>
              ))}
            </Select>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

interface SortableRowProps {
  row: ProductCatalogRow
  columns: DataTableColumn<ProductCatalogRow>[]
}

function SortableRow({ row, columns }: SortableRowProps) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: row.modelCode })

  const style: CSSProperties = {
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.5 : 1,
  }

  return (
    <tr ref={setNodeRef} style={style} data-testid={`estimate-items-row-${row.modelCode}`}>
      <td style={sortableTdStyle}>
        <DragHandle
          label={`${row.modelCode} 드래그`}
          listeners={listeners as Record<string, unknown> | undefined}
          attributes={attributes as unknown as Record<string, unknown>}
          setActivatorNodeRef={setActivatorNodeRef}
          dragging={isDragging}
        />
      </td>
      {columns.filter((c) => c.key !== '_drag').map((col) => (
        <td key={String(col.key)} style={sortableTdStyle}>
          {col.render
            ? col.render(row)
            : String((row as unknown as Record<string, unknown>)[String(col.key)] ?? '')}
        </td>
      ))}
    </tr>
  )
}

export function EstimateItemsCatalogPage() {
  const setPageTitle = usePageTitleStore((s) => s.setPageTitle)
  const queryClient = useQueryClient()
  const { canAccess } = usePermissions()
  const canEdit = canAccess('products.admin', 'update')

  const [searchInput, setSearchInput] = useState('')
  const [committedSearch, setCommittedSearch] = useState('')
  const [committedCategory, setCommittedCategory] = useState<EstimateCategory | ''>('')
  const [currentPage, setCurrentPage] = useState(0)
  const [selectedProduct, setSelectedProduct] = useState<ProductOption | null>(null)
  const [patchingCode, setPatchingCode] = useState<string | null>(null)
  const [mutationError, setMutationError] = useState<string | null>(null)
  const [sortableRows, setSortableRows] = useState<ProductCatalogRow[]>([])
  const [orderDirty, setOrderDirty] = useState(false)
  const [orderSaving, setOrderSaving] = useState(false)
  const [orderError, setOrderError] = useState<string | null>(null)

  const isDragEnabled = canEdit && !!committedCategory && !committedSearch

  const sensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  )

  useEffect(() => {
    setPageTitle({ title: '견적품목 관리', meta: '품목' })
    return () => setPageTitle({ title: '' })
  }, [setPageTitle])

  useEffect(() => {
    if (isMockMode()) return
    const ctrl = ProductRealtimeClient.subscribe('catalog', () => {
      void queryClient.invalidateQueries({ queryKey: ['estimate-items-catalog'] })
    })
    return () => ctrl.abort()
  }, [queryClient])

  const listQuery = useQuery({
    queryKey: ['estimate-items-catalog', committedSearch, committedCategory, currentPage],
    queryFn: () =>
      listProducts({
        q: committedSearch || undefined,
        category: committedCategory || undefined,
        page: currentPage,
        size: PAGE_SIZE,
      }),
    staleTime: 30_000,
  })

  const rawRows = listQuery.data?.content ?? []
  const rows = rawRows.filter((row) => row.usageScope !== 'NONE')
  const prevRowsRef = useRef<ProductCatalogRow[]>([])

  useEffect(() => {
    if (!orderDirty) {
      setSortableRows(rows)
      prevRowsRef.current = rows
    }
  }, [rows, orderDirty])

  const patchMutation = useMutation({
    mutationFn: ({
      modelCode,
      scope,
      estimateCategories,
    }: {
      modelCode: string
      scope: UsageScope
      estimateCategories: EstimateCategory[]
    }) =>
      updateProductUsage(modelCode, {
        usageScope: scope,
        estimateCategories: scope === 'ESTIMATE' || scope === 'BOTH'
          ? estimateCategories
          : [],
      }),
    onSuccess: () => {
      setMutationError(null)
      setPatchingCode(null)
      void queryClient.invalidateQueries({ queryKey: ['estimate-items-catalog'] })
      void queryClient.invalidateQueries({ queryKey: ['product-catalog'] })
    },
    onError: (err) => {
      setMutationError(errorMsg(err))
      setPatchingCode(null)
    },
  })

  const addProductMutation = useMutation({
    mutationFn: async (product: ProductOption) => {
      if (!committedCategory) {
        throw new Error('카테고리를 먼저 선택해 주세요.')
      }
      const modelCode = product.modelCode ?? product.modelName
      const detail = await listProducts({ q: modelCode, page: 0, size: 20 })
      const existing = detail.content.find((row) => row.modelCode === modelCode)
      const nextCategories = Array.from(new Set([
        ...(existing ? estimateCategoryValues(existing) : []),
        committedCategory,
      ]))
      const nextScope = nextScopeForEstimateAppend(existing?.usageScope ?? 'NONE')
      return updateProductUsage(modelCode, {
        usageScope: nextScope,
        estimateCategories: nextCategories,
      })
    },
    onSuccess: () => {
      setSelectedProduct(null)
      setMutationError(null)
      void queryClient.invalidateQueries({ queryKey: ['estimate-items-catalog'] })
      void queryClient.invalidateQueries({ queryKey: ['product-catalog'] })
    },
    onError: (err) => {
      setMutationError(errorMsg(err))
    },
  })

  const handleQuery = useCallback(() => {
    setCurrentPage(0)
    setOrderDirty(false)
    setCommittedSearch(searchInput)
  }, [searchInput])

  const handleCategoryChange = useCallback((value: EstimateCategory | '') => {
    setCommittedCategory(value)
    setCurrentPage(0)
    setOrderDirty(false)
  }, [])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') handleQuery()
    },
    [handleQuery],
  )

  const handlePatch = useCallback(
    (modelCode: string, scope: UsageScope, estimateCategories: EstimateCategory[]) => {
      setPatchingCode(modelCode)
      setMutationError(null)
      patchMutation.mutate({ modelCode, scope, estimateCategories })
    },
    [patchMutation],
  )

  const handleDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    setSortableRows((prev) => {
      const oldIndex = prev.findIndex((r) => r.modelCode === String(active.id))
      const newIndex = prev.findIndex((r) => r.modelCode === String(over.id))
      if (oldIndex < 0 || newIndex < 0) return prev
      return arrayMove(prev, oldIndex, newIndex)
    })
    setOrderDirty(true)
  }, [])

  const handleSaveOrder = useCallback(async () => {
    if (!committedCategory) return
    setOrderSaving(true)
    setOrderError(null)
    try {
      const firstPage = await listProducts({
        category: committedCategory,
        page: 0,
        size: DISPLAY_ORDER_FULL_SIZE,
      })
      const remainingPages = await Promise.all(
        Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, i) =>
          listProducts({
            category: committedCategory,
            page: i + 1,
            size: DISPLAY_ORDER_FULL_SIZE,
          }),
        ),
      )
      const allRows = [firstPage, ...remainingPages].flatMap((page) => page.content)
      const allExposed = allRows.filter((r) => r.usageScope !== 'NONE')
      const currentPageCodes = new Set(
        sortableRows.filter((r) => r.usageScope !== 'NONE').map((r) => r.modelCode),
      )
      const outsideItems = allExposed.filter((r) => !currentPageCodes.has(r.modelCode))
      const firstPageItemOriginalIdx = allExposed.findIndex((r) =>
        currentPageCodes.has(r.modelCode),
      )
      const insertAt = firstPageItemOriginalIdx < 0 ? outsideItems.length : firstPageItemOriginalIdx
      const currentPageOrdered = sortableRows.filter((r) => r.usageScope !== 'NONE')
      const merged = [
        ...outsideItems.slice(0, insertAt),
        ...currentPageOrdered,
        ...outsideItems.slice(insertAt),
      ]
      const orders = buildCategoryDisplayOrderInputs(merged, committedCategory)

      if (orders.length === 0) {
        setOrderError('노출 품목이 없어 순서를 저장할 수 없습니다.')
        return
      }

      await updateDisplayOrders(orders)
      setOrderDirty(false)
      void queryClient.invalidateQueries({ queryKey: ['estimate-items-catalog'] })
    } catch (err) {
      setOrderError(errorMsg(err))
    } finally {
      setOrderSaving(false)
    }
  }, [sortableRows, queryClient, committedCategory])

  const searchMasterProducts = useCallback((q: string) => searchProductsApi(q), [])

  const totalElements = listQuery.data?.totalElements ?? 0
  const totalPages = listQuery.data?.totalPages ?? 1
  const selectedProductCode = selectedProduct
    ? selectedProduct.modelCode ?? selectedProduct.modelName
    : ''

  const columns: DataTableColumn<ProductCatalogRow>[] = [
    ...(isDragEnabled
      ? [
          {
            key: '_drag' as const,
            header: '',
            width: '32px',
            render: () => null,
          } as DataTableColumn<ProductCatalogRow>,
        ]
      : []),
    {
      key: 'modelCode',
      header: '모델명',
      width: '160px',
      render: (row) => (
        <span style={{ fontFamily: 'monospace', fontSize: 12 }}>{row.modelCode}</span>
      ),
    },
    {
      key: 'name',
      header: '품목명',
      width: '220px',
    },
    {
      key: 'estimateCategory',
      header: '카테고리',
      width: '220px',
      render: (row) => {
        const exposures = normalizeEstimateCategoryExposures(row)
        return (
          <div style={{ display: 'flex', gap: 4, alignItems: 'center', flexWrap: 'wrap' }}>
            {row.productCategory ? (
              <span style={{ fontSize: 12, color: 'var(--color-neutral-600)' }}>
                {PRODUCT_CATEGORY_LABEL[row.productCategory]}
              </span>
            ) : null}
            {exposures.length > 0 ? (
              exposures.map((entry) => (
                <Badge key={entry.category} variant="brand">
                  {ESTIMATE_CATEGORY_LABEL[entry.category]}
                </Badge>
              ))
            ) : row.productCategory ? null : (
              <span style={{ color: 'var(--color-neutral-400)' }}>—</span>
            )}
          </div>
        )
      },
    },
    {
      key: 'usageScope',
      header: '노출 설정',
      width: '280px',
      render: (row) => (
        <ToggleCell
          row={row}
          canEdit={canEdit}
          onPatch={handlePatch}
          patchLoading={patchingCode === row.modelCode}
        />
      ),
    },
    {
      key: 'displayOrder',
      header: '표시순서',
      width: '80px',
      render: (row) => {
        if (normalizeEstimateCategoryExposures(row).length === 0) {
          return <span style={{ color: 'var(--color-neutral-400)' }}>—</span>
        }
        if (!committedCategory) {
          return (
            <span
              title="카테고리 선택 시 표시"
              style={{ color: 'var(--color-neutral-500)', whiteSpace: 'nowrap' }}
            >
              카테고리별
            </span>
          )
        }
        const order = exposureDisplayOrder(row, committedCategory)
        return order != null ? String(order) : <span style={{ color: 'var(--color-neutral-400)' }}>—</span>
      },
    },
  ]

  return (
    <div style={pageStyle}>
      <div style={headerRowStyle}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <h3 style={{ margin: 0 }}>견적품목 관리</h3>
          <span style={subtitleStyle}>
            기초품목에서 선택한 판매 노출 항목과 카테고리별 표시순서를 관리합니다.
          </span>
        </div>
      </div>

      {canEdit ? null : (
        <div role="status" style={readOnlyBannerStyle} data-testid="estimate-items-readonly-banner">
          조회 전용 — 노출 변경 권한이 없습니다.
        </div>
      )}

      <section style={toolbarStyle} aria-label="조회 조건">
        <div style={fieldStyle}>
          <Input
            label="모델명 검색"
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="모델명 또는 품목명 입력"
            data-testid="estimate-items-search-input"
            inputSize="sm"
            fullWidth={false}
            style={{ minWidth: 220 }}
          />
        </div>
        <div style={fieldStyle}>
          <Select
            label="카테고리"
            value={committedCategory}
            onChange={(e) => handleCategoryChange(e.target.value as EstimateCategory | '')}
            selectSize="sm"
            fullWidth={false}
            style={{ minWidth: 130 }}
            data-testid="estimate-items-category-select"
          >
            <option value="">전체</option>
            {ESTIMATE_CATEGORY_OPTIONS.map((opt) => (
              <option key={opt.value} value={opt.value}>{opt.label}</option>
            ))}
          </Select>
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
          <Button
            variant="primary"
            onClick={handleQuery}
            loading={listQuery.isFetching}
            disabled={listQuery.isFetching}
            data-testid="estimate-items-query-button"
          >
            조회
          </Button>
          {canEdit && committedCategory ? (
            <Button
              variant="primary"
              onClick={() => { void handleSaveOrder() }}
              loading={orderSaving}
              disabled={orderSaving || listQuery.isFetching || rows.length === 0}
              data-testid="estimate-items-save-order-button"
            >
              순서 저장
            </Button>
          ) : null}
          {isDragEnabled && !orderDirty && rows.length > 0 ? (
            <span style={{ fontSize: 11, color: 'var(--color-neutral-400)' }}>
              행을 드래그하여 순서 조정
            </span>
          ) : null}
          {canEdit && !committedCategory && !committedSearch ? (
            <span
              style={{ fontSize: 11, color: 'var(--color-neutral-500)' }}
              data-testid="estimate-items-drag-disabled-caption"
            >
              카테고리를 선택하면 순서를 조정할 수 있습니다
            </span>
          ) : null}
          {committedSearch ? (
            <span style={{ fontSize: 11, color: 'var(--color-neutral-400)' }}>
              검색 중 — 드래그 비활성
            </span>
          ) : null}
          {listQuery.isError ? (
            <span role="alert" style={errorBannerStyle}>
              {errorMsg(listQuery.error)}
            </span>
          ) : null}
        </div>
      </section>

      {canEdit ? (
        <section
          style={toolbarStyle}
          aria-label="기초품목 선택 추가"
          data-testid="estimate-items-add-product"
        >
          <ProductAutocomplete
            value={selectedProduct}
            onChange={setSelectedProduct}
            searchProducts={searchMasterProducts}
            label="기초품목 선택"
            placeholder="모델명 또는 품목명 입력"
            minChars={1}
          />
          <Button
            variant="secondary"
            size="sm"
            onClick={() => selectedProduct && addProductMutation.mutate(selectedProduct)}
            loading={addProductMutation.isPending}
            disabled={!committedCategory || !selectedProduct || addProductMutation.isPending}
            data-testid="estimate-items-add-product-button"
          >
            {selectedProductCode ? `${selectedProductCode} 추가` : '현재 카테고리에 추가'}
          </Button>
          {!committedCategory ? (
            <span style={{ fontSize: 11, color: 'var(--color-neutral-500)' }}>
              추가할 견적 카테고리를 먼저 선택하세요.
            </span>
          ) : null}
        </section>
      ) : null}

      {mutationError ? (
        <div role="alert" style={errorBannerStyle} data-testid="estimate-items-mutation-error">
          {mutationError}
        </div>
      ) : null}

      {orderError ? (
        <div role="alert" style={errorBannerStyle} data-testid="estimate-items-order-error">
          {orderError}
        </div>
      ) : null}

      <section style={tableSectionStyle} data-testid="estimate-items-table">
        {isDragEnabled ? (
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext
              items={sortableRows.map((r) => r.modelCode)}
              strategy={verticalListSortingStrategy}
            >
              <div style={sortableTableWrapStyle}>
                <div style={{ width: '100%', overflowX: 'auto' }}>
                  <table style={sortableTableStyle}>
                    <thead style={sortableTheadStyle}>
                      <tr>
                        <th style={{ ...sortableThStyle, width: 32 }} />
                        {columns.filter((c) => c.key !== '_drag').map((col) => (
                          <th
                            key={String(col.key)}
                            style={{
                              ...sortableThStyle,
                              ...(col.width ? { width: col.width } : {}),
                            }}
                          >
                            {col.header}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {sortableRows.length === 0 && !listQuery.isFetching ? (
                        <tr>
                          <td
                            colSpan={columns.length + 1}
                            style={{ textAlign: 'center', padding: '24px 12px', color: 'var(--color-neutral-400)', fontSize: 13 }}
                          >
                            조회 결과가 없습니다.
                          </td>
                        </tr>
                      ) : (
                        sortableRows.map((row) => (
                          <SortableRow key={row.modelCode} row={row} columns={columns} />
                        ))
                      )}
                    </tbody>
                  </table>
                </div>
                {listQuery.isFetching ? (
                  <div style={loadingOverlayStyle} role="status" aria-live="polite">
                    로딩 중…
                  </div>
                ) : null}
              </div>
            </SortableContext>
          </DndContext>
        ) : (
          <DataTable<ProductCatalogRow>
            columns={columns}
            rows={sortableRows}
            rowKey={(row) => row.modelCode}
            loading={listQuery.isFetching}
            emptyMessage="조회 결과가 없습니다."
          />
        )}
      </section>

      {listQuery.isError && rows.length === 0 ? (
        <div role="alert" style={errorBannerStyle} data-testid="estimate-items-list-error">
          목록 조회 중 오류가 발생했습니다. {errorMsg(listQuery.error)}
        </div>
      ) : null}

      {rows.length > 0 ? (
        <div style={summaryStyle} data-testid="estimate-items-summary">
          <span>
            총 <strong>{totalElements.toLocaleString('ko-KR')}</strong>건
            {listQuery.isFetching ? <span style={{ marginLeft: 6, color: 'var(--color-neutral-400)' }}>갱신 중…</span> : null}
          </span>
          {totalPages > 1 ? (
            <div style={paginationStyle}>
              <button
                type="button"
                style={{
                  ...pageButtonStyle,
                  ...(currentPage === 0 || listQuery.isFetching ? pageButtonDisabledStyle : {}),
                }}
                disabled={currentPage === 0 || listQuery.isFetching}
                onClick={() => {
                  setOrderDirty(false)
                  setCurrentPage((p) => Math.max(0, p - 1))
                }}
                aria-label="이전 페이지"
              >
                이전
              </button>
              <span style={pageInfoStyle}>
                {currentPage + 1} / {totalPages}
              </span>
              <button
                type="button"
                style={{
                  ...pageButtonStyle,
                  ...(currentPage >= totalPages - 1 || listQuery.isFetching ? pageButtonDisabledStyle : {}),
                }}
                disabled={currentPage >= totalPages - 1 || listQuery.isFetching}
                onClick={() => {
                  setOrderDirty(false)
                  setCurrentPage((p) => Math.min(totalPages - 1, p + 1))
                }}
                aria-label="다음 페이지"
              >
                다음
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

const pageStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 12,
  height: '100%',
}

const headerRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
  flexWrap: 'wrap',
}

const subtitleStyle: CSSProperties = {
  fontSize: 12,
  color: 'var(--color-neutral-500, #6B7280)',
}

const toolbarStyle: CSSProperties = {
  display: 'flex',
  gap: 12,
  flexWrap: 'wrap',
  alignItems: 'flex-end',
  padding: '12px 16px',
  background: 'var(--color-bg, #FFFFFF)',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 8,
}

const fieldStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
}

const checkboxLabelStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 3,
  fontSize: 12,
  color: 'var(--color-neutral-700, #363D49)',
  cursor: 'pointer',
  userSelect: 'none',
}

const tableSectionStyle: CSSProperties = {
  flex: 1,
  minHeight: 0,
  overflow: 'auto',
}

const summaryStyle: CSSProperties = {
  padding: '8px 12px',
  background: 'var(--color-neutral-50, #F7F8FA)',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 6,
  fontSize: 13,
  color: 'var(--color-neutral-700, #363D49)',
  display: 'flex',
  alignItems: 'center',
  flexWrap: 'wrap',
  gap: 8,
}

const paginationStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  marginLeft: 'auto',
}

const pageButtonStyle: CSSProperties = {
  appearance: 'none',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 4,
  background: 'var(--color-bg, #FFFFFF)',
  color: 'var(--color-neutral-700, #363D49)',
  padding: '2px 10px',
  fontSize: 12,
  cursor: 'pointer',
}

const pageButtonDisabledStyle: CSSProperties = {
  opacity: 0.4,
  cursor: 'not-allowed',
  background: 'var(--color-neutral-50, #F7F8FA)',
}

const pageInfoStyle: CSSProperties = {
  fontSize: 12,
  color: 'var(--color-neutral-500, #6B7280)',
  minWidth: 48,
  textAlign: 'center',
}

const errorBannerStyle: CSSProperties = {
  fontSize: 12,
  color: 'var(--color-danger-700, #991B1B)',
  background: 'var(--color-danger-50, #FEF2F2)',
  border: '1px solid var(--color-danger-200, #FECACA)',
  borderRadius: 4,
  padding: '4px 8px',
}

const readOnlyBannerStyle: CSSProperties = {
  fontSize: 12,
  color: 'var(--color-neutral-600, #4B5563)',
  background: 'var(--color-neutral-50, #F7F8FA)',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 4,
  padding: '6px 10px',
}

const sortableTableWrapStyle: CSSProperties = {
  position: 'relative',
  width: '100%',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 6,
  background: 'var(--color-bg, #FFFFFF)',
  overflow: 'hidden',
}

const sortableTableStyle: CSSProperties = {
  width: '100%',
  borderCollapse: 'separate',
  borderSpacing: 0,
  fontSize: 13,
  color: 'var(--color-text, #1A1D23)',
}

const sortableTheadStyle: CSSProperties = {
  position: 'sticky',
  top: 0,
  zIndex: 1,
  background: 'var(--color-bg-subtle, #F7F8FA)',
}

const sortableThStyle: CSSProperties = {
  padding: '8px 12px',
  borderBottom: '1px solid var(--color-border, #E5E7EB)',
  background: 'var(--color-bg-subtle, #F7F8FA)',
  color: 'var(--color-text-muted, #6B7280)',
  fontSize: 12,
  fontWeight: 600,
  whiteSpace: 'nowrap',
  textAlign: 'left',
}

const sortableTdStyle: CSSProperties = {
  padding: '6px 12px',
  borderBottom: '1px solid var(--color-border, #E5E7EB)',
  verticalAlign: 'middle',
}

const loadingOverlayStyle: CSSProperties = {
  position: 'absolute',
  inset: 0,
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
  background: 'rgba(255,255,255,0.6)',
  pointerEvents: 'none',
  fontSize: 12,
  color: 'var(--color-neutral-500, #6B7280)',
}
