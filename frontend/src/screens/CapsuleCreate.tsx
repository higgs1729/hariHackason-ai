import { useState } from 'react'
import { IconDots } from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, apiMode, type ApiError } from '../api'
import { ErrorNote } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { toApiError } from '../state/useAsync'
import styles from './CapsuleCreate.module.css'

type Choice = { label: string; ms: number }
const CHOICES: Choice[] = [
  { label: '1年後', ms: 365 * 86_400_000 },
  { label: '卒業式（半年後）', ms: 182 * 86_400_000 },
  { label: '1分後（デモ用）', ms: 60_000 },
]

/** ISO-8601 with the local offset, the format the backend expects (§3.1). */
function isoWithOffset(d: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  const off = -d.getTimezoneOffset()
  const sign = off >= 0 ? '+' : '-'
  const abs = Math.abs(off)
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}${sign}${pad(Math.floor(abs / 60))}:${pad(abs % 60)}`
}

/** Screen 06: seal the album. POST /capsules {albumId, openTime, capsuleMsg}. */
export function CapsuleCreate() {
  const navigate = useNavigate()
  const albumId = numParam(useParams().albumId)
  const [choice, setChoice] = useState<Choice>(CHOICES[0])
  const [message, setMessage] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const openDate = new Date(Date.now() + choice.ms)
  const visibleChoices = apiMode === 'mock' || import.meta.env.DEV ? CHOICES : CHOICES.slice(0, 2)

  const create = async () => {
    if (!albumId) return
    setBusy(true)
    setError(null)
    try {
      const capsule = await api.capsules.create({ albumId, openTime: isoWithOffset(openDate), capsuleMsg: message })
      navigate(routes.capsuleDone(capsule.id))
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar
          title="タイムカプセル"
          to={albumId ? routes.share(albumId) : routes.home()}
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
          <h1>{choice.label}の自分へ</h1>
          <p className={styles.message}>
            この思い出を、
            <br />
            未来の自分に届けよう。
          </p>
          <div className={styles.choices} role="radiogroup" aria-label="開ける日">
            {visibleChoices.map((c) => (
              <button key={c.label} type="button" role="radio" aria-checked={c === choice} className={c === choice ? styles.choiceOn : styles.choice} onClick={() => setChoice(c)}>
                {c.label}
              </button>
            ))}
          </div>
          <textarea
            className={styles.memo}
            placeholder="未来の自分へひとこと（開けるまで誰にも見えません）"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            rows={3}
            maxLength={2000}
          />
          <div className={styles.divider} aria-hidden="true" />
          <p className={styles.date}>{isoWithOffset(openDate).slice(0, 10).replace(/-/g, '.')}</p>
          <ErrorNote error={error} />
          <button type="button" className={styles.primary} disabled={busy || !albumId} onClick={create}>
            {busy ? '封印中…' : 'タイムカプセルを作成する'}
          </button>
        </main>
      </Photo>
    </Screen>
  )
}
