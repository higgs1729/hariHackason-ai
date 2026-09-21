import { IconAntennaBars5, IconBatteryFilled, IconWifi } from '@tabler/icons-react'
import styles from './StatusBar.module.css'

type Props = { tone?: 'light' | 'dark' }

/** iOS-style status bar at the top of every screen (the poster shows 9:41). */
export function StatusBar({ tone = 'dark' }: Props) {
  return (
    <div className={`${styles.bar} ${tone === 'light' ? styles.light : ''}`} aria-hidden="true">
      <span>9:41</span>
      <span className={styles.icons}>
        <IconAntennaBars5 size={16} stroke={2} />
        <IconWifi size={16} stroke={2} />
        <IconBatteryFilled size={18} stroke={2} />
      </span>
    </div>
  )
}
