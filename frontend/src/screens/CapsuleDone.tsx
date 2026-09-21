import { IconDots } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './CapsuleDone.module.css'

export function CapsuleDone() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <Photo asset="skyNight" className={styles.night}>
        <span className={`${styles.star} ${styles.starOne}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starTwo}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starThree}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starFour}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starFive}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starSix}`} aria-hidden="true" />

        <StatusBar tone="light" />
        <TopBar
          left="arrow"
          tone="light"
          to={routes.capsuleCreate}
          right={
            <button type="button" className={styles.more} aria-label="その他のオプション">
              <IconDots size={24} stroke={1.8} />
            </button>
          }
        />

        <main className={styles.celebration}>
          <h1>タイムカプセルが<br />完成しました！</h1>
          <div className={styles.illustration} role="img" aria-label="きらめくタイムカプセル">
            <span className={`${styles.sparkle} ${styles.sparkleOne}`} />
            <span className={`${styles.sparkle} ${styles.sparkleTwo}`} />
            <span className={`${styles.sparkle} ${styles.sparkleThree}`} />
            <span className={`${styles.sparkle} ${styles.sparkleFour}`} />
            <span className={styles.capsule}>
              <span className={styles.seam} />
            </span>
          </div>
          <button type="button" className={styles.primary} onClick={() => navigate(routes.detail)}>
            タイムカプセルを開ける
          </button>
        </main>
      </Photo>
    </Screen>
  )
}
