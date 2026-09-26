import { useState, type FormEvent } from 'react'
import { IconMinus, IconPlus, IconSparkles, IconX } from '@tabler/icons-react'
import { api, type ApiError, type ShootHint } from '../api'
import { ideaToHint, shootIdeas } from '../shootIdeas'
import { toApiError } from '../state/useAsync'
import { ErrorNote } from './Notice'
import styles from './ShootHintSheet.module.css'

const moods = ['わちゃわちゃ', 'エモく', 'かわいく', 'かっこよく'] as const

type Props = {
  onClose: () => void
  /** "これで撮る": the hint stays on the viewfinder while shooting */
  onUse: (hint: ShootHint) => void
}

/**
 * Bottom sheet for the shoot hint (row 7, 07-screens-beyond-poster §3).
 * POST /api/hints/shoot always answers 200 with some text; only 429 is an error.
 * `aiGenerated` is never shown (05-backend-answers §8.1).
 */
export function ShootHintSheet({ onClose, onUse }: Props) {
  const [count, setCount] = useState(3)
  const [mood, setMood] = useState<string>(moods[0])
  const [place, setPlace] = useState('')
  const [hint, setHint] = useState<ShootHint | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const ask = async (e?: FormEvent) => {
    e?.preventDefault()
    setBusy(true)
    setError(null)
    try {
      setHint(await api.hints.shoot({ memberCount: count, mood, place: place.trim() || undefined }))
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className={styles.backdrop} onClick={onClose}>
      <section className={styles.sheet} role="dialog" aria-modal="true" aria-labelledby="shoot-hint-title" onClick={(e) => e.stopPropagation()}>
        <header className={styles.head}>
          <h2 id="shoot-hint-title">
            <IconSparkles size={18} stroke={1.8} aria-hidden="true" /> AIに撮り方を聞く
          </h2>
          <button type="button" className={styles.close} onClick={onClose} aria-label="閉じる">
            <IconX size={20} stroke={2} />
          </button>
        </header>

        {hint ? (
          <div className={styles.result} aria-live="polite">
            <p className={styles.hint}>{hint.hint}</p>
            <ul className={styles.poses}>
              {hint.poses.map((p) => (
                <li key={p}>{p}</li>
              ))}
            </ul>
            <ErrorNote error={error} />
            <div className={styles.actions}>
              <button type="button" className={styles.secondary} disabled={busy} onClick={() => void ask()}>
                {busy ? '考え中…' : 'ほかの案'}
              </button>
              <button type="button" className={styles.primary} onClick={() => onUse(hint)}>
                これで撮る
              </button>
            </div>
          </div>
        ) : (
          <form className={styles.form} onSubmit={ask}>
            <div className={styles.field}>
              <span className={styles.label}>何人？</span>
              <div className={styles.stepper}>
                <button type="button" onClick={() => setCount((n) => Math.max(1, n - 1))} aria-label="1人減らす" disabled={count <= 1}>
                  <IconMinus size={18} stroke={2} />
                </button>
                <output aria-live="polite">{count}人</output>
                <button type="button" onClick={() => setCount((n) => Math.min(10, n + 1))} aria-label="1人増やす" disabled={count >= 10}>
                  <IconPlus size={18} stroke={2} />
                </button>
              </div>
            </div>

            <div className={styles.field}>
              <span className={styles.label}>どんな感じ？</span>
              <div className={styles.chips} role="radiogroup" aria-label="雰囲気">
                {moods.map((m) => (
                  <button key={m} type="button" role="radio" aria-checked={mood === m} className={mood === m ? styles.chipOn : styles.chip} onClick={() => setMood(m)}>
                    {m}
                  </button>
                ))}
              </div>
            </div>

            <label className={styles.field}>
              <span className={styles.label}>
                どこで？<small>（なくてもOK）</small>
              </span>
              <input className={styles.input} value={place} onChange={(e) => setPlace(e.target.value)} placeholder="梅田のプリ機の前" maxLength={40} />
            </label>

            <ErrorNote error={error} />
            <button type="submit" className={styles.primary} disabled={busy}>
              {busy ? '考え中…' : '聞いてみる'}
            </button>

            {/* no API call: works even when the AI is down */}
            <div className={styles.field}>
              <span className={styles.label}>お題から選ぶ</span>
              <div className={styles.ideas}>
                {shootIdeas
                  .filter((x) => x.minMembers <= count)
                  .map((x) => (
                    <button key={x.title} type="button" className={styles.chip} onClick={() => setHint(ideaToHint(x))}>
                      {x.title}
                    </button>
                  ))}
              </div>
            </div>
          </form>
        )}
      </section>
    </div>
  )
}
