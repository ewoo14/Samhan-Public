import { forwardRef, type SVGAttributes } from 'react'
import styles from './Spinner.module.css'

export type SpinnerSize = 'xs' | 'sm' | 'md' | 'lg'

const sizePx: Record<SpinnerSize, number> = {
  xs: 12,
  sm: 16,
  md: 24,
  lg: 32,
}

export interface SpinnerProps extends Omit<SVGAttributes<SVGSVGElement>, 'children'> {
  /** Size token. Default 'md'. */
  size?: SpinnerSize
  /**
   * Color value. Defaults to currentColor so the spinner inherits text color.
   * Pass a token reference like `var(--color-brand-500)` for explicit color.
   */
  tone?: string
  /** Accessible label for screen readers (visually hidden). */
  label?: string
}

export const Spinner = forwardRef<SVGSVGElement, SpinnerProps>(function Spinner(
  { size = 'md', tone = 'currentColor', label = '로딩 중', className, ...rest },
  ref,
) {
  const px = sizePx[size]
  const classes = [styles['spinner'], className].filter(Boolean).join(' ')

  return (
    <svg
      ref={ref}
      role="status"
      aria-label={label}
      className={classes}
      width={px}
      height={px}
      viewBox="0 0 24 24"
      xmlns="http://www.w3.org/2000/svg"
      {...rest}
    >
      <circle
        cx="12"
        cy="12"
        r="10"
        fill="none"
        stroke={tone}
        strokeOpacity="0.18"
        strokeWidth="3"
      />
      <path
        d="M22 12a10 10 0 0 0-10-10"
        fill="none"
        stroke={tone}
        strokeWidth="3"
        strokeLinecap="round"
      />
    </svg>
  )
})

export default Spinner
