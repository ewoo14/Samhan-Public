/**
 * `<CsvUploadDialog>` — CSV 일괄 등록 다이얼로그 (PR-D Phase B 신규).
 *
 * 4 admin 페이지 (RegionsPage / DcConfigImportPage / ChatRoomsPage /
 * BlockedPartnersPage) 가 공통 사용. 사용자가 노션에서 다운로드한 CSV 를
 * 우리 시스템에 native import 하는 핵심 UX 컴포넌트.
 *
 * 3 단계 UX:
 *   1) select   — 드래그 앤 드롭 + 파일 선택 (확장자/크기 가드)
 *   2) uploading — progress bar (0~100%) + 취소 버튼
 *   3) result   — 성공 통계 + reject 보고서 표 + CSV 다운로드
 *
 * 접근성:
 * - role="dialog" + aria-modal + aria-labelledby
 * - Esc 닫기 (단, uploading 중에는 비활성)
 * - focus trap (Tab cycling), 진입 시 첫 focusable 로 이동, 닫을 때 복원
 * - body scroll lock
 *
 * 한국어 라벨 의무 — 영문 라벨 금지.
 */
import {
  useCallback,
  useEffect,
  useId,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent as ReactDragEvent,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react'
import { createPortal } from 'react-dom'
import styles from './CsvUploadDialog.module.css'
import { Button } from '../Button/Button'
import { Spinner } from '../Spinner/Spinner'

/**
 * 거부된 행 1건. inputData 는 헤더 → 값 매핑.
 */
export interface RejectedRow {
  rowNumber: number
  inputData: Record<string, string>
  reason: string
}

/**
 * 업로드 결과. inserted/updated 는 필수, skipped/rejected 는 옵션.
 */
export interface UploadResult {
  inserted: number
  updated: number
  skipped?: number
  rejected: RejectedRow[]
}

export interface CsvUploadDialogProps {
  /** 모달 open 여부. */
  open: boolean
  /** 닫기 콜백 (× / overlay click / Esc / 닫기 버튼). uploading 중에는 호출 X. */
  onClose: () => void
  /** 다이얼로그 헤더 제목 (예: "단톡방 매핑 일괄 등록"). */
  title: string
  /** 안내 텍스트 (옵션). */
  description?: string
  /** 허용 확장자 배열 (lowercase, 점 포함). 기본 ['.csv']. */
  acceptExtensions?: string[]
  /** 허용 최대 파일 크기 (MB). 기본 5. */
  maxFileSizeMB?: number
  /**
   * 업로드 콜백 — 호출자가 API 호출을 책임짐.
   * Promise resolve 시 result 단계 진입, reject 시 error 표시 후 select 단계로 복귀.
   */
  onUpload: (file: File) => Promise<UploadResult>
  /** 샘플 CSV 다운로드 URL (옵션, select 단계에 노출). */
  sampleDownloadUrl?: string
}

/** 다이얼로그 내부 단계. */
type Step = 'select' | 'uploading' | 'result'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled]):not([type="hidden"])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])',
].join(',')

function getFocusable(root: HTMLElement): HTMLElement[] {
  return Array.from(root.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (el) => !el.hasAttribute('disabled') && el.offsetParent !== null,
  )
}

/** 파일 크기를 사람이 읽기 쉬운 단위로 변환 (KB / MB). */
function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

/** 파일명에서 확장자 추출 (lowercase, 점 포함). */
function getExtension(name: string): string {
  const idx = name.lastIndexOf('.')
  return idx === -1 ? '' : name.substring(idx).toLowerCase()
}

/**
 * RejectedRow[] 를 CSV 문자열로 직렬화 (BOM 포함, Excel 한글 호환).
 */
