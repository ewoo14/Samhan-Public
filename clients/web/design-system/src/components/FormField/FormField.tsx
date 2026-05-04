import { useId, type ReactElement, type ReactNode } from 'react'
import styles from './FormField.module.css'
import { Label } from '../Label/Label'

export interface FormFieldRenderArgs {
  /** Field id to attach to the rendered control. */
  id: string
  /** id of the hint element if any. */
  hintId?: string
  /** id of the error element if any. */
  errorId?: string
  /** Combined `aria-describedby` value. */
  ariaDescribedBy?: string
  /** True when the field has an error. */
  invalid: boolean
  /** True when the field is required. */
  required: boolean
}

export interface FormFieldProps {
  /** Visible label text. Required for accessibility unless ariaLabel is supplied to inner control. */
  label: ReactNode
  hint?: ReactNode
  error?: ReactNode
  required?: boolean
  /** Optional explicit id; otherwise generated. */
  id?: string
  /**
   * Render-prop receiving id/aria wiring. Use this to wrap any control
   * (Input, Select, custom) so it inherits the field's a11y attributes.
   */
  render: (args: FormFieldRenderArgs) => ReactElement
  className?: string
}

export function FormField({
  label,
  hint,
  error,
  required = false,
  id,
  render,
  className,
}: FormFieldProps) {
  const reactId = useId()
  const fieldId = id ?? `ds-field-${reactId}`
  const hintId = hint ? `${fieldId}-hint` : undefined
  const errorId = error ? `${fieldId}-error` : undefined
  const ariaDescribedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  const invalid = Boolean(error)

  const wrapperClasses = [styles['field'], className].filter(Boolean).join(' ')

  return (
    <div className={wrapperClasses}>
      <Label htmlFor={fieldId} required={required}>
        {label}
      </Label>
      {render({ id: fieldId, hintId, errorId, ariaDescribedBy, invalid, required })}
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
}

export default FormField
