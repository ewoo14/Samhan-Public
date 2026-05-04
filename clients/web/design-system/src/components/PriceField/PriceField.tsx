import { forwardRef, useId, type ChangeEvent } from 'react'
import styles from './PriceField.module.css'

export interface PriceFieldProps {
  /**
   * BigDecimal 호환 평문 string. 예: "1234567" 또는 "1234567.00".
   * 표시는 천단위 콤마로 자동 포맷되며, onChange 는 평문 string 으로 회신.
   */
  value: string
  onChange: (next: string) => void
  /** ISO 4217 코드. 기본 'KRW' (₩ prefix). */
  currency?: string
  disabled?: boolean
  /** 에러 메시지 — 있으면 빨간 outline + 메시지. */
  error?: string
  className?: string
  ariaLabel?: string
}

/** 통화 코드 → prefix 심볼 매핑. fallback: 코드 그대로. */
const currencySymbol = (code: string): string => {
  switch (code.toUpperCase()) {
    case 'KRW':
      return '₩'
    case 'USD':
      return '$'
    case 'EUR':
      return '€'
    case 'JPY':
      return '¥'
    case 'GBP':
      return '£'
    case 'CNY':
      return '¥'
    default:
      return code.toUpperCase()
  }
}

/**
 * 평문 → 표시용 천단위 콤마 변환.
 * 소수부는 그대로 보존. 음수 부호는 미리 차단되므로 등장하지 않음.
 */
const formatDisplay = (raw: string): string => {
  if (!raw) return ''
  const [intPart = '', decPart] = raw.split('.')
  const grouped = intPart.replace(/\B(?=(\d{3})+(?!\d))/g, ',')
  return decPart !== undefined ? `${grouped}.${decPart}` : grouped
}

/**
 * 사용자 입력 → 평문 정규화.
 * - 콤마/공백 제거
 * - 음수 부호 제거 (음수 차단)
 * - 숫자/소수점 외 모두 제거
 * - 소수점 2개 이상이면 첫번째만 유지
 */
const normalize = (input: string): string => {
  const stripped = input.replace(/[\s,]/g, '').replace(/-/g, '')
  const cleaned = stripped.replace(/[^0-9.]/g, '')
  const firstDot = cleaned.indexOf('.')
  if (firstDot === -1) return cleaned
  return (
    cleaned.slice(0, firstDot + 1) +
    cleaned.slice(firstDot + 1).replace(/\./g, '')
  )
}

/**
 * PriceField — 통화 prefix + 천단위 콤마 표시 + 음수 차단 입력.
 *
 * 내부 표시값은 콤마 포맷, 외부 onChange 는 BigDecimal 호환 평문 string.
 */
export const PriceField = forwardRef<HTMLInputElement, PriceFieldProps>(
  function PriceField(
    {
      value,
      onChange,
      currency = 'KRW',
      disabled = false,
      error,
      className,
      ariaLabel,
    },
    ref,
  ) {
    const reactId = useId()
    const fieldId = `ds-price-${reactId}`
    const errorId = error ? `${fieldId}-error` : undefined

    const symbol = currencySymbol(currency)
    const display = formatDisplay(value)

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
      const next = normalize(e.target.value)
      onChange(next)
    }

    const wrapperClasses = [styles['wrapper'], className]
      .filter(Boolean)
      .join(' ')

    const fieldClasses = [
      styles['field'],
      disabled ? styles['disabled'] : null,
      error ? styles['hasError'] : null,
    ]
      .filter(Boolean)
      .join(' ')

    return (
      <div className={wrapperClasses}>
        <div className={fieldClasses}>
          <span className={styles['prefix']} aria-hidden="true">
            {symbol}
          </span>
          <input
            ref={ref}
            id={fieldId}
            type="text"
            inputMode="decimal"
            className={styles['input']}
            value={display}
            onChange={handleChange}
            disabled={disabled}
            aria-label={ariaLabel ?? `금액 (${currency})`}
            aria-invalid={error ? true : undefined}
            aria-describedby={errorId}
          />
        </div>
        {error ? (
          <span id={errorId} className={styles['error']} role="alert">
            {error}
          </span>
        ) : null}
      </div>
    )
  },
)

export default PriceField
