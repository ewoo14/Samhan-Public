/**
 * 기초품목 관리 페이지 (`/products/catalog`) — 물리 SKU master 등록/수정 전용 화면.
 *
 * 견적/주문 노출, 견적 카테고리, 표시순서 관리는 `EstimateItemsCatalogPage` 로 분리한다.
 * 슬1에서는 세트 구성품 모달을 현 위치에 유지한다.
 */
import {
  useCallback,
  useEffect,
  useState,
  type CSSProperties,
} from 'react'
import { useNavigate } from 'react-router-dom'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { isMockMode } from '../api/mock'
import { ProductRealtimeClient } from '../realtime/ProductRealtimeClient'
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
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import {
  Badge,
  Button,
  DataTable,
  DragHandle,
  Input,
  Modal,
  ProductAutocomplete,
  Select,
  type DataTableColumn,
  type ProductOption,
} from '@samhan/design-system'
import {
  listProducts,
  listBundleComponents,
  updateBundleComponents,
  type ProductCatalogRow,
  type ProductCategory,
  type BundleComponentInput,
  type ComponentKind,
} from '../api/productCatalogApi'
import { searchProducts as searchProductsApi } from '../api/productApi'
import { usePageTitleStore } from '../stores/pageTitle'
import { usePermissions } from '../hooks/usePermissions'
import {
  buildBundleComponentInputs,
  groupBundleComponentDrafts,
  normalizeBundleComponentDraftOrder,
  reorderBundleComponentDrafts,
  toggleComponentDefault,
  type ComponentDraftModel,
} from './componentsModalModel'

// ---------------------------------------------------------------------------
// 상수
// ---------------------------------------------------------------------------

const PAGE_SIZE = 50

/*
const PRODUCT_CATEGORY_LABEL_BROKEN_ENCODING: Record<ProductCategory, string> = {
  HOME_MULTI: '?덈???,
  SINGLE_SET: '?⑥씪 ?명듃',
  SINGLE_PART: '?⑥씪 援ъ꽦??,
  COMMERCIAL_MULTI: '?곸뾽硫??,
  COMMERCIAL_PART: '?곸뾽 援ъ꽦??,
  OLD: '?덇굅??,
  MATERIAL: '자재',
}

*/
const PRODUCT_CATEGORY_LABEL: Record<ProductCategory, string> = {
  HOME_MULTI: '홈멀티',
  SINGLE_SET: '싱글중대형',
  SINGLE_PART: '싱글 구성품',
  COMMERCIAL_MULTI: '상업 멀티',
  COMMERCIAL_PART: '상업 구성품',
  OLD: '구형',
  MATERIAL: '자재',
}

const COMPONENT_KIND_OPTIONS: Array<{ value: ComponentKind; label: string }> = [
  { value: 'INDOOR', label: '실내기' },
  { value: 'OUTDOOR', label: '실외기' },
  { value: 'PANEL', label: '판넬' },
  { value: 'REMOTE', label: '리모컨' },
  { value: 'MATERIAL', label: '자재' },
  { value: 'ACCESSORY', label: '부속품' },
  { value: 'FOOT', label: '받침대' },
]

// ---------------------------------------------------------------------------
// 에러 메시지 추출
// ---------------------------------------------------------------------------

