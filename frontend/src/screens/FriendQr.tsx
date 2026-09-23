import { useEffect, useRef, useState, type FormEvent } from 'react'
import { IconQrcode, IconScan, IconUserCheck } from '@tabler/icons-react'
import jsQR from 'jsqr'
import QRCode from 'qrcode'
import { Link, useSearchParams } from 'react-router-dom'
import { api, friendQrPayload, parseFriendQr, type ApiError, type User } from '../api'
import { ErrorNote, Loading } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import { toApiError, useAsync } from '../state/useAsync'
import styles from './FriendQr.module.css'

type Mode = 'show' | 'scan'

/**
 * Add a friend by showing / scanning a QR (row 8, 07-screens-beyond-poster §4).
 * Web Bluetooth / NFC are not on iOS Safari, so "近くのスマホ" is a QR shown in person.
 * The token lives 10 minutes and works once (05 §3.2); the screen refreshes it when it runs out.
 */
export function FriendQr() {
  const [params, setParams] = useSearchParams()
  const mode: Mode = params.get('mode') === 'scan' ? 'scan' : 'show'
  const [friend, setFriend] = useState<User | null>(null)

  return (
    <Screen className={styles.screen}>
      <header className={styles.header}>
        <StatusBar tone="light" />
        <TopBar tone="light" title="友達を追加" to={routes.me('friends')} />
        <div className={styles.switch} role="tablist" aria-label="QR">
          <button type="button" role="tab" aria-selected={mode === 'show'} className={mode === 'show' ? styles.on : undefined} onClick={() => setParams({ mode: 'show' }, { replace: true })}>
            <IconQrcode size={18} stroke={1.8} aria-hidden="true" /> 見せる
          </button>
          <button type="button" role="tab" aria-selected={mode === 'scan'} className={mode === 'scan' ? styles.on : undefined} onClick={() => setParams({ mode: 'scan' }, { replace: true })}>
            <IconScan size={18} stroke={1.8} aria-hidden="true" /> 読み取る
          </button>
        </div>
      </header>

      <main className={styles.content}>{friend ? <Added friend={friend} /> : mode === 'show' ? <ShowQr /> : <ScanQr onAdded={setFriend} />}</main>
    </Screen>
  )
}

function ShowQr() {
  const qr = useAsync(() => api.friends.qr(), [])
  const [image, setImage] = useState<string | null>(null)
  const [left, setLeft] = useState<number | null>(null)

  useEffect(() => {
    if (!qr.data) return
    let cancelled = false
    QRCode.toDataURL(friendQrPayload(qr.data.qrToken), { width: 560, margin: 1, color: { dark: '#14264d', light: '#ffffff' } })
      .then((url) => !cancelled && setImage(url))
      .catch(() => !cancelled && setImage(null))
    return () => {
      cancelled = true
    }
  }, [qr.data])

  // countdown; a new token is fetched when this one runs out
  const { data, reload } = qr
  useEffect(() => {
    if (!data) return
    const expires = new Date(data.expireTime).getTime()
    const tick = () => {
      const s = Math.max(0, Math.round((expires - Date.now()) / 1000))
      setLeft(s)
      if (s === 0) reload()
    }
    tick()
    const t = setInterval(tick, 1000)
    return () => clearInterval(t)
  }, [data, reload])

  if (qr.loading && !qr.data) return <Loading label="QRを作っています" />
  if (qr.error) return <ErrorNote error={qr.error} />
  return (
    <div className={styles.show}>
      <div className={styles.qrCard}>{image ? <img src={image} alt="友達追加のQRコード" className={styles.qr} /> : <Loading />}</div>
      <p className={styles.lead}>友達にこの画面を読み取ってもらってね</p>
      {left !== null && (
        <p className={styles.timer}>
          あと {Math.floor(left / 60)}:{String(left % 60).padStart(2, '0')} で新しいQRに変わります
        </p>
      )}
    </div>
  )
}

