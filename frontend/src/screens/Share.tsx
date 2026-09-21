import { IconCheck, IconChevronRight, IconDots, IconSend } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import styles from './Share.module.css'

const friends = [
  { asset: 'avatarMe', name: 'わたし' },
  { asset: 'avatarA', name: 'あやか' },
  { asset: 'avatarB', name: 'みき' },
  { asset: 'avatarC', name: 'りん' },
] as const

export function Share() {
  const navigate = useNavigate()

  return (
    <Screen className={styles.screen}>
      <StatusBar />
      <TopBar
        to={routes.decorate}
        right={
          <button type="button" className={styles.more} aria-label="その他のオプション">
            <IconDots size={24} stroke={1.8} />
          </button>
        }
      />

      <main className={styles.content}>
        <div className={styles.friends} aria-label="アルバムのメンバー">
          {friends.map((friend, index) => (
            <div className={styles.friend} key={friend.asset}>
              <div className={`${styles.avatarFrame} ${index === 0 ? styles.selected : ''}`}>
                <Photo asset={friend.asset} className={styles.avatar} label={friend.name} />
              </div>
              <span>{friend.name}</span>
            </div>
          ))}
          <IconChevronRight className={styles.friendsChevron} size={21} stroke={1.8} aria-hidden="true" />
        </div>

        <div className={styles.sectionHeading}>
          <h1>放課後プリクラ</h1>
          <IconChevronRight size={21} stroke={1.8} aria-hidden="true" />
        </div>

        <article className={styles.albumCard}>
          <div className={styles.chips}>
            <span>2025.09.20</span>
            <span>梅田</span>
            <span>3人</span>
          </div>
          <div className={styles.albumPreview}>
            <Photo asset="friendsSunset" className={styles.albumPhoto} label="放課後の友達との思い出" />
            <div className={styles.albumMembers} aria-label="アルバムの友達">
              <Photo asset="avatarA" className={styles.memberAvatar} label="あやか" />
              <Photo asset="avatarB" className={styles.memberAvatar} label="みき" />
            </div>
          </div>
          <p className={styles.aiNote}>AIがまとめました ✨</p>
        </article>

        <div className={styles.sectionHeading}>
          <h2>見せる相手</h2>
          <IconChevronRight size={21} stroke={1.8} aria-hidden="true" />
        </div>

        <article className={styles.recipientCard}>
          <Photo asset="tile5" className={styles.recipientPhoto} label="友達と共有する写真" />
          <div className={styles.recipients}>
            {friends.slice(1).map((friend) => (
              <div className={styles.recipient} key={friend.asset}>
                <span>{friend.name}</span>
                <IconCheck size={15} stroke={2.2} aria-label="選択済み" />
              </div>
            ))}
          </div>
          <button
            type="button"
            className={styles.send}
            aria-label="友達と共有してタイムカプセルを作る"
            onClick={() => navigate(routes.capsuleCreate)}
          >
            <IconSend size={24} stroke={1.8} />
          </button>
        </article>
        <p className={styles.caption}>友達と一緒に思い出を作れる！</p>
      </main>
    </Screen>
  )
}
