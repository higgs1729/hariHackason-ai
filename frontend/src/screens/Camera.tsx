import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { IconBolt, IconCameraRotate, IconPhoto } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { api, type ApiError } from '../api'
import { ErrorNote } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import { toApiError } from '../state/useAsync'
import styles from './Camera.module.css'

/**
 * Capture screen.
 *
 * Photos go through `<input type="file" accept="image/*" capture>` rather than a
 * canvas snapshot of the live stream: the file keeps its EXIF, and the backend
 * needs DateTimeOriginal / GPS for clustering and the metadata rows
 * (03-detailed-design §5.1, §5.2). The live stream is only the viewfinder look.
 */
export function Camera() {
  const navigate = useNavigate()
  const videoRef = useRef<HTMLVideoElement>(null)
  const captureRef = useRef<HTMLInputElement>(null)
  const libraryRef = useRef<HTMLInputElement>(null)
  const [facing, setFacing] = useState<'environment' | 'user'>('environment')
  const [hasStream, setHasStream] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [lastThumb, setLastThumb] = useState<string | null>(null)

  useEffect(() => {
    let stream: MediaStream | null = null
    let cancelled = false
    if (!navigator.mediaDevices?.getUserMedia) return
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: facing }, audio: false })
      .then((s) => {
        if (cancelled) {
          s.getTracks().forEach((t) => t.stop())
          return
        }
        stream = s
        if (videoRef.current) videoRef.current.srcObject = s
        setHasStream(true)
      })
      .catch(() => setHasStream(false))
    return () => {
      cancelled = true
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [facing])

  const onFiles = async (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length === 0) return
    setBusy(true)
    setError(null)
    try {
      const result = await api.photos.upload(files.slice(0, 20))
      if (result.uploaded.length > 0) {
        setLastThumb(result.uploaded[result.uploaded.length - 1].url)
        navigate(routes.albumCreate())
      } else if (result.rejected.length > 0) {
        setError(toApiError({ message: result.rejected[0].code }))
      }
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen className={styles.screen}>
      <StatusBar tone="light" />
      <TopBar
        left="close"
        to={routes.me()}
        tone="light"
        right={
          <button type="button" className={styles.flash} aria-label="フラッシュ">
            <IconBolt size={22} stroke={1.7} />
          </button>
        }
      />

      <Photo asset="friendsSunset" className={styles.viewfinder} label="カメラのプレビュー">
        <video ref={videoRef} className={styles.video} autoPlay playsInline muted hidden={!hasStream} />
        <button type="button" className={styles.libraryIcon} aria-label="写真ライブラリから選ぶ" onClick={() => libraryRef.current?.click()}>
          <IconPhoto size={18} stroke={1.8} />
        </button>
        {busy && <div className={styles.uploading}>アップロード中…</div>}
      </Photo>

      <div className={styles.modes} aria-label="撮影モード">
        <span>ビデオ</span>
        <span className={styles.activeMode}>写真</span>
      </div>

      <ErrorNote error={error} />

      <div className={styles.controls}>
        <button type="button" className={styles.thumbnail} aria-label="アルバムを作成" onClick={() => navigate(routes.albumCreate())}>
          <Photo asset="tile2" src={lastThumb} className={styles.thumbnailPhoto} />
        </button>
        <button type="button" className={styles.shutter} aria-label="写真を撮る" disabled={busy} onClick={() => captureRef.current?.click()}>
          <span className={styles.shutterCenter} />
        </button>
        <button
          type="button"
          className={styles.flip}
          aria-label="カメラを切り替える"
          onClick={() => setFacing((f) => (f === 'environment' ? 'user' : 'environment'))}
        >
          <IconCameraRotate size={30} stroke={1.6} />
        </button>
      </div>

      <input ref={captureRef} type="file" accept="image/jpeg,image/png" capture="environment" hidden onChange={onFiles} />
      <input ref={libraryRef} type="file" accept="image/jpeg,image/png" multiple hidden onChange={onFiles} />

      <div className={styles.homeIndicator} aria-hidden="true" />
    </Screen>
  )
}
