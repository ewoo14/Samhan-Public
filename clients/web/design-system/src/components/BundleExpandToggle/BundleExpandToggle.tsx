import styles from './BundleExpandToggle.module.css'

/**
 * BUNDLE 품목의 EXPAND / KEEP 모드를 전환하는 segmented 토글 버튼.
 *
 * Legacy migration 사전 작업 (DS 6 신규 컴포넌트 중 2번).
 *
 * 비즈니스 의미 (DOMAIN-EXTENSIONS §2 — bundleMode):
 * - **EXPAND**: 견적/주문 라인에 BUNDLE component 를 펼쳐 개별 라인으로 표시
 *   (사용자가 component 단위로 가격/수량 조정 가능)
 * - **KEEP**: BUNDLE 자체를 단일 라인으로 유지 (component 합산 가격 노출)
 *
 * 사용처:
 * - `clients/desktop` EstimateFormPage / OrderFormPage 에서 BUNDLE 라인 옆 inline toggle
 * - `clients/web/order-app` PartnerOrderDetailPage BUNDLE 라인 옆 inline toggle
 *
 * 출처: `migration/analysis/06-frontend-design.md` §3.2 / §1.2.5
 */
export type BundleExpandMode = 'EXPAND' | 'KEEP'

export interface BundleExpandToggleProps {
  /** 현재 모드 */
  mode: BundleExpandMode
  /** 모드 변경 콜백 */
  onChange: (next: BundleExpandMode) => void
  /** 비활성화 (read-only 라인 등) */
  disabled?: boolean
  /** 추가 className */
  className?: string
  /** 접근성 — 어떤 BUNDLE 인지 식별 (예: `라인 3 BUNDLE 모드`). 기본 "BUNDLE 모드". */
  ariaLabel?: string
}

/**
 * BundleExpandToggle — BUNDLE EXPAND/KEEP segmented toggle.
 *
 * @param props mode + onChange + disabled
 * @example
 * ```tsx
 * <BundleExpandToggle
 *   mode={line.bundleMode}
 *   onChange={(m) => updateBundleMode(line.id, m)}
 *   disabled={readOnly}
 *   ariaLabel={`라인 ${line.no} BUNDLE 모드`}
 * />
 * ```
 */
export function BundleExpandToggle({
  mode,
  onChange,
  disabled = false,
  className,
  ariaLabel = 'BUNDLE 모드',
}: BundleExpandToggleProps) {
  const wrapperClasses = [
    styles['wrapper'],
    disabled ? styles['disabled'] : null,
    className,
  ]
    .filter(Boolean)
    .join(' ')

  const handleClick = (next: BundleExpandMode) => {
    if (disabled || next === mode) return
    onChange(next)
  }

  return (
    <div
      className={wrapperClasses}
      role="group"
      aria-label={ariaLabel}
      data-mode={mode}
    >
      <button
        type="button"
        className={[
          styles['btn'],
          mode === 'EXPAND' ? styles['active'] : null,
        ]
          .filter(Boolean)
          .join(' ')}
        onClick={() => handleClick('EXPAND')}
        disabled={disabled}
        aria-pressed={mode === 'EXPAND'}
        title="BUNDLE component 를 개별 라인으로 펼쳐 표시"
      >
        펼침
      </button>
      <button
        type="button"
        className={[
          styles['btn'],
          mode === 'KEEP' ? styles['active'] : null,
        ]
          .filter(Boolean)
          .join(' ')}
        onClick={() => handleClick('KEEP')}
        disabled={disabled}
        aria-pressed={mode === 'KEEP'}
        title="BUNDLE 단일 라인 유지"
      >
        유지
      </button>
    </div>
  )
}

export default BundleExpandToggle
