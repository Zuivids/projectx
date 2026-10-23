import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'

export interface Loaded<T> {
  data: T | null
  /** Latvian error text, or null */
  error: string | null
  /** HTTP status of the error, e.g. 404 */
  status: number | null
  loading: boolean
  /** Replaces the data, e.g. with what a save returned */
  setData: (data: T) => void
}

/** Loads data when the component appears and whenever `key` changes. */
export function useLoad<T>(load: () => Promise<T>, key: string): Loaded<T> {
  const [state, setState] = useState<{ key: string; data: T | null; error: string | null; status: number | null }>(
    { key: '', data: null, error: null, status: null },
  )

  useEffect(() => {
    let cancelled = false
    load().then(
      (data) => !cancelled && setState({ key, data, error: null, status: null }),
      (err: unknown) => {
        if (cancelled) return
        const status = err instanceof ApiError ? err.status : null
        setState({ key, data: null, error: errorText(status), status })
      },
    )
    return () => {
      cancelled = true
    }
    // `load` is a new function on every render; `key` says when to load again
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])

  const setData = useCallback((data: T) => setState((s) => ({ ...s, data, error: null, status: null })), [])
  // Data from an earlier key is not shown while the new key loads
  const current = state.key === key
  return {
    data: current ? state.data : null,
    error: current ? state.error : null,
    status: current ? state.status : null,
    loading: !current,
    setData,
  }
}

function errorText(status: number | null): string {
  if (status === 404) return 'Nav atrasts'
  if (status === 403) return 'Nav piekļuves'
  return 'Neizdevās ielādēt. Mēģini vēlreiz.'
}