function rejectedRowsToCsv(rejected: RejectedRow[]): string {
  if (rejected.length === 0) return '﻿행번호,거부사유\n'
  // 모든 inputData 키 union
  const keySet = new Set<string>()
  rejected.forEach((r) => Object.keys(r.inputData).forEach((k) => keySet.add(k)))
  const keys = Array.from(keySet)
  const header = ['행번호', ...keys, '거부사유']
  const escapeCell = (v: string): string => {
    if (v.includes('"') || v.includes(',') || v.includes('\n')) {
      return `"${v.replace(/"/g, '""')}"`
    }
    return v
  }
  const lines = [header.map(escapeCell).join(',')]
  for (const r of rejected) {
    const cells = [String(r.rowNumber), ...keys.map((k) => r.inputData[k] ?? ''), r.reason]
    lines.push(cells.map(escapeCell).join(','))
  }
  return '﻿' + lines.join('\n')
}

/**
 * `<CsvUploadDialog>` — CSV 일괄 등록 공통 다이얼로그.
 *
 * @example
 * ```tsx
 * <CsvUploadDialog
 *   open={open}
 *   onClose={() => setOpen(false)}
 *   title="단톡방 매핑 일괄 등록"
 *   description="노션에서 다운로드한 CSV 를 업로드하세요."
 *   onUpload={async (file) => await api.uploadChatRoomCsv(file)}
 * />
 * ```
 */
