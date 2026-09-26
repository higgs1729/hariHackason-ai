import { useState, type FormEvent, type ReactNode } from 'react'
import { IconCamera, IconChevronRight, IconLock, IconLockOpen, IconPencil, IconQrcode, IconUser } from '@tabler/icons-react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { api, tokens, type ApiError, type Capsule, type User } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import { useAuth } from '../state/auth'
import { toApiError, useAsync, type AsyncState } from '../state/useAsync'
import styles from './MyPage.module.css'

const tabs = [
  { key: 'albums', label: 'アルバム' },
  { key: 'capsules', label: 'カプセル' },
  { key: 'friends', label: '友達' },
] as const
type Tab = (typeof tabs)[number]['key']

const fmtDate = (iso: string) => iso.slice(0, 10).replace(/-/g, '.')

/**
 * Hub that is not on the poster (07-screens-beyond-poster §1).
 * Lists what the user already has; the camera button starts the poster flow.
 * The tab lives in `?tab=` so a reload or the back button keeps it.
 */
export function MyPage() {
  const navigate = useNavigate()
  const { user } = useAuth()
  const [params, setParams] = useSearchParams()
  const tab: Tab = tabs.some((t) => t.key === params.get('tab')) ? (params.get('tab') as Tab) : 'albums'

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar tone="light" left="none" />
        <div className={styles.profile}>
          <Photo asset="avatarMe" src={user?.userAvatar} className={styles.avatar} />
          <NameEditor />
        </div>
        <div className={styles.tabs} role="tablist" aria-label="マイページ">
          {tabs.map((t) => (
            <button
              key={t.key}
              type="button"
              role="tab"
              aria-selected={tab === t.key}
              className={tab === t.key ? styles.tabOn : styles.tab}
              onClick={() => setParams({ tab: t.key }, { replace: true })}
            >
              {t.label}
            </button>
          ))}
        </div>
      </header>

      <main className={styles.content} role="tabpanel">
        {tab === 'albums' && <AlbumsTab />}
        {tab === 'capsules' && <CapsulesTab />}
        {tab === 'friends' && <FriendsTab />}
      </main>

      <button type="button" className={styles.camera} onClick={() => navigate(routes.camera())} aria-label="写真を撮る">
        <IconCamera size={28} stroke={1.8} />
      </button>
    </Screen>
  )
}

