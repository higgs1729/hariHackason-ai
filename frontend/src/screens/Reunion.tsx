import { useEffect, useState } from 'react'
import { IconCamera, IconPlayerPause, IconPlayerPlay } from '@tabler/icons-react'
import { Link, useParams } from 'react-router-dom'
import { api } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { useAsync } from '../state/useAsync'
import styles from './Reunion.module.css'

const SLIDE_MS = 3000

type Slide = { key: string; src: string; albumId: number; albumTitle: string; date: string; caption: string | null }

/**
 * Reunion mode (row 10, 07-screens-beyond-poster §5): after meeting a friend
 * again, play every album you share with them as a slideshow.
 *
 * `?memberId=` (05 §3.2) is sent, but the albums are also filtered here by their
 * member list, so a backend that does not support the parameter yet still gives
 * the right answer, only with more requests.
 */
export function Reunion() {
  const userId = numParam(useParams().userId)
  const friend = useAsync(() => (userId ? api.users.get(userId) : Promise.reject(new Error('no user'))), [userId])
  const slides = useAsync(async (): Promise<Slide[]> => {
    if (!userId) return []
    const page = await api.albums.list({ memberId: userId, limit: 100 })
    const albums = await Promise.all(page.items.map((a) => api.albums.get(a.id)))
    return albums
      .filter((a) => a.members.some((m) => m.userId === userId))
      .sort((a, b) => a.albumDate.localeCompare(b.albumDate))
      .flatMap((a) =>
        a.photos.map((p) => ({
          key: `${a.id}-${p.id}`,
          src: p.compositeUrl ?? p.photoUrl,
          albumId: a.id,
          albumTitle: a.title,
          date: a.albumDate.replace(/-/g, '.'),
          caption: p.caption,
        })),
      )
  }, [userId])

  const [index, setIndex] = useState(0)
  const [playing, setPlaying] = useState(true)
  const count = slides.data?.length ?? 0

  useEffect(() => {
    if (!playing || count < 2) return
    const t = setTimeout(() => setIndex((i) => (i + 1) % count), SLIDE_MS)
    return () => clearTimeout(t)
  }, [index, playing, count])

  const name = friend.data?.userName ?? friend.data?.userAccount ?? '友達'
  const s = slides.data?.[index % Math.max(1, count)]

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar tone="light" left="close" title={`${name}との思い出`} to={routes.me('friends')} />
        {count > 1 && (
          <div className={styles.progress} aria-hidden="true">
            {slides.data!.map((sl, i) => (
              <span key={sl.key} className={i < index ? styles.done : i === index ? (playing ? styles.running : styles.done) : undefined} />
            ))}
          </div>
        )}
      </header>

      {slides.loading ? (
        <Loading label="思い出を集めています" />
      ) : slides.error ? (
        <div className={styles.message}>
          <ErrorNote error={slides.error} />
        </div>
      ) : !s ? (
        <div className={styles.message}>
          <p>まだ{name}と一緒のアルバムがありません。</p>
          <p>今日を最初の1枚にしよう。</p>
          <Link to={routes.camera()} className={styles.cta}>
            <IconCamera size={20} stroke={1.8} aria-hidden="true" /> 撮る
          </Link>
        </div>
      ) : (
        <>
          <button type="button" className={styles.stage} onClick={() => setIndex((i) => (i + 1) % count)} aria-label="次の写真">
            <Photo key={s.key} src={s.src} className={styles.slide} label={s.caption ?? s.albumTitle} />
          </button>
          <footer className={styles.footer}>
            <Link to={routes.detail(s.albumId)} className={styles.album}>
              <span className={styles.albumTitle}>{s.albumTitle}</span>
              <span className={styles.albumDate}>
                {s.date}
                {s.caption ? ` ・ ${s.caption}` : ''}
              </span>
            </Link>
            <button type="button" className={styles.play} onClick={() => setPlaying((p) => !p)} aria-label={playing ? '止める' : '再生'}>
              {playing ? <IconPlayerPause size={22} stroke={1.8} /> : <IconPlayerPlay size={22} stroke={1.8} />}
            </button>
          </footer>
        </>
      )}
    </Screen>
  )
}
