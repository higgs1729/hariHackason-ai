import { useCallback, useEffect, useState, type DependencyList } from 'react'
import { ApiError } from '../api'

export interface AsyncState<T> {
  data: T | null
  error: ApiError | null
  loading: boolean
  reload: () => void
}

/** Runs `fn` on mount and whenever `deps` change. Errors are kept as ApiError for `code` branching. */
export function useAsync<T>(fn: () => Promise<T>, deps: DependencyList): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<ApiError | null>(null)
  const [loading, setLoading] = useState(true)
  const [tick, setTick] = useState(0)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fn()
      .then((d) => {
        if (!cancelled) setData(d)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(toApiError(e))
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick])

  const reload = useCallback(() => setTick((t) => t + 1), [])
  return { data, error, loading, reload }
}

export function toApiError(e: unknown): ApiError {
  if (e instanceof ApiError) return e
  return new ApiError(0, { code: 'NETWORK_ERROR', message: e instanceof Error ? e.message : String(e) })
}

/** Short Japanese message per error code. Anything unknown falls back to the server message. */
export function describeError(e: ApiError): string {
  switch (e.code) {
    case 'NETWORK_ERROR':
      return '通信できませんでした'
    case 'CREDENTIALS_INVALID':
      return 'IDかパスワードが違います'
    case 'ACCOUNT_EXISTS':
      return 'そのIDはもう使われています'
    case 'VALIDATION_FAILED':
      return 'IDは4文字以上、パスワードは8文字以上です'
    case 'TOKEN_INVALID':
    case 'TOKEN_EXPIRED':
      return 'もう一度ログインしてください'
    case 'JOB_ALREADY_RUNNING':
      return 'アルバムを作成中です。少し待ってね'
    case 'NO_VALID_PHOTOS':
      return '写真を選んでください'
    case 'VERSION_CONFLICT':
      return '誰かが先に編集しました。読み込み直します'
    case 'CAPSULE_NOT_YET_OPEN':
      return 'まだ開けられません'
    case 'NOT_FRIENDS':
      return '友達だけ招待できます'
    case 'FILE_TOO_LARGE':
      return '10MBより小さい写真にしてください'
    case 'UNSUPPORTED_MEDIA':
      return 'JPEGかPNGの写真にしてください'
    case 'RATE_LIMITED':
      return '少し待ってね'
    default:
      return e.message || e.code
  }
}
