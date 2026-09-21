import type { CSSProperties, ReactNode } from 'react'
import styles from './Screen.module.css'

type Props = { children: ReactNode; className?: string; style?: CSSProperties }

/** Full-height column that every screen renders into. */
export function Screen({ children, className, style }: Props) {
  return (
    <div className={[styles.screen, className].filter(Boolean).join(' ')} style={style}>
      {children}
    </div>
  )
}
