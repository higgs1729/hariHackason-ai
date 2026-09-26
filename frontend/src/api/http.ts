/**
 * HTTP implementation of the API contract. Talks to Spring Boot under `/api`.
 *
 * Rules from 03-detailed-design §3.1 / §3.2 / 02-basic-design §6.1:
 * - `Authorization: Bearer <access>` on everything except /api/auth/**
 * - 401 TOKEN_EXPIRED → refresh once (rotating), retry the original request once
 * - any other 401 → clear session (caller redirects to login)
 * - `If-Match` on album / album_photo / decoration writes; ETag comes back as `version`
 * - `Idempotency-Key` on POST /albums/generate
 * - errors are `{code, message, details, traceId}` → thrown as ApiError
 */
import type { Api } from './contract'
import { tokens } from './tokens'
import { ApiError, type ApiErrorBody, type Decoration, type TokenPair } from './types'

const BASE = '/api'

type Query = Record<string, string | number | boolean | undefined>

function qs(params?: Query): string {
  if (!params) return ''
  const sp = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined) sp.set(k, String(v))
  }
  const s = sp.toString()
  return s ? `?${s}` : ''
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'
  json?: unknown
  form?: FormData
  headers?: Record<string, string>
  /** skip Authorization + refresh logic (auth endpoints) */
  anonymous?: boolean
  /** read the body as a Blob instead of JSON (images) */
  blob?: boolean
}

async function parseError(res: Response): Promise<ApiError> {
  let body: ApiErrorBody
  try {
    body = (await res.json()) as ApiErrorBody
  } catch {
    body = { code: 'NETWORK_ERROR', message: `${res.status} ${res.statusText}` }
  }
  return new ApiError(res.status, body)
}

let refreshInFlight: Promise<TokenPair> | null = null

