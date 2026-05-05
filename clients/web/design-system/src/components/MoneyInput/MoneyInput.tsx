import { forwardRef, useId, type ChangeEvent } from 'react'
import styles from './MoneyInput.module.css'

/**
 * 금액 입력 컴포넌트 (KRW 전용, accounting-slice-A 신규).
 *
 * PriceField 와의 차이점:
 * - PriceField: BigDecimal 호환 string 입출력 (전표 단가 / 통화 prefix 변경 가능)
 * - MoneyInput: integer (number) 입출력 + KRW 고정 (회계 분개 차변/대변 셀)
 *
 * 분개는 원화 정수 단위로만 입력되므로 (BE BigDecimal scale 0) `number` 가 더
 * 자연스러운 표현이며, 셀 안에서 표시는 천단위 콤마로 자동 포맷.
 *
 * 회계 가드:
 * - 음수 차단 (회계는 차변/대변으로 부호 표현)
 * - 소수 차단 (KRW 정수)
 * - 빈 입력 시 onChange 에 `0` 전달 (NaN 방지)
 */
export interface MoneyInputProps {
  /** 현재 금액 (KRW 정수). 빈 셀은 `0` 으로 표현. */
  value: number
  /** 변경 시 호출. NaN 은 절대 전달되지 않으며, 빈 입력은 `0`. */
  onChange: (next: number) => void
  /** placeholder — 입력 비어있을 때만 표시 (value=0 이어도 "0" 으로 표시되지 않음). */
  placeholder?: string
  /** 비활성화. 회계 마감(POSTED) 셀에서 `true`. */
  disabled?: boolean
  /** 최대값 — 초과 입력 시 max 로 잘라냄. */
  max?: number
  /** 최소값 — 이하 입력 시 min 으로 올림. 기본 `0` (음수 차단). */
  min?: number
  /** 에러 메시지 — 있으면 빨간 outline + 메시지. */
  error?: string
  className?: string
  ariaLabel?: string
}

/** 정수 → 표시용 천단위 콤마 변환. `0` 은 빈 문자열로 (placeholder 노출). */
const formatDisplay = (n: number): string => {
  if (!Number.isFinite(n) || n === 0) return ''
  return Math.trunc(n)
    .toString()
    .replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 사용자 입력 → 정수. NaN 시 `0`. */
const parseInput = (input: string): number => {
  const stripped = input.replace(/[\s,]/g, '').replace(/[^0-9]/g, '')
  if (!stripped) return 0
  const n = Number.parseInt(stripped, 10)
  return Number.isFinite(n) ? n : 0
}

/**
 * MoneyInput — KRW 정수 입력. 자동 콤마, 음수/소수 차단.
 *
 * @example
 * ```tsx
 * <MoneyInput
 *   value={debit}
 *   onChange={setDebit}
 *   placeholder="0"
 *   max={9_999_999_999}
 * />
 * ```
 */
export const MoneyInput = forwardRef<HTMLInputElement, MoneyInputProps>(
  function MoneyInput(
    {
      value,
      onChange,
      placeholder = '0',
      disabled = false,
      max,
      min = 0,
      error,
      className,
      ariaLabel,
    },
    ref,
  ) {
    const reactId = useId()
    const fieldId = `ds-money-${reactId}`
    const errorId = error ? `${fieldId}-error` : undefined

    const display = formatDisplay(value)

    const handleChange = (e: ChangeEvent<HTMLInputElement>) => {
      let next = parseInput(e.target.value)
      if (next < min) next = min
      if (typeof max === 'number' && next > max) next = max
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
            ₩
          </span>
          <input
            ref={ref}
            id={fieldId}
            type="text"
            inputMode="numeric"
            className={styles['input']}
            value={display}
            onChange={handleChange}
            placeholder={placeholder}
            disabled={disabled}
            aria-label={ariaLabel ?? '금액 (KRW)'}
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

export default MoneyInput
