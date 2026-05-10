/**
 * vendor 발주서 OCR 업로드 (`/sales/vendor-order-upload`).
 *
 * Phase 10 PR-F2 — Designer mock (3-step UI). BE 미연결 (Tesseract OCR endpoint
 * 미구현). 실 OCR endpoint 합류 시 mock state 를 useMutation 으로 교체.
 *
 * <h2>용도</h2>
 * legacy GAS #10 (에어디자이너) + #14 (제이시스템) 운송장/발주서 OCR 자동화 native
 * 이식. vendor 가 우리에게 보내준 PDF / 이미지 형태의 발주서를 desktop 에서 직접
 * OCR 하여 견적 / 주문서 line item 을 자동 생성. 사용자는 매칭 결과만 확인 후
 * 확정 → PartnerOrder 발행.
 *
 * <h2>3-step UX</h2>
 * <ol>
 *   <li><b>Step 1 — Upload:</b> vendor 라디오 (에어디자이너 / 제이시스템) +
 *       파일 drag-drop (.pdf, .png, .jpg, 단일) + 미리보기 + "OCR 분석 시작".</li>
 *   <li><b>Step 2 — Preview:</b> 좌측 OCR raw 텍스트 read-only + 우측 파싱된
 *       line item 표 (수량/단가 수정 가능, 매칭 실패 행 빨간 highlight) +
 *       거래처 정보 자동 lookup + 합계 row + "다시 업로드" / "확정".</li>
 *   <li><b>Step 3 — Confirm:</b> 발주 생성 결과 (PartnerOrder no + 상태 + 총액)
 *       + "발주서 보기" link + "다른 vendor 업로드".</li>
 * </ol>
 *
 * <h2>설계 노트</h2>
 * <ul>
 *   <li>UUID 비공개 (feedback_uuid_no_user_visibility) — 사용자 노출 = vendorName
 *       + partnerCode + productName 만. 내부 식별자 partnerOrderId 는 link 의
 *       path param 으로만 전달.</li>
 *   <li>풀네임 ROLE — 라우트 가드는 routes/index.tsx 에서 부여 (영업 그룹).</li>
 *   <li>한국어 라벨 100%.</li>
 *   <li>(주)삼한공조시스템 표기 금지 — vendor 발주는 우리가 받는 입장이므로
 *       vendor 명만 강조.</li>
 *   <li>mock 데이터 명확 (TODO comment "BE 연결 시점에 실 OCR 결과 사용").</li>
 *   <li>Designer mock 색상 / Stepper / drag-drop UX 보존 — CSS module 별도.</li>
 * </ul>
 *
 * <h2>data-testid</h2>
 * <ul>
 *   <li>{@code vendor-order-stepper}</li>
 *   <li>{@code vendor-radio-airdesigner / vendor-radio-jsystem}</li>
 *   <li>{@code vendor-order-file-input / vendor-order-drop-zone}</li>
 *   <li>{@code vendor-order-ocr-run-btn / vendor-order-confirm-btn /
 *       vendor-order-restart-btn}</li>
 *   <li>{@code vendor-order-item-row-{idx} / vendor-order-item-qty-{idx} /
 *       vendor-order-item-price-{idx}}</li>
 *   <li>{@code vendor-order-result-card / vendor-order-view-link}</li>
 * </ul>
 */
import {
  useCallback,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent as ReactDragEvent,
} from 'react'
import { Button } from '@samhan/design-system'
import { usePageTitle } from '../hooks/usePageTitle'
import styles from './SalesVendorOrderUploadPage.module.css'

// ---------------------------------------------------------------------------
// 도메인 타입 (mock — BE 연결 시점에 dispatchVendorOrderOcrApi.ts 분리 예정)
// ---------------------------------------------------------------------------

/** vendor 종류 — 사용자 결정 2종 (에어디자이너 / 제이시스템). */
type VendorType = 'AIRDESIGNER' | 'JSYSTEM'

interface VendorOption {
  type: VendorType
  name: string
  hint: string
  testId: string
}

