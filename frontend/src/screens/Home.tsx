import { useNavigate } from 'react-router-dom'
import { Logo } from '../components/Logo'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './Home.module.css'

export function Home() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <Photo asset="skySunset" className={styles.background}>
        <StatusBar tone="light" />
        <TopBar tone="light" to={routes.home} />

        <main className={styles.intro}>
          <Logo size={110} color="var(--white)" />
          <p className={styles.tagline}>あの時の、最高を、ずっと。</p>
        </main>

        <div className={styles.actions}>
          <button type="button" className={styles.start} onClick={() => navigate(routes.camera)}>
            はじめる
          </button>
          <button type="button" className={styles.login} onClick={() => navigate(routes.home)}>
            ログイン
          </button>
        </div>
      </Photo>
    </Screen>
  )
}
