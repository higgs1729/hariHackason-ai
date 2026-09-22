/**
 * Entry point for screens. Picks the implementation from `VITE_API_MODE`:
 *   mock (default in dev) — in-memory, no backend needed
 *   http                  — Spring Boot under /api (Vite proxies to :8080)
 *
 * Production builds always use http.
 */
import type { Api } from './contract'
import { httpApi } from './http'
import { mockApi } from './mock'

const mode = (import.meta.env.VITE_API_MODE as string | undefined) ?? (import.meta.env.DEV ? 'mock' : 'http')

export const api: Api = mode === 'mock' ? mockApi : httpApi
export const apiMode = mode

export * from './types'
export type { Api } from './contract'
export { tokens } from './tokens'
