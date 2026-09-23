import { useState } from 'react'
import { IconDots } from '@tabler/icons-react'
import { useNavigate, useParams } from 'react-router-dom'
import { api, apiMode, type ApiError } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { numParam, routes } from '../routes'
import { toApiError, useAsync } from '../state/useAsync'
import styles from './CapsuleDone.module.css'

/**
 * Screen 07: the capsule is sealed. GET /capsules/{id} returns the SEALED DTO
 * (no message, no album) until openTime; "開ける" calls POST /open and moves to
 * the album detail. Before openTime the backend answers 409 CAPSULE_NOT_YET_OPEN;
 * in dev/mock we fall back to /unseal-now so the demo never gets stuck.
 */
export function CapsuleDone() {
  const navigate = useNavigate()
  const capsuleId = numParam(useParams().capsuleId)
  const capsule = useAsync(() => (capsuleId ? api.capsules.get(capsuleId) : Promise.reject(new Error('no capsule'))), [capsuleId])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  const open = async () => {
    if (!capsuleId) return
    setBusy(true)
    setError(null)
    try {
      let c
      try {
        c = await api.capsules.open(capsuleId)
      } catch (err) {
        const e = toApiError(err)
        if (e.code === 'CAPSULE_NOT_YET_OPEN' && (apiMode === 'mock' || import.meta.env.DEV)) c = await api.capsules.unsealNow(capsuleId)
        else throw e
      }
      if (c.status === 'OPENED') navigate(routes.detail(c.album.id, c.id))
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  const c = capsule.data
  const remaining = c?.status === 'SEALED' ? c.daysRemaining : 0

  return (
    <Screen className={styles.screen}>
      <Photo asset="skyNight" className={styles.night}>
        <span className={`${styles.star} ${styles.starOne}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starTwo}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starThree}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starFour}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starFive}`} aria-hidden="true" />
        <span className={`${styles.star} ${styles.starSix}`} aria-hidden="true" />
        <StatusBar tone="light" />
        <TopBar
          left="arrow"
          tone="light"
          to={routes.me('capsules')}
          right={
            <button type="button" className={styles.more} aria-label="その他のオプション">
              <IconDots size={24} stroke={1.8} />
            </button>
          }
        />
        <main className={styles.celebration}>
          {capsule.loading ? (
            <Loading label="カプセルを確認中" />
          ) : (
            <>
              <h1>
                タイムカプセルが
                <br />
                完成しました！
              </h1>
              <div className={styles.illustration} role="img" aria-label="きらめくタイムカプセル">
                <span className={`${styles.sparkle} ${styles.sparkleOne}`} />
                <span className={`${styles.sparkle} ${styles.sparkleTwo}`} />
                <span className={`${styles.sparkle} ${styles.sparkleThree}`} />
                <span className={`${styles.sparkle} ${styles.sparkleFour}`} />
                <span className={styles.capsule}>
                  <span className={styles.seam} />
                </span>
              </div>
              <p className={styles.remaining}>
                {c?.status === 'OPENED' ? '開封済み' : remaining > 0 ? `あと ${remaining} 日で開けられます` : 'もう開けられます'}
              </p>
              <ErrorNote error={capsule.error ?? error} />
              <button type="button" className={styles.primary} disabled={busy || !c} onClick={open}>
                {busy ? '開封中…' : 'タイムカプセルを開ける'}
              </button>
            </>
          )}
        </main>
      </Photo>
    </Screen>
  )
}
