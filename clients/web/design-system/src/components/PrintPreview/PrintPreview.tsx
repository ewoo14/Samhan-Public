import { useCallback, type ReactNode } from 'react'
import styles from './PrintPreview.module.css'

/**
 * 인쇄 미리보기 wrapper — react-pdf 또는 브라우저 print 분기.
 *
 * Legacy migration 사전 작업 (DS 6 신규 컴포넌트 중 6번).
 *
 * F3 결정 (DECISIONS.md): **react-pdf 채택** + fallback 브라우저 print.
 * F1 하이브리드: 인쇄 양식 영역만 legacy CSS 보존, 외곽은 SamhanLogis DS 사용.
 *
 * 본 컴포넌트는 design-system 패키지에서 react-pdf 의존성을 직접 import 하지 않는다 —
 * peerDep 회피 + 번들 크기 가드. `mode='pdf'` 이고 `pdfRenderer` prop 미주입 시 자동
 * 브라우저 print fallback 으로 전환.
 *
 * F5: Sub-team A 의 EstimatePrintRenderer / SlipPrintRenderer 가 본 PrintPreview 의
 * children 으로 들어가는 패턴 (estimate-service Frontend / slip-service Frontend).
 *
 * `feedback_print_design_iteration.md` 가드: 인쇄 양식은 단번 완성 가정 금지 — 본 wrapper
 * 는 외부 레이아웃만 책임지고, 실제 인쇄 양식 디자인은 Sub-team A QA iteration (Edge
 * 캡처 → CSS 미세 조정 3~5회) 으로 정정. 본 컴포넌트의 paperSize/orientation token 은
 * 그 iteration 의 시작점.
 *
 * 사용처:
 * - estimate-service Frontend EstimatePrintPreviewPage
 * - slip-service Frontend SlipPrintPage
 * - partner-order-service Frontend PartnerOrderPrintPage
 *
 * 출처: `migration/analysis/06-frontend-design.md` §3.2 / DECISIONS.md F3
 */
export type PrintMode = 'pdf' | 'browser'
export type PrintPaperSize = 'A4' | 'A3' | 'A5'
export type PrintOrientation = 'portrait' | 'landscape'

export interface PrintPreviewProps {
  /**
   * 인쇄 모드:
   * - `'pdf'` (기본): react-pdf 렌더 (별도 `pdfRenderer` 주입 필요)
   * - `'browser'`: 브라우저 window.print() — `@media print` CSS 의무
   * 미주입 시 자동 'pdf'.
   */
  mode?: PrintMode
  /** 미리보기 본문 — EstimatePrintRenderer 등 (F5). */
  children: ReactNode
  /** 용지 크기. 기본 'A4'. */
  paperSize?: PrintPaperSize
  /** 방향. 기본 'portrait'. */
  orientation?: PrintOrientation
  /**
   * `mode='pdf'` 사용 시 외부에서 react-pdf 의 PDFViewer 등으로 children 을 감싸는 함수.
   * 미주입 시 mode='pdf' 라도 브라우저 fallback 으로 동작.
   *
   * @example
   * ```tsx
   * import { PDFViewer } from '@react-pdf/renderer'
   * <PrintPreview pdfRenderer={(node) => <PDFViewer>{node}</PDFViewer>}>
   *   <EstimatePdfDoc estimate={...} />
   * </PrintPreview>
   * ```
   */
  pdfRenderer?: (node: ReactNode) => ReactNode
  /** "인쇄" 버튼 표시 여부. 기본 true. */
  showPrintButton?: boolean
  /** "인쇄" 버튼 클릭 시 콜백. 미주입 시 mode='browser' 는 window.print() 자동 호출. */
  onPrint?: () => void
  /** 추가 className */
  className?: string
}

/**
 * paperSize × orientation → CSS width/height (mm 단위).
 * react-pdf 사용 시에는 PDF 라이브러리가 자체 렌더링 → 본 size 는 외곽 wrapper 표시 용.
 */
function paperDimensions(
  size: PrintPaperSize,
  orientation: PrintOrientation,
): { width: string; height: string } {
  const dim: Record<PrintPaperSize, { w: number; h: number }> = {
    A3: { w: 297, h: 420 },
    A4: { w: 210, h: 297 },
    A5: { w: 148, h: 210 },
  }
  const { w, h } = dim[size]
  if (orientation === 'landscape') {
    return { width: `${h}mm`, height: `${w}mm` }
  }
  return { width: `${w}mm`, height: `${h}mm` }
}

/**
 * PrintPreview — 인쇄 미리보기 wrapper (react-pdf 또는 브라우저 print).
 *
 * @param props mode / paperSize / orientation / pdfRenderer / children
 * @example
 * ```tsx
 * // 1) react-pdf 모드
 * <PrintPreview mode="pdf" pdfRenderer={(n) => <PDFViewer>{n}</PDFViewer>}>
 *   <MyPdfDoc />
 * </PrintPreview>
 *
 * // 2) 브라우저 print 모드 (CSS @media print 의무)
 * <PrintPreview mode="browser" paperSize="A4" orientation="portrait">
 *   <EstimatePrintRenderer estimate={...} />
 * </PrintPreview>
 * ```
 */
export function PrintPreview({
  mode = 'pdf',
  children,
  paperSize = 'A4',
  orientation = 'portrait',
  pdfRenderer,
  showPrintButton = true,
  onPrint,
  className,
}: PrintPreviewProps) {
  // pdf 모드인데 renderer 미주입 시 자동 fallback
  const effectiveMode: PrintMode = mode === 'pdf' && !pdfRenderer ? 'browser' : mode
  const fellBack = mode === 'pdf' && !pdfRenderer
  const { width, height } = paperDimensions(paperSize, orientation)

  const handlePrint = useCallback(() => {
    if (onPrint) {
      onPrint()
      return
    }
    if (effectiveMode === 'browser' && typeof window !== 'undefined') {
      window.print()
    }
  }, [onPrint, effectiveMode])

  return (
    <div
      className={[styles['root'], className].filter(Boolean).join(' ')}
      data-mode={effectiveMode}
      data-paper={paperSize}
      data-orientation={orientation}
    >
      {showPrintButton ? (
        <div className={styles['toolbar']}>
          <span className={styles['meta']}>
            {paperSize} · {orientation === 'portrait' ? '세로' : '가로'} ·{' '}
            {effectiveMode === 'pdf' ? 'PDF' : '브라우저 인쇄'}
            {fellBack ? ' (fallback)' : ''}
          </span>
          <button
            type="button"
            className={styles['printBtn']}
            onClick={handlePrint}
            aria-label="인쇄"
          >
            인쇄
          </button>
        </div>
      ) : null}

      <div className={styles['stage']}>
        {effectiveMode === 'pdf' && pdfRenderer ? (
          <div className={styles['pdfFrame']}>{pdfRenderer(children)}</div>
        ) : (
          <div
            className={styles['paper']}
            style={{ width, height }}
            data-printable="true"
          >
            {children}
          </div>
        )}
      </div>
    </div>
  )
}

export default PrintPreview
