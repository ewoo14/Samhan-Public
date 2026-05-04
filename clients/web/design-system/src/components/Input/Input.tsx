import { forwardRef, useId, type InputHTMLAttributes } from 'react'
import styles from './Input.module.css'
import { Label } from '../Label/Label'

export type InputSize = 'sm' | 'md' | 'lg'

export interface InputProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string
  hint?: string
  error?: string
  required?: boolean
  /** Visual size. Default 'md'. */
  inputSize?: InputSize
  /** Render the input full-width within its container. Default true. */
  fullWidth?: boolean
}

const sizeClass: Record<InputSize, string> = {
  sm: styles['size-sm']!,
  md: styles['size-md']!,
  lg: styles['size-lg']!,
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  {
    id,
    label,
    hint,
    error,
    required,
    inputSize = 'md',
    fullWidth = true,
    className,
    'aria-describedby': ariaDescribedBy,
    ...rest
  },
  ref,
) {
  const reactId = useId()
  const fieldId = id ?? `ds-input-${reactId}`
  const hintId = hint ? `${fieldId}-hint` : undefined
  const errorId = error ? `${fieldId}-error` : undefined

  const describedBy = [ariaDescribedBy, hintId, errorId].filter(Boolean).join(' ') || undefined

  const wrapperClasses = [
    styles['wrapper'],
    fullWidth ? styles['fullWidth'] : null,
    className,
  ]
    .filter(Boolean)
    .join(' ')

  const inputClasses = [
    styles['input'],
    sizeClass[inputSize],
    error ? styles['hasError'] : null,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <div className={wrapperClasses}>
      {label ? (
        <Label htmlFor={fieldId} required={required}>
          {label}
        </Label>
      ) : null}
      <input
        ref={ref}
        id={fieldId}
        className={inputClasses}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy}
        aria-required={required || undefined}
        required={required}
        {...rest}
      />
      {hint && !error ? (
        <span id={hintId} className={styles['hint']}>
          {hint}
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

export default Input
