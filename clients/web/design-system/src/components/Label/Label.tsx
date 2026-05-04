import { forwardRef, type LabelHTMLAttributes } from 'react'
import styles from './Label.module.css'

export interface LabelProps extends LabelHTMLAttributes<HTMLLabelElement> {
  required?: boolean
  /** Visual size. Default 'md'. */
  size?: 'sm' | 'md'
}

export const Label = forwardRef<HTMLLabelElement, LabelProps>(function Label(
  { required = false, size = 'md', className, children, ...rest },
  ref,
) {
  const classes = [
    styles['label'],
    size === 'sm' ? styles['size-sm'] : styles['size-md'],
    className,
  ]
    .filter(Boolean)
    .join(' ')

  return (
    <label ref={ref} className={classes} {...rest}>
      <span>{children}</span>
      {required ? (
        <span className={styles['required']} aria-hidden="true">
          *
        </span>
      ) : null}
      {required ? <span className={styles['srOnly']}>(필수)</span> : null}
    </label>
  )
})

export default Label