const VENDOR_OPTIONS: ReadonlyArray<VendorOption> = [
  {
    type: 'AIRDESIGNER',
    name: '에어디자이너',
    hint: 'legacy GAS #10 — PDF/이미지 발주서 OCR',
    testId: 'vendor-radio-airdesigner',
  },
  {
    type: 'JSYSTEM',
    name: '제이시스템',
    hint: 'legacy GAS #14 — PDF/이미지 발주서 OCR',
    testId: 'vendor-radio-jsystem',
  },
]

/** 파싱된 vendor 거래처 정보 (자동 lookup 결과). */
interface VendorPartnerInfo {
  partnerCode: string
  partnerName: string
  businessRegNo: string
  dcRate: number // %
  dcDesc: string
}

/** 파싱된 line item (정규식 매칭 + 수정 가능). */
interface ParsedLineItem {
  productName: string
  modelCode: string
  quantity: number
  sheetPrice: number
  dcPrice: number
  finalPrice: number
  /** 정규식 매칭 실패 시 true — 빨간 highlight + 사용자 보정 안내. */
  matchFailed: boolean
  /** 매칭 실패 사유 (예: "모델 코드 정규식 miss"). */
  failReason?: string
}

interface OcrParseResult {
  rawText: string
  partner: VendorPartnerInfo
  items: ParsedLineItem[]
}

// ---------------------------------------------------------------------------
// Mock OCR 결과 — vendor 별 fixture
// TODO(BE 연결 시점): 실 OCR endpoint (POST /api/v1/vendor-order-ocr) 응답으로 교체.
// ---------------------------------------------------------------------------

const MOCK_RESULTS: Record<VendorType, OcrParseResult> = {
  AIRDESIGNER: {
    rawText: [
      '에어디자이너 (주)',
      '발주서  No. AD-2026-05-009',
      '거래처: (주)에어디자이너 / 사업자번호 123-45-67890',
      '주소: 서울 강남구 ...',
      '------------------------------------------------------',
      ' 품명           모델           수량   단가    금액',
      ' 천장형 4WAY    AD-CST4-3HP   2     820,000  1,640,000',
      ' 벽걸이형 인버터 AD-WLI-2HP    3     450,000  1,350,000',
      ' 시스템 콘트롤러 AD-CTRL-X10   1     180,000    180,000',
      ' [매칭미상] AD-???-NEW1      1     350,000    350,000',
      '------------------------------------------------------',
      '합계 (DC 적용 전)   3,520,000',
      'DC 5% 적용         -176,000',
      '최종 합계          3,344,000',
    ].join('\n'),
    partner: {
      partnerCode: 'AIRD-001',
      partnerName: '(주)에어디자이너',
      businessRegNo: '123-45-67890',
      dcRate: 5,
      dcDesc: '연간 누적 매출 DC 5%',
    },
    items: [
      {
        productName: '천장형 4WAY',
        modelCode: 'AD-CST4-3HP',
        quantity: 2,
        sheetPrice: 820_000,
        dcPrice: 779_000,
        finalPrice: 1_558_000,
        matchFailed: false,
      },
      {
        productName: '벽걸이형 인버터',
        modelCode: 'AD-WLI-2HP',
        quantity: 3,
        sheetPrice: 450_000,
        dcPrice: 427_500,
        finalPrice: 1_282_500,
        matchFailed: false,
      },
      {
        productName: '시스템 콘트롤러',
        modelCode: 'AD-CTRL-X10',
        quantity: 1,
        sheetPrice: 180_000,
        dcPrice: 171_000,
        finalPrice: 171_000,
        matchFailed: false,
      },
      {
        productName: '[매칭미상]',
        modelCode: 'AD-???-NEW1',
        quantity: 1,
        sheetPrice: 350_000,
        dcPrice: 350_000,
        finalPrice: 350_000,
        matchFailed: true,
        failReason: '품목 마스터 모델 코드 정규식 miss — 신규 코드일 가능성',
      },
    ],
  },
  JSYSTEM: {
    rawText: [
      '제이시스템 (주)',
      '구매 발주서  No. JS-26-0510-022',
      '공급처: (주)제이시스템 / 등록번호 456-78-90123',
      'TEL: 02-555-1234',
      '------------------------------------------------------',
      ' 항목          모델            수량  시트가     적용가',
      ' 정온항온기    JS-PCH-300L     1    1,200,000  1,140,000',
      ' 송풍기        JS-FAN-550W     4      85,000    323,000',
      ' [모델 누락]   ---             2      40,000     76,000',
      '------------------------------------------------------',
      '소계               1,539,000',
      'DC 5% 차감          -76,950',
      '청구 합계          1,462,050',
    ].join('\n'),
    partner: {
      partnerCode: 'JSYS-001',
      partnerName: '(주)제이시스템',
      businessRegNo: '456-78-90123',
      dcRate: 5,
      dcDesc: '월 정기 거래 DC 5%',
    },
    items: [
      {
        productName: '정온항온기',
        modelCode: 'JS-PCH-300L',
        quantity: 1,
        sheetPrice: 1_200_000,
        dcPrice: 1_140_000,
        finalPrice: 1_140_000,
        matchFailed: false,
      },
      {
        productName: '송풍기',
        modelCode: 'JS-FAN-550W',
        quantity: 4,
        sheetPrice: 85_000,
        dcPrice: 80_750,
        finalPrice: 323_000,
        matchFailed: false,
      },
      {
        productName: '[모델 누락]',
        modelCode: '',
        quantity: 2,
        sheetPrice: 40_000,
        dcPrice: 38_000,
        finalPrice: 76_000,
        matchFailed: true,
        failReason: '모델 코드 인식 실패 — vendor 양식 변형 가능, 수동 보정 필요',
      },
    ],
  },
}

