/**
 * 전표 작성 화면 (출고/입고 공용) — sales-form-polish 슬라이스 v3.
 *
 * Designer (5-team) spec (`docs/design/sales-form-polish-slice/`) 충실 반영.
 *
 * v3 변경사항 (sales-form-polish 슬라이스 — 본 PR):
 * - 라인 입력 → `<LineRow>` 디자인 시스템 컴포넌트 (9-column dense table)
 * - drag-and-drop 라인 순서 변경 — `@dnd-kit/sortable`
 *   (DndContext + SortableContext + useSortable + 마우스 + 키보드 sensor)
 * - 행 체크박스 + 헤더 체크박스 (전체 선택 / indeterminate)
 * - 행 클릭/체크 시 selected state — 좌측 4px 파란 띠 + 배경 변화
 * - 헤더에 [선택 항목 재고조회] 버튼 — `POST /inventory/balances/batch`
 * - `<StockBalanceModal>` 모달 (모델명 × 창고 matrix + 합계)
 * - 자동 라인 번호 (drag 시 자동 갱신)
 * - 합계 영역 헤더 — 4건 / 공급가액 / 부가세 / 총 (모던 미니멀 dense)
 * - 신규 디자인 토큰 (`--surface-*`, `--ink-*`, `--row-h` 등) 적용
 *
 * UUID 비공개 가드 (memory `feedback_uuid_no_user_visibility.md`):
 * - LineDraft.id 는 dnd-kit key 용 — 화면 미노출 (서버 UUID 또는 'tmp-N')
 * - LineDraft.productId 는 부모 state 로만 보관, 서버 호출 시 사용
 * - 모든 화면 표시 식별자는 modelName / productName / 창고 코드 등 비즈니스 라벨
 *
 * 본 컴포넌트는 `mode` prop 으로 OUTBOUND / INBOUND 양쪽 화면에서 재사용.
 */
import { useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import {
  Button,
  Card,
  DeliveryTagSelector,
  FormField,
  LineRow,
  LineTableHeader,
  StockBalanceModal,
  WarehouseSelector,
  type DeliveryTagOption,
  type LineDraft,
  type StockBalanceRow,
  type WarehouseColumn,
} from '@samhan/design-system'
import {
  DndContext,
  KeyboardSensor,
  PointerSensor,
  closestCenter,
  useSensor,
  useSensors,
  type DragEndEvent,
} from '@dnd-kit/core'
import {
  SortableContext,
  arrayMove,
  sortableKeyboardCoordinates,
  useSortable,
  verticalListSortingStrategy,
} from '@dnd-kit/sortable'
import { CSS } from '@dnd-kit/utilities'
import axios from 'axios'
import { fetchStockBalanceBatch, listWarehouses } from '../api/inventory'
import {
  createSlip,
  lookupProductByModelName,
  type SlipLineInput,
  type SlipType,
} from '../api/slip'
import { usePageTitle } from '../hooks/usePageTitle'

/**
 * 본 슬라이스용 OUTBOUND 배송태그 옵션 — BE `DeliveryTag` enum 의 OUTBOUND 8종.
 */
const OUTBOUND_TAG_OPTIONS: DeliveryTagOption[] = [
  { code: 'DAY', displayName: '당일', direction: 'OUTBOUND', autoMemo: false },
  { code: 'STACK', displayName: '야적', direction: 'OUTBOUND', autoMemo: true },
  { code: 'REGION', displayName: '지방', direction: 'OUTBOUND', autoMemo: true },
  { code: 'LOGEN', displayName: '로젠택배', direction: 'OUTBOUND', autoMemo: false },
  { code: 'GYEONGDONG_PARCEL', displayName: '경동택배', direction: 'OUTBOUND', autoMemo: false },
  { code: 'GYEONGDONG_FREIGHT', displayName: '경동화물', direction: 'OUTBOUND', autoMemo: false },
  { code: 'RENTAL', displayName: '대여', direction: 'OUTBOUND', autoMemo: false },
  { code: 'RETURN_RENTAL', displayName: '반납', direction: 'OUTBOUND', autoMemo: false },
]

/** 임시 라인 ID 생성기 — UUID 노출 방지를 위해 프론트 prefix 사용. */
let __tempIdCounter = 0
const nextTempId = (): string => `tmp-${++__tempIdCounter}`

const emptyLine = (): LineDraft => ({
  id: nextTempId(),
  productId: null,
  modelName: '',
  productName: '',
  specification: '', // Slice A 신규 (피드백 #4)
  quantity: '1',
  unitPrice: '0',
  lookupError: null,
  lookupLoading: false,
})

export interface SlipFormPageProps {
  /** OUTBOUND (판매/출고) 또는 INBOUND (구매/입고). */
  mode: SlipType
}

/**
 * dnd-kit useSortable 을 적용한 LineRow wrapper — SlipFormPage 내부 전용.
 *
 * useSortable 은 hook 이라 LineRow 외부에서 호출하고 setNodeRef + transform CSS
 * 를 wrapper div 에 부착, dragHandleProps 는 LineRow 의 DragHandle 에 전달.
 */
function SortableLineRow(props: {
  line: LineDraft
  lineNumber: number
  selected: boolean
  canDelete: boolean
  onSelect: (s: boolean) => void
  onModelNameChange: (v: string) => void
  onModelNameBlur: (v: string) => void
  onSpecificationChange: (v: string) => void
  onQuantityChange: (v: string) => void
  onUnitPriceChange: (v: string) => void
  onDelete: () => void
}) {
  const {
    attributes,
    listeners,
    setNodeRef,
    setActivatorNodeRef,
    transform,
    transition,
    isDragging,
  } = useSortable({ id: props.line.id })

  const style = {
    transform: CSS.Transform.toString(transform),
    transition,
  }

  return (
    <LineRow
      ref={setNodeRef}
      style={style}
      isDragging={isDragging}
      lineNumber={props.lineNumber}
      line={props.line}
      selected={props.selected}
      canDelete={props.canDelete}
      onSelect={props.onSelect}
      onModelNameChange={props.onModelNameChange}
      onModelNameBlur={props.onModelNameBlur}
      onSpecificationChange={props.onSpecificationChange}
      onQuantityChange={props.onQuantityChange}
      onUnitPriceChange={props.onUnitPriceChange}
      onDelete={props.onDelete}
      dragHandleProps={{
        attributes: attributes as unknown as Record<string, unknown>,
        listeners: listeners as Record<string, unknown> | undefined,
        setActivatorNodeRef,
      }}
    />
  )
}

/**
 * 출고/입고 공용 작성 화면.
 *
 * mode 별 차이:
 * - OUTBOUND: 출발/도착 창고 + 배송태그, 저장 후 `/sales` 로 이동
 * - INBOUND: 도착 창고 (출발은 거래처 측), 배송태그 미노출, 저장 후 `/purchases` 로 이동
 */
export function SlipFormPage({ mode }: SlipFormPageProps) {
  const navigate = useNavigate()
  const isOutbound = mode === 'OUTBOUND'
  const listPath = isOutbound ? '/sales' : '/purchases'
  const titleLabel = isOutbound ? '새 출고전표' : '새 입고전표'

  // Slice A: AppHeader 동적 화면명 (Designer wireframes.md § 1.3)
  usePageTitle(titleLabel)

  const [sourceWh, setSourceWh] = useState<string | null>(null)
  const [destWh, setDestWh] = useState<string | null>(null)
  const [partnerName, setPartnerName] = useState('')
  const [memo, setMemo] = useState('')
  const [tag, setTag] = useState<DeliveryTagOption['code'] | null>(null)
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()])
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set())

  // 재고조회 모달 state
  const [stockModalOpen, setStockModalOpen] = useState(false)
  const [stockRows, setStockRows] = useState<StockBalanceRow[] | null>(null)
  const [stockError, setStockError] = useState<string | null>(null)
  const [stockSelectedSnapshot, setStockSelectedSnapshot] = useState<
    Array<{ productId: string; modelName: string; productName: string }>
  >([])

  const warehousesQuery = useQuery({
    queryKey: ['warehouses'],
    queryFn: listWarehouses,
  })

  const today = useMemo(() => new Date().toISOString().slice(0, 10), [])

  // dnd-kit 마우스 + 키보드 sensor (Designer ux-flow.md § 1.2 + § 2.2 인용)
  const sensors = useSensors(
    useSensor(PointerSensor, {
      activationConstraint: { distance: 4 }, // 4px 이상 드래그 시 시작 (text 선택 보호)
    }),
    useSensor(KeyboardSensor, {
      coordinateGetter: sortableKeyboardCoordinates,
    }),
  )

  /** 창고 컬럼 메타 (재고 모달용) — listWarehouses 결과에서 자동 생성. */
  const warehouseColumns = useMemo<WarehouseColumn[]>(() => {
    const ws = warehousesQuery.data ?? []
    return ws.map((w) => ({
      code: w.code,
      label: w.name.length > 6 ? w.name.slice(0, 6) : w.name,
      virtual: w.type === 'VIRTUAL',
    }))
  }, [warehousesQuery.data])

  // ── 라인 조작 핸들러 ─────────────────────────────────────

  const addLine = () => {
    const next = emptyLine()
    setLines((ls) => [...ls, next])
  }

  const removeLine = (id: string) => {
    setLines((ls) => (ls.length === 1 ? ls : ls.filter((l) => l.id !== id)))
    setSelectedIds((prev) => {
      const next = new Set(prev)
      next.delete(id)
      return next
    })
  }

  const updateLine = (id: string, patch: Partial<LineDraft>) =>
    setLines((ls) => ls.map((l) => (l.id === id ? { ...l, ...patch } : l)))

  const toggleSelect = (id: string, selected: boolean) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (selected) next.add(id)
      else next.delete(id)
      return next
    })
  }

  const toggleAll = (selected: boolean) => {
    if (selected) setSelectedIds(new Set(lines.map((l) => l.id)))
    else setSelectedIds(new Set())
  }

  // dnd-kit drag end 핸들러 — 라인 순서 변경 + selectedIds 유지
  const handleDragEnd = (event: DragEndEvent) => {
    const { active, over } = event
    if (!over || active.id === over.id) return
    setLines((ls) => {
      const oldIdx = ls.findIndex((l) => l.id === active.id)
      const newIdx = ls.findIndex((l) => l.id === over.id)
      if (oldIdx < 0 || newIdx < 0) return ls
      return arrayMove(ls, oldIdx, newIdx)
    })
  }

  /**
   * 모델명 onBlur lookup — `GET /slips/lookup-product?modelName=...`.
   *
   * 200 시 productId / productName / sellingPrice fill,
   * 404 시 lookupError 메시지 + productId null 유지.
   */
  const handleModelNameBlur = async (id: string, modelName: string) => {
    const trimmed = modelName.trim()
    if (!trimmed) {
      updateLine(id, { productId: null, lookupError: null, productName: '' })
      return
    }
    updateLine(id, { lookupLoading: true, lookupError: null })
    try {
      const product = await lookupProductByModelName(trimmed)
      updateLine(id, {
        productId: product.productId,
        productName: product.productName,
        unitPrice: product.sellingPrice,
        lookupError: null,
        lookupLoading: false,
      })
    } catch (err) {
      const msg = axios.isAxiosError(err) && err.response?.status === 404
        ? '해당 모델명을 찾을 수 없습니다'
        : '모델명 조회에 실패했습니다'
      updateLine(id, {
        productId: null,
        productName: '',
        lookupError: msg,
        lookupLoading: false,
      })
    }
  }

  // ── 재고조회 mutation ───────────────────────────────────

  const stockMutation = useMutation({
    mutationFn: (productIds: string[]) => fetchStockBalanceBatch(productIds),
    onMutate: () => {
      setStockRows(null)
      setStockError(null)
    },
    onSuccess: (data) => {
      setStockRows(data.rows as StockBalanceRow[])
    },
    onError: () => {
      setStockError('재고 조회에 실패했습니다. 다시 시도해 주세요.')
      setStockRows([])
    },
  })

  const selectedProductLines = useMemo(() => {
    return lines
      .filter((l) => selectedIds.has(l.id) && l.productId)
      .map((l) => ({
        productId: l.productId!,
        modelName: l.modelName,
        productName: l.productName,
      }))
  }, [lines, selectedIds])

  const openStockModal = () => {
    if (selectedProductLines.length === 0) return
    const ids = selectedProductLines.map((l) => l.productId)
    setStockSelectedSnapshot(selectedProductLines)
    setStockModalOpen(true)
    stockMutation.mutate(ids)
  }

  const closeStockModal = () => setStockModalOpen(false)

  // ── 합계 계산 (Designer components.md § 6.2 인용) ──────

  const totals = useMemo(() => {
    const valid = lines.filter((l) => l.productId && Number(l.quantity) > 0)
    const supply = valid.reduce(
      (sum, l) => sum + Number(l.quantity) * Number(l.unitPrice || 0),
      0,
    )
    const vat = Math.round(supply * 0.1)
    return { count: valid.length, supply, vat, total: supply + vat }
  }, [lines])

  // ── 저장 mutation ───────────────────────────────────────

  const mutation = useMutation({
    mutationFn: () => {
      const payload: Parameters<typeof createSlip>[0] = {
        slipType: mode,
        slipDate: today,
        sourceWarehouseId: sourceWh ?? undefined,
        destinationWarehouseId: destWh ?? undefined,
        partnerName: partnerName.trim() || undefined,
        deliveryTag: isOutbound ? tag ?? undefined : undefined,
        memo: memo.trim() || undefined,
        lines: lines
          .filter((l) => l.productId && Number(l.quantity) > 0)
          .map<SlipLineInput>((l) => ({
            productId: l.productId!,
            productName: l.productName.trim() || undefined,
            modelName: l.modelName.trim() || undefined,
            specification: l.specification.trim() || undefined,
            quantity: Number(l.quantity),
            unitPrice: l.unitPrice || '0',
          })),
      }
      return createSlip(payload)
    },
    onSuccess: () => navigate(listPath),
  })

  const errorMessage = (() => {
    if (!mutation.isError) return null
    const err = mutation.error
    if (axios.isAxiosError(err)) {
      const data = err.response?.data as { message?: string } | undefined
      return data?.message ?? '전표 생성에 실패했습니다.'
    }
    return '알 수 없는 오류'
  })()

  const validLineCount = lines.filter(
    (l) => l.productId && Number(l.quantity) > 0,
  ).length
  const requiredWh = isOutbound ? sourceWh : destWh
  const canSubmit = !!requiredWh && validLineCount > 0 && !mutation.isPending

  // ── Header 체크박스 상태 ────────────────────────────────

  const allSelected = selectedIds.size === lines.length && lines.length > 0
  const someSelected = selectedIds.size > 0 && selectedIds.size < lines.length

  const stockButtonLabel =
    selectedProductLines.length === 0
      ? '재고조회'
      : selectedProductLines.length === 1
        ? '재고조회'
        : `선택 항목 재고조회 (${selectedProductLines.length}건)`

  // ── render ──────────────────────────────────────────────

  return (
    <div className="sales-form-polish">
      <div className="sfp-page-header">
        <h2 className="sfp-page-title">{titleLabel}</h2>
        <div className="sfp-page-actions">
          <Button variant="ghost" onClick={() => navigate(listPath)}>
            목록으로
          </Button>
        </div>
      </div>

      {/* 헤더 정보 카드 */}
      <Card padding={6} shadow="sm" className="sfp-card">
        <div className="sfp-section-title">헤더 정보</div>
        <div className="sfp-form-grid sfp-form-grid--3">
          <WarehouseSelector
            label={isOutbound ? '출발 창고' : '입고 창고'}
            required={isOutbound}
            warehouses={warehousesQuery.data ?? []}
            value={sourceWh}
            onChange={(id) => setSourceWh(id)}
            hideVirtual
          />
          <WarehouseSelector
            label={isOutbound ? '도착 창고' : '출발 창고 (옵션)'}
            required={!isOutbound}
            warehouses={warehousesQuery.data ?? []}
            value={destWh}
            onChange={(id) => setDestWh(id)}
            hideVirtual
          />
          {isOutbound ? (
            <DeliveryTagSelector
              options={OUTBOUND_TAG_OPTIONS}
              value={tag}
              onChange={(code) => setTag(code)}
              direction="OUTBOUND"
              slipDate={today}
            />
          ) : (
            <span aria-hidden="true" />
          )}
        </div>

        <div className="sfp-form-grid sfp-form-grid--2" style={{ marginTop: 16 }}>
          <FormField
            label="거래처명"
            render={({ id }) => (
              <input
                id={id}
                value={partnerName}
                onChange={(e) => setPartnerName(e.target.value)}
                maxLength={100}
                className="sfp-input"
              />
            )}
          />
          <FormField
            label="메모"
            render={({ id }) => (
              <input
                id={id}
                value={memo}
                onChange={(e) => setMemo(e.target.value)}
                maxLength={1000}
                className="sfp-input"
              />
            )}
          />
        </div>
      </Card>

      {/* 라인 카드 */}
      <Card padding={6} shadow="sm" className="sfp-card">
        <div className="sfp-line-toolbar">
          <div className="sfp-section-title">전표 라인</div>
          <div className="sfp-line-actions">
            <Button
              variant="secondary"
              size="sm"
              onClick={openStockModal}
              disabled={selectedProductLines.length === 0}
            >
              {stockButtonLabel}
            </Button>
            <Button variant="primary" size="sm" onClick={addLine}>
              + 라인 추가
            </Button>
          </div>
        </div>

        <div className="sfp-line-table">
          <LineTableHeader
            allSelected={allSelected}
            someSelected={someSelected}
            onToggleAll={toggleAll}
          />
          <DndContext
            sensors={sensors}
            collisionDetection={closestCenter}
            onDragEnd={handleDragEnd}
          >
            <SortableContext
              items={lines.map((l) => l.id)}
              strategy={verticalListSortingStrategy}
            >
              {lines.map((line, idx) => (
                <SortableLineRow
                  key={line.id}
                  line={line}
                  lineNumber={idx + 1}
                  selected={selectedIds.has(line.id)}
                  canDelete={lines.length > 1}
                  onSelect={(s) => toggleSelect(line.id, s)}
                  onModelNameChange={(v) => updateLine(line.id, { modelName: v })}
                  onModelNameBlur={(v) => void handleModelNameBlur(line.id, v)}
                  onSpecificationChange={(v) => updateLine(line.id, { specification: v })}
                  onQuantityChange={(v) => updateLine(line.id, { quantity: v })}
                  onUnitPriceChange={(v) => updateLine(line.id, { unitPrice: v })}
                  onDelete={() => removeLine(line.id)}
                />
              ))}
            </SortableContext>
          </DndContext>
        </div>

        {/* 합계 영역 (Designer wireframes.md § 1.1 인용) */}
        <div className="sfp-totals">
          <span className="sfp-totals-item">
            <span className="sfp-totals-label">합계</span>
            <span className="sfp-totals-value">{totals.count}건</span>
          </span>
          <span className="sfp-totals-divider" aria-hidden="true">|</span>
          <span className="sfp-totals-item">
            <span className="sfp-totals-label">공급가액</span>
            <span className="sfp-totals-value sfp-totals-num">
              ₩{totals.supply.toLocaleString()}
            </span>
          </span>
          <span className="sfp-totals-divider" aria-hidden="true">|</span>
          <span className="sfp-totals-item">
            <span className="sfp-totals-label">부가세</span>
            <span className="sfp-totals-value sfp-totals-num">
              ₩{totals.vat.toLocaleString()}
            </span>
          </span>
          <span className="sfp-totals-divider" aria-hidden="true">|</span>
          <span className="sfp-totals-item sfp-totals-item--strong">
            <span className="sfp-totals-label">총</span>
            <span className="sfp-totals-value sfp-totals-num">
              ₩{totals.total.toLocaleString()}
            </span>
          </span>
        </div>

        {errorMessage ? (
          <div className="sfp-error-banner" role="alert">
            <span aria-hidden="true">ⓘ</span>
            <span>{errorMessage}</span>
          </div>
        ) : null}

        <div className="sfp-submit-bar">
          <Button variant="ghost" onClick={() => navigate(listPath)}>
            취소
          </Button>
          <Button
            variant="primary"
            onClick={() => mutation.mutate()}
            loading={mutation.isPending}
            disabled={!canSubmit}
          >
            저장
          </Button>
        </div>
      </Card>

      {/* 재고조회 모달 */}
      <StockBalanceModal
        open={stockModalOpen}
        onClose={closeStockModal}
        selectedLines={stockSelectedSnapshot}
        warehouseColumns={warehouseColumns}
        rows={stockRows}
        error={stockError}
      />
    </div>
  )
}
