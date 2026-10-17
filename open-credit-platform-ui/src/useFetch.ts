import { useEffect, useState } from 'react'
import { msg } from './api'

type State<T> = { key: string; data?: T; error?: string }

// Loads on mount and whenever `deps` change; `loading` is derived, so no setState runs
// synchronously in the effect.
export function useFetch<T>(load: () => Promise<T>, deps: unknown[]) {
  const [tick, setTick] = useState(0)
  const [state, setState] = useState<State<T>>({ key: '' })
  const key = JSON.stringify([...deps, tick])
  useEffect(() => {
    let live = true
    load()
      .then((data) => live && setState({ key, data }))
      .catch((e) => live && setState({ key, error: msg(e) }))
    return () => {
      live = false
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key])
  const fresh = state.key === key
  return {
    // Stale data stays visible while a reload/page change is in flight (callers key by entity).
    data: state.data,
    error: fresh ? state.error : undefined,
    loading: !fresh,
    reload: () => setTick((t) => t + 1),
  }
}