function ScanQr({ onAdded }: { onAdded: (u: User) => void }) {
  const videoRef = useRef<HTMLVideoElement>(null)
  const [camera, setCamera] = useState<'starting' | 'on' | 'off'>('starting')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)
  const [notOurs, setNotOurs] = useState(false)
  const [code, setCode] = useState('')
  const busyRef = useRef(false)

  const accept = async (token: string) => {
    if (busyRef.current) return
    busyRef.current = true
    setBusy(true)
    setError(null)
    try {
      onAdded(await api.friends.acceptQr(token))
    } catch (err) {
      setError(toApiError(err))
    } finally {
      busyRef.current = false
      setBusy(false)
    }
  }
  // the scan loop reads the latest `accept` through a ref instead of restarting the camera
  const acceptRef = useRef(accept)
  useEffect(() => {
    acceptRef.current = accept
  })

  useEffect(() => {
    let stream: MediaStream | null = null
    let frame = 0
    let cancelled = false
    const canvas = document.createElement('canvas')
    const ctx = canvas.getContext('2d', { willReadFrequently: true })

    const scan = () => {
      const v = videoRef.current
      if (cancelled || !v || !ctx) return
      if (v.readyState >= v.HAVE_ENOUGH_DATA && !busyRef.current) {
        // decode a downscaled frame: jsQR is pure JS and full-res frames drop the frame rate
        const scale = Math.min(1, 480 / Math.max(v.videoWidth, v.videoHeight))
        canvas.width = Math.round(v.videoWidth * scale)
        canvas.height = Math.round(v.videoHeight * scale)
        ctx.drawImage(v, 0, 0, canvas.width, canvas.height)
        const img = ctx.getImageData(0, 0, canvas.width, canvas.height)
        const hit = jsQR(img.data, img.width, img.height, { inversionAttempts: 'dontInvert' })
        if (hit) {
          const token = parseFriendQr(hit.data)
          setNotOurs(token === null)
          if (token) void acceptRef.current(token)
        }
      }
      frame = requestAnimationFrame(scan)
    }

    if (!navigator.mediaDevices?.getUserMedia) {
      setCamera('off')
      return
    }
    navigator.mediaDevices
      .getUserMedia({ video: { facingMode: 'environment' }, audio: false })
      .then((s) => {
        if (cancelled) return s.getTracks().forEach((t) => t.stop())
        stream = s
        if (videoRef.current) {
          videoRef.current.srcObject = s
          void videoRef.current.play().catch(() => undefined)
        }
        setCamera('on')
        frame = requestAnimationFrame(scan)
      })
      .catch(() => setCamera('off'))
    return () => {
      cancelled = true
      cancelAnimationFrame(frame)
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [])

  const submitCode = (e: FormEvent) => {
    e.preventDefault()
    const token = parseFriendQr(code) ?? code.trim()
    if (token) void accept(token)
  }

  return (
    <div className={styles.scan}>
      <div className={styles.viewfinder}>
        <video ref={videoRef} className={styles.video} playsInline muted hidden={camera !== 'on'} />
        {camera === 'starting' && <Loading label="カメラを起動中" />}
        {camera === 'off' && <p className={styles.noCamera}>カメラが使えません。下にコードを入力してね</p>}
        {camera === 'on' && <span className={styles.frame} aria-hidden="true" />}
        {busy && <div className={styles.busy}>友達を追加中…</div>}
      </div>
      {notOurs && !busy && <p className={styles.note}>aiのQRではないみたい</p>}
      <ErrorNote error={error} />

      <form className={styles.codeForm} onSubmit={submitCode}>
        <label htmlFor="qr-code">読み取れないときはコードを入力</label>
        <div>
          <input id="qr-code" value={code} onChange={(e) => setCode(e.target.value)} placeholder="ai://friend/…" autoCapitalize="none" autoComplete="off" />
          <button type="submit" disabled={busy || !code.trim()}>
            追加
          </button>
        </div>
      </form>
    </div>
  )
}

function Added({ friend }: { friend: User }) {
  return (
    <div className={styles.added} aria-live="polite">
      <Photo asset="avatarA" src={friend.userAvatar} className={styles.addedAvatar} />
      <p className={styles.addedName}>
        <IconUserCheck size={20} stroke={1.8} aria-hidden="true" /> {friend.userName ?? friend.userAccount} と友達になった！
      </p>
      <Link to={routes.reunion(friend.id)} className={styles.primary}>
        一緒の思い出を見る
      </Link>
      <Link to={routes.me('friends')} className={styles.secondary}>
        マイページへ
      </Link>
    </div>
  )
}
