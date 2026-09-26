import { useState } from 'react'
import { IconCheck, IconChevronRight, IconCopy, IconSend } from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, type ApiError } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { MoreMenu } from '../components/MoreMenu'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { useAuth } from '../state/auth'
import { toApiError, useAsync } from '../state/useAsync'
import styles from './Share.module.css'

/**
 * Screen 05: pick friends as album members, then hand the share link to the OS share sheet.
 * Afterwards the link stays on screen with the way on to the time capsule.
 * GET /friends → POST /albums/{id}/members (each) → POST /albums/{id}/share → navigator.share.
 * On desktop (no share sheet) the link is copied to the clipboard instead.
 */
export function Share() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const albumId = numParam(useParams().albumId)
  const album = useAsync(() => (albumId ? api.albums.get(albumId) : Promise.reject(new Error('no album'))), [albumId])
  const friends = useAsync(() => api.friends.list(), [])
  const [picked, setPicked] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [shareUrl, setShareUrl] = useState<string | null>(null)

  const memberIds = new Set(album.data?.members.map((m) => m.userId) ?? [])
  const toggle = (id: number) =>
    setPicked((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  const send = async () => {
    if (!albumId) return
    setBusy(true)
    setError(null)
    try {
      for (const id of picked) {
        if (!memberIds.has(id)) await api.albums.addMember(albumId, id)
      }
      const link = await api.share.create(albumId)
      setShareUrl(link.shareUrl)
      const title = album.data?.title ?? 'アルバム'
      if (typeof navigator.share === 'function') {
        try {
          await navigator.share({ title, text: `${title} をシェアします`, url: link.shareUrl })
        } catch {
          /* user dismissed the sheet: link is still shown below */
        }
      } else {
        await navigator.clipboard?.writeText(link.shareUrl).catch(() => undefined)
      }
      // Stays here: the link is shown, and the capsule is the next, separate
      // step (the demo shows the friend's view of the link in between).
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  const a = album.data
  const chips = a ? [a.albumDate.replace(/-/g, '.'), a.place ?? '', `${a.memberNum + picked.size}人`].filter(Boolean) : []

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar
          tone="light"
          to={a && a.photos[0] ? routes.decorate(a.id, a.photos[0].id) : routes.albumCreate()}
          right={
            <MoreMenu className={styles.more} />
          }
        />
      </header>

      <main className={styles.content}>
        {friends.loading ? (
          <Loading label="友達を読み込み中" />
        ) : (
          <div className={styles.friends} aria-label="友達">
            <div className={styles.friend}>
              <div className={`${styles.avatarFrame} ${styles.selected}`}>
                <Photo asset="avatarMe" src={user?.userAvatar} className={styles.avatar} label={user?.userName ?? 'わたし'} />
              </div>
              <span>{user?.userName ?? 'わたし'}</span>
            </div>
            {(friends.data ?? []).map((f) => {
              const on = picked.has(f.id) || memberIds.has(f.id)
              return (
                <button type="button" className={styles.friend} key={f.id} onClick={() => toggle(f.id)} aria-pressed={on} aria-label={`${f.userName ?? f.userAccount}を見せる相手にする`}>
                  <div className={`${styles.avatarFrame} ${on ? styles.selected : ''}`}>
                    <Photo asset="avatarA" src={f.userAvatar} className={styles.avatar} label={f.userName ?? f.userAccount} />
                  </div>
                  <span>{f.userName ?? f.userAccount}</span>
                </button>
              )
            })}
            <IconChevronRight className={styles.friendsChevron} size={21} stroke={1.8} aria-hidden="true" />
          </div>
        )}

        <div className={styles.sectionHeading}>
          <h1>{a?.title ?? '…'}</h1>
          <IconChevronRight size={21} stroke={1.8} aria-hidden="true" />
        </div>

        <article className={styles.albumCard}>
          <div className={styles.chips}>
            {chips.map((c) => (
              <span key={c}>{c}</span>
            ))}
          </div>
          <div className={styles.albumPreview}>
            <Photo asset="friendsSunset" src={a?.photos[0]?.compositeUrl ?? a?.coverThumbUrl} className={styles.albumPhoto} label={a?.title ?? 'アルバム'} />
            <div className={styles.albumMembers} aria-label="アルバムの友達">
              {(a?.members ?? []).slice(0, 3).map((m) => (
                <Photo key={m.userId} asset="avatarA" src={m.userAvatar} className={styles.memberAvatar} label={m.userName ?? ''} />
              ))}
            </div>
          </div>
          <p className={styles.aiNote}>{a?.aiGenerated ? 'AIがまとめました ✨' : 'まとめました ✨'}</p>
        </article>

        <div className={styles.sectionHeading}>
          <h2>見せる相手</h2>
          <IconChevronRight size={21} stroke={1.8} aria-hidden="true" />
        </div>

        <article className={styles.recipientCard}>
          <Photo asset="tile5" src={a?.photos[1]?.thumbUrl ?? a?.coverThumbUrl} className={styles.recipientPhoto} label="共有する写真" />
          <div className={styles.recipients}>
            {(friends.data ?? [])
              .filter((f) => picked.has(f.id) || memberIds.has(f.id))
              .map((f) => (
                <div className={styles.recipient} key={f.id}>
                  <span>{f.userName ?? f.userAccount}</span>
                  <IconCheck size={15} stroke={2.2} aria-label="選択済み" />
                </div>
              ))}
            {picked.size === 0 && memberIds.size <= 1 && <span className={styles.hint}>上の友達をタップ</span>}
          </div>
          <button type="button" className={styles.send} aria-label="友達と共有する" disabled={busy || !a} onClick={send}>
            <IconSend size={24} stroke={1.8} />
          </button>
        </article>

        {shareUrl && albumId && (
          <>
            <p className={styles.link}>
              <IconCopy size={14} stroke={2} aria-hidden="true" />
              <a href={shareUrl} target="_blank" rel="noreferrer">
                {shareUrl}
              </a>
            </p>
            <button type="button" className={styles.capsule} onClick={() => navigate(routes.capsuleCreate(albumId))}>
              タイムカプセルを作成する
            </button>
          </>
        )}
        <ErrorNote error={album.error ?? friends.error ?? error} />
        <p className={styles.caption}>友達と一緒に思い出を作れる！</p>
      </main>
    </Screen>
  )
}
