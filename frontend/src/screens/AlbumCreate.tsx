import { useState } from 'react'
import { IconCheck, IconHeart, IconPhoto, IconSparkles } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { api, type ApiError } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { MoreMenu } from '../components/MoreMenu'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import { toApiError, useAsync } from '../state/useAsync'
import styles from './AlbumCreate.module.css'

const fmtDate = (iso: string) => iso.slice(0, 10).replace(/-/g, '.')
const fmtTime = (iso: string) => iso.slice(11, 16)

/**
 * Screen 03: pick unassigned photos, hand them to the AI.
 * `GET /photos?unassigned=true` → `POST /albums/generate` → job screen.
 */
export function AlbumCreate() {
  const navigate = useNavigate()
  const photos = useAsync(() => api.photos.list({ unassigned: true, limit: 100 }), [])
  const [excluded, setExcluded] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  // Kept until the server answers 202, so a retap after a timeout replays the
  // same job instead of starting a second one (05-backend-answers §2).
  const [idempotencyKey, setIdempotencyKey] = useState<string | null>(null)

  const items = photos.data?.items ?? []
  const selected = items.filter((p) => !excluded.has(p.id))
  const hero = selected[0] ?? items[0] ?? null

  const toggle = (id: number) => {
    setIdempotencyKey(null) // a different selection is a different request
    setExcluded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const generate = async () => {
    if (selected.length === 0) return
    setBusy(true)
    setError(null)
    try {
      const key = idempotencyKey ?? crypto.randomUUID()
      setIdempotencyKey(key)
      const { jobId } = await api.albums.generate(
        selected.map((p) => p.id),
        key,
      )
      setIdempotencyKey(null)
      navigate(routes.albumGenerating(jobId))
    } catch (err) {
      const e = toApiError(err)
      setError(e)
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen className={styles.screen}>
      <Photo asset="friendsSunset" src={hero?.url} className={styles.hero} label="選んだ写真のプレビュー">
        <StatusBar tone="light" />
        <TopBar
          to={routes.camera()}
          tone="light"
          right={
            <MoreMenu className={styles.more} />
          }
        />
        <span className={styles.scribble} aria-hidden="true" />
        <div className={styles.lettering}>
          Best Friends ♡
          <IconSparkles className={styles.letterSparkle} size={24} stroke={1.6} aria-hidden="true" />
        </div>
        <IconHeart className={styles.heroHeart} size={29} stroke={1.5} aria-hidden="true" />
        <IconSparkles className={styles.heroSparkle} size={24} stroke={1.3} aria-hidden="true" />
      </Photo>

      <div className={styles.editor}>
        <div className={styles.dateRow}>
          <h1>{hero ? fmtDate(hero.takenTime) : 'まだ写真がありません'}</h1>
          <span className={styles.count}>
            <IconPhoto size={20} stroke={1.7} aria-hidden="true" />
            {selected.length}/{items.length}
          </span>
        </div>

        {photos.loading ? (
          <Loading />
        ) : (
          <div className={styles.grid}>
            {items.map((photo) => {
              const on = !excluded.has(photo.id)
              return (
                <button
                  key={photo.id}
                  type="button"
                  className={styles.tile}
                  onClick={() => toggle(photo.id)}
                  aria-pressed={on}
                  aria-label={`${fmtTime(photo.takenTime)}の写真を${on ? '外す' : '入れる'}`}
                >
                  <Photo src={photo.thumbUrl ?? photo.url} className={`${styles.tilePhoto} ${on ? '' : styles.tileOff}`}>
                    <span className={styles.heartBadge}>
                      {on ? <IconCheck size={13} stroke={2.2} aria-hidden="true" /> : <IconHeart size={13} stroke={1.7} aria-hidden="true" />}
                    </span>
                  </Photo>
                  <span className={styles.caption}>{fmtTime(photo.takenTime)}</span>
                </button>
              )
            })}
          </div>
        )}
        <ErrorNote error={photos.error ?? error} />
      </div>

      <div className={styles.footer}>
        <button type="button" className={styles.primary} disabled={busy || selected.length === 0} onClick={generate}>
          {busy ? '送信中…' : `AIでアルバムにまとめる（${selected.length}枚）`}
        </button>
      </div>
    </Screen>
  )
}
