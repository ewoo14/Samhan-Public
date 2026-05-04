/**
 * `<StockBalanceModal>` — 재고 조회 모달 (sales-form-polish 슬라이스 신규).
 *
 * Designer `components.md` § 2 + `wireframes.md` § 2 spec 충실 반영:
 * - max-width 720px / max-height 80vh
 * - overlay rgba(0,0,0,0.6)
 * - 상단 선택 품목 요약 + 모델명 × 창고 matrix 표 + 합계 컬럼
 * - 셀 렌더 규칙: > 0 일반, = 0 dim, null '-' dim
 * - 가상 창고 컬럼은 thead 에 italic + dim 표기 (재고 차감 대상 외)
 * - focus trap + Esc 닫기 + body scroll lock + backdrop click 닫기
 *
 * UUID 비공개: 응답 row 의 productId 는 React key 로만 사용, 화면 미노출.
 *
 * 본 컴포넌트는 BE 데이터 페칭 책임을 지지 않는다 — 호출자가 `rows` / `error`
 * prop 으로 결과를 주입. (페치는 SlipFormPage 가 useMutation 으로 수행)
 */
import {
  useCallback,
  useEffect,
  useId,
  useRef,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react'
import { createPortal } from 'react-dom'
import styles from './StockBalanceModal.module.css'
import { Button } from '../Button/Button'
import { Spinner } from '../Spinner/Spinner'

/**
 * 백엔드 `POST /inventory/balances/batch` 응답 row 1건.
 *
 * - `perWarehouse`: 창고 코드 → 수량 (재고 0 이면 0, 가상창고는 null)
 * - `total`: 가상창고 제외 합계
 */
export interface StockBalanceRow {
  productId: string
  modelName: string
  productName: string
  /** 창고 코드 → 재고 수량. null 은 데이터 없음 (가상창고 등). */
  perWarehouse: Record<string, number | null>
  /** 합계 (가상창고 제외). */
  total: number
}

/**
 * 창고 컬럼 메타. 헤더 표시명 + 가상창고 여부 (italic + dim 표기용).
 */
export interface WarehouseColumn {
  /** 창고 코드 (perWarehouse 키와 일치). */
  code: string
  /** 헤더 표시 라벨 (예: '본사', '차량1'). */
  label: string
  /** 가상창고 여부 — true 면 thead italic + 행 cell 도 모두 dim. */
  virtual?: boolean
}

export interface StockBalanceModalProps {
  /** 모달 open 여부. */
  open: boolean
  /** 닫기 콜백 (× / overlay click / Esc). */
  onClose: () => void
  /** 조회 대상 라인 (선택된 항목들). 상단 요약에 표시. */
  selectedLines: Array<{
    productId: string
    modelName: string
    productName: string
  }>
  /** 표 컬럼 정의 — 좌측 모델명 셀 외의 창고 컬럼들. */
  warehouseColumns: WarehouseColumn[]
  /** 백엔드 응답 — null 이면 로딩 중, [] 이면 데이터 없음. */
  rows: StockBalanceRow[] | null
  /** 조회 실패 시 에러 메시지. */
  error?: string | null
}

const FOCUSABLE_SELECTOR = [
  'a[href]',
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

/**
 * 셀 렌더 (Designer components.md § 2.5 인용).
 */
function StockCell({ value, dim }: { value: number | null; dim?: boolean }) {
  if (value === null) {
    return <td className={`${styles['num']} ${styles['dim']}`}>-</td>
  }
  if (value === 0) {
    return <td className={`${styles['num']} ${styles['dim']}`}>0</td>
  }
  return (
    <td className={`${styles['num']}${dim ? ' ' + styles['dim'] : ''}`}>
      {value.toLocaleString()}
    </td>
  )
}

export function StockBalanceModal({
  open,
  onClose,
  selectedLines,
  warehouseColumns,
  rows,
  error,
}: StockBalanceModalProps) {
  const dialogRef = useRef<HTMLDivElement | null>(null)
  const previouslyFocusedRef = useRef<HTMLElement | null>(null)
  const reactId = useId()
  const titleId = `stock-modal-title-${reactId}`

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

  // ESC handler
  useEffect(() => {
    if (!open) return
    const handler = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.stopPropagation()
        onClose()
      }
    }
    document.addEventListener('keydown', handler)
    return () => document.removeEventListener('keydown', handler)
  }, [open, onClose])

  const handleBackdropClick = useCallback(
    (e: ReactMouseEvent<HTMLDivElement>) => {
      if (e.target === e.currentTarget) onClose()
    },
    [onClose],
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

  if (!open || typeof document === 'undefined') return null

  const isLoading = rows === null && !error
  const isEmpty = rows !== null && rows.length === 0 && !error

  return createPortal(
    <div className={styles['backdrop']} onMouseDown={handleBackdropClick}>
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className={styles['dialog']}
        onKeyDown={handleKeyDown}
      >
        <header className={styles['header']}>
          <h2 id={titleId} className={styles['title']}>
            재고 조회
          </h2>
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
        </header>

        <div className={styles['body']}>
          {/* 선택 품목 요약 */}
          <div className={styles['summary']}>
            <div className={styles['summaryLabel']}>
              선택 품목 ({selectedLines.length}건)
            </div>
            <ul className={styles['summaryList']}>
              {selectedLines.map((l) => (
                <li key={l.productId} className={styles['summaryItem']}>
                  <span className={styles['summaryModelName']}>{l.modelName}</span>
                  <span>— {l.productName}</span>
                </li>
              ))}
            </ul>
          </div>

          {error ? (
            <div className={styles['errorBanner']} role="alert">
              <span aria-hidden="true">ⓘ</span> {error}
            </div>
          ) : null}

          {isLoading ? (
            <div className={styles['loadingState']}>
              <Spinner size="md" tone="var(--action-brand)" />
              <span>조회 중...</span>
            </div>
          ) : null}

          {isEmpty ? (
            <div className={styles['emptyState']}>
              <span>재고 데이터가 없습니다</span>
            </div>
          ) : null}

          {rows && rows.length > 0 ? (
            <>
              <table className={styles['table']}>
                <thead>
                  <tr>
                    <th>모델명</th>
                    {warehouseColumns.map((col) => (
                      <th
                        key={col.code}
                        className={col.virtual ? styles['virtualHeader'] : ''}
                        scope="col"
                      >
                        {col.label}
                      </th>
                    ))}
                    <th>합계</th>
                  </tr>
                </thead>
                <tbody>
                  {rows.map((row) => (
                    <tr key={row.productId}>
                      <td className={styles['modelCell']}>{row.modelName}</td>
                      {warehouseColumns.map((col) => {
                        const value = row.perWarehouse[col.code] ?? null
                        return (
                          <StockCell
                            key={col.code}
                            value={value}
                            dim={col.virtual}
                          />
                        )
                      })}
                      <td className={`${styles['num']} ${styles['totalCell']}`}>
                        {row.total.toLocaleString()}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
              <div className={styles['footnote']}>
                <span>• 가상창고는 재고 차감 대상 외 (회색 dash)</span>
                <span>• 0 인 항목도 표시</span>
              </div>
            </>
          ) : null}
        </div>

        <footer className={styles['footer']}>
          <Button variant="primary" onClick={onClose}>
            닫기
          </Button>
        </footer>
      </div>
    </div>,
    document.body,
  )
}

export default StockBalanceModal
