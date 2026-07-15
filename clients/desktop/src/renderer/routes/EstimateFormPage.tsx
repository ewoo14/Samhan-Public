/**
 * 견적서 작성/편집 화면 — `/sales/estimates/new` + `/:id/edit` (P2-1 #6).
 *
 * <p>UX:
 * <ul>
 *   <li>거래처 선택 — partner-service `searchPartners` 자동완성 (snapshot 자동 입력).</li>
 *   <li>유효기간 — 작성일 기준 +30일 default. 사용자 변경 가능.</li>
 *   <li>라인 입력 — 모델명 onBlur lookup → productId / productName / 단가 자동 채움.</li>
 *   <li>저장 — DRAFT 생성/갱신 후 상세로 이동.</li>
 *   <li>발송 — 편집 모드에서만. DRAFT → SENT 전이.</li>
 * </ul>
 *
 * <p>매뉴얼 출처: {@code docs/manual/01-영업/06-견적서.md}.
 * UUID 비공개 가드 — productId / partnerId 는 state 에만, 화면 표시는 modelName / partnerName.
 */
import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Button, Card, PartnerAutocomplete, Spinner, type PartnerOption } from '@samhan/design-system'
import {
  createEstimate,
  getEstimate,
  sendEstimate,
  updateEstimate,
  type BundleSetOptions,
  type CreateEstimateRequest,
  type EstimateDetail,
  type EstimateLineRequest,
  type UpdateEstimateRequest,
} from '../api/estimateApi'
import { searchPartners, type PartnerSummary } from '../api/sales'
import {
  lookupProductByModelName,
  getPriceMemories,
  getPriceMemory,
  emptyBundleSetOptions,
  toApiBundleSetOptions,
} from '../api/slip'
import { useIsMobile } from '../hooks/useIsMobile'
import { usePageTitle } from '../hooks/usePageTitle'
import { usePermissions } from '../hooks/usePermissions'
import { isAutoPriceSource, shouldAutoFillPrice } from '../utils/priceSourceRules'
import { CollaborativeSlipInput } from '../components/collab/CollaborativeSlipInput'
import { createDocCoeditProvider, type DocCoeditProvider } from '../realtime/createCoeditProvider'
import { LineLookupReferenceModal } from './components/LineLookupReferenceModal'
import { BundleOptionRow } from './components/BundleOptionRow'

let __lineUidCounter = 0
const nextLineUid = (): string => `est-line-${++__lineUidCounter}`
const UUID_PATTERN =
  /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

interface DraftLine {
  uid: string
  /** lookup 성공 시 채워지는 product UUID — 화면 미노출. */
  productId: string | null
  modelName: string
  productName: string
  specification: string
  quantity: string
  unitPrice: string
  priceSource?: 'REMEMBERED' | 'CATALOG' | 'USER' | null
  catalogUnitPrice?: string | null
  priceMemoryUpdatedAt?: string | null
  priceRefreshChanged?: boolean
  /**
   * legacy(단가 부가세포함 전환 이전, unitPriceWithVat=null) 라인의 원 공급단가 —
   * 편집 hydrate 시 박제. 사용자가 단가를 수정하지 않은 라인은 저장 시
   * priceVatInclusive=false + 이 원값으로 전송해 /1.1 재분리(약 9.1% 하락)와
   * 가격기억 오염을 막는다(R4-F2 — 전표 복사는 R6-H2 부터 BE 서버 복사
   * POST /slips/{id}/duplicate 가 동일 원칙을 라인 verbatim 승계로 보장).
   * 신규 라인/VAT포함 저장 라인은 null.
   */
  legacySupplyUnitPrice?: string | null
  /** legacy 공급단가를 사용자/원격 편집이 건드리지 않았는지 나타내는 명시적 provenance. */
  legacyPriceUntouched?: boolean
  note: string
  lookupError: string | null
  lookupLoading: boolean
  /** 품목 유형 — "SINGLE" | "BUNDLE". BUNDLE 일 때만 세트 옵션 노출. */
  productType: string | null
  /** 세트 전개 옵션 — BUNDLE 라인에 한해 채움 (BE BundleSetOptions). */
  setOptions: BundleSetOptions
}

const emptyLine = (): DraftLine => ({
  uid: nextLineUid(),
  productId: null,
  modelName: '',
  productName: '',
  specification: '',
  quantity: '1',
  unitPrice: '0',
  priceSource: null,
  catalogUnitPrice: null,
  priceMemoryUpdatedAt: null,
  priceRefreshChanged: false,
  legacySupplyUnitPrice: null,
  legacyPriceUntouched: false,
  note: '',
  lookupError: null,
  lookupLoading: false,
  productType: null,
  setOptions: emptyBundleSetOptions(),
})

const today = (): string => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