export function CsvUploadDialog({
  open,
  onClose,
  title,
  description,
  acceptExtensions = ['.csv'],
  maxFileSizeMB = 5,
  onUpload,
  sampleDownloadUrl,
}: CsvUploadDialogProps) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)
  const cancelRequestedRef = useRef(false)
  const reactId = useId()
  const titleId = `csv-upload-dialog-title-${reactId}`
  const descId = description ? `csv-upload-dialog-desc-${reactId}` : undefined

  const [step, setStep] = useState<Step>('select')
  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [validationError, setValidationError] = useState<string | null>(null)
  const [isDragOver, setIsDragOver] = useState(false)
  const [progress, setProgress] = useState(0)
  const [result, setResult] = useState<UploadResult | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)

  // 모달 닫혔다 다시 열릴 때 상태 reset
  useEffect(() => {
    if (open) {
      setStep('select')
      setSelectedFile(null)
      setValidationError(null)
      setProgress(0)
      setResult(null)
      setUploadError(null)
      cancelRequestedRef.current = false
    }
  }, [open])

  // 포커스 진입 + 복원
  useEffect(() => {
    if (!open) return
    previouslyFocusedRef.current = (document.activeElement as HTMLElement) ?? null
    const node = dialogRef.current
    if (node) {
      const focusables = getFocusable(node)
      const target = focusables[0] ?? node
      window.requestAnimationFrame(() => target.focus())
    }
    return () => {
      const prev = previouslyFocusedRef.current
      if (prev && typeof prev.focus === 'function') prev.focus()
    }
  }, [open])

  // body scroll lock
  useEffect(() => {
    if (!open) return
    const original = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = original
    }
  }, [open])

  // ESC handler — uploading 중에는 닫기 금지
  useEffect(() => {
    if (!open) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && step !== 'uploading') {
        e.stopPropagation()
        onClose()
      }
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [open, onClose, step])

  const handleBackdropClick = useCallback(
    (e: ReactMouseEvent<HTMLDivElement>) => {
      if (step === 'uploading') return
      if (e.target === e.currentTarget) onClose()
    },
    [onClose, step],
  )

  const handleKeyDown = useCallback((e: ReactKeyboardEvent<HTMLDivElement>) => {
    if (e.key !== 'Tab') return
    const node = dialogRef.current
    if (!node) return
    const focusables = getFocusable(node)
    if (focusables.length === 0) {
      e.preventDefault()
      node.focus()
      return
    }
    const first = focusables[0]!
    const last = focusables[focusables.length - 1]!
    const active = document.activeElement as HTMLElement | null
    if (e.shiftKey) {
      if (active === first || !node.contains(active)) {
        e.preventDefault()
        last.focus()
      }
    } else if (active === last) {
      e.preventDefault()
      first.focus()
    }
  }, [])

  /** 파일 검증 — 확장자/크기 체크. 통과 시 selectedFile 세팅, 실패 시 validationError. */
  const validateAndSetFile = useCallback(
    (file: File) => {
      const ext = getExtension(file.name)
      const allowed = acceptExtensions.map((e) => e.toLowerCase())
      if (!allowed.includes(ext)) {
        const allowedLabel = allowed.join(', ')
        setSelectedFile(null)
        setValidationError(
          `${allowedLabel} 파일만 업로드 가능합니다 (선택한 파일: ${file.name})`,
        )
        return
      }
      const maxBytes = maxFileSizeMB * 1024 * 1024
      if (file.size > maxBytes) {
        setSelectedFile(null)
        setValidationError(
          `파일 크기가 ${maxFileSizeMB}MB 를 초과합니다 (선택한 파일: ${formatSize(file.size)})`,
        )
        return
      }
      setValidationError(null)
      setSelectedFile(file)
    },
    [acceptExtensions, maxFileSizeMB],
  )

  const handleFileInputChange = (e: ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0]
    if (file) validateAndSetFile(file)
    // input value reset — 동일 파일 재선택 허용
    e.target.value = ''
  }

  const handleDragOver = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.stopPropagation()
    if (step !== 'select') return
    setIsDragOver(true)
  }

  const handleDragLeave = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
  }

  const handleDrop = (e: ReactDragEvent<HTMLDivElement>) => {
    e.preventDefault()
    e.stopPropagation()
    setIsDragOver(false)
    if (step !== 'select') return
    const file = e.dataTransfer.files?.[0]
    if (file) validateAndSetFile(file)
  }

  const handleStartUpload = useCallback(async () => {
    if (!selectedFile) return
    setStep('uploading')
    setProgress(0)
    setUploadError(null)
    cancelRequestedRef.current = false

    // 자체 progress 시뮬레이션 — XHR/streamed body 가 아닌 fetch 기반 onUpload 콜백을
    // 받기 때문에 단순 ramp 로 표시. 실제 완료는 Promise resolve 시점.
    const startedAt = Date.now()
    const tickHandle = window.setInterval(() => {
      const elapsed = Date.now() - startedAt
      // 0 ~ 90% 까지 30 초에 걸쳐 ease-out 스타일로 증가
      const pct = Math.min(90, Math.round((1 - Math.exp(-elapsed / 8000)) * 100))
      setProgress(pct)
    }, 200)

    try {
      const uploaded = await onUpload(selectedFile)
      window.clearInterval(tickHandle)
      if (cancelRequestedRef.current) {
        // 취소 요청 → result 무시하고 select 로 복귀
        setStep('select')
        setProgress(0)
        return
      }
      setProgress(100)
      setResult(uploaded)
      setStep('result')
    } catch (err) {
      window.clearInterval(tickHandle)
      const message =
        err instanceof Error ? err.message : '업로드에 실패했습니다. 다시 시도해 주세요.'
      setUploadError(message)
      setStep('select')
      setProgress(0)
    }
  }, [onUpload, selectedFile])

  const handleCancelUpload = useCallback(() => {
    cancelRequestedRef.current = true
    // 즉시 select 로 복귀 — 실제 fetch abort 는 호출자가 AbortController 로 처리해야 함
    setStep('select')
    setProgress(0)
  }, [])

  const handleRetry = useCallback(() => {
    setStep('select')
    setSelectedFile(null)
    setProgress(0)
    setResult(null)
    setValidationError(null)
    setUploadError(null)
  }, [])

  const handleDownloadRejected = useCallback(() => {
    if (!result || result.rejected.length === 0) return
    const csv = rejectedRowsToCsv(result.rejected)
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    const stamp = new Date().toISOString().replace(/[:.]/g, '-').substring(0, 19)
    a.download = `거부보고서_${stamp}.csv`
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)
  }, [result])

  if (!open || typeof document === 'undefined') return null

  const acceptAttr = acceptExtensions.join(',')

  return createPortal(
    <div
      className={styles['backdrop']}
      onMouseDown={handleBackdropClick}
      data-testid="csv-upload-dialog-backdrop"
    >
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={descId}
        tabIndex={-1}
        className={styles['dialog']}
        onKeyDown={handleKeyDown}
      >
        <header className={styles['header']}>
          <h2 id={titleId} className={styles['title']}>
            {title}
          </h2>
          {step !== 'uploading' ? (
            <button
              type="button"
              className={styles['closeBtn']}
              aria-label="닫기"
              onClick={onClose}
            >
              <svg width="16" height="16" viewBox="0 0 16 16" aria-hidden="true">
                <path
                  d="M3 3l10 10M13 3L3 13"
                  stroke="currentColor"
                  strokeWidth="1.6"
                  strokeLinecap="round"
                />
              </svg>
            </button>
          ) : null}
        </header>

        {description ? (
          <p id={descId} className={styles['description']}>
            {description}
          </p>
        ) : null}

        <div className={styles['body']}>
          {/* ── 단계 1: 파일 선택 ───────────────────────────── */}
          {step === 'select' ? (
            <>
              <div
                className={[
                  styles['dropZone'],
                  isDragOver ? styles['dropZoneActive'] : null,
                  selectedFile ? styles['dropZoneFilled'] : null,
                ]
                  .filter(Boolean)
                  .join(' ')}
                onDragOver={handleDragOver}
                onDragLeave={handleDragLeave}
                onDrop={handleDrop}
                role="button"
                tabIndex={0}
                aria-label="CSV 파일 선택"
                onClick={() => fileInputRef.current?.click()}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault()
                    fileInputRef.current?.click()
                  }
                }}
              >
                <input
                  ref={fileInputRef}
                  type="file"
                  accept={acceptAttr}
                  className={styles['fileInput']}
                  onChange={handleFileInputChange}
                  aria-hidden="true"
                  tabIndex={-1}
                />
                {selectedFile ? (
                  <div className={styles['fileSelected']}>
                    <div className={styles['fileIcon']} aria-hidden="true">
                      CSV
                    </div>
                    <div className={styles['fileMeta']}>
                      <div className={styles['fileName']}>{selectedFile.name}</div>
                      <div className={styles['fileSize']}>{formatSize(selectedFile.size)}</div>
                    </div>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={(e) => {
                        e.stopPropagation()
                        setSelectedFile(null)
                        setValidationError(null)
                      }}
                    >
                      제거
                    </Button>
                  </div>
                ) : (
                  <>
                    <div className={styles['dropIcon']} aria-hidden="true">
                      ⬆
                    </div>
                    <div className={styles['dropPrimary']}>
                      파일을 선택하세요
                    </div>
                    <div className={styles['dropSecondary']}>
                      또는 이 영역으로 파일을 끌어다 놓으세요
                    </div>
                    <div className={styles['dropHint']}>
                      허용: {acceptExtensions.join(', ')} · 최대 {maxFileSizeMB}MB
                    </div>
                  </>
                )}
              </div>

              {validationError ? (
                <div className={styles['errorBanner']} role="alert">
                  <span aria-hidden="true">⚠</span> {validationError}
                </div>
              ) : null}

              {uploadError ? (
                <div className={styles['errorBanner']} role="alert">
                  <span aria-hidden="true">⚠</span> {uploadError}
                </div>
              ) : null}

              {sampleDownloadUrl ? (
                <div className={styles['sampleLinkRow']}>
                  <a
                    href={sampleDownloadUrl}
                    download
                    className={styles['sampleLink']}
                  >
                    샘플 CSV 다운로드
                  </a>
                </div>
              ) : null}
            </>
          ) : null}

          {/* ── 단계 2: 업로드 진행 ─────────────────────────── */}
          {step === 'uploading' ? (
            <div className={styles['uploadingState']}>
              <Spinner size="md" tone="var(--color-brand-500)" label="업로드 중" />
              <div className={styles['uploadingLabel']}>업로드 중...</div>
              <div
                className={styles['progressTrack']}
                role="progressbar"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={progress}
                aria-label="업로드 진행률"
              >
                <div
                  className={styles['progressFill']}
                  style={{ width: `${progress}%` }}
                />
              </div>
              <div className={styles['progressPct']}>{progress}%</div>
              {selectedFile ? (
                <div className={styles['uploadingFileMeta']}>
                  {selectedFile.name} ({formatSize(selectedFile.size)})
                </div>
              ) : null}
            </div>
          ) : null}

          {/* ── 단계 3: 결과 표시 ───────────────────────────── */}
          {step === 'result' && result ? (
            <div className={styles['resultState']}>
              <div className={styles['resultStats']}>
                <div className={styles['statBox']}>
                  <div className={styles['statValue']}>{result.inserted.toLocaleString()}</div>
                  <div className={styles['statLabel']}>신규</div>
                </div>
                <div className={styles['statBox']}>
                  <div className={styles['statValue']}>{result.updated.toLocaleString()}</div>
                  <div className={styles['statLabel']}>갱신</div>
                </div>
                {typeof result.skipped === 'number' ? (
                  <div className={styles['statBox']}>
                    <div className={styles['statValue']}>{result.skipped.toLocaleString()}</div>
                    <div className={styles['statLabel']}>건너뜀</div>
                  </div>
                ) : null}
                <div className={`${styles['statBox']} ${styles['statBoxRejected']}`}>
                  <div className={styles['statValue']}>
                    {result.rejected.length.toLocaleString()}
                  </div>
                  <div className={styles['statLabel']}>거부</div>
                </div>
              </div>

              <div className={styles['resultSummary']}>
                신규 {result.inserted.toLocaleString()}건
                {' / '}
                갱신 {result.updated.toLocaleString()}건
                {typeof result.skipped === 'number'
                  ? ` / 건너뜀 ${result.skipped.toLocaleString()}건`
                  : ''}
                {result.rejected.length > 0
                  ? ` / 거부 ${result.rejected.length.toLocaleString()}건`
                  : ''}
              </div>

              {result.rejected.length > 0 ? (
                <div className={styles['rejectedSection']}>
                  <div className={styles['rejectedHeader']}>
                    <div className={styles['rejectedTitle']}>
                      다음 {result.rejected.length.toLocaleString()}건이 거부되었습니다
                    </div>
                    <Button variant="secondary" size="sm" onClick={handleDownloadRejected}>
                      거부 보고서 다운로드
                    </Button>
                  </div>
                  <div className={styles['rejectedTableWrap']}>
                    <table className={styles['rejectedTable']}>
                      <thead>
                        <tr>
                          <th scope="col" className={styles['rejectedRowNum']}>
                            행번호
                          </th>
                          <th scope="col">입력 데이터</th>
                          <th scope="col">거부 사유</th>
                        </tr>
                      </thead>
                      <tbody>
                        {result.rejected.map((r) => (
                          <tr key={r.rowNumber}>
                            <td className={styles['rejectedRowNum']}>{r.rowNumber}</td>
                            <td className={styles['rejectedInputCell']}>
                              {Object.entries(r.inputData)
                                .map(([k, v]) => `${k}: ${v}`)
                                .join(' · ')}
                            </td>
                            <td className={styles['rejectedReasonCell']}>{r.reason}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                </div>
              ) : (
                <div className={styles['successBanner']} role="status">
                  <span aria-hidden="true">✓</span> 모든 행이 정상 처리되었습니다
                </div>
              )}
            </div>
          ) : null}
        </div>

        <footer className={styles['footer']}>
          {step === 'select' ? (
            <>
              <Button variant="ghost" onClick={onClose}>
                닫기
              </Button>
              <Button
                variant="primary"
                onClick={handleStartUpload}
                disabled={!selectedFile || !!validationError}
              >
                업로드
              </Button>
            </>
          ) : null}
          {step === 'uploading' ? (
            <Button variant="ghost" onClick={handleCancelUpload}>
              취소
            </Button>
          ) : null}
          {step === 'result' ? (
            <>
              <Button variant="ghost" onClick={handleRetry}>
                다시 업로드
              </Button>
              <Button variant="primary" onClick={onClose}>
                닫기
              </Button>
            </>
          ) : null}
        </footer>
      </div>
    </div>,
    document.body,
  )
}

export default CsvUploadDialog
