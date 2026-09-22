/**
 * Token storage (02-basic-design §6.1).
 *
 * - access token: memory only, 15 min, sent as `Authorization: Bearer`
 * - refresh token: localStorage, 30 days, rotated on every refresh
 *
 * The spec names memory for the access token but leaves the refresh token's
 * client home open. localStorage is the choice here so a reload does not log
 * the user out mid-demo. Recorded in docs/design/04-frontend-handoff.md.
 */
import type { User } from './types'

const REFRESH_KEY = 'ai.refreshToken'

let accessToken: string | null = null
let currentUser: User | null = null
const listeners = new Set<() => void>()

function notify() {
  for (const l of listeners) l()
}

export const tokens = {
  getAccess: () => accessToken,
  getRefresh: (): string | null => {
    try {
      return localStorage.getItem(REFRESH_KEY)
    } catch {
      return null
    }
  },
  getUser: () => currentUser,

  set(pair: { accessToken: string; refreshToken: string; user: User }) {
    accessToken = pair.accessToken
    currentUser = pair.user
    try {
      localStorage.setItem(REFRESH_KEY, pair.refreshToken)
    } catch {
      /* private mode: session survives until reload only */
    }
    notify()
  },

  setUser(user: User) {
    currentUser = user
    notify()
  },

  clear() {
    accessToken = null
    currentUser = null
    try {
      localStorage.removeItem(REFRESH_KEY)
    } catch {
      /* ignore */
    }
    notify()
  },

  subscribe(listener: () => void) {
    listeners.add(listener)
    return () => listeners.delete(listener)
  },
}
