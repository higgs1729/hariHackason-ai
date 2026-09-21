import { IconDots } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './CapsuleCreate.module.css'

export function CapsuleCreate() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar
          title="タイムカプセル"
          to={routes.share}
          tone="light"
          right={
            <button type="button" className={styles.more} aria-label="その他のオプション">
              <IconDots size={24} stroke={1.8} />
            </button>
          }
        />
      </header>

      <Photo asset="skySunset" className={styles.sky}>
        <main className={styles.card}>
          <h1>1年後の自分へ</h1>
          <p className={styles.message}>
            この思い出を、<br />
            未来の自分に届けよう。
          </p>
          <div className={styles.divider} aria-hidden="true" />
          <p className={styles.date}>2026.09.20</p>
          <button type="button" className={styles.primary} onClick={() => navigate(routes.capsuleDone)}>
            タイムカプセルを作成する
          </button>
        </main>
      </Photo>
    </Screen>
  )
}