/** Rotate the refresh token. Shared promise so concurrent 401s refresh once, not N times. */
async function refreshOnce(): Promise<TokenPair> {
  if (!refreshInFlight) {
    const refreshToken = tokens.getRefresh()
    if (!refreshToken) throw new ApiError(401, { code: 'TOKEN_INVALID', message: 'no refresh token' })
    refreshInFlight = rawRequest<TokenPair>('/auth/refresh', { method: 'POST', json: { refreshToken }, anonymous: true })
      .then(({ data }) => {
        tokens.set(data)
        return data
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight as Promise<TokenPair>
}

async function rawRequest<T>(path: string, opts: RequestOptions = {}): Promise<{ data: T; res: Response }> {
  const headers: Record<string, string> = { ...opts.headers }
  if (opts.json !== undefined) headers['Content-Type'] = 'application/json'
  if (!opts.anonymous) {
    const access = tokens.getAccess()
    if (access) headers.Authorization = `Bearer ${access}`
  }
  let res: Response
  try {
    res = await fetch(BASE + path, {
      method: opts.method ?? 'GET',
      headers,
      body: opts.form ?? (opts.json !== undefined ? JSON.stringify(opts.json) : undefined),
    })
  } catch (e) {
    throw new ApiError(0, { code: 'NETWORK_ERROR', message: e instanceof Error ? e.message : 'network error' })
  }
  if (!res.ok) throw await parseError(res)
  if (res.status === 204) return { data: undefined as T, res }
  if (opts.blob) return { data: (await res.blob()) as T, res }
  const text = await res.text()
  return { data: (text ? JSON.parse(text) : undefined) as T, res }
}

async function request<T>(path: string, opts: RequestOptions = {}): Promise<{ data: T; res: Response }> {
  try {
    return await rawRequest<T>(path, opts)
  } catch (e) {
    if (e instanceof ApiError && e.status === 401 && !opts.anonymous) {
      if (e.code === 'TOKEN_EXPIRED') {
        await refreshOnce() // throws → falls through to clear below
        return rawRequest<T>(path, opts)
      }
      tokens.clear()
    }
    throw e
  }
}

const json = async <T>(path: string, opts?: RequestOptions) => (await request<T>(path, opts)).data

/**
 * GET an image that sits behind auth (`/api/photos/{id}`, `/thumb`, `/composite`).
 * `<img>` and CSS backgrounds cannot send `Authorization`, so the caller turns
 * the Blob into an object URL. Same refresh-on-TOKEN_EXPIRED rule as JSON calls.
 */
export const fetchImage = async (apiUrl: string): Promise<Blob> =>
  (await request<Blob>(apiUrl.replace(/^\/api/, ''), { blob: true })).data

/** Reads the ETag header as the numeric `version`. */
function etagVersion(res: Response): number {
  const raw = res.headers.get('ETag') ?? ''
  return Number(raw.replace(/"/g, '')) || 0
}

export const httpApi: Api = {
  auth: {
    register: (body) => json('/auth/register', { method: 'POST', json: body, anonymous: true }),
    login: (body) => json('/auth/login', { method: 'POST', json: body, anonymous: true }),
    refresh: refreshOnce,
    logout: (refreshToken) => json('/auth/logout', { method: 'POST', json: { refreshToken } }),
    me: () => json('/auth/me'),
  },

  users: {
    me: () => json('/users/me'),
    patchMe: (body) => json('/users/me', { method: 'PATCH', json: body }),
    get: (id) => json(`/users/${id}`),
    search: (q) => json(`/users/search${qs({ q })}`),
  },

  friends: {
    list: () => json('/friends'),
    incoming: () => json('/friends/requests'),
    request: (userId) => json('/friends/requests', { method: 'POST', json: { userId } }),
    accept: (requestId) => json(`/friends/requests/${requestId}/accept`, { method: 'POST' }),
    qr: () => json('/friends/qr', { method: 'POST' }),
    acceptQr: (qrToken) => json(`/friends/qr/${encodeURIComponent(qrToken)}/accept`, { method: 'POST' }),
  },

  photos: {
    upload: (files) => {
      const form = new FormData()
      for (const f of files) form.append('files', f, f.name)
      return json('/photos', { method: 'POST', form })
    },
    list: (params) => json(`/photos${qs(params as Query)}`),
  },

  albums: {
    generate: (photoIds, idempotencyKey) =>
      json('/albums/generate', { method: 'POST', json: { photoIds }, headers: { 'Idempotency-Key': idempotencyKey } }),
    job: (jobId) => json(`/albums/jobs/${jobId}`),
    list: (params) => json(`/albums${qs(params)}`),
    get: (albumId) => json(`/albums/${albumId}`),
    patch: (albumId, version, body) =>
      json(`/albums/${albumId}`, { method: 'PATCH', json: body, headers: { 'If-Match': `"${version}"` } }),
    patchPhoto: (albumId, albumPhotoId, version, body) =>
      json(`/albums/${albumId}/photos/${albumPhotoId}`, {
        method: 'PATCH',
        json: body,
        headers: { 'If-Match': `"${version}"` },
      }),
    addMember: (albumId, userId) => json(`/albums/${albumId}/members`, { method: 'POST', json: { userId } }),
  },

  decoration: {
    get: async (albumId, albumPhotoId) => {
      const { data, res } = await request<Decoration>(
        `/albums/${albumId}/photos/${albumPhotoId}/decoration`,
      )
      return { ...data, version: etagVersion(res) }
    },
    put: async (albumId, albumPhotoId, version, elements) => {
      const { res } = await request(`/albums/${albumId}/photos/${albumPhotoId}/decoration`, {
        method: 'PUT',
        json: { elements },
        headers: { 'If-Match': `"${version}"` },
      })
      return { version: etagVersion(res) || version + 1 }
    },
    uploadRendered: (albumId, albumPhotoId, overlay, composite) => {
      const form = new FormData()
      form.append('overlay', overlay, 'overlay.png')
      form.append('composite', composite, 'composite.jpg')
      return json(`/albums/${albumId}/photos/${albumPhotoId}/decoration/rendered`, { method: 'POST', form })
    },
  },

  share: {
    create: (albumId) => json(`/albums/${albumId}/share`, { method: 'POST' }),
    get: (albumId) => json(`/albums/${albumId}/share`),
  },

  capsules: {
    create: (body) => json('/capsules', { method: 'POST', json: body }),
    list: () => json('/capsules'),
    get: (capsuleId) => json(`/capsules/${capsuleId}`),
    open: (capsuleId) => json(`/capsules/${capsuleId}/open`, { method: 'POST' }),
    unsealNow: (capsuleId) => json(`/capsules/${capsuleId}/unseal-now`, { method: 'POST' }),
  },

  hints: {
    shoot: (body) => json('/hints/shoot', { method: 'POST', json: body }),
  },
}