function errorMsg(err: unknown): string {
  // axios error 의 envelope message 우선 추출 (BE ApiResponse.message 한국어)
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

// ---------------------------------------------------------------------------
// 구성품 편집 모달
// ---------------------------------------------------------------------------

interface ComponentsModalProps {
  open: boolean
  modelCode: string
  canEdit: boolean
  onClose: () => void
  onSaved: () => void
}

function ComponentsModal({
  open,
  modelCode,
  canEdit,
  onClose,
  onSaved,
}: ComponentsModalProps) {
  const queryClient = useQueryClient()
  const [drafts, setDrafts] = useState<ComponentDraftModel[]>([])
  const [selectedProduct, setSelectedProduct] = useState<ProductOption | null>(null)
  const [modalError, setModalError] = useState<string | null>(null)

  // 구성품 목록 로드
  const componentsQuery = useQuery({
    queryKey: ['bundle-components', modelCode],
    queryFn: () => listBundleComponents(modelCode),
    enabled: open && modelCode.length > 0,
    staleTime: 0,
  })

  // 구성품 저장 (PUT replace-all)
  const saveMutation = useMutation({
    mutationFn: (components: BundleComponentInput[]) =>
      updateBundleComponents(modelCode, components),
    onSuccess: () => {
      setModalError(null)
      void queryClient.invalidateQueries({ queryKey: ['product-catalog'] })
      void queryClient.invalidateQueries({ queryKey: ['bundle-components', modelCode] })
      onSaved()
    },
    onError: (err) => {
      setModalError(errorMsg(err))
    },
  })

  // 모달 열릴 때 drafts 초기화 — BE 응답 메타 전체 보존
  useEffect(() => {
    if (open && componentsQuery.data) {
      setDrafts(
        componentsQuery.data.map((c, idx) => ({
          componentProductCode: c.componentProductCode,
          componentName: c.componentName,
          defaultQty: c.defaultQty,
          qtyMode: c.qtyMode,
          componentKind: c.componentKind,
          componentVariant: c.componentVariant,
          isDefault: c.isDefault,
          specText: c.specText,
          displayOrder: idx + 1,
          _localId: `existing-${c.componentProductCode}-${idx}`,
          _isNew: false,
        })),
      )
    }
  }, [open, componentsQuery.data])

  const handleQuantityChange = (localId: string, value: string) => {
    const parsed = parseInt(value, 10)
    if (!isFinite(parsed) || parsed < 1) return
    setDrafts((prev) =>
      prev.map((d) => (d._localId === localId ? { ...d, defaultQty: parsed } : d)),
    )
  }

  const handleKindChange = (localId: string, value: string) => {
    setDrafts((prev) =>
      normalizeBundleComponentDraftOrder(
        prev.map((d) =>
          d._localId === localId
            ? { ...d, componentKind: value ? (value as ComponentKind) : null }
            : d,
        ),
      ),
    )
  }

  const handleDefaultChange = (localId: string, checked: boolean) => {
    setDrafts((prev) => normalizeBundleComponentDraftOrder(toggleComponentDefault(prev, localId, checked)))
  }

  const handleDelete = (localId: string) => {
    setDrafts((prev) => {
      const next = prev.filter((d) => d._localId !== localId)
      return normalizeBundleComponentDraftOrder(next)
    })
  }

  const searchComponentProducts = async (q: string): Promise<ProductOption[]> => {
    const products = await searchProductsApi(q)
    return products.filter((product) => {
      const visibleCode = product.modelCode ?? product.modelName
      return visibleCode !== modelCode && product.productType !== 'BUNDLE'
    })
  }

  const handleAdd = (product: ProductOption | null) => {
    if (!product) return
    const visibleCode = product.modelCode ?? product.modelName
    // 중복 추가 방지
    if (drafts.some((d) => d.componentProductCode === visibleCode)) return
    const newDraft: ComponentDraftModel = {
      componentProductCode: visibleCode,
      componentName: product.productName,
      defaultQty: 1,
      qtyMode: 'FOLLOW_SET',
      componentKind: null, // 신규: 사용자가 선택하거나 BE 기본(ACCESSORY) 적용
      componentVariant: null,
      isDefault: false,
      specText: null,
      displayOrder: drafts.length + 1,
      _localId: `new-${visibleCode}-${Date.now()}`,
      _isNew: true,
    }
    setDrafts((prev) => normalizeBundleComponentDraftOrder([...prev, newDraft]))
    setSelectedProduct(null)
  }

  const componentSensors = useSensors(
    useSensor(PointerSensor),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  )

  const handleComponentDragEnd = useCallback((event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    setDrafts((prev) =>
      reorderBundleComponentDrafts(prev, String(active.id), String(over.id)),
    )
  }, [])

  const handleSave = () => {
    if (drafts.length === 0) {
      setModalError('구성품이 없습니다. 최소 1개 이상 등록해 주세요.')
      return
    }
    saveMutation.mutate(buildBundleComponentInputs(drafts))
  }

  const handleClose = () => {
    setSelectedProduct(null)
    setModalError(null)
    onClose()
  }

  const isLoading = componentsQuery.isLoading
  const isSaving = saveMutation.isPending
  const selectedProductCode = selectedProduct
    ? selectedProduct.modelCode ?? selectedProduct.modelName
    : ''
  const selectedAlreadyAdded = selectedProductCode
    ? drafts.some((d) => d.componentProductCode === selectedProductCode)
    : false
  const componentGroups = groupBundleComponentDrafts(drafts)
  const orderedDrafts = componentGroups.flatMap((group) => group.items)

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={`구성품 편집 — ${modelCode}`}
      size="lg"
      footer={
        canEdit ? (
          <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
            <Button variant="secondary" onClick={handleClose} disabled={isSaving}>
              닫기
            </Button>
            <Button
              variant="primary"
              onClick={handleSave}
              loading={isSaving}
              disabled={isSaving || isLoading}
              data-testid="components-modal-save-button"
            >
              저장
            </Button>
          </div>
        ) : (
          <Button variant="secondary" onClick={handleClose}>닫기</Button>
        )
      }
    >
      <div
        style={{ display: 'flex', flexDirection: 'column', gap: 16, minHeight: 200 }}
        data-testid="components-modal"
      >
        {isLoading ? (
          <p style={{ color: 'var(--color-neutral-500)', fontSize: 13 }}>불러오는 중…</p>
        ) : null}

        {modalError ? (
          <div role="alert" style={errorBannerStyle} data-testid="components-modal-error">
            {modalError}
          </div>
        ) : null}

        {/* 현재 구성품 목록 */}
        <section aria-label="구성품 목록">
          <h4 style={{ margin: '0 0 8px', fontSize: 13, color: 'var(--color-neutral-700)' }}>
            구성품 ({drafts.length}개)
          </h4>
          {drafts.length === 0 && !isLoading ? (
            <p style={{ fontSize: 12, color: 'var(--color-neutral-400)' }}>구성품이 없습니다.</p>
          ) : null}
          <DndContext
            sensors={componentSensors}
            collisionDetection={closestCenter}
            onDragEnd={handleComponentDragEnd}
          >
            <SortableContext
              items={orderedDrafts.map((draft) => draft._localId)}
              strategy={verticalListSortingStrategy}
            >
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {componentGroups.map((group) => (
                  <div
                    key={group.kind}
                    style={componentGroupStyle}
                    data-testid={`components-modal-kind-group-${group.kind}`}
                  >
                    <div style={componentKindHeaderStyle}>
                      {COMPONENT_KIND_OPTIONS.find((option) => option.value === group.kind)?.label ?? group.kind}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      {group.items.map((draft) => {
                        const idx = orderedDrafts.findIndex((item) => item._localId === draft._localId)
                        return (
                          <SortableComponentRow
                            key={draft._localId}
                            draft={draft}
                            index={idx}
                            canEdit={canEdit}
                            isSaving={isSaving}
                            onKindChange={handleKindChange}
                            onDefaultChange={handleDefaultChange}
                            onQuantityChange={handleQuantityChange}
                            onDelete={handleDelete}
                          />
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </SortableContext>
          </DndContext>
        </section>

        {/* 품목 검색 + 추가 (canEdit 시에만) */}
        {canEdit ? (
          <section aria-label="구성품 추가">
            <h4 style={{ margin: '0 0 8px', fontSize: 13, color: 'var(--color-neutral-700)' }}>
              품목 추가 (단품만)
            </h4>
            <div style={{ display: 'flex', gap: 8, alignItems: 'flex-end', flexWrap: 'wrap' }}>
              <ProductAutocomplete
                value={selectedProduct}
                onChange={setSelectedProduct}
                searchProducts={searchComponentProducts}
                label="품목 검색"
                placeholder="모델명 또는 품목명 입력"
                minChars={1}
              />
              <Button
                variant="secondary"
                size="sm"
                onClick={() => handleAdd(selectedProduct)}
                disabled={!selectedProduct || isSaving || selectedAlreadyAdded}
                data-testid={
                  selectedProduct
                    ? `components-modal-add-${selectedProductCode}`
                    : 'components-modal-add-button'
                }
              >
                {selectedAlreadyAdded ? '추가됨' : '추가'}
              </Button>
            </div>
          </section>
        ) : null}
      </div>
    </Modal>
  )
}

interface SortableComponentRowProps {
  draft: ComponentDraftModel
  index: number
  canEdit: boolean
  isSaving: boolean
  onKindChange: (localId: string, value: string) => void
  onDefaultChange: (localId: string, checked: boolean) => void
  onQuantityChange: (localId: string, value: string) => void
  onDelete: (localId: string) => void
}

function SortableComponentRow({
  draft,
  index,
  canEdit,
  isSaving,
  onKindChange,
  onDefaultChange,
  onQuantityChange,
  onDelete,
}: SortableComponentRowProps) {
  const canDrag = canEdit && !isSaving && !draft.isDefault
  const dragHandleTitle = draft.isDefault
    ? '기본 구성품은 종류 안 최상단에 고정됩니다'
    : isSaving
      ? '저장 중에는 구성품 순서를 변경할 수 없습니다'
      : '같은 종류 안에서 드래그'
  const dragHandleLabel = canDrag
    ? `${draft.componentProductCode} 구성품 드래그`
    : `${draft.componentProductCode} 구성품 드래그 비활성`
  const dragHandleDisabledStyle: CSSProperties | undefined = !canDrag
    ? { opacity: 0.35, cursor: 'not-allowed' }
    : undefined
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: draft._localId, disabled: !canDrag })

  const style: CSSProperties = {
    ...componentRowStyle,
    transform: CSS.Transform.toString(transform),
    transition,
    opacity: isDragging ? 0.55 : 1,
  }

  return (
    <div
      ref={setNodeRef}
      data-testid={`components-modal-component-row-${index}`}
      style={style}
    >
      {canEdit ? (
        <DragHandle
          label={dragHandleLabel}
          listeners={canDrag ? listeners as Record<string, unknown> | undefined : undefined}
          attributes={canDrag ? attributes as unknown as Record<string, unknown> : undefined}
          setActivatorNodeRef={setActivatorNodeRef}
          dragging={isDragging}
          disabled={!canDrag}
          data-testid={`components-modal-drag-handle-${index}`}
          title={dragHandleTitle}
          style={dragHandleDisabledStyle}
        />
      ) : null}
      <span style={{ flex: 1, fontSize: 12 }}>
        <span style={{ fontFamily: 'monospace' }}>{draft.componentProductCode}</span>
        {draft.componentName ? (
          <span style={{ color: 'var(--color-neutral-500)', marginLeft: 6 }}>{draft.componentName}</span>
        ) : null}
      </span>
      {draft._isNew && canEdit ? (
        <Select
          value={draft.componentKind ?? ''}
          disabled={isSaving}
          onChange={(e) => onKindChange(draft._localId, e.target.value)}
          data-testid={`components-modal-kind-${index}`}
          selectSize="sm"
          fullWidth={false}
          style={{ minWidth: 80 }}
        >
          <option value="">분류</option>
          {COMPONENT_KIND_OPTIONS.map((opt) => (
            <option key={opt.value} value={opt.value}>{opt.label}</option>
          ))}
        </Select>
      ) : draft.componentKind ? (
        <span style={{ fontSize: 11, color: 'var(--color-neutral-500)' }}>
          {COMPONENT_KIND_OPTIONS.find((o) => o.value === draft.componentKind)?.label ?? draft.componentKind}
        </span>
      ) : null}
      <label style={componentDefaultLabelStyle}>
        <input
          type="checkbox"
          checked={draft.isDefault}
          disabled={!canEdit || isSaving}
          onChange={(e) => onDefaultChange(draft._localId, e.target.checked)}
          data-testid={`components-modal-default-${index}`}
          aria-label={`기본 구성품 ${index + 1}`}
        />
        <span>기본</span>
      </label>
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        <span style={{ fontSize: 11, color: 'var(--color-neutral-500)' }}>수량</span>
        <Input
          type="number"
          value={String(draft.defaultQty)}
          disabled={!canEdit || isSaving}
          onChange={(e) => onQuantityChange(draft._localId, e.target.value)}
          data-testid={`components-modal-quantity-${index}`}
          inputSize="sm"
          fullWidth={false}
          style={{ width: 64, textAlign: 'right' }}
          aria-label={`수량 ${index + 1}`}
          min={1}
          max={999}
          step={1}
        />
      </div>
      {canEdit ? (
        <button
          type="button"
          onClick={() => onDelete(draft._localId)}
          disabled={isSaving}
          data-testid={`components-modal-delete-${index}`}
          style={{ ...orderButtonStyle, color: 'var(--color-danger-600, #DC2626)' }}
          aria-label="삭제"
        >
          ✕
        </button>
      ) : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// 메인 컴포넌트
// ---------------------------------------------------------------------------

/**
 * 기초품목 관리 페이지 — 물리 SKU master 등록/수정 + 세트 구성품 편집.
 */
export function ProductCatalogPage() {
  const setPageTitle = usePageTitleStore((s) => s.setPageTitle)
  const queryClient = useQueryClient()
  const navigate = useNavigate()
  const { canAccess } = usePermissions()
  const canEdit = canAccess('products.admin', 'update')
  const canCreate = canAccess('products.admin', 'create')

  const [searchInput, setSearchInput] = useState('')
  const [committedSearch, setCommittedSearch] = useState('')
  const [currentPage, setCurrentPage] = useState(0)

  // 구성품 모달
  const [componentsModalCode, setComponentsModalCode] = useState<string | null>(null)

  useEffect(() => {
    setPageTitle({ title: '기초품목 관리', meta: '품목' })
    return () => setPageTitle({ title: '' })
  }, [setPageTitle])

  /**
   * §2-2 실시간 동기화: ProductCatalogPage 마운트 시 카탈로그 레벨 SSE 구독.
   * 이벤트 수신 시 react-query cache invalidate → 목록 자동 갱신.
   * VITE_MOCK_MODE 에서는 구독 skip (SSE 서버 미가동).
   * unmount 시 abort() 로 cleanup ([[SlipDetailPage 348행 패턴]]).
   */
  useEffect(() => {
    if (isMockMode()) return // mock 모드: SSE 구독 skip
    const ctrl = ProductRealtimeClient.subscribe('catalog', () => {
      void queryClient.invalidateQueries({ queryKey: ['product-catalog'] })
    })
    return () => ctrl.abort()
  }, [queryClient])

  const listQuery = useQuery({
    queryKey: ['product-catalog', committedSearch, currentPage],
    queryFn: () =>
      listProducts({
        q: committedSearch || undefined,
        page: currentPage,
        size: PAGE_SIZE,
      }),
    staleTime: 30_000,
  })

  const rows = listQuery.data?.content ?? []

  const handleQuery = useCallback(() => {
    setCurrentPage(0)
    setCommittedSearch(searchInput)
  }, [searchInput])

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter') handleQuery()
    },
    [handleQuery],
  )

  const totalElements = listQuery.data?.totalElements ?? 0
  const totalPages = listQuery.data?.totalPages ?? 1

  // ---------------------------------------------------------------------------
  // DataTable 컬럼 정의
  // ---------------------------------------------------------------------------

  const columns: DataTableColumn<ProductCatalogRow>[] = [
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
      width: '160px',
      render: (row) => row.productCategory ? (
        <span style={{ fontSize: 12, color: 'var(--color-neutral-600)' }}>
          {PRODUCT_CATEGORY_LABEL[row.productCategory]}
        </span>
      ) : (
        <span style={{ color: 'var(--color-neutral-400)' }}>—</span>
      ),
    },
    {
      key: 'productType',
      header: '세트',
      width: '100px',
      render: (row) =>
        row.productType === 'BUNDLE' ? (
          <Badge
            variant="brand"
            data-testid={`product-catalog-set-badge-${row.modelCode}`}
          >
            {`세트 · ${row.componentCount ?? 0}`}
          </Badge>
        ) : (
          <span style={{ color: 'var(--color-neutral-400)' }}>—</span>
        ),
    },
    {
      key: '_components' as const,
      header: '구성품',
      width: '90px',
      render: (row) =>
        row.productType === 'BUNDLE' ? (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => setComponentsModalCode(row.modelCode)}
            data-testid={`product-catalog-components-button-${row.modelCode}`}
          >
            구성품
          </Button>
        ) : null,
    },
    {
      key: '_actions' as const,
      header: '관리',
      width: '80px',
      render: (row) =>
        canEdit ? (
          <Button
            variant="secondary"
            size="sm"
            onClick={() => navigate(`/products/${encodeURIComponent(row.modelCode)}/edit`)}
            data-testid={`product-catalog-edit-button-${row.modelCode}`}
          >
            수정
          </Button>
        ) : null,
    },
  ]

  return (
    <div style={pageStyle}>
      {/* ── 헤더 ─────────────────────────────────────── */}
      <div style={headerRowStyle}>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 12, flexWrap: 'wrap' }}>
          <h3 style={{ margin: 0 }}>기초품목 관리</h3>
          <span style={subtitleStyle}>
            물리 SKU master 등록/수정과 세트 구성품을 관리합니다.
          </span>
        </div>
        {canCreate ? (
          <Button
            variant="primary"
            onClick={() => navigate('/products/new')}
            data-testid="product-catalog-create-button"
          >
            품목 등록
          </Button>
        ) : null}
      </div>

      {canEdit ? null : (
        <div role="status" style={readOnlyBannerStyle} data-testid="product-catalog-readonly-banner">
          조회 전용 — 품목 수정 권한이 없습니다.
        </div>
      )}

      {/* ── Toolbar ──────────────────────────────────── */}
      <section style={toolbarStyle} aria-label="조회 조건">
        <div style={fieldStyle}>
          <Input
            label="모델명 검색"
            type="text"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="모델명 또는 품목명 입력"
            data-testid="product-catalog-search-input"
            inputSize="sm"
            fullWidth={false}
            style={{ minWidth: 220 }}
          />
        </div>
        <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8 }}>
          <Button
            variant="primary"
            onClick={handleQuery}
            loading={listQuery.isFetching}
            disabled={listQuery.isFetching}
            data-testid="product-catalog-query-button"
          >
            조회
          </Button>
          {listQuery.isError ? (
            <span role="alert" style={errorBannerStyle}>
              {errorMsg(listQuery.error)}
            </span>
          ) : null}
        </div>
      </section>

      <section style={tableSectionStyle} data-testid="product-catalog-table">
        <DataTable<ProductCatalogRow>
          columns={columns}
          rows={rows}
          rowKey={(row) => row.modelCode}
          loading={listQuery.isFetching}
          emptyMessage="조회 결과가 없습니다."
        />
      </section>

      {/* ── 오류 안내 ─────────────────────────────────── */}
      {listQuery.isError && rows.length === 0 ? (
        <div role="alert" style={errorBannerStyle} data-testid="product-catalog-list-error">
          목록 조회 중 오류가 발생했습니다. {errorMsg(listQuery.error)}
        </div>
      ) : null}

      {/* ── 하단 요약 + 페이지네이션 ───────────────────── */}
      {rows.length > 0 ? (
        <div style={summaryStyle} data-testid="product-catalog-summary">
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

      {/* ── 구성품 편집 모달 ───────────────────────────── */}
      {componentsModalCode ? (
        <ComponentsModal
          open={true}
          modelCode={componentsModalCode}
          canEdit={canEdit}
          onClose={() => setComponentsModalCode(null)}
          onSaved={() => setComponentsModalCode(null)}
        />
      ) : null}
    </div>
  )
}

// ---------------------------------------------------------------------------
// 스타일
// ---------------------------------------------------------------------------

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

const componentRowStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  gap: 8,
  padding: '6px 8px',
  background: 'var(--color-neutral-50, #F7F8FA)',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 4,
}

const componentGroupStyle: CSSProperties = {
  display: 'flex',
  flexDirection: 'column',
  gap: 4,
}

const componentKindHeaderStyle: CSSProperties = {
  padding: '2px 4px',
  borderBottom: '1px solid var(--color-border, #E5E7EB)',
  color: 'var(--color-neutral-600, #4B5563)',
  fontSize: 11,
  fontWeight: 600,
}

const componentDefaultLabelStyle: CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  gap: 4,
  fontSize: 11,
  color: 'var(--color-neutral-600, #4B5563)',
  cursor: 'pointer',
  userSelect: 'none',
  whiteSpace: 'nowrap',
}

const orderButtonStyle: CSSProperties = {
  appearance: 'none',
  border: '1px solid var(--color-border, #E5E7EB)',
  borderRadius: 3,
  background: 'var(--color-bg, #FFFFFF)',
  cursor: 'pointer',
  padding: '2px 5px',
  fontSize: 10,
  lineHeight: 1,
}
