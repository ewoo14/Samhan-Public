import { type HTMLAttributes, type ReactNode } from 'react'
import samhaniStatic from '../../assets/mascot/samhani-static.png'
import styles from './MascotEmptyState.module.css'

export interface MascotEmptyStateProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
  title: string
  description?: string
  action?: ReactNode
}

export function MascotEmptyState({
  title,
  description,
  action,
  className,
  ...rest
}: MascotEmptyStateProps) {
  const classes = [styles['emptyState'], className].filter(Boolean).join(' ')

  return (
    <div className={classes} {...rest}>
      <img
        className={styles['image']}
        src={samhaniStatic}
        alt=""
        aria-hidden="true"
        draggable={false}
      />
      <div className={styles['copy']}>
        <strong className={styles['title']}>{title}</strong>
        {description ? <p className={styles['description']}>{description}</p> : null}
      </div>
      {action ? <div className={styles['action']}>{action}</div> : null}
    </div>
  )
}

export default MascotEmptyState
