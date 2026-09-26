import { useEffect, useRef, useState, type ChangeEvent } from 'react'
import { IconBolt, IconBoltOff, IconCameraRotate, IconPhoto, IconSparkles, IconX } from '@tabler/icons-react'
import { useNavigate } from 'react-router-dom'
import { api, type ApiError, type ShootHint } from '../api'
import { ErrorNote } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { ShootHintSheet } from '../components/ShootHintSheet'
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
  /** shots uploaded from this screen; the thumbnail shows it and leads to screen 03 */
  const [shotCount, setShotCount] = useState(0)
  const trackRef = useRef<MediaStreamTrack | null>(null)
  /** null = this camera has no torch (iOS Safari, most desktops): the button is hidden */
  const [torch, setTorch] = useState<boolean | null>(null)
  const [sheetOpen, setSheetOpen] = useState(false)
  /** the hint the user chose; pinned on the viewfinder while shooting */
  const [hint, setHint] = useState<ShootHint | null>(null)

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
        const track = s.getVideoTracks()[0] ?? null
        trackRef.current = track
        const caps = (track?.getCapabilities?.() ?? {}) as { torch?: boolean }
        setTorch(caps.torch ? false : null)
      })
      .catch(() => setHasStream(false))
    return () => {
      cancelled = true
      trackRef.current = null
      setTorch(null)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [facing])

  const toggleTorch = async () => {
    const next = !torch
    try {
      await trackRef.current?.applyConstraints({ advanced: [{ torch: next } as MediaTrackConstraintSet] })
      setTorch(next)
    } catch {
      setTorch(null)
    }
  }

  /**
   * The shutter keeps you here so a group can take several shots in a row
   * (the demo takes three); the thumbnail, with its count, moves on to 03.
   * Picking from the library is a batch already, so it moves on at once.
   */
  const onFiles = (stay: boolean) => async (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? [])
    e.target.value = ''
    if (files.length === 0) return
    setBusy(true)
    setError(null)
    try {
      const result = await api.photos.upload(files.slice(0, 20))
      if (result.uploaded.length > 0) {
        setLastThumb(result.uploaded[result.uploaded.length - 1].url)
        setShotCount((n) => n + result.uploaded.length)
        if (!stay) navigate(routes.albumCreate())
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
          torch !== null && (
            <button type="button" className={styles.flash} aria-label={torch ? 'ライトを消す' : 'ライトをつける'} aria-pressed={torch} onClick={() => void toggleTorch()}>
              {torch ? <IconBolt size={22} stroke={1.7} /> : <IconBoltOff size={22} stroke={1.7} />}
            </button>
          )
        }
      />

      <Photo asset="friendsSunset" className={styles.viewfinder} label="カメラのプレビュー">
        <video ref={videoRef} className={styles.video} autoPlay playsInline muted hidden={!hasStream} />
        <button type="button" className={styles.libraryIcon} aria-label="写真ライブラリから選ぶ" onClick={() => libraryRef.current?.click()}>
          <IconPhoto size={18} stroke={1.8} />
        </button>
        {hint ? (
          <div className={styles.pinnedHint}>
            <p>{hint.hint}</p>
            <button type="button" onClick={() => setHint(null)} aria-label="撮り方を消す">
              <IconX size={16} stroke={2} />
            </button>
          </div>
        ) : (
          <button type="button" className={styles.askHint} onClick={() => setSheetOpen(true)}>
            <IconSparkles size={16} stroke={1.8} aria-hidden="true" /> AIに撮り方を聞く
          </button>
        )}
        {busy && <div className={styles.uploading}>アップロード中…</div>}
      </Photo>

      <div className={styles.modes} aria-label="撮影モード">
        <span>ビデオ</span>
        <span className={styles.activeMode}>写真</span>
      </div>

      <ErrorNote error={error} />

      <div className={styles.controls}>
        <button type="button" className={styles.thumbnail} aria-label={shotCount > 0 ? `撮った${shotCount}枚でアルバムを作る` : 'アルバムを作成'} onClick={() => navigate(routes.albumCreate())}>
          <Photo asset="tile2" src={lastThumb} className={styles.thumbnailPhoto} />
          {shotCount > 0 && <span className={styles.shotCount}>{shotCount}</span>}
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

      <input ref={captureRef} type="file" accept="image/jpeg,image/png" capture="environment" hidden onChange={onFiles(true)} />
      <input ref={libraryRef} type="file" accept="image/jpeg,image/png" multiple hidden onChange={onFiles(false)} />

      <div className={styles.homeIndicator} aria-hidden="true" />

      {sheetOpen && (
        <ShootHintSheet
          onClose={() => setSheetOpen(false)}
          onUse={(h) => {
            setHint(h)
            setSheetOpen(false)
          }}
        />
      )}
    </Screen>
  )
}