/** Display name, edited in place (07 §7). PATCH /users/me, then the session user is updated. */
function NameEditor() {
  const { user } = useAuth()
  const [draft, setDraft] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const save = async (e: FormEvent) => {
    e.preventDefault()
    const userName = draft?.trim()
    if (!userName) return
    setBusy(true)
    setError(null)
    try {
      tokens.setUser(await api.users.patchMe({ userName }))
      setDraft(null)
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  if (draft !== null)
    return (
      <form className={styles.nameForm} onSubmit={save}>
        <input value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={30} aria-label="表示名" autoFocus required />
        <div className={styles.nameActions}>
          <button type="button" onClick={() => setDraft(null)}>
            やめる
          </button>
          <button type="submit" className={styles.nameSave} disabled={busy}>
            {busy ? '…' : '保存'}
          </button>
        </div>
        <ErrorNote error={error} />
      </form>
    )

  return (
    <div className={styles.names}>
      <h1>
        {user?.userName ?? user?.userAccount}
        <button type="button" className={styles.nameEdit} onClick={() => setDraft(user?.userName ?? '')} aria-label="名前を変える">
          <IconPencil size={16} stroke={1.8} />
        </button>
      </h1>
      <span>@{user?.userAccount}</span>
    </div>
  )
}

/** Loading / error / empty handling shared by the three tabs. */
function TabBody<T>({ state, empty, children }: { state: AsyncState<T[]>; empty: string; children: (items: T[]) => ReactNode }) {
  if (state.loading) return <Loading />
  if (state.error) return <ErrorNote error={state.error} />
  const items = state.data ?? []
  if (items.length === 0) return <p className={styles.empty}>{empty}</p>
  return <>{children(items)}</>
}

function AlbumsTab() {
  const albums = useAsync(() => api.albums.list({ limit: 100 }).then((p) => p.items), [])
  return (
    <TabBody state={albums} empty="まだアルバムがありません。カメラから始めよう">
      {(items) => (
        <ul className={styles.grid}>
          {items.map((a) => (
            <li key={a.id}>
              <Link to={routes.detail(a.id)} className={styles.albumCard}>
                <Photo asset="tile1" src={a.coverThumbUrl} className={styles.cover} />
                <span className={styles.albumTitle}>{a.title}</span>
                <span className={styles.albumDate}>{fmtDate(a.albumDate)}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </TabBody>
  )
}

function CapsulesTab() {
  const capsules = useAsync(() => api.capsules.list(), [])
  return (
    <TabBody state={capsules} empty="まだカプセルがありません。アルバムを共有したあとに作れます">
      {(items) => (
        <ul className={styles.list}>
          {[...items].sort((a, b) => a.openTime.localeCompare(b.openTime)).map((c) => (
            <li key={c.id}>
              <CapsuleRow c={c} />
            </li>
          ))}
        </ul>
      )}
    </TabBody>
  )
}

function CapsuleRow({ c }: { c: Capsule }) {
  const sealed = c.status === 'SEALED'
  return (
    <Link to={sealed ? routes.capsuleDone(c.id) : routes.detail(c.album.id, c.id)} className={styles.row}>
      <span className={sealed ? styles.iconSealed : styles.iconOpened}>
        {sealed ? <IconLock size={22} stroke={1.7} aria-hidden="true" /> : <IconLockOpen size={22} stroke={1.7} aria-hidden="true" />}
      </span>
      <span className={styles.rowText}>
        <span className={styles.rowTitle}>{sealed ? `あと${c.daysRemaining}日` : c.album.title}</span>
        <span className={styles.rowSub}>
          {sealed ? `${fmtDate(c.openTime)} に開けられる` : `${fmtDate(c.openedTime)} に開けた`}
        </span>
      </span>
    </Link>
  )
}

function FriendsTab() {
  const [version, setVersion] = useState(0)
  const friends = useAsync(() => api.friends.list(), [version])
  const incoming = useAsync(() => api.friends.incoming(), [version])
  const changed = () => setVersion((v) => v + 1)
  return (
    <>
      <Link to={routes.friendQr()} className={styles.addFriend}>
        <IconQrcode size={20} stroke={1.8} aria-hidden="true" /> QRで友達を追加
      </Link>
      <FriendSearch friendIds={new Set((friends.data ?? []).map((f) => f.id))} onChange={changed} />
      {incoming.data && incoming.data.length > 0 && (
        <section aria-label="友達申請">
          <h2 className={styles.subhead}>友達申請</h2>
          <ul className={styles.list}>
            {incoming.data.map((r) => (
              <li key={r.id} className={styles.row}>
                <span className={styles.iconOpened}>
                  <IconUser size={22} stroke={1.7} aria-hidden="true" />
                </span>
                <span className={styles.rowTitle}>{r.userName ?? `ユーザー${r.userId}`}</span>
                <ActionButton label="承認" doneLabel="友達になりました" aria={`${r.userName ?? ''}の申請を承認`} run={() => api.friends.accept(r.id)} onDone={changed} />
              </li>
            ))}
          </ul>
        </section>
      )}
      <TabBody state={friends} empty="まだ友達がいません">
        {(items) => (
          <ul className={styles.list}>
            {items.map((f) => (
              <li key={f.id}>
                {/* a friend opens reunion mode: every album you share, as a slideshow */}
                <Link to={routes.reunion(f.id)} className={styles.row}>
                  {f.userAvatar ? (
                    <Photo src={f.userAvatar} className={styles.friendAvatar} />
                  ) : (
                    <span className={styles.iconOpened}>
                      <IconUser size={22} stroke={1.7} aria-hidden="true" />
                    </span>
                  )}
                  <span className={styles.rowText}>
                    <span className={styles.rowTitle}>{f.userName ?? f.userAccount}</span>
                    <span className={styles.rowSub}>@{f.userAccount}</span>
                  </span>
                  <IconChevronRight className={styles.rowChevron} size={20} stroke={1.8} aria-hidden="true" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </TabBody>
    </>
  )
}

/** FR-08: find someone by ID or name and send a request (they approve it from their own list). */
function FriendSearch({ friendIds, onChange }: { friendIds: Set<number>; onChange: () => void }) {
  const [q, setQ] = useState('')
  const [results, setResults] = useState<User[] | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const search = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    try {
      setResults(await api.users.search(q.trim()))
    } catch (err) {
      setError(toApiError(err))
    }
  }
  return (
    <div className={styles.search}>
      <form className={styles.searchForm} onSubmit={search} role="search">
        <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="IDか名前でさがす" aria-label="友達をさがす" autoCapitalize="none" />
        <button type="submit" disabled={!q.trim()}>
          さがす
        </button>
      </form>
      <ErrorNote error={error} />
      {results && (
        <ul className={styles.list} aria-label="検索結果">
          {results.length === 0 && <li className={styles.searchEmpty}>見つかりませんでした</li>}
          {results.map((u) => (
            <li key={u.id} className={styles.row}>
              <span className={styles.iconOpened}>
                <IconUser size={22} stroke={1.7} aria-hidden="true" />
              </span>
              <span className={styles.rowText}>
                <span className={styles.rowTitle}>{u.userName ?? u.userAccount}</span>
                <span className={styles.rowSub}>@{u.userAccount}</span>
              </span>
              {friendIds.has(u.id) ? (
                <span className={styles.friendBadge}>友達</span>
              ) : (
                <ActionButton label="申請" doneLabel="申請しました" aria={`${u.userName ?? u.userAccount}に友達申請`} run={() => api.friends.request(u.id)} onDone={onChange} />
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function ActionButton({ label, doneLabel, aria, run, onDone }: { label: string; doneLabel: string; aria: string; run: () => Promise<unknown>; onDone: () => void }) {
  const [state, setState] = useState<'idle' | 'busy' | 'done' | 'error'>('idle')
  const click = async () => {
    setState('busy')
    try {
      await run()
      setState('done')
      onDone()
    } catch {
      setState('error')
    }
  }
  if (state === 'done') return <span className={styles.friendBadge}>{doneLabel}</span>
  return (
    <button type="button" className={styles.rowAction} onClick={click} disabled={state === 'busy'} aria-label={aria}>
      {state === 'error' ? 'もう一度' : label}
    </button>
  )
}
