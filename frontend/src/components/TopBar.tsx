import type { ReactNode } from 'react'
import { useNavigate } from 'react-router-dom'
import { IconArrowLeft, IconChevronLeft, IconX } from '@tabler/icons-react'
import styles from './TopBar.module.css'

type Props = {
  title?: string
  /** 'back' = chevron, 'arrow' = long arrow, 'close' = X, 'none' = no left control */
  left?: 'back' | 'arrow' | 'close' | 'none'
  /** where the left control navigates; default is history back */
  to?: string
  right?: ReactNode
  tone?: 'light' | 'dark'
}

/** Screen header: left control, centered title, optional right slot. */
export function TopBar({ title, left = 'back', to, right, tone = 'dark' }: Props) {
  const navigate = useNavigate()
  const go = () => (to ? navigate(to) : navigate(-1))
  return (
    <div className={`${styles.bar} ${tone === 'light' ? styles.light : ''}`}>
      <div className={styles.slot}>
        {left !== 'none' && (
          <button type="button" className={styles.btn} onClick={go} aria-label={left === 'close' ? '閉じる' : '戻る'}>
            {left === 'close' ? (
              <IconX size={22} stroke={2} />
            ) : left === 'arrow' ? (
              <IconArrowLeft size={24} stroke={2} />
            ) : (
              <IconChevronLeft size={24} stroke={2} />
            )}
          </button>
        )}
      </div>
      {title ? <h1 className={styles.title}>{title}</h1> : <span className={styles.spacer} />}
      <div className={`${styles.slot} ${styles.right}`}>{right}</div>
    </div>
  )
}
