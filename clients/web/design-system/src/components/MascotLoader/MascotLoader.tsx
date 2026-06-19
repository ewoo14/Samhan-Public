import { forwardRef, type CSSProperties, type HTMLAttributes } from 'react'
import samhaniStatic from '../../assets/mascot/samhani-static.png'
import samhaniWebp from '../../assets/mascot/samhani.webp'
import styles from './MascotLoader.module.css'

export type MascotLoaderSize = 'sm' | 'md' | 'lg'

const sizePx: Record<MascotLoaderSize, number> = {
  sm: 48,
  md: 80,
  lg: 120,
}

export interface MascotLoaderProps extends Omit<HTMLAttributes<HTMLDivElement>, 'children'> {
  /** Accessible label and visible loading text. */
  label?: string
  /** Mascot image size. Default 'md'. */
  size?: MascotLoaderSize
}

export const MascotLoader = forwardRef<HTMLDivElement, MascotLoaderProps>(function MascotLoader(
  { label = '불러오는 중', size = 'md', className, style, ...rest },
  ref,
) {
  const px = sizePx[size]
  const classes = [styles['loader'], className].filter(Boolean).join(' ')

  return (
    <div
      ref={ref}
      role="status"
      aria-label={label}
      className={classes}
      style={{ '--mascot-loader-size': `${px}px`, ...style } as CSSProperties}
      {...rest}
    >
      <img
        className={[styles['image'], styles['animated']].join(' ')}
        src={samhaniWebp}
        alt=""
        aria-hidden="true"
        draggable={false}
      />
      <img
        className={[styles['image'], styles['static']].join(' ')}
        src={samhaniStatic}
        alt=""
        aria-hidden="true"
        draggable={false}
      />
      <span className={styles['label']} aria-hidden="true">
        {label}
      </span>
    </div>
  )
})

export default MascotLoader
