/**
 * Resolves image URLs from API responses into something `<img>` / CSS / canvas
 * can load.
 *
 * Every `/api/...` image needs `Authorization: Bearer`, which the browser does
 * not send for `<img src>` or `background-image`. So those are fetched once
 * with the token and exposed as `blob:` object URLs. Anything else (data URLs
 * in mock mode, `/storage/...`, absolute URLs) is returned as-is.
 *
 * The cache lives for the session and is dropped on logout.
 */
import { useEffect, useState } from 'react'
import { fetchImage } from './http'
import { tokens } from './tokens'

const cache = new Map<string, Promise<string>>()

tokens.subscribe(() => {
  if (tokens.getAccess() !== null) return
  for (const p of cache.values()) p.then(URL.revokeObjectURL, () => undefined)
  cache.clear()
})

const needsAuth = (src: string) => src.startsWith('/api/')

export function resolveImage(src: string): Promise<string> {
  if (!needsAuth(src)) return Promise.resolve(src)
  let p = cache.get(src)
  if (!p) {
    p = fetchImage(src).then((blob) => URL.createObjectURL(blob))
    p.catch(() => cache.delete(src)) // let a later render retry
    cache.set(src, p)
  }
  return p
}

/** Hook form of `resolveImage`. Returns null while an authed image is loading. */
export function useImageSrc(src: string | null | undefined): string | null {
  const [resolved, setResolved] = useState<{ from: string; to: string } | null>(null)

  useEffect(() => {
    if (!src || !needsAuth(src)) return
    let alive = true
    resolveImage(src).then(
      (to) => alive && setResolved({ from: src, to }),
      () => undefined,
    )
    return () => {
      alive = false
    }
  }, [src])

  if (!src) return null
  if (!needsAuth(src)) return src
  return resolved?.from === src ? resolved.to : null
}
