import type { ReactNode } from 'react'
import { IconAlertCircle, IconLoader2 } from '@tabler/icons-react'
import type { ApiError } from '../api'
import { describeError } from '../state/useAsync'
import styles from './Notice.module.css'

/** Inline error line. Renders nothing when there is no error. */
export function ErrorNote({ error, action }: { error: ApiError | null; action?: ReactNode }) {
  if (!error) return null
  return (
    <p className={styles.error} role="alert">
      <IconAlertCircle size={16} stroke={2} aria-hidden="true" />
      <span>{describeError(error)}</span>
      {action}
    </p>
  )
}

/** Centered spinner for a screen that is still loading its data. */
export function Loading({ label = '読み込み中' }: { label?: string }) {
  return (
    <div className={styles.loading} role="status" aria-live="polite">
      <IconLoader2 size={28} stroke={1.8} className={styles.spin} aria-hidden="true" />
      <span>{label}</span>
    </div>
  )
}
