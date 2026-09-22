/**
 * Session state. Wraps `tokens` in React so screens re-render on login/logout.
 *
 * On first mount, if a refresh token survived in localStorage, we try one
 * refresh so a page reload keeps the user signed in (02-basic-design §6.1).
 */
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import { api, tokens, type LoginRequest, type RegisterRequest, type User } from '../api'

interface AuthState {
  user: User | null
  /** true until the initial refresh attempt has settled */
  loading: boolean
  login(body: LoginRequest): Promise<User>
  register(body: RegisterRequest): Promise<User>
  logout(): Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(tokens.getUser())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const unsubscribe = tokens.subscribe(() => setUser(tokens.getUser()))
    return () => {
      unsubscribe()
    }
  }, [])

  useEffect(() => {
    let cancelled = false
    const refresh = tokens.getRefresh()
    if (!refresh) {
      setLoading(false)
      return
    }
    api.auth
      .refresh(refresh)
      .then((pair) => {
        if (!cancelled) tokens.set(pair)
      })
      .catch(() => tokens.clear())
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [])

  const value: AuthState = {
    user,
    loading,
    async login(body) {
      const pair = await api.auth.login(body)
      tokens.set(pair)
      return pair.user
    },
    async register(body) {
      const pair = await api.auth.register(body)
      tokens.set(pair)
      return pair.user
    },
    async logout() {
      const refresh = tokens.getRefresh()
      tokens.clear()
      if (refresh) await api.auth.logout(refresh).catch(() => undefined)
    },
  }

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}