// ---------------------------------------------------------------------------
// 파일 검증
// ---------------------------------------------------------------------------

const ACCEPT_EXT = ['.pdf', '.png', '.jpg', '.jpeg']
const MAX_FILE_SIZE_MB = 10

function getExtension(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx === -1 ? '' : name.substring(idx).toLowerCase()
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function formatKrw(value: number): string {
  return value.toLocaleString('ko-KR') + '원'
}

// ---------------------------------------------------------------------------
// Stepper — 3-step 진행 상황 시각화
// ---------------------------------------------------------------------------

type StepKey = 'UPLOAD' | 'PREVIEW' | 'CONFIRM'

const STEP_LABELS: Record<StepKey, string> = {
  UPLOAD: 'Step 1: 파일 업로드',
  PREVIEW: 'Step 2: 분석 결과 확인',
  CONFIRM: 'Step 3: 발주 확정',
}

const STEP_ORDER: ReadonlyArray<StepKey> = ['UPLOAD', 'PREVIEW', 'CONFIRM']

interface StepperProps {
  current: StepKey
}

function Stepper({ current }: StepperProps) {
  const currentIdx = STEP_ORDER.indexOf(current)
  return (
    <div
      className={styles.stepper}
      data-testid="vendor-order-stepper"
      aria-label="발주 OCR 진행 단계"
    >
      {STEP_ORDER.map((step, idx) => {
        const stepIdx = idx
        const isActive = stepIdx === currentIdx
        const isDone = stepIdx < currentIdx
        const cls = [
          styles.step,
          isActive ? styles.stepActive : '',
          isDone ? styles.stepDone : '',
        ]
          .filter(Boolean)
          .join(' ')
        const badgeCls = [
          styles.stepBadge,
          !isActive && !isDone ? styles.stepBadgeDefault : '',
        ]
          .filter(Boolean)
          .join(' ')
        return (
          <div key={step} style={{ display: 'flex', alignItems: 'center' }}>
            <div
              className={cls}
              role="status"
              aria-current={isActive ? 'step' : undefined}
            >
              <span className={badgeCls}>{isDone ? '✓' : stepIdx + 1}</span>
              {STEP_LABELS[step]}
            </div>
            {stepIdx < STEP_ORDER.length - 1 ? (
              <div className={styles.stepConnector} aria-hidden="true" />
            ) : null}
          </div>
        )
      })}
    </div>
  )
}

// ---------------------------------------------------------------------------
// 컴포넌트
// ---------------------------------------------------------------------------

export function SalesVendorOrderUploadPage() {
  usePageTitle('vendor 발주서 OCR 업로드')

  // ----- step + 입력 state -----
  const [step, setStep] = useState<StepKey>('UPLOAD')
  const [vendor, setVendor] = useState<VendorType>('AIRDESIGNER')
  const [file, setFile] = useState<File | null>(null)
  const [previewUrl, setPreviewUrl] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [dragOver, setDragOver] = useState(false)
  const fileInputRef = useRef<HTMLInputElement>(null)

  // ----- Step 2 OCR 결과 (mock) -----
  const [ocrResult, setOcrResult] = useState<OcrParseResult | null>(null)
  const [items, setItems] = useState<ParsedLineItem[]>([])
  const [analyzing, setAnalyzing] = useState(false)

  // ----- Step 3 발주 결과 (mock) -----
  const [partnerOrderNo, setPartnerOrderNo] = useState<string>('')
  const [submitting, setSubmitting] = useState(false)

  // 파일 미리보기 URL revoke
  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl)
    }
  }, [previewUrl])

  // ----- 파일 업로드 -----

  const accept = useCallback(
    (incoming: File) => {
      const ext = getExtension(incoming.name)
      if (!ACCEPT_EXT.includes(ext)) {
        setError(
          `지원하지 않는 파일 형식입니다 (${incoming.name}). ${ACCEPT_EXT.join(', ')} 만 허용.`,
        )
        return
      }
      if (incoming.size > MAX_FILE_SIZE_MB * 1024 * 1024) {
        setError(
          `${incoming.name} 파일이 ${MAX_FILE_SIZE_MB}MB 를 초과합니다 (${formatSize(incoming.size)}).`,
        )
        return
      }
      setError(null)
      setFile(incoming)
      // 이미지 형식만 즉시 직접 미리보기 (PDF 는 placeholder 안내)
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      if (ext === '.pdf') {
        setPreviewUrl(null)
      } else {
        setPreviewUrl(URL.createObjectURL(incoming))
      }
    },
    [previewUrl],
  )

  const handleFileInput = (e: ChangeEvent<HTMLInputElement>) => {
    const list = e.target.files
    if (!list || list.length === 0) return
    const first = list[0]
    if (first) accept(first)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  const handleDrop = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(false)
    const list = e.dataTransfer.files
    if (!list || list.length === 0) return
    const first = list[0]
    if (first) accept(first)
  }

  const handleDragOver = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    setDragOver(true)
  }

  const handleDragLeave = () => setDragOver(false)

  // ----- Step 1 → Step 2: OCR 실행 (mock) -----
  // TODO(BE 연결 시점): mock setTimeout 을 useMutation(POST /api/v1/vendor-order-ocr) 로 교체.
  const handleRunOcr = () => {
    if (!file) {
      setError('vendor 발주서 파일을 업로드하세요.')
      return
    }
    setError(null)
    setAnalyzing(true)
    setTimeout(() => {
      const result = MOCK_RESULTS[vendor]
      setOcrResult(result)
      setItems(result.items.map((i) => ({ ...i })))
      setAnalyzing(false)
      setStep('PREVIEW')
    }, 600)
  }

  // ----- Step 2 line item 수정 -----

  const updateItem = (idx: number, patch: Partial<ParsedLineItem>) => {
    setItems((prev) =>
      prev.map((it, i) => {
        if (i !== idx) return it
        const next = { ...it, ...patch }
        // 단가가 변경되면 finalPrice 재계산 (DC 적용가 * 수량)
        next.finalPrice = next.dcPrice * next.quantity
        return next
      }),
    )
  }

  const totalAmount = useMemo(
    () => items.reduce((sum, it) => sum + it.finalPrice, 0),
    [items],
  )

  const failedCount = useMemo(
    () => items.filter((it) => it.matchFailed).length,
    [items],
  )

  // ----- Step 2 → Step 3: 발주 확정 (mock) -----
  // TODO(BE 연결 시점): POST /api/v1/partner-orders/from-vendor-ocr 호출.
  const handleConfirm = () => {
    if (!ocrResult) return
    setSubmitting(true)
    setTimeout(() => {
      // mock 발주서 번호 — vendor prefix + YYMMDD + sequence
      const today = new Date()
      const yymmdd
        = String(today.getFullYear()).slice(2)
        + String(today.getMonth() + 1).padStart(2, '0')
        + String(today.getDate()).padStart(2, '0')
      const prefix = vendor === 'AIRDESIGNER' ? 'PO-AD' : 'PO-JS'
      const seq = String(Math.floor(Math.random() * 900) + 100)
      setPartnerOrderNo(`${prefix}-${yymmdd}-${seq}`)
      setSubmitting(false)
      setStep('CONFIRM')
    }, 500)
  }

  // ----- Step 3 → Step 1: 다른 vendor 업로드 -----

  const handleReset = () => {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    setPreviewUrl(null)
    setFile(null)
    setOcrResult(null)
    setItems([])
    setPartnerOrderNo('')
    setError(null)
    setStep('UPLOAD')
  }

  // ----- Step 2 → Step 1: 다시 업로드 (vendor 유지) -----

  const handleRestart = () => {
    setOcrResult(null)
    setItems([])
    setError(null)
    setStep('UPLOAD')
  }

  // ----- render -----

  return (
    <div
      style={{
        padding: 16,
        display: 'flex',
        flexDirection: 'column',
        gap: 16,
      }}
    >
      <header>
        <h3 style={{ margin: '0 0 4px' }}>vendor 발주서 OCR 업로드</h3>
        <div style={{ fontSize: 12, color: 'var(--color-neutral-600, #4B5563)' }}>
          legacy GAS #10 (에어디자이너) + #14 (제이시스템) 운송장/발주서 OCR
          native 이식. PDF / 이미지 → 자동 line item 파싱 → 매칭 후 발주 생성.
        </div>
      </header>

      <Stepper current={step} />

      {error ? (
        <div
          role="alert"
          style={{
            padding: '8px 12px',
            border: '1px solid var(--color-danger-300, #fca5a5)',
            background: 'var(--color-danger-50, #fef2f2)',
            color: 'var(--color-danger-700, #b91c1c)',
            borderRadius: 6,
            fontSize: 13,
          }}
        >
          {error}
        </div>
      ) : null}

      {/* ───────── Step 1 — Upload ───────── */}
      {step === 'UPLOAD' ? (
        <section style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* vendor 라디오 */}
          <div>
            <div
              style={{
                fontSize: 13,
                fontWeight: 600,
                marginBottom: 8,
                color: 'var(--color-neutral-700, #374151)',
              }}
            >
              vendor 선택
            </div>
            <div className={styles.vendorList} role="radiogroup" aria-label="vendor 선택">
              {VENDOR_OPTIONS.map((opt) => {
                const active = vendor === opt.type
                const cls = [
                  styles.vendorCard,
                  active ? styles.vendorCardActive : '',
                ]
                  .filter(Boolean)
                  .join(' ')
                return (
                  <label
                    key={opt.type}
                    className={cls}
                    data-testid={opt.testId}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <input
                        type="radio"
                        name="vendor"
                        value={opt.type}
                        checked={active}
                        onChange={() => setVendor(opt.type)}
                      />
                      <span className={styles.vendorTitle}>{opt.name}</span>
                    </div>
                    <div className={styles.vendorHint}>{opt.hint}</div>
                  </label>
                )
              })}
            </div>
          </div>

          {/* drag-drop 업로드 영역 */}
          <div
            data-testid="vendor-order-drop-zone"
            onDrop={handleDrop}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onClick={() => fileInputRef.current?.click()}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') {
                e.preventDefault()
                fileInputRef.current?.click()
              }
            }}
            className={[
              styles.dropZone,
              dragOver ? styles.dropZoneActive : '',
            ]
              .filter(Boolean)
              .join(' ')}
          >
            <div className={styles.dropTitle}>
              발주서 파일을 끌어다 놓거나 클릭하여 선택
            </div>
            <div className={styles.dropHint}>
              {ACCEPT_EXT.join(', ')} 만 허용 · 단일 파일 · 최대{' '}
              {MAX_FILE_SIZE_MB}MB
            </div>
            <input
              ref={fileInputRef}
              type="file"
              accept={ACCEPT_EXT.join(',')}
              onChange={handleFileInput}
              data-testid="vendor-order-file-input"
              style={{ display: 'none' }}
            />
          </div>

          {/* 파일 미리보기 */}
          {file ? (
            <div className={styles.previewBox}>
              <div className={styles.previewThumb}>
                {previewUrl ? (
                  <img src={previewUrl} alt={`${file.name} 미리보기`} />
                ) : (
                  <span>PDF 미리보기 — 첫 페이지 (BE 연결 시점에 실 렌더)</span>
                )}
              </div>
              <div className={styles.previewMeta}>
                <div className={styles.previewMetaRow}>
                  <span className={styles.previewMetaLabel}>파일명</span>
                  <strong>{file.name}</strong>
                </div>
                <div className={styles.previewMetaRow}>
                  <span className={styles.previewMetaLabel}>크기</span>
                  <span>{formatSize(file.size)}</span>
                </div>
                <div className={styles.previewMetaRow}>
                  <span className={styles.previewMetaLabel}>대상 vendor</span>
                  <strong>
                    {VENDOR_OPTIONS.find((v) => v.type === vendor)?.name}
                  </strong>
                </div>
              </div>
            </div>
          ) : null}

          {/* 액션 */}
          <div className={styles.actionRow}>
            <Button
              variant="primary"
              onClick={handleRunOcr}
              disabled={!file || analyzing}
              data-testid="vendor-order-ocr-run-btn"
            >
              {analyzing ? '분석 중…' : 'OCR 분석 시작'}
            </Button>
          </div>
        </section>
      ) : null}

      {/* ───────── Step 2 — Preview ───────── */}
      {step === 'PREVIEW' && ocrResult ? (
        <section style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* 거래처 정보 (자동 lookup 결과) */}
          <div className={styles.partnerInfo}>
            <span className={styles.partnerInfoLabel}>거래처</span>
            <strong>
              {ocrResult.partner.partnerName} ({ocrResult.partner.partnerCode})
            </strong>
            <span className={styles.partnerInfoLabel}>사업자번호</span>
            <span>{ocrResult.partner.businessRegNo}</span>
            <span className={styles.partnerInfoLabel}>DC 정보</span>
            <span>
              {ocrResult.partner.dcRate}% &middot; {ocrResult.partner.dcDesc}
            </span>
          </div>

          {failedCount > 0 ? (
            <div
              role="alert"
              style={{
                padding: '8px 12px',
                border: '1px solid var(--color-warning-300, #fcd34d)',
                background: 'var(--color-warning-50, #fffbeb)',
                color: 'var(--color-warning-800, #92400e)',
                borderRadius: 6,
                fontSize: 13,
              }}
            >
              품목 매칭 실패 — 수동 보정 필요 ({failedCount}건). 빨간 행을
              확인 후 수정하세요.
            </div>
          ) : null}

          {/* OCR 텍스트 + line item 표 (좌/우 grid) */}
          <div className={styles.previewGrid}>
            <div>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  marginBottom: 6,
                  color: 'var(--color-neutral-700, #374151)',
                }}
              >
                OCR 결과 (read-only)
              </div>
              <pre className={styles.ocrText} aria-readonly="true">
                {ocrResult.rawText}
              </pre>
            </div>
            <div>
              <div
                style={{
                  fontSize: 12,
                  fontWeight: 600,
                  marginBottom: 6,
                  color: 'var(--color-neutral-700, #374151)',
                }}
              >
                파싱된 line item ({items.length}건)
              </div>
              <div style={{ overflowX: 'auto' }}>
                <table className={styles.itemTable}>
                  <thead>
                    <tr>
                      <th>품목</th>
                      <th>모델</th>
                      <th className={styles.colNumeric} style={{ width: 80 }}>
                        수량
                      </th>
                      <th className={styles.colNumeric} style={{ width: 110 }}>
                        단가 (시트)
                      </th>
                      <th className={styles.colNumeric} style={{ width: 110 }}>
                        DC 적용가
                      </th>
                      <th className={styles.colNumeric} style={{ width: 130 }}>
                        최종 단가
                      </th>
                    </tr>
                  </thead>
                  <tbody>
                    {items.map((it, idx) => (
                      <tr
                        key={`${it.modelCode}-${idx}`}
                        data-testid={`vendor-order-item-row-${idx}`}
                        className={it.matchFailed ? styles.itemRowFail : ''}
                      >
                        <td>
                          {it.productName}
                          {it.matchFailed ? (
                            <span
                              className={styles.itemRowFailHint}
                              title={it.failReason}
                            >
                              매칭 실패
                            </span>
                          ) : null}
                        </td>
                        <td>{it.modelCode || '—'}</td>
                        <td className={styles.colNumeric}>
                          <input
                            type="number"
                            min={0}
                            value={it.quantity}
                            onChange={(e) =>
                              updateItem(idx, {
                                quantity: Math.max(0, Number(e.target.value) || 0),
                              })
                            }
                            className={styles.itemInput}
                            data-testid={`vendor-order-item-qty-${idx}`}
                          />
                        </td>
                        <td className={styles.colNumeric}>
                          {formatKrw(it.sheetPrice)}
                        </td>
                        <td className={styles.colNumeric}>
                          <input
                            type="number"
                            min={0}
                            value={it.dcPrice}
                            onChange={(e) =>
                              updateItem(idx, {
                                dcPrice: Math.max(0, Number(e.target.value) || 0),
                              })
                            }
                            className={styles.itemInput}
                            data-testid={`vendor-order-item-price-${idx}`}
                          />
                        </td>
                        <td className={styles.colNumeric}>
                          {formatKrw(it.finalPrice)}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                  <tfoot>
                    <tr>
                      <td colSpan={5} className={styles.colNumeric}>
                        합계
                      </td>
                      <td className={styles.colNumeric}>
                        {formatKrw(totalAmount)}
                      </td>
                    </tr>
                  </tfoot>
                </table>
              </div>
            </div>
          </div>

          <div className={styles.actionRow}>
            <Button
              variant="secondary"
              onClick={handleRestart}
              disabled={submitting}
              data-testid="vendor-order-restart-btn"
            >
              다시 업로드
            </Button>
            <Button
              variant="primary"
              onClick={handleConfirm}
              disabled={submitting || items.length === 0}
              data-testid="vendor-order-confirm-btn"
            >
              {submitting ? '발주 생성 중…' : '확정'}
            </Button>
          </div>
        </section>
      ) : null}

      {/* ───────── Step 3 — Confirm ───────── */}
      {step === 'CONFIRM' && partnerOrderNo ? (
        <section style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <div
            className={styles.confirmCard}
            data-testid="vendor-order-result-card"
          >
            <div className={styles.confirmTitle}>발주가 정상 생성되었습니다.</div>
            <div className={styles.confirmRow}>
              <span className={styles.confirmLabel}>발주서 번호</span>
              <span className={styles.confirmStrong}>{partnerOrderNo}</span>
            </div>
            <div className={styles.confirmRow}>
              <span className={styles.confirmLabel}>vendor</span>
              <span>
                {VENDOR_OPTIONS.find((v) => v.type === vendor)?.name}
              </span>
            </div>
            <div className={styles.confirmRow}>
              <span className={styles.confirmLabel}>거래처</span>
              <span>
                {ocrResult?.partner.partnerName} (
                {ocrResult?.partner.partnerCode})
              </span>
            </div>
            <div className={styles.confirmRow}>
              <span className={styles.confirmLabel}>상태</span>
              <span>대기 (PENDING) — 승인 시 정식 등록</span>
            </div>
            <div className={styles.confirmRow}>
              <span className={styles.confirmLabel}>총 금액</span>
              <span className={styles.confirmStrong}>
                {formatKrw(totalAmount)}
              </span>
            </div>
          </div>

          <div className={styles.actionRow}>
            {/* 발주서 보기 — 기존 partner-order-service 페이지 link */}
            <a
              href={`#/sales/partner-orders/${partnerOrderNo}`}
              data-testid="vendor-order-view-link"
              style={{
                padding: '8px 14px',
                borderRadius: 6,
                border: '1px solid var(--color-brand-500, #2563eb)',
                color: 'var(--color-brand-700, #1d4ed8)',
                background: '#fff',
                fontSize: 13,
                textDecoration: 'none',
                fontWeight: 600,
              }}
            >
              발주서 보기
            </a>
            <Button variant="secondary" onClick={handleReset}>
              다른 vendor 업로드
            </Button>
          </div>
        </section>
      ) : null}
    </div>
  )
}
