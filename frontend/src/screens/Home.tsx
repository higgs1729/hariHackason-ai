import { useState, type FormEvent } from 'react'
import { useLocation, useNavigate } from 'react-router-dom'
import { apiMode, type ApiError } from '../api'
import { Logo } from '../components/Logo'
import { ErrorNote } from '../components/Notice'
import { Photo } from '../components/Photo'
import { Screen } from '../components/Screen'
import { StatusBar } from '../components/StatusBar'
import { TopBar } from '../components/TopBar'
import { routes } from '../routes'
import { useAuth } from '../state/auth'
import { toApiError } from '../state/useAsync'
import styles from './Home.module.css'

type Mode = 'closed' | 'login' | 'register'

export function Home() {
  const navigate = useNavigate()
  const location = useLocation()
  const { user, login, register } = useAuth()
  const [mode, setMode] = useState<Mode>('closed')
  const [account, setAccount] = useState(apiMode === 'mock' ? 'nao' : '')
  const [password, setPassword] = useState(apiMode === 'mock' ? 'password' : '')
  const [name, setName] = useState('')
  const [error, setError] = useState<ApiError | null>(null)
  const [busy, setBusy] = useState(false)

  const from = (location.state as { from?: string } | null)?.from
  const next = () => navigate(from ?? routes.camera())

  const start = () => (user ? next() : setMode('register'))

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      if (mode === 'login') await login({ userAccount: account, userPassword: password })
      else await register({ userAccount: account, userPassword: password, userName: name || account })
      next()
    } catch (err) {
      setError(toApiError(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Screen className={styles.screen}>
      <Photo asset="skySunset" className={styles.background}>
        <StatusBar tone="light" />
        <TopBar tone="light" left="none" />

        <main className={styles.intro}>
          <Logo size={110} color="var(--white)" />
          <p className={styles.tagline}>あの時の、最高を、ずっと。</p>
        </main>

        {mode === 'closed' ? (
          <div className={styles.actions}>
            <button type="button" className={styles.start} onClick={start}>
              {user ? `${user.userName ?? user.userAccount} として続ける` : 'はじめる'}
            </button>
            {!user && (
              <button type="button" className={styles.login} onClick={() => setMode('login')}>
                ログイン
              </button>
            )}
          </div>
        ) : (
          <form className={styles.sheet} onSubmit={submit}>
            <h2>{mode === 'login' ? 'ログイン' : 'アカウントを作る'}</h2>
            {mode === 'register' && (
              <input
                className={styles.field}
                placeholder="表示名（友達に見える名前）"
                value={name}
                onChange={(e) => setName(e.target.value)}
                autoComplete="nickname"
              />
            )}
            <input
              className={styles.field}
              placeholder="ID（4文字以上）"
              value={account}
              onChange={(e) => setAccount(e.target.value)}
              autoComplete="username"
              autoCapitalize="none"
              required
              minLength={4}
            />
            <input
              className={styles.field}
              type="password"
              placeholder="パスワード（8文字以上）"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
              required
              minLength={8}
            />
            <ErrorNote error={error} />
            <button type="submit" className={styles.start} disabled={busy}>
              {busy ? '…' : mode === 'login' ? 'ログイン' : 'はじめる'}
            </button>
            <button
              type="button"
              className={styles.login}
              onClick={() => {
                setError(null)
                setMode(mode === 'login' ? 'register' : 'login')
              }}
            >
              {mode === 'login' ? 'アカウントを作る' : 'ログインはこちら'}
            </button>
          </form>
        )}
      </Photo>
    </Screen>
  )
}
