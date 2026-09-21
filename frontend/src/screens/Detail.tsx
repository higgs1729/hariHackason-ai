import { IconChevronRight, IconClock, IconCloud, IconDots, IconMessage, IconMusic, IconUser, IconUsers } from '@tabler/icons-react'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './Detail.module.css'

export function Detail() {
  return (
    <Screen className={styles.screen}>
      <StatusBar />
      <TopBar
        to={routes.capsuleDone}
        right={
          <button type="button" className={styles.more} aria-label="その他のオプション">
            <IconDots size={24} stroke={1.8} />
          </button>
        }
      />

      <main className={styles.content}>
        <header className={styles.albumHeader}>
          <div className={styles.titleRow}>
            <h1>2025 プリクラ</h1>
            <span className={styles.userChip} aria-label="アルバムのユーザー">
              <IconUser size={22} stroke={1.7} />
            </span>
          </div>
          <div className={styles.subtitleRow}>
            <span>2025.09.20</span>
            <button type="button" className={styles.edit}>編集</button>
          </div>
        </header>

        <div className={styles.details}>
          <div className={styles.row}>
            <Photo asset="tile1" className={styles.thumbnail} label="梅田での写真" />
            <div className={styles.rowText}>
              <span className={styles.label}>場所</span>
              <span className={styles.value}>梅田</span>
            </div>
            <IconChevronRight className={styles.chevron} size={20} stroke={1.8} aria-hidden="true" />
          </div>

          <div className={styles.row}>
            <span className={styles.iconBox}><IconClock size={22} stroke={1.7} aria-hidden="true" /></span>
            <div className={styles.rowText}>
              <span className={styles.label}>時間</span>
              <span className={styles.value}>2025.09.20 17:30</span>
            </div>
          </div>

          <div className={styles.row}>
            <span className={styles.iconBox}><IconMusic size={22} stroke={1.7} aria-hidden="true" /></span>
            <div className={styles.rowText}>
              <span className={styles.label}>音楽</span>
              <span className={styles.value}>I'm yours / Jason Mraz</span>
            </div>
          </div>

          <div className={styles.row}>
            <span className={styles.iconBox}><IconMessage size={22} stroke={1.7} aria-hidden="true" /></span>
            <div className={styles.rowText}>
              <span className={styles.label}>コメント</span>
              <span className={styles.value}>テスト終わりの放課後、最高だった♡</span>
            </div>
            <IconChevronRight className={styles.chevron} size={20} stroke={1.8} aria-hidden="true" />
          </div>

          <div className={styles.row}>
            <span className={styles.iconBox}><IconUsers size={22} stroke={1.7} aria-hidden="true" /></span>
            <div className={styles.rowText}>
              <span className={styles.label}>メンバー</span>
              <span className={styles.value}>あやか、みき、りん</span>
            </div>
          </div>

          <div className={styles.row}>
            <span className={styles.iconBox}><IconCloud size={22} stroke={1.7} aria-hidden="true" /></span>
            <div className={styles.rowText}>
              <span className={styles.label}>天気</span>
              <span className={styles.value}>晴れ</span>
            </div>
          </div>
        </div>
      </main>
    </Screen>
  )
}
