import { forwardRef, type CSSProperties, type HTMLAttributes } from 'react'
import styles from './Card.module.css'
import type { ShadowKey, SpacingKey } from '../../tokens'

export interface CardProps extends HTMLAttributes<HTMLDivElement> {
  /** Padding spacing token key. Default 4 (16px). */
  padding?: SpacingKey
  /** Shadow token key, or 'none'. Default 'sm'. */
  shadow?: ShadowKey | 'none'
  /** Visual variant. */
  variant?: 'elevated' | 'outlined' | 'plain'
  /** Render as a different element (e.g. 'section', 'article'). */
  as?: 'div' | 'section' | 'article' | 'aside'
}

export const Card = forwardRef<HTMLDivElement, CardProps>(function Card(
  {
    padding = 4,
    shadow = 'sm',
    variant = 'elevated',
    as: Tag = 'div',
    className,
    style,
    children,
    ...rest
  },
  ref,
) {
  const classes = [
    styles['card'],
    styles[`variant-${variant}`],
    className,
  ]
    .filter(Boolean)
    .join(' ')

  const composedStyle = {
    ...style,
    padding: `var(--space-${padding})`,
    boxShadow: shadow === 'none' ? 'none' : `var(--shadow-${shadow})`,
  } as CSSProperties

  return (
    <Tag ref={ref as never} className={classes} style={composedStyle} {...rest}>
      {children}
    </Tag>
  )
})

export default Card
