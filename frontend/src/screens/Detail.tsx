import { useState } from 'react'
import { IconChevronRight, IconClock, IconCloud, IconDots, IconMessage, IconMusic, IconUser, IconUsers } from '@tabler/icons-react'
import { useParams, useSearchParams } from 'react-router-dom'
import { api, type AlbumPhoto, type ApiError } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { toApiError, useAsync } from '../state/useAsync'
import styles from './Detail.module.css'

const fmtDate = (iso: string) => iso.slice(0, 10).replace(/-/g, '.')
const fmtDateTime = (iso: string) => `${fmtDate(iso)} ${iso.slice(11, 16)}`

/**
 * Screen 08: one album, one photo's metadata rows.
 * GET /albums/{id}; the rows come from album_photo (02-basic-design §3.3).
 * "編集" edits the comment via PATCH /albums/{id}/photos/{albumPhotoId} with If-Match.
 */
export function Detail() {
  const albumId = numParam(useParams().albumId)
  // Re-fetched from the capsule, not passed in router state, so a reload keeps it (05 A-10).
  const capsuleId = numParam(useSearchParams()[0].get('capsule') ?? undefined)
  const capsule = useAsync(() => (capsuleId ? api.capsules.get(capsuleId) : Promise.resolve(null)), [capsuleId])
  const capsuleMsg = capsule.data?.status === 'OPENED' ? capsule.data.capsuleMsg : null
  const album = useAsync(() => (albumId ? api.albums.get(albumId) : Promise.reject(new Error('no album'))), [albumId])
  const [index, setIndex] = useState(0)
  const [error, setError] = useState<ApiError | null>(null)

  const a = album.data
  const photo: AlbumPhoto | null = a?.photos[index] ?? a?.photos[0] ?? null

  // Inline editor instead of window.prompt() (blocked in some WebViews).
  const [draft, setDraft] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const saveComment = async () => {
    if (!a || !photo || !albumId || draft === null) return
    setError(null)
    setSaving(true)
    try {
      await api.albums.patchPhoto(albumId, photo.id, photo.version, { photoComment: draft })
      setDraft(null)
      album.reload()
    } catch (err) {
      const e = toApiError(err)
      setError(e)
      // someone else saved first: show their version, keep the user's draft
      if (e.code === 'VERSION_CONFLICT') album.reload()
    } finally {
      setSaving(false)
    }
  }

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar
          tone="light"
          to={routes.me()}
          right={
            <button type="button" className={styles.more} aria-label="その他のオプション">
              <IconDots size={24} stroke={1.8} />
            </button>
          }
        />
      </header>

      {album.loading || !a || !photo ? (
        <main className={styles.content}>
          {album.loading ? <Loading /> : <ErrorNote error={album.error} />}
        </main>
      ) : (
        <main className={styles.content}>
          {capsuleMsg && (
            <blockquote className={styles.capsuleMsg}>
              <span>過去の自分から</span>
              {capsuleMsg}
            </blockquote>
          )}
          <header className={styles.albumHeader}>
            <div className={styles.titleRow}>
              <h1>{a.title}</h1>
              <span className={styles.userChip} aria-label={a.userName ?? 'ユーザー'}>
                <IconUser size={22} stroke={1.7} />
              </span>
            </div>
            <div className={styles.subtitleRow}>
              <span>{fmtDate(a.albumDate)}</span>
              <button type="button" className={styles.edit} onClick={() => setDraft(draft === null ? (photo.photoComment ?? '') : null)} aria-expanded={draft !== null}>
                {draft === null ? '編集' : '閉じる'}
              </button>
            </div>
          </header>

          <div className={styles.strip} aria-label="アルバムの写真">
            {a.photos.map((p, i) => (
              <button key={p.id} type="button" className={i === index ? styles.stripOn : styles.stripItem} onClick={() => setIndex(i)} aria-label={p.caption ?? `写真${i + 1}`} aria-pressed={i === index}>
                <Photo src={p.compositeUrl ?? p.thumbUrl} className={styles.stripPhoto} />
              </button>
            ))}
          </div>

          {draft !== null && (
            <div className={styles.commentEdit}>
              <textarea value={draft} onChange={(e) => setDraft(e.target.value)} rows={3} maxLength={2000} aria-label="コメントを編集" autoFocus />
              <div className={styles.commentActions}>
                <button type="button" onClick={() => setDraft(null)}>
                  やめる
                </button>
                <button type="button" className={styles.commentSave} disabled={saving} onClick={saveComment}>
                  {saving ? '保存中…' : '保存'}
                </button>
              </div>
            </div>
          )}

          <div className={styles.details}>
            <div className={styles.row}>
              <Photo src={photo.compositeUrl ?? photo.thumbUrl} className={styles.thumbnail} label={photo.caption ?? '写真'} />
              <div className={styles.rowText}>
                <span className={styles.label}>場所</span>
                <span className={styles.value}>{photo.place ?? a.place ?? '—'}</span>
              </div>
              <IconChevronRight className={styles.chevron} size={20} stroke={1.8} aria-hidden="true" />
            </div>
            <div className={styles.row}>
              <span className={styles.iconBox}>
                <IconClock size={22} stroke={1.7} aria-hidden="true" />
              </span>
              <div className={styles.rowText}>
                <span className={styles.label}>時間</span>
                <span className={styles.value}>{fmtDateTime(photo.takenTime)}</span>
              </div>
            </div>
            <div className={styles.row}>
              <span className={styles.iconBox}>
                <IconMusic size={22} stroke={1.7} aria-hidden="true" />
              </span>
              <div className={styles.rowText}>
                <span className={styles.label}>音楽</span>
                <span className={styles.value}>{photo.music ?? '—'}</span>
              </div>
            </div>
            <div className={styles.row}>
              <span className={styles.iconBox}>
                <IconMessage size={22} stroke={1.7} aria-hidden="true" />
              </span>
              <div className={styles.rowText}>
                <span className={styles.label}>コメント</span>
                <span className={styles.value}>{photo.photoComment ?? a.summary ?? '—'}</span>
              </div>
              <IconChevronRight className={styles.chevron} size={20} stroke={1.8} aria-hidden="true" />
            </div>
            <div className={styles.row}>
              <span className={styles.iconBox}>
                <IconUsers size={22} stroke={1.7} aria-hidden="true" />
              </span>
              <div className={styles.rowText}>
                <span className={styles.label}>メンバー</span>
                <span className={styles.value}>{a.members.map((m) => m.userName ?? '?').join('、')}</span>
              </div>
            </div>
            <div className={styles.row}>
              <span className={styles.iconBox}>
                <IconCloud size={22} stroke={1.7} aria-hidden="true" />
              </span>
              <div className={styles.rowText}>
                <span className={styles.label}>天気</span>
                <span className={styles.value}>{photo.weather ?? '—'}</span>
              </div>
            </div>
          </div>
          <ErrorNote error={error} />
        </main>
      )}
    </Screen>
  )
}
