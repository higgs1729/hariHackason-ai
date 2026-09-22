import { useEffect, useState } from 'react'
import { IconSparkles } from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, type ApiError, type GenerateJob } from '../api'
import { ErrorNote } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { toApiError } from '../state/useAsync'
import styles from './AlbumGenerating.module.css'

const POLL_MS = 1500

const stageLabel: Record<GenerateJob['status'], string> = {
  PENDING: '準備中',
  CLUSTERING: '写真を日付でまとめています',
  ENRICHING: 'AIがタイトルとコメントを考えています',
  READY: 'できました！',
  FAILED: 'うまくいきませんでした',
}

/**
 * Waits on `GET /albums/jobs/{id}` and moves to the decorate screen of the
 * first album once READY. `aiGenerated=0` (rule-based fallback) is not an
 * error here: the album still exists and the flow continues (NFR-02).
 */
export function AlbumGenerating() {
  const navigate = useNavigate()
  const jobId = numParam(useParams().jobId)
  const [job, setJob] = useState<GenerateJob | null>(null)
  const [error, setError] = useState<ApiError | null>(null)

  useEffect(() => {
    if (!jobId) return
    let timer: number | undefined
    let cancelled = false
    const tick = async () => {
      try {
        const j = await api.albums.job(jobId)
        if (cancelled) return
        setJob(j)
        if (j.status === 'READY' && j.albumIds.length > 0) {
          const album = await api.albums.get(j.albumIds[0])
          if (cancelled) return
          const first = album.photos[0]
          navigate(first ? routes.decorate(album.id, first.id) : routes.detail(album.id), { replace: true })
          return
        }
        if (j.status !== 'FAILED') timer = window.setTimeout(tick, POLL_MS)
      } catch (err) {
        if (!cancelled) setError(toApiError(err))
      }
    }
    void tick()
    return () => {
      cancelled = true
      window.clearTimeout(timer)
    }
  }, [jobId, navigate])

  const progress = job?.progress ?? 0

  return (
    <Screen className={styles.screen}>
      <Photo asset="skySunset" className={styles.sky}>
        <StatusBar tone="light" />
        <TopBar tone="light" left="none" />
        <main className={styles.card}>
          <IconSparkles size={40} stroke={1.4} className={styles.icon} aria-hidden="true" />
          <h1>AIがアルバムにまとめています</h1>
          <p className={styles.stage}>{job ? stageLabel[job.status] : '接続中'}</p>
          <div className={styles.bar} role="progressbar" aria-valuenow={progress} aria-valuemin={0} aria-valuemax={100}>
            <span style={{ width: `${progress}%` }} />
          </div>
          <p className={styles.percent}>{progress}%</p>
          {job?.status === 'FAILED' && <p className={styles.stage}>{job.errorMsg ?? '写真を選び直してください'}</p>}
          <ErrorNote error={error} />
          {(job?.status === 'FAILED' || error) && (
            <button type="button" className={styles.primary} onClick={() => navigate(routes.albumCreate())}>
              写真を選び直す
            </button>
          )}
        </main>
      </Photo>
    </Screen>
  )
}
