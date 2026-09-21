import type { ReactNode } from 'react'
import styles from './PhoneFrame.module.css'

/**
 * On phones the app fills the viewport. On wider viewports it is shown inside
 * a 390×844 frame so the mock looks like the poster. The frame is CSS only;
 * remove this component (and its CSS) to ship without it.
 */
export function PhoneFrame({ children }: { children: ReactNode }) {
  return (
    <div className={styles.stage}>
      <div className={styles.phone}>{children}</div>
    </div>
  )
}