const datePlusDays = (iso: string, days: number): string => {
  const m = /^(\d{4})-(\d{2})-(\d{2})/.exec(iso)
  if (!m) return ''
  const d = new Date(`${m[1]}-${m[2]}-${m[3]}`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

const fmt = (n: number): string => Math.trunc(n).toLocaleString('ko-KR')
const ESTIMATE_HEADER_TEXT_FIELDS = new Set<string>(['memo'])

const calcLineSupply = (qty: string, unitPrice: string): number => {
  const q = Number.parseFloat(qty || '0')
  const p = Number.parseFloat(unitPrice || '0')
  if (!Number.isFinite(q) || !Number.isFinite(p)) return 0
  return Math.trunc(q * p)
}

/**
 * 단가 출처 마커 라벨/설명 — 전표(LineRow/SlipMobileLineCard)와 동일 카피.
 *
 * D-R4-1: 자동채움 실체 = 제품 등록 화면 '판매가'(sellingPrice) — '정가' 라벨 금지(출고가 별칭 오도).
 * R4-D4(a): 거래처 미선택(hasPartner=false) 시 CATALOG 설명이 거래처를 단정하지 않는다.
 * D-R4-4: 거래처 해제 시 REMEMBERED 마커(저장일 포함)만 해제 — 단가값·priceSource state 는 유지해
 * 재선택 시 재조회(refreshAutoPricesForPartner) 대상 자격을 보존한다.
 */
function priceSourceStatus(line: DraftLine, hasPartner: boolean): {
  label: string
  description: string
} | null {
  if (line.priceSource === 'REMEMBERED') {
    if (!hasPartner) return null
    return {
      label: '거래처 최근단가',
      description: `이 거래처에 마지막으로 저장된 단가${line.priceMemoryUpdatedAt ? ` · ${line.priceMemoryUpdatedAt.slice(0, 10)} 저장` : ''}`,
    }
  }
  if (line.priceSource === 'CATALOG') {
    return {
      label: '판매가',
      description: hasPartner
        ? '이 거래처에 저장된 최근단가가 없어 판매가를 적용했습니다'
        : '판매가를 적용했습니다',
    }
  }
  return null
}

function PriceChangeIndicator({ id }: { id: string }) {
  return (
    <span id={id} className="price-change-indicator">
      <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
        <path d="M3 2v7m0 0L1.5 7.5M3 9l1.5-1.5M9 10V3m0 0L7.5 4.5M9 3l1.5 1.5" fill="none" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
      단가 변경
    </span>
  )
}

function toDraftLinesFromEstimate(estimate: EstimateDetail): DraftLine[] {
  return estimate.lines.length > 0
    ? estimate.lines.map((line) => {
        // legacy(unitPriceWithVat=null) 라인의 unitPrice 는 공급단가다. 값은 그대로 노출하되
        // 원 공급단가를 legacySupplyUnitPrice 에 박제 — 저장 시 미수정 라인만
        // priceVatInclusive=false 로 원값 전송(R4-F2 — 전표 복사는 R6-H2 부터 BE 서버
        // 복사가 동일 원칙 보장, FE 재조립 패턴은 제거됨).
        const hasVatInclusivePrice = line.unitPriceWithVat != null
        const canonicalUnitPrice = String(
          hasVatInclusivePrice ? line.unitPriceWithVat : line.unitPrice,
        )
        return {
          uid: nextLineUid(),
          productId: line.productId,
          modelName: line.modelName ?? '',
          productName: line.productName ?? '',
          specification: line.specification ?? '',
          quantity: String(line.quantity),
          // 단가 부가세포함: 폼 단가 입력은 VAT 포함값. 편집 hydrate/coedit seed 모두 같은 값으로 보존.
          unitPrice: canonicalUnitPrice,
          // 서버에서 hydrate 된 저장 단가는 사용자 확정값으로 취급해 거래처 변경 자동재조회에서 보호한다.
          priceSource: 'USER',
          catalogUnitPrice: null,
          priceMemoryUpdatedAt: null,
          priceRefreshChanged: false,
          legacySupplyUnitPrice: hasVatInclusivePrice ? null : String(line.unitPrice),
          legacyPriceUntouched: !hasVatInclusivePrice,
          note: line.note ?? '',
          lookupError: null,
          lookupLoading: false,
          // 편집 모드: 이미 전개·저장된 구성품 라인이므로 재전개하지 않음.
          productType: null,
          setOptions: emptyBundleSetOptions(),
        }
      })
    : [emptyLine()]
}

function seedEstimateCoeditProvider(provider: DocCoeditProvider, estimate: EstimateDetail) {
  provider.setHeaderValue('partnerName', estimate.partnerName)
  provider.setHeaderValue('partnerBusinessNo', estimate.partnerBusinessNo ?? '')
  provider.setHeaderValue('partnerAddress', estimate.partnerAddress ?? '')
  provider.setHeaderValue('estimateDate', estimate.estimateDate)
  provider.setHeaderValue('validUntil', estimate.validUntil ?? '')
  provider.setHeaderValue('memo', estimate.memo ?? '')
  provider.replaceItems(
    toDraftLinesFromEstimate(estimate).map((line) => ({
      modelName: line.modelName,
      productName: line.productName,
      specification: line.specification,
      quantity: line.quantity,
      unitPrice: line.unitPrice,
      productId: line.productId ?? '',
    })),
  )
}

type LocalAutoPriceWrite = Pick<
  DraftLine,
  'unitPrice' | 'priceSource' | 'priceMemoryUpdatedAt' | 'priceRefreshChanged'
>

function coeditLinesToDraftLines(
  provider: DocCoeditProvider,
  current: DraftLine[],
  localAutoPriceWrites?: Map<string, LocalAutoPriceWrite>,
): DraftLine[] {
  return provider.items.toArray().map((_, index) => {
    const previous = current[index]
    const unitPrice = provider.getItemValue(index, 'unitPrice') || '0'
    const expectedAutoWrite = previous ? localAutoPriceWrites?.get(previous.uid) : undefined
    const isExpectedAutoWrite = expectedAutoWrite?.unitPrice === unitPrice
    if (previous && expectedAutoWrite) localAutoPriceWrites?.delete(previous.uid)
    const isRemoteUnitPriceChange = Boolean(
      previous && unitPrice !== previous.unitPrice && !isExpectedAutoWrite,
    )
    return {
      uid: previous?.uid ?? nextLineUid(),
      productId: provider.getItemValue(index, 'productId') || null,
      modelName: provider.getItemValue(index, 'modelName'),
      productName: provider.getItemValue(index, 'productName'),
      specification: provider.getItemValue(index, 'specification'),
      quantity: provider.getItemValue(index, 'quantity') || '0',
      unitPrice,
      priceSource: isExpectedAutoWrite
        ? expectedAutoWrite.priceSource
        : isRemoteUnitPriceChange
          ? 'USER'
          : previous?.priceSource ?? null,
      catalogUnitPrice: previous?.catalogUnitPrice ?? null,
      priceMemoryUpdatedAt: isExpectedAutoWrite
        ? expectedAutoWrite.priceMemoryUpdatedAt
        : isRemoteUnitPriceChange
          ? null
          : previous?.priceMemoryUpdatedAt ?? null,
      priceRefreshChanged: isExpectedAutoWrite
        ? expectedAutoWrite.priceRefreshChanged
        : isRemoteUnitPriceChange
          ? false
          : previous?.priceRefreshChanged ?? false,
      // legacy 공급단가 박제/provenance 는 라인 identity(uid) 에 따라 보존한다. 값 자체를 원값으로
      // 되돌려도 원격 편집이 있었으면 untouched 로 복귀하지 않는다(R5-H2).
      legacySupplyUnitPrice: previous?.legacySupplyUnitPrice ?? null,
      legacyPriceUntouched: isRemoteUnitPriceChange
        ? false
        : previous?.legacyPriceUntouched ?? false,
      note: previous?.note ?? '',
      // 원격 doc 변경마다 재빌드되므로 진행 중 lookup 상태는 previous 에서 보존(스피너 조기소멸 방지, 리뷰 MED).
      lookupError: previous?.lookupError ?? null,
      lookupLoading: previous?.lookupLoading ?? false,
      productType: previous?.productType ?? null,
      setOptions: previous?.setOptions ?? emptyBundleSetOptions(),
    }
  })
}

function EstimateMobileLineCard(props: {
  line: DraftLine
  index: number
  isReadOnly: boolean
  provider: DocCoeditProvider | null
  coeditPending: boolean
  lineStructureLocked: boolean
  lineIncl: number
  lineSupply: number
  lineVat: number
  /** 거래처 선택 여부 (R4-D4) — 마커 카피 분기/해제 기준. */
  hasPartner: boolean
  onUpdate: (patch: Partial<DraftLine>) => void
  onLookup: () => void
  onRemove: () => void
  children?: ReactNode
}) {
  const lineNumber = props.index + 1
  const priceStatus = priceSourceStatus(props.line, props.hasPartner)
  const priceStatusId = `estimate-mobile-price-status-${props.line.uid}`
  const priceChangedStatusId = `estimate-mobile-price-changed-${props.line.uid}`
  return (
    <div
      className={`mobile-line-card${props.line.priceRefreshChanged ? ' price-memory-refreshed-row' : ''}`}
      aria-describedby={props.line.priceRefreshChanged ? priceChangedStatusId : undefined}
      data-testid={`estimate-form-line-${props.index}`}
      data-price-source={props.line.priceSource ?? ''}
    >
      <div className="mobile-line-card-header">
        <span className="mobile-line-card-index">{lineNumber}</span>
        {props.line.priceRefreshChanged ? <PriceChangeIndicator id={priceChangedStatusId} /> : null}
        <button
          type="button"
          className="mobile-line-remove-button"
          onClick={props.onRemove}
          disabled={props.lineStructureLocked}
          aria-label={`라인 ${lineNumber} 삭제`}
        >
          삭제
        </button>
      </div>

      <div className="mobile-line-field">
        <label className="mobile-line-field-label">모델명</label>
        <CollaborativeSlipInput
          provider={props.provider}
          coeditPending={props.coeditPending}
          fieldPath={`items.${props.index}.modelName`}
          value={props.line.modelName}
          onValueChange={(value) => props.onUpdate({ modelName: value })}
          onBlur={props.onLookup}
          inputSize="sm"
          readOnly={props.isReadOnly}
          type="text"
          placeholder="예: AJ040RXH4BC1"
          error={props.line.lookupError ?? undefined}
          aria-label={`라인 ${lineNumber} 모델명`}
        />
      </div>

      <div className="mobile-line-field">
        <label className="mobile-line-field-label">품목명</label>
        <CollaborativeSlipInput
          provider={props.provider}
          coeditPending={props.coeditPending}
          fieldPath={`items.${props.index}.productName`}
          value={props.line.productName}
          onValueChange={(value) => props.onUpdate({ productName: value })}
          inputSize="sm"
          readOnly={props.isReadOnly}
          type="text"
          aria-label={`라인 ${lineNumber} 품목명`}
        />
      </div>

      <div className="mobile-line-field">
        <label className="mobile-line-field-label">규격</label>
        <CollaborativeSlipInput
          provider={props.provider}
          coeditPending={props.coeditPending}
          fieldPath={`items.${props.index}.specification`}
          value={props.line.specification}
          onValueChange={(value) => props.onUpdate({ specification: value })}
          inputSize="sm"
          readOnly={props.isReadOnly}
          type="text"
          aria-label={`라인 ${lineNumber} 규격`}
        />
      </div>

      <div className="mobile-line-field">
        <label className="mobile-line-field-label">수량</label>
        <CollaborativeSlipInput
          provider={props.provider}
          coeditPending={props.coeditPending}
          fieldPath={`items.${props.index}.quantity`}
          value={props.line.quantity}
          onValueChange={(value) => props.onUpdate({ quantity: value })}
          inputSize="sm"
          readOnly={props.isReadOnly}
          type="text"
          inputMode="numeric"
          inputStyle={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
          aria-label={`라인 ${lineNumber} 수량`}
        />
      </div>

      <div className="mobile-line-field">
        <label className="mobile-line-field-label">단가(VAT포함)</label>
        <CollaborativeSlipInput
          provider={props.provider}
          coeditPending={props.coeditPending}
          fieldPath={`items.${props.index}.unitPrice`}
          value={props.line.unitPrice}
          onValueChange={(value) => props.onUpdate({
            unitPrice: value,
            priceSource: 'USER',
            priceMemoryUpdatedAt: null,
            priceRefreshChanged: false,
            lookupLoading: false,
            legacyPriceUntouched: false,
          })}
          // doc-sync 유래 값 반영은 분류(priceSource) 를 건드리지 않는다 — 자동채움 provider write
          // 가 pending REMEMBERED/CATALOG 분류를 USER 로 덮어 마커가 소멸하는 것을 차단(R4-F6).
          // 분류 판정은 페이지 구독(coeditLinesToDraftLines + localAutoPriceWrites)이 단일 소스.
          onDocSyncValueChange={(value) => props.onUpdate({ unitPrice: value })}
          inputSize="sm"
          readOnly={props.isReadOnly}
          type="text"
          inputMode="decimal"
          inputStyle={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
          aria-label={`라인 ${lineNumber} 단가`}
          aria-describedby={priceStatus ? priceStatusId : undefined}
        />
        {/* R4-D2: 라인별 aria-live 제거 — 전역 고지는 배너(role="status") 1곳이 담당. */}
        {priceStatus ? (
          <span
            id={priceStatusId}
            role="note"
            aria-label={priceStatus.description}
            title={priceStatus.description}
            className="price-source-note"
          >
            {priceStatus.label}
          </span>
        ) : null}
      </div>

      <div className="mobile-line-field">
        <label className="mobile-line-field-label">합계(VAT포함)</label>
        <div className="mobile-line-readonly mobile-line-readonly--strong">
          {fmt(props.lineIncl)}
          <span>공급 {fmt(props.lineSupply)} · VAT {fmt(props.lineVat)}</span>
        </div>
      </div>

      {props.children}
    </div>
  )
}

export function EstimateFormPage() {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const params = useParams<{ id?: string }>()
  const editId = params['id']
  const isEdit = Boolean(editId)
  const { canAccess } = usePermissions()
  const canViewProductLookups = canAccess('products.list', 'view')
  const isMobile = useIsMobile()

  usePageTitle(isEdit ? '견적서 편집' : '견적서 작성')

  const detailQuery = useQuery({
    queryKey: ['estimate', editId],
    queryFn: () => getEstimate(editId!),
    enabled: isEdit,
  })

  const [partner, setPartner] = useState<PartnerSummary | null>(null)
  const [partnerName, setPartnerName] = useState<string>('')
  const [partnerBusinessNo, setPartnerBusinessNo] = useState<string>('')
  const [partnerAddress, setPartnerAddress] = useState<string>('')
  const [partnerIdSnapshot, setPartnerIdSnapshot] = useState<string>('')
  const [estimateDate, setEstimateDate] = useState<string>(today())
  const [validUntil, setValidUntil] = useState<string>(datePlusDays(today(), 30))
  const [memo, setMemo] = useState<string>('')
  const [lines, setLines] = useState<DraftLine[]>([emptyLine()])
  const [topError, setTopError] = useState<string>('')
  const [lineLookupOpen, setLineLookupOpen] = useState(false)
  const [estimateFormCoeditProvider, setEstimateFormCoeditProvider] = useState<DocCoeditProvider | null>(null)
  const [estimateFormCoeditPending, setEstimateFormCoeditPending] = useState(false)
  const [priceLookupAnnouncement, setPriceLookupAnnouncement] = useState('')
  const selectedPartnerIdRef = useRef<string>('')
  const priceRefreshRequestRef = useRef(0)
  const modelLookupRequestRef = useRef(new Map<string, number>())
  const localAutoPriceWritesRef = useRef(new Map<string, LocalAutoPriceWrite>())
  const linesRef = useRef(lines)
  linesRef.current = lines
  // R4-D4: 마커 카피 분기/해제 기준 — 저장 payload partnerId·가격기억 흐름과 동일 소스(반응형 스냅샷).
  const hasPartner = Boolean(partnerIdSnapshot)
  // R4-D9: 배너 live region 은 상시 마운트 — 내용과 함께 조건부 마운트하면 일부 SR 이 미낭독.
  const priceRefreshNoticeActive = lines.some((line) => line.priceRefreshChanged)

  const isReadOnly =
    isEdit &&
    detailQuery.data &&
    detailQuery.data.status !== 'QUOTE_DRAFT' &&
    detailQuery.data.status !== 'QUOTE_SENT'
  const canCollabEdit =
    isEdit &&
    !!editId &&
    !!detailQuery.data &&
    !isReadOnly &&
    canAccess('estimates.list', 'update')
  const coeditActive = Boolean(estimateFormCoeditProvider) || estimateFormCoeditPending
  // coedit useEffect 가 detailQuery.data 객체를 deps 로 두면 React Query 리페치/SSE invalidate 마다
  // provider 가 재생성돼 협업 세션이 끊기고 미저장 CRDT 델타가 재시드로 유실된다(듀얼리뷰 HIGH).
  // seed 용 최신 스냅샷은 ref 로 읽어 effect 를 안정 트리거(canCollabEdit/editId/isEdit)로만 재실행한다.
  const estimateDataRef = useRef<EstimateDetail | null>(null)
  estimateDataRef.current = detailQuery.data ?? null

  // edit mode hydrate
  useEffect(() => {
    if (!isEdit) return
    const e = detailQuery.data
    if (!e) return
    if (estimateFormCoeditProvider) return
    selectedPartnerIdRef.current = e.partnerId
    setPartnerIdSnapshot(e.partnerId)
    setPartnerName(e.partnerName)
    setPartnerBusinessNo(e.partnerBusinessNo ?? '')
    setPartnerAddress(e.partnerAddress ?? '')
    setEstimateDate(e.estimateDate)
    setValidUntil(e.validUntil ?? '')
    setMemo(e.memo ?? '')
    const draftLines = toDraftLinesFromEstimate(e)
    linesRef.current = draftLines
    setLines(draftLines)
  }, [isEdit, detailQuery.data, estimateFormCoeditProvider])

  useEffect(() => {
    const estimate = estimateDataRef.current
    if (!isEdit || !editId || !estimate || !canCollabEdit) {
      setEstimateFormCoeditProvider(null)
      setEstimateFormCoeditPending(false)
      return undefined
    }

    let disposed = false
    let provider: DocCoeditProvider | null = null
    let unsubscribeDoc: (() => void) | null = null
    setEstimateFormCoeditPending(true)

    const applyProviderState = (nextProvider: DocCoeditProvider) => {
      setPartnerName(nextProvider.getHeaderValue('partnerName'))
      setPartnerBusinessNo(nextProvider.getHeaderValue('partnerBusinessNo'))
      setPartnerAddress(nextProvider.getHeaderValue('partnerAddress'))
      setEstimateDate(nextProvider.getHeaderValue('estimateDate'))
      setValidUntil(nextProvider.getHeaderValue('validUntil'))
      setMemo(nextProvider.getHeaderValue('memo'))
      const nextLines = coeditLinesToDraftLines(
        nextProvider,
        linesRef.current,
        localAutoPriceWritesRef.current,
      )
      linesRef.current = nextLines
      setLines(nextLines)
    }

    void createDocCoeditProvider({
      documentId: editId,
      basePath: `/slips/estimates/${editId}`,
      headerTextFields: ESTIMATE_HEADER_TEXT_FIELDS,
    }).then((nextProvider) => {
      if (disposed) {
        nextProvider.destroy()
        return
      }
      provider = nextProvider
      const serverLineCount = toDraftLinesFromEstimate(estimate).length
      const providerLineCount = nextProvider.items.toArray().length
      // 슬1은 협업 중 라인 추가/삭제를 잠가 index seed-lock 을 유지한다.
      // provider 라인수와 서버 라인수가 다르면 stale snapshot 으로 보고 서버 기준 재시드한다.
      if (nextProvider.isEmpty() || providerLineCount !== serverLineCount) {
        seedEstimateCoeditProvider(nextProvider, estimate)
      }
      applyProviderState(nextProvider)
      unsubscribeDoc = nextProvider.subscribeDoc(() => applyProviderState(nextProvider))
      setEstimateFormCoeditProvider(nextProvider)
      setEstimateFormCoeditPending(false)
    }).catch(() => {
      if (disposed) return
      setEstimateFormCoeditProvider(null)
      setEstimateFormCoeditPending(false)
    })

    return () => {
      disposed = true
      unsubscribeDoc?.()
      if (provider) provider.destroy()
      setEstimateFormCoeditProvider(null)
      setEstimateFormCoeditPending(false)
    }
    // deps 에서 detailQuery.data 제외 — 리페치/SSE 재생성 방지(estimate 는 estimateDataRef 로 최신값 사용).
  }, [canCollabEdit, editId, isEdit])

  const totals = useMemo(() => {
    // 단가 부가세포함(라인 단위 eCount, 원 단위): 라인별 합계(VAT포함)=round(수량×단가),
    // 공급가액=round(합계/1.1), 부가세=차액 → 라인별 반올림 후 합산(BE 와 동일).
    let supply = 0
    let total = 0
    for (const l of lines) {
      const incl = Math.round(
        (Number.parseFloat(l.quantity || '0') || 0) * (Number.parseFloat(l.unitPrice || '0') || 0),
      )
      supply += Math.round(incl / 1.1)
      total += incl
    }
    return { supply, vat: total - supply, total }
  }, [lines])

  const handleSelectPartner = (p: PartnerSummary) => {
    const nextPartnerId = p.partnerId && UUID_PATTERN.test(p.partnerId) ? p.partnerId : ''
    selectedPartnerIdRef.current = nextPartnerId
    setPartner(p)
    setPartnerIdSnapshot(nextPartnerId)
    setPartnerName(p.companyName)
    setPartnerBusinessNo(p.businessRegistrationNumber)
    setPartnerAddress(p.address ?? '')
    if (nextPartnerId) {
      void refreshAutoPricesForPartner(nextPartnerId)
    }
  }

  const searchPartnerOptions = async (q: string): Promise<PartnerOption[]> => {
    const rows = await searchPartners(q, 8)
    return rows.map((row) => ({
      id: row.partnerId ?? undefined,
      partnerCode: row.businessRegistrationNumber,
      name: row.companyName,
      bizNo: row.businessRegistrationNumber,
      phone: row.contactPhone ?? undefined,
    }))
  }

  const handlePartnerOptionChange = (option: PartnerOption | null) => {
    if (!option) {
      selectedPartnerIdRef.current = ''
      priceRefreshRequestRef.current += 1
      setPartner(null)
      setPartnerIdSnapshot('')
      setLines((prev) => {
        const next = prev.map((line) => ({
          ...line,
          lookupLoading: false,
          priceRefreshChanged: false,
        }))
        linesRef.current = next
        return next
      })
      return
    }
    handleSelectPartner({
      partnerId: option.id ?? null,
      businessRegistrationNumber: option.bizNo ?? option.partnerCode,
      companyName: option.name,
      representativeName: null,
      contactPhone: option.phone ?? null,
      address: null,
      groupName: null,
      note: null,
    })
  }

  const updateLine = (index: number, patch: Partial<DraftLine>) => {
    setLines((prev) => {
      const next = prev.map((l, i) => (i === index ? { ...l, ...patch } : l))
      linesRef.current = next
      return next
    })
  }

  const refreshAutoPricesForPartner = async (effectivePartnerId: string) => {
    setPriceLookupAnnouncement('')
    const requestId = ++priceRefreshRequestRef.current
    const candidates = linesRef.current
      .map((line, index) => ({ line, index }))
      .filter(({ line }) => line.productId && isAutoPriceSource(line.priceSource))
    if (candidates.length === 0) return
    const snapshotByUid = new Map(candidates.map(({ line }) => [line.uid, line]))
    const candidateUids = new Set(snapshotByUid.keys())
    const loadingLines = linesRef.current.map((line) =>
      candidateUids.has(line.uid)
        ? { ...line, lookupLoading: true, priceRefreshChanged: false }
        : line,
    )
    linesRef.current = loadingLines
    setLines(loadingLines)

    const applyResolvedPrices = (memoryByProductId: Map<string, { unitPrice: number; updatedAt: string | null }>) => {
      if (
        priceRefreshRequestRef.current !== requestId
        || selectedPartnerIdRef.current !== effectivePartnerId
      ) return
      const providerWrites: Array<{ index: number; line: DraftLine }> = []
      const nextLines = linesRef.current.map((current, index) => {
        const snapshot = snapshotByUid.get(current.uid)
        if (!snapshot || current.productId !== snapshot.productId) return current
        if (!isAutoPriceSource(current.priceSource)) {
          return { ...current, lookupLoading: false, priceRefreshChanged: false }
        }
        const fallback = snapshot.catalogUnitPrice ?? snapshot.unitPrice
        const memory = memoryByProductId.get(snapshot.productId!)
        const nextUnitPrice = memory == null ? fallback : String(memory.unitPrice)
        const nextLine: DraftLine = {
          ...current,
          unitPrice: nextUnitPrice,
          priceSource: memory == null ? 'CATALOG' : 'REMEMBERED',
          priceMemoryUpdatedAt: memory?.updatedAt ?? null,
          priceRefreshChanged: nextUnitPrice !== current.unitPrice,
          lookupLoading: false,
        }
        providerWrites.push({ index, line: nextLine })
        return nextLine
      })
      linesRef.current = nextLines
      setLines(nextLines)
      for (const write of providerWrites) {
        if (!estimateFormCoeditProvider) continue
        localAutoPriceWritesRef.current.set(write.line.uid, {
          unitPrice: write.line.unitPrice,
          priceSource: write.line.priceSource,
          priceMemoryUpdatedAt: write.line.priceMemoryUpdatedAt,
          priceRefreshChanged: write.line.priceRefreshChanged,
        })
        try {
          estimateFormCoeditProvider.setItemValue(write.index, 'unitPrice', write.line.unitPrice)
        } catch {
          localAutoPriceWritesRef.current.delete(write.line.uid)
        }
      }
    }

    try {
      const { hits: memories } = await getPriceMemories(
        effectivePartnerId,
        candidates.map(({ line }) => line.productId!),
      )
      applyResolvedPrices(new Map(memories.map((memory) => [memory.productId, memory])))
    } catch {
      // 현재 요청/거래처/uid/product/source 가 여전히 같을 때만 판매가(catalog) fallback 한다.
      applyResolvedPrices(new Map())
    }
  }
  const updateSetOption = (index: number, patch: Partial<BundleSetOptions>) => {
    setLines((prev) => {
      const next = prev.map((l, i) =>
        i === index ? { ...l, setOptions: { ...l.setOptions, ...patch } } : l,
      )
      linesRef.current = next
      return next
    })
  }
  const addLine = () => setLines((prev) => {
    const next = [...prev, emptyLine()]
    linesRef.current = next
    return next
  })
  const removeLine = (index: number) => {
    setLines((prev) => {
      const next = prev.filter((_, i) => i !== index)
      const normalized = next.length === 0 ? [emptyLine()] : next
      linesRef.current = normalized
      return normalized
    })
  }

  // 모델명 onBlur lookup
  const handleModelLookup = async (index: number) => {
    const line = linesRef.current[index]
    // provider 가 언마운트 중 destroy 됐을 때 getItemValue 예외로 lookup 이 중단되지 않게 방어(리뷰 LOW).
    let coeditModelName = ''
    try {
      coeditModelName = estimateFormCoeditProvider?.getItemValue(index, 'modelName') ?? ''
    } catch {
      coeditModelName = ''
    }
    const modelName = (coeditModelName || line?.modelName || '').trim()
    if (!line || !modelName) return
    const requestId = (modelLookupRequestRef.current.get(line.uid) ?? 0) + 1
    modelLookupRequestRef.current.set(line.uid, requestId)
    setPriceLookupAnnouncement('')
    updateLine(index, { lookupLoading: true, lookupError: null })

    // R4-F3: 품목 바인딩과 가격 적용의 신선도 게이트 분리.
    // 품목 게이트 — 같은 라인(uid)·같은 모델명 텍스트·최신 요청이면 lookup 결과의 품목 필드
    // (productId/productName/productType/catalogUnitPrice)를 적용한다. 기존에는 priceSource/
    // 거래처 변화까지 한 게이트여서 lookup 중 단가 타이핑·거래처 선택 시 productId 바인딩이
    // 통째로 폐기 → 저장 차단 + 사유 무표시가 발생했다(전표 applyProductSelection 과 정렬).
    const isProductBindCurrent = (current: DraftLine): boolean =>
      modelLookupRequestRef.current.get(line.uid) === requestId
      && current.uid === line.uid
      && (current.modelName || '').trim() === modelName

    const finishStaleRequest = () => {
      // 최신 요청이 따로 시작됐다면(requestId 불일치) 그 요청이 스피너를 관리한다. 모델명
      // 텍스트가 이미 바뀐 경우에도 스피너는 해제해야 저장 busy 게이트(R4-F4)가 고착되지 않는다.
      setLines((prev) => {
        const next = prev.map((current) =>
          modelLookupRequestRef.current.get(line.uid) === requestId && current.uid === line.uid
            ? { ...current, lookupLoading: false }
            : current,
        )
        linesRef.current = next
        return next
      })
    }

    try {
      const result = await lookupProductByModelName(modelName)
      const currentAfterProductLookup = linesRef.current.find((current) => current.uid === line.uid)
      if (!currentAfterProductLookup || !isProductBindCurrent(currentAfterProductLookup)) {
        finishStaleRequest()
        return
      }
      // R4-F1: 전표(applyProductSelection)와 동일 semantics(공유 헬퍼) — 빈 단가뿐 아니라 이전
      // 품목의 자동채움(CATALOG/REMEMBERED) 단가도 새 품목 기준으로 재채움 + 가격기억 재조회.
      const shouldAutoFill = shouldAutoFillPrice(line.priceSource, line.unitPrice)
      let nextUnitPrice = String(result.sellingPrice)
      let nextPriceSource: DraftLine['priceSource'] = 'CATALOG'
      let nextPriceMemoryUpdatedAt: string | null = null
      let resolvedPartnerId = selectedPartnerIdRef.current
      if (shouldAutoFill) {
        // 품목 lookup 중 거래처가 바뀌면 새 거래처는 아직 productId 를 보지 못해 bulk 후보가 0건이다.
        // 현재 거래처가 응답 동안 다시 바뀌면 최신 partnerId 로 반복 resolve하고 busy 를 유지한다.
        while (true) {
          nextUnitPrice = String(result.sellingPrice)
          nextPriceSource = 'CATALOG'
          nextPriceMemoryUpdatedAt = null
          if (resolvedPartnerId) {
            try {
              const memory = await getPriceMemory(resolvedPartnerId, result.productId)
              if (memory?.unitPrice != null) {
                nextUnitPrice = String(memory.unitPrice)
                nextPriceSource = 'REMEMBERED'
                nextPriceMemoryUpdatedAt = memory.updatedAt ?? null
              }
            } catch {
              // 가격기억 조회 실패는 모델 lookup 자체를 실패시키지 않는다. 판매가 fallback 유지.
            }
          }
          const currentAfterPriceLookup = linesRef.current.find((candidate) => candidate.uid === line.uid)
          if (!currentAfterPriceLookup || !isProductBindCurrent(currentAfterPriceLookup)) {
            finishStaleRequest()
            return
          }
          if (currentAfterPriceLookup.priceSource === 'USER') break
          if (selectedPartnerIdRef.current === resolvedPartnerId) break
          resolvedPartnerId = selectedPartnerIdRef.current
        }
      }
      const current = linesRef.current.find((candidate) => candidate.uid === line.uid)
      if (!current || !isProductBindCurrent(current)) {
        finishStaleRequest()
        return
      }
      // 명시적 USER 편집만 현재 단가를 보존한다. 거래처 stale 은 위에서 최신 partner+새 product 로
      // 재resolve했으므로 0원 중간 상태로 품목만 바인딩하지 않는다(R5-H3).
      const applyPrice = shouldAutoFill
        && current.priceSource !== 'USER'
        && selectedPartnerIdRef.current === resolvedPartnerId
      const currentIndex = linesRef.current.findIndex((candidate) => candidate.uid === line.uid)
      const nextLine: DraftLine = {
        ...current,
        productId: result.productId,
        productName: result.productName,
        productType: result.productType ?? 'SINGLE',
        catalogUnitPrice: result.sellingPrice,
        unitPrice: applyPrice ? nextUnitPrice : current.unitPrice,
        priceSource: applyPrice ? nextPriceSource : current.priceSource,
        priceMemoryUpdatedAt: applyPrice ? nextPriceMemoryUpdatedAt : current.priceMemoryUpdatedAt,
        priceRefreshChanged: applyPrice ? false : current.priceRefreshChanged,
        lookupError: null,
        lookupLoading: false,
      }
      const nextLines = linesRef.current.map((candidate) =>
        candidate.uid === line.uid ? nextLine : candidate,
      )
      linesRef.current = nextLines
      setLines(nextLines)
      if (applyPrice) {
        setPriceLookupAnnouncement(
          `라인 ${currentIndex + 1} ${nextPriceSource === 'REMEMBERED' ? '거래처 최근단가' : '판매가'} 적용`,
        )
      }
      if (estimateFormCoeditProvider) {
        try {
          estimateFormCoeditProvider.setItemValue(currentIndex, 'productName', result.productName)
          if (applyPrice) {
            localAutoPriceWritesRef.current.set(line.uid, {
              unitPrice: nextUnitPrice,
              priceSource: nextPriceSource,
              priceMemoryUpdatedAt: nextPriceMemoryUpdatedAt,
              priceRefreshChanged: false,
            })
            estimateFormCoeditProvider.setItemValue(currentIndex, 'unitPrice', nextUnitPrice)
          }
          estimateFormCoeditProvider.setItemValue(currentIndex, 'productId', result.productId)
        } catch {
          localAutoPriceWritesRef.current.delete(line.uid)
          // 언마운트 중 provider destroy 가능 — 로컬 state 는 이미 갱신됨. 동기화 실패는 무시(가짜 lookup 오류 방지, 리뷰 LOW).
        }
      }
    } catch (err: unknown) {
      const current = linesRef.current.find((candidate) => candidate.uid === line.uid)
      // lookup 실패 안내도 품목 게이트 기준 — 단가 타이핑/거래처 변경이 있었어도 같은 모델명
      // 텍스트의 최신 요청이면 실패 사유를 표시한다(사유 무표시 방지, R4-F3).
      if (!current || !isProductBindCurrent(current)) {
        finishStaleRequest()
        return
      }
      updateLine(linesRef.current.findIndex((candidate) => candidate.uid === line.uid), {
        lookupError: err instanceof Error ? '모델 미존재 또는 lookup 실패' : '알 수 없는 오류',
        lookupLoading: false,
      })
    }
  }

  const createMutation = useMutation({
    mutationFn: (body: CreateEstimateRequest) => createEstimate(body),
    onSuccess: (created) => {
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      navigate(`/sales/estimates/${created.id}`, { replace: true })
    },
    onError: (err: Error) => setTopError(`저장 실패: ${err.message}`),
  })

  const updateMutation = useMutation({
    mutationFn: (body: UpdateEstimateRequest) => updateEstimate(editId!, body),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      queryClient.invalidateQueries({ queryKey: ['estimate', updated.id] })
      navigate(`/sales/estimates/${updated.id}`, { replace: true })
    },
    onError: (err: Error) => setTopError(`수정 실패: ${err.message}`),
  })

  const sendMutation = useMutation({
    mutationFn: (id: string) => sendEstimate(id),
    onSuccess: (sent) => {
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      queryClient.invalidateQueries({ queryKey: ['estimate', sent.id] })
      alert(`발송 완료: ${sent.estimateNo}`)
      navigate(`/sales/estimates/${sent.id}`, { replace: true })
    },
    onError: (err: Error) => setTopError(`발송 실패: ${err.message}`),
  })

  const buildBody = (): CreateEstimateRequest | null => {
    setTopError('')
    // R4-F4: 거래처 변경 최근단가 재조회/모델 lookup 이 in-flight 인 동안 저장하면 이전 거래처
    // 단가가 새 거래처(partnerId)로 전송되어 가격기억이 교차 오염된다 — 완료 전 저장/발송 차단.
    // (버튼 disabled 와 이중 방어 — 발송 등 프로그래매틱 경로 포함)
    if (lines.some((l) => l.lookupLoading)) {
      setTopError('최근단가 확인 중입니다. 잠시 후 다시 시도해 주세요.')
      return null
    }
    const effectivePartnerId =
      partnerIdSnapshot && UUID_PATTERN.test(partnerIdSnapshot)
        ? partnerIdSnapshot
        : partner?.partnerId && UUID_PATTERN.test(partner.partnerId)
          ? partner.partnerId
          : ''
    if (!effectivePartnerId) {
      setTopError(partnerName.trim()
        ? '거래처 정보를 다시 불러올 수 없습니다. 거래처를 다시 선택해 주세요.'
        : '거래처를 선택하세요.')
      return null
    }
    if (!partnerName.trim()) {
      setTopError('거래처명이 비어있습니다.')
      return null
    }
    const valid = lines.filter(
      (l) => l.productId && Number.parseInt(l.quantity || '0', 10) > 0,
    )
    if (valid.length === 0) {
      setTopError(
        '라인 1개 이상 (모델명 lookup 성공 + 수량 > 0) 을 입력하세요.',
      )
      return null
    }
    const apiLines: EstimateLineRequest[] = valid.map((l) => {
      // R4-F2: legacy(unitPriceWithVat=null) 라인의 단가는 공급단가다. 사용자가 단가를 수정하지
      // 않았으면(hydrate 원값 그대로) priceVatInclusive=false + 원 공급단가로 전송해 편집-저장 시
      // /1.1 재분리(약 9.1% 하락)와 가격기억 오염을 막는다 — 전표 복사는 R6-H2 부터
      // BE 서버 복사(POST /slips/{id}/duplicate)가 동일 원칙을 보장한다.
      // 사용자가 수정한 값은 '단가(VAT포함)' 입력이므로 기존대로 true.
      const keepsLegacySupplyPrice =
        l.legacySupplyUnitPrice != null && l.legacyPriceUntouched === true
      return {
        productId: l.productId!,
        productName: l.productName.trim() || undefined,
        modelName: l.modelName.trim() || undefined,
        specification: l.specification.trim() || undefined,
        quantity: Number.parseInt(l.quantity || '0', 10),
        unitPrice: l.unitPrice || '0',
        note: l.note.trim() || undefined,
        setOptions: toApiBundleSetOptions(l.productType, l.setOptions),
        // 단가 부가세포함 — BE 가 라인 단위로 공급가액/부가세 분리(eCount). legacy 미수정 라인만 예외.
        priceVatInclusive: !keepsLegacySupplyPrice,
      }
    })
    return {
      estimateDate: estimateDate || undefined,
      partnerId: effectivePartnerId,
      partnerName: partnerName.trim(),
      partnerBusinessNo: partnerBusinessNo.trim() || undefined,
      partnerAddress: partnerAddress.trim() || undefined,
      validUntil: validUntil || undefined,
      memo: memo.trim() || undefined,
      lines: apiLines,
    }
  }

  const handleSave = () => {
    const body = buildBody()
    if (!body) return
    if (isEdit) {
      const updateBody: UpdateEstimateRequest = {
        partnerId: body.partnerId,
        partnerName: body.partnerName,
        partnerBusinessNo: body.partnerBusinessNo,
        partnerAddress: body.partnerAddress,
        validUntil: body.validUntil,
        memo: body.memo,
        lines: body.lines,
      }
      updateMutation.mutate(updateBody)
    } else {
      createMutation.mutate(body)
    }
  }

  const handleSend = async () => {
    if (!isEdit || !editId) {
      setTopError('먼저 저장 후 발송할 수 있습니다.')
      return
    }
    if (
      !confirm(
        '이 견적서를 발송하시겠습니까?\n발송 후 거래처가 수락/거절을 결정합니다.',
      )
    )
      return
    const body = buildBody()
    if (!body) return
    try {
      const updateBody: UpdateEstimateRequest = {
        partnerId: body.partnerId,
        partnerName: body.partnerName,
        partnerBusinessNo: body.partnerBusinessNo,
        partnerAddress: body.partnerAddress,
        validUntil: body.validUntil,
        memo: body.memo,
        lines: body.lines,
      }
      await updateEstimate(editId, updateBody)
      sendMutation.mutate(editId)
    } catch (err: unknown) {
      setTopError(
        `발송 전 저장 실패: ${err instanceof Error ? err.message : String(err)}`,
      )
    }
  }

  if (isEdit && detailQuery.isLoading) {
    return (
      <div style={{ display: 'grid', placeItems: 'center', minHeight: 200 }}>
        <Spinner size="lg" label="견적서 불러오는 중" />
      </div>
    )
  }

  const isPending =
    createMutation.isPending ||
    updateMutation.isPending ||
    sendMutation.isPending
  // 최근단가 재조회/모델 lookup in-flight — 저장/발송 차단 + busy 단서(R4-F4, 전표 폼과 대칭).
  const priceResolutionBusy = lines.some((l) => l.lookupLoading)

  return (
    <>
      <div
        style={{
          marginBottom: 16,
          display: 'flex',
          alignItems: 'flex-start',
          justifyContent: 'space-between',
          gap: 16,
          flexWrap: 'wrap',
        }}
      >
        <div>
          <h3 style={{ margin: 0 }}>
            {isEdit ? '견적서 편집' : '견적서 작성'}
          </h3>
          <p style={{ marginTop: 4, fontSize: 13, color: '#6B7280' }}>
            모델명을 입력하고 다른 영역을 클릭하면 품목명/단가가 자동 입력됩니다.
          </p>
        </div>
      </div>

      {isReadOnly ? (
        <div
          className="error-banner"
          role="alert"
          style={{ marginBottom: 16, padding: 12 }}
        >
          이 견적서는 수락/거절/변환되어 더 이상 수정할 수 없습니다.
        </div>
      ) : null}

      <Card>
        {/* 거래처 선택 */}
        <div style={{ marginBottom: 16 }}>
          <PartnerAutocomplete
            label="거래처 검색"
            placeholder="거래처명 또는 사업자번호"
            value={partner
              ? {
                  partnerCode: partner.businessRegistrationNumber,
                  name: partner.companyName,
                  bizNo: partner.businessRegistrationNumber,
                  phone: partner.contactPhone ?? undefined,
                }
              : null}
            onChange={handlePartnerOptionChange}
            searchPartners={searchPartnerOptions}
            disabled={Boolean(isReadOnly) || coeditActive}
          />
        </div>

        <div
          className="mobile-form-grid"
          style={{
            display: 'grid',
            gridTemplateColumns: '2fr 1fr 1fr 1fr',
            gap: 16,
            marginBottom: 16,
          }}
        >
          <CollaborativeSlipInput
            provider={estimateFormCoeditProvider}
            coeditPending={estimateFormCoeditPending}
            fieldPath="header.partnerName"
            label="거래처명"
            value={partnerName}
            onValueChange={setPartnerName}
            readOnly={Boolean(isReadOnly)}
            required
            aria-label="거래처명"
            data-testid="estimate-form-partner-name"
          />
          <CollaborativeSlipInput
            provider={estimateFormCoeditProvider}
            coeditPending={estimateFormCoeditPending}
            fieldPath="header.partnerBusinessNo"
            label="사업자번호"
            value={partnerBusinessNo}
            onValueChange={setPartnerBusinessNo}
            readOnly={Boolean(isReadOnly)}
            aria-label="사업자번호"
            data-testid="estimate-form-partner-business-no"
          />
          <CollaborativeSlipInput
            provider={estimateFormCoeditProvider}
            coeditPending={estimateFormCoeditPending}
            fieldPath="header.estimateDate"
            label="작성일"
            type="date"
            value={estimateDate}
            onValueChange={setEstimateDate}
            readOnly={Boolean(isReadOnly)}
            aria-label="작성일"
            data-testid="estimate-form-estimate-date"
          />
          <CollaborativeSlipInput
            provider={estimateFormCoeditProvider}
            coeditPending={estimateFormCoeditPending}
            fieldPath="header.validUntil"
            label="유효기간"
            type="date"
            value={validUntil}
            onValueChange={setValidUntil}
            readOnly={Boolean(isReadOnly)}
            aria-label="유효기간"
            data-testid="estimate-form-valid-until"
          />
        </div>
        <div style={{ marginBottom: 16 }}>
          <CollaborativeSlipInput
            provider={estimateFormCoeditProvider}
            coeditPending={estimateFormCoeditPending}
            fieldPath="header.partnerAddress"
            label="주소"
            value={partnerAddress}
            onValueChange={setPartnerAddress}
            readOnly={Boolean(isReadOnly)}
            aria-label="주소"
          />
        </div>
        <div style={{ marginBottom: 16 }}>
          <CollaborativeSlipInput
            provider={estimateFormCoeditProvider}
            coeditPending={estimateFormCoeditPending}
            fieldPath="header.memo"
            label="비고"
            value={memo}
            onValueChange={setMemo}
            readOnly={Boolean(isReadOnly)}
            aria-label="비고"
          />
        </div>

        {/* R4-D9: live region 은 빈 컨테이너로 상시 렌더하고 텍스트만 토글 — ARIA 관행상
            live region 이 선존재해야 SR 낭독이 신뢰된다. 비활성 시 class 미부여로 시각 0px. */}
        <div
          className={priceRefreshNoticeActive
            ? 'price-memory-refresh-banner'
            : priceLookupAnnouncement
              ? 'price-lookup-status'
              : undefined}
          role="status"
          aria-live="polite"
          data-testid="estimate-price-refresh-banner"
        >
          {priceRefreshNoticeActive
            ? '거래처 변경으로 최근단가 재적용 · 변경된 행을 확인해 주세요.'
            : priceLookupAnnouncement || null}
        </div>

        {!isMobile ? (
          /* 라인 헤더 */
          <div
            style={{
              display: 'grid',
              gridTemplateColumns:
                '32px 160px 1fr 100px 80px 130px 130px 36px',
              gap: 8,
              padding: '8px 0',
              borderBottom: '2px solid var(--line-default)',
              fontSize: 12,
              color: '#6B7280',
              fontWeight: 600,
            }}
          >
            <div style={{ textAlign: 'center' }}>#</div>
            <div>모델명</div>
            <div>품목명</div>
            <div>규격</div>
            <div style={{ textAlign: 'right' }}>수량</div>
            <div style={{ textAlign: 'right' }}>단가(VAT포함)</div>
            <div style={{ textAlign: 'right' }}>합계(VAT포함)</div>
            <div />
          </div>
        ) : null}

        {isMobile && !isReadOnly && canViewProductLookups ? (
          <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'flex-end' }}>
            <Button
              variant="ghost"
              size="sm"
              onClick={() => setLineLookupOpen(true)}
              disabled={estimateFormCoeditPending}
              data-testid="estimate-line-lookup-btn"
            >
              참조 조회
            </Button>
          </div>
        ) : null}

        <div className={isMobile ? 'mobile-line-card-list' : undefined}>
        {lines.map((line, i) => {
          // 단가 부가세포함: 합계(VAT포함)=round(수량×단가), 공급가액=round(합계/1.1), 부가세=차액.
          const lineIncl = Math.round(calcLineSupply(line.quantity, line.unitPrice))
          const lineSupply = Math.round(lineIncl / 1.1)
          const lineVat = lineIncl - lineSupply
          const isBundle = line.productType === 'BUNDLE'
          const priceStatus = priceSourceStatus(line, hasPartner)
          const priceStatusId = `estimate-price-status-${line.uid}`
          const priceChangedStatusId = `estimate-price-changed-${line.uid}`
          if (isMobile) {
            return (
              <EstimateMobileLineCard
                key={line.uid}
                line={line}
                index={i}
                isReadOnly={Boolean(isReadOnly)}
                provider={estimateFormCoeditProvider}
                coeditPending={estimateFormCoeditPending}
                lineStructureLocked={Boolean(isReadOnly) || coeditActive}
                lineIncl={lineIncl}
                lineSupply={lineSupply}
                lineVat={lineVat}
                hasPartner={hasPartner}
                onUpdate={(patch) => updateLine(i, patch)}
                onLookup={() => handleModelLookup(i)}
                onRemove={() => removeLine(i)}
              >
                {isBundle ? (
                  <BundleOptionRow
                    line={line}
                    index={i}
                    disabled={Boolean(isReadOnly) || coeditActive}
                    onChange={(patch) => updateSetOption(i, patch)}
                  />
                ) : null}
              </EstimateMobileLineCard>
            )
          }
          return (
           <div key={line.uid}>
            {/* R6-L2: role="row" 는 부모 table/rowgroup 없는 orphan(axe aria-required-parent
                serious)이라 제거 — aria-describedby 는 전역 attribute 라 role 없이 유효. */}
            <div
              aria-describedby={line.priceRefreshChanged ? priceChangedStatusId : undefined}
              style={{
                display: 'grid',
                gridTemplateColumns:
                  '32px 160px 1fr 100px 80px 130px 130px 36px',
                gap: 8,
                padding: '6px 0',
                alignItems: 'center',
                // R6-H4: 강조행 구분선 #F3F4F6 on --surface-selected(#EFF6FF)=1.01:1 —
                // LineRow.module.css(.priceRefreshed border-bottom-color)와 동일하게
                // 강조행 한정 --line-focus(#3B82F6, 3.38:1)로 상향. 기본 행은 기존 유지.
                borderBottom: isBundle
                  ? 'none'
                  : `1px solid ${line.priceRefreshChanged ? 'var(--line-focus)' : '#F3F4F6'}`,
                borderLeft: line.priceRefreshChanged ? '4px solid var(--action-brand)' : '4px solid transparent',
                background: line.priceRefreshChanged ? 'var(--surface-selected)' : undefined,
                // R6-H4: inset 링 --state-info-border(#BFDBFE) on #EFF6FF=1.31:1 —
                // LineRow.module.css:202 교정과 1:1 정렬(--action-brand #1E40AF, 8.02:1).
                boxShadow: line.priceRefreshChanged ? 'inset 0 0 0 1px var(--action-brand)' : undefined,
              }}
              data-testid={`estimate-form-line-${i}`}
              data-price-source={line.priceSource ?? ''}
            >
              <div
                style={{
                  textAlign: 'center',
                  // R4-D1: 강조행 배경(--surface-selected 실값 #EFF6FF) 위 #6B7280 은 4.44:1 로
                  // AA(4.5) 미달 — 강조행 한정 --ink-secondary(실값 #5C6773, 5.30:1 PASS) 상향.
                  // 흰 배경 기본 행은 4.83:1 통과라 기존 색 유지.
                  color: line.priceRefreshChanged ? 'var(--ink-secondary, #5C6773)' : '#6B7280',
                }}
              >
                {i + 1}
              </div>
              <div>
                <CollaborativeSlipInput
                  provider={estimateFormCoeditProvider}
                  coeditPending={estimateFormCoeditPending}
                  fieldPath={`items.${i}.modelName`}
                  type="text"
                  value={line.modelName}
                  onValueChange={(value) => updateLine(i, { modelName: value })}
                  onBlur={() => handleModelLookup(i)}
                  readOnly={Boolean(isReadOnly)}
                  placeholder="예: AJ040RXH4BC1"
                  error={line.lookupError ?? undefined}
                  aria-label={`라인 ${i + 1} 모델명`}
                  data-testid={`estimate-form-line-${i}-model`}
                />
              </div>
              <CollaborativeSlipInput
                provider={estimateFormCoeditProvider}
                coeditPending={estimateFormCoeditPending}
                fieldPath={`items.${i}.productName`}
                type="text"
                value={line.productName}
                onValueChange={(value) => updateLine(i, { productName: value })}
                readOnly={Boolean(isReadOnly)}
                aria-label={`라인 ${i + 1} 품목명`}
              />
              <CollaborativeSlipInput
                provider={estimateFormCoeditProvider}
                coeditPending={estimateFormCoeditPending}
                fieldPath={`items.${i}.specification`}
                type="text"
                value={line.specification}
                onValueChange={(value) => updateLine(i, { specification: value })}
                readOnly={Boolean(isReadOnly)}
                aria-label={`라인 ${i + 1} 규격`}
              />
              <CollaborativeSlipInput
                provider={estimateFormCoeditProvider}
                coeditPending={estimateFormCoeditPending}
                fieldPath={`items.${i}.quantity`}
                type="text"
                value={line.quantity}
                onValueChange={(value) => updateLine(i, { quantity: value })}
                readOnly={Boolean(isReadOnly)}
                inputMode="numeric"
                inputStyle={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                aria-label={`라인 ${i + 1} 수량`}
                data-testid={`estimate-form-line-${i}-qty`}
              />
              <div>
                <CollaborativeSlipInput
                  provider={estimateFormCoeditProvider}
                  coeditPending={estimateFormCoeditPending}
                  fieldPath={`items.${i}.unitPrice`}
                  type="text"
                  value={line.unitPrice}
                  onValueChange={(value) => updateLine(i, {
                    unitPrice: value,
                    priceSource: 'USER',
                    priceMemoryUpdatedAt: null,
                    priceRefreshChanged: false,
                    lookupLoading: false,
                    legacyPriceUntouched: false,
                  })}
                  // doc-sync 유래 값 반영은 분류(priceSource) 를 건드리지 않는다 — 자동채움 provider
                  // write 가 pending REMEMBERED/CATALOG 분류를 USER 로 덮는 마커 소멸 차단(R4-F6).
                  // 분류 판정은 페이지 구독(coeditLinesToDraftLines + localAutoPriceWrites)이 단일 소스.
                  onDocSyncValueChange={(value) => updateLine(i, { unitPrice: value })}
                  readOnly={Boolean(isReadOnly)}
                  inputMode="decimal"
                  inputStyle={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}
                  aria-label={`라인 ${i + 1} 단가`}
                  aria-describedby={priceStatus ? priceStatusId : undefined}
                  data-testid={`estimate-form-line-${i}-unit-price`}
                />
                {/* R4-D2: 라인별 aria-live 제거 — 전역 고지는 배너(role="status") 1곳이 담당. */}
                {priceStatus ? (
                  <span
                    id={priceStatusId}
                    role="note"
                    aria-label={priceStatus.description}
                    title={priceStatus.description}
                    className="price-source-note"
                  >
                    {priceStatus.label}
                  </span>
                ) : null}
                {line.priceRefreshChanged ? <PriceChangeIndicator id={priceChangedStatusId} /> : null}
              </div>
              <div
                style={{
                  textAlign: 'right',
                  fontSize: 13,
                  color: 'var(--ink-primary)',
                  fontVariantNumeric: 'tabular-nums',
                  background: '#F9FAFB',
                  padding: '6px 8px',
                  borderRadius: 4,
                }}
              >
                {fmt(lineIncl)}
                <div style={{ fontSize: 10, color: 'var(--ink-secondary, #5C6773)', fontWeight: 400 }}>
                  공급 {fmt(lineSupply)} · VAT {fmt(lineVat)}
                </div>
              </div>
              <button
                type="button"
                onClick={() => removeLine(i)}
                disabled={Boolean(isReadOnly) || coeditActive}
                aria-label={`라인 ${i + 1} 삭제`}
                style={{
                  height: 32,
                  width: 32,
                  border: '1px solid var(--color-neutral-300)',
                  borderRadius: 4,
                  background: '#fff',
                  color: 'var(--state-danger)',
                  cursor: isReadOnly || coeditActive ? 'not-allowed' : 'pointer',
                }}
              >
                ×
              </button>
            </div>
            {isBundle ? (
              <BundleOptionRow
                line={line}
                index={i}
                disabled={Boolean(isReadOnly) || coeditActive}
                onChange={(patch) => updateSetOption(i, patch)}
              />
            ) : null}
           </div>
          )
        })}
        </div>

        {!isReadOnly ? (
          <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
            <Button
              variant="ghost"
              size="sm"
              onClick={addLine}
              disabled={coeditActive}
              data-testid="estimate-form-add-line"
            >
              + 라인 추가
            </Button>
            {!isMobile && canViewProductLookups ? (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => setLineLookupOpen(true)}
                disabled={estimateFormCoeditPending}
                data-testid="estimate-line-lookup-btn"
              >
                참조 조회
              </Button>
            ) : null}
          </div>
        ) : null}

        {/* 합계 */}
        <div
          className="mobile-form-grid"
          style={{
            marginTop: 16,
            padding: '12px 16px',
            background: '#F9FAFB',
            borderRadius: 6,
            display: 'grid',
            gridTemplateColumns: '1fr 140px 140px 140px',
            gap: 16,
            fontSize: 14,
            fontVariantNumeric: 'tabular-nums',
            alignItems: 'center',
          }}
          data-testid="estimate-form-totals"
        >
          <div style={{ fontWeight: 600 }}>합계</div>
          <div style={{ textAlign: 'right' }}>
            공급가액 <strong>{fmt(totals.supply)}</strong>
          </div>
          <div style={{ textAlign: 'right' }}>
            부가세 <strong>{fmt(totals.vat)}</strong>
          </div>
          <div style={{ textAlign: 'right', fontSize: 16 }}>
            총합 <strong>{fmt(totals.total)}</strong>
          </div>
        </div>
      </Card>

      {topError ? (
        <div
          className="error-banner"
          role="alert"
          style={{ marginTop: 16, padding: 12, color: 'var(--state-danger)' }}
        >
          {topError}
        </div>
      ) : null}

      {estimateFormCoeditPending ? (
        <p role="status" data-testid="estimate-form-coedit-pending">
          협업 연결 중…
        </p>
      ) : null}

      {/* R4-F4 busy 단서 — R4-D9 계열 sweep: live region 은 상시 렌더하고 텍스트만 토글 —
          ARIA 관행상 live region 이 선존재해야 SR 낭독이 신뢰된다. 비활성 시 margin 0
          빈 p = 시각 0px. */}
      <p
        role="status"
        aria-live="polite"
        data-testid="estimate-form-price-refresh-busy"
        style={priceResolutionBusy ? undefined : { margin: 0 }}
      >
        {priceResolutionBusy ? '최근단가 확인 중…' : null}
      </p>

      <div
        style={{
          display: 'flex',
          justifyContent: 'flex-end',
          gap: 8,
          marginTop: 16,
        }}
      >
        <Button variant="ghost" onClick={() => navigate(-1)}>
          취소
        </Button>
        {!isReadOnly ? (
          <>
            <Button
              variant="ghost"
              onClick={handleSave}
              disabled={isPending || estimateFormCoeditPending || priceResolutionBusy}
              data-testid="estimate-form-save-button"
            >
              {isPending ? '저장 중...' : '임시저장'}
            </Button>
            {isEdit ? (
              <Button
                variant="primary"
                onClick={handleSend}
                disabled={isPending || estimateFormCoeditPending || priceResolutionBusy}
                data-testid="estimate-form-send-button"
              >
                {sendMutation.isPending ? '발송 중...' : '발송'}
              </Button>
            ) : null}
          </>
        ) : null}
      </div>

      <LineLookupReferenceModal
        open={lineLookupOpen}
        onClose={() => setLineLookupOpen(false)}
      />
    </>
  )
}
