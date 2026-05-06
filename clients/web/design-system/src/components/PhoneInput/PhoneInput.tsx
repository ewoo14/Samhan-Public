/**
 * PhoneInput — 한국 휴대폰 번호 전용 입력 컴포넌트.
 *
 * link-dispatch-slice (LinkDispatchListPage) 의 기사 연락처 입력에 사용된다.
 * Designer `components.md` 의 PhoneInput spec 그대로 반영.
 *
 * 주요 동작:
 * - 사용자가 입력한 숫자에 자동으로 하이픈을 삽입한다 (010-XXXX-XXXX).
 *   예) "01012345678" → "010-1234-5678".
 *   3-4-4 또는 3-3-4 분절을 자동 결정 (숫자 10/11 자리에 따라).
 * - 입력 가능 최대 길이 13자 (하이픈 2개 포함).
 * - error prop 으로 외부 검증 메시지 표시. 자체 검증은 PATTERN export 만 제공.
 * - FormField 호환을 위해 label / helperText / required 를 위임 받는다.
 * - 숫자/하이픈 외 모든 문자는 입력 시 즉시 제거.
 *
 * UUID 비공개 가드: 본 컴포넌트는 사용자 식별자 미노출, 단순 PII (전화번호) 만 다룸.
 */
import { forwardRef, useCallback, type ChangeEvent, type InputHTMLAttributes } from 'react'
import styles from './PhoneInput.module.css'
import { Label } from '../Label/Label'

/** 한국 휴대폰 번호 패턴 — 010 / 011 / 016 / 017 / 018 / 019 모두 허용. */
export const KOREAN_MOBILE_PHONE_PATTERN = /^01[016789]-\d{3,4}-\d{4}$/

/**
 * 숫자만 남긴 문자열에 하이픈을 자동 삽입한다.
 *
 * 규칙:
 * - 0-3자리 → 그대로 (010)
 * - 4-7자리 → 010-XXX[X] (3-3 또는 3-4)
 * - 8-10자리 → 010-XXX-XXXX (3-3-4)
 * - 11자리 → 010-XXXX-XXXX (3-4-4)
 *
 * @param raw 사용자 입력 (숫자/하이픈 혼합 가능)
 * @return 하이픈 자동 삽입된 정규화 문자열 (최대 13자)
 */
export function formatKoreanMobilePhone(raw: string): string {
  const digits = raw.replace(/\D/g, '').slice(0, 11)
  if (digits.length <= 3) return digits
  if (digits.length <= 7) {
    // 010-1234 (4자리 중간) 또는 010-123 (3자리 중간)
    return `${digits.slice(0, 3)}-${digits.slice(3)}`
  }
  if (digits.length <= 10) {
    // 010-123-4567 (3-3-4)
    return `${digits.slice(0, 3)}-${digits.slice(3, 6)}-${digits.slice(6)}`
  }
  // 010-1234-5678 (3-4-4)
  return `${digits.slice(0, 3)}-${digits.slice(3, 7)}-${digits.slice(7, 11)}`
}

export interface PhoneInputProps
  extends Omit<InputHTMLAttributes<HTMLInputElement>, 'value' | 'onChange' | 'size' | 'type'> {
  /** 전화번호 문자열 (외부 상태로 관리 — controlled). */
  value: string
  /** 정규화된 새 값 (자동 하이픈 적용 후) — 부모 onChange. */
  onChange: (next: string) => void
  /** 외부 검증 메시지. 표시 시 input border 가 danger 색으로 변환된다. */
  error?: string
  /** 라벨 텍스트 (선택) — 미지정 시 라벨 미표시. */
  label?: string
  /** Hint/helper text — 사용자 안내 (예: "예: 010-1234-5678"). */
  helperText?: string
  /** required 여부 — 라벨 우측 빨강 별표. */
  required?: boolean
  /** placeholder 텍스트 — 미지정 시 "010-0000-0000". */
  placeholder?: string
  /** name attribute — form submit 시 사용. */
  name?: string
}

/**
 * 한국 휴대폰 번호 입력 — 자동 하이픈 + 13자 제한.
 *
 * 외부 검증은 부모가 KOREAN_MOBILE_PHONE_PATTERN.test(value) 로 수행.
 */
export const PhoneInput = forwardRef<HTMLInputElement, PhoneInputProps>(function PhoneInput(
  {
    id,
    value,
    onChange,
    error,
    label,
    helperText,
    required,
    placeholder = '010-0000-0000',
    name,
    disabled,
    className,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const reactId = id ?? `ds-phone-${name ?? 'input'}`
  const helperId = helperText ? `${reactId}-helper` : undefined
  const errorId = error ? `${reactId}-error` : undefined
  const describedBy = [ariaDescribedBy, helperId, errorId].filter(Boolean).join(' ') || undefined

  const handleChange = useCallback(
    (e: ChangeEvent<HTMLInputElement>) => {
      const formatted = formatKoreanMobilePhone(e.target.value)
      onChange(formatted)
    },
    [onChange],
  )

  const wrapperClasses = [styles['wrapper'], className].filter(Boolean).join(' ')
  const inputClasses = [styles['input'], error ? styles['hasError'] : null]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={wrapperClasses}>
      {label ? (
        <Label htmlFor={reactId} required={required}>
          {label}
        </Label>
      ) : null}
      <input
        ref={ref}
        id={reactId}
        type="tel"
        inputMode="numeric"
        autoComplete="tel"
        name={name}
        value={value}
        onChange={handleChange}
        placeholder={placeholder}
        maxLength={13}
        disabled={disabled}
        required={required}
        className={inputClasses}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        aria-required={required || undefined}
        {...rest}
      />
      {helperText && !error ? (
        <span id={helperId} className={styles['helper']}>
          {helperText}
        </span>
      ) : null}
      {error ? (
        <span id={errorId} className={styles['error']} role="alert">
          {error}
        </span>
      ) : null}
    </div>
  )
})

export default PhoneInput
