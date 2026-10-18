export const API = import.meta.env.VITE_API_URL ?? 'http://localhost:9000'

export class ApiFailure extends Error {
  code?: string
  constructor(message: string, code?: string) {
    super(message)
    this.code = code
  }
}

type Envelope<T> = {
  status: 'SUCCESS' | 'ERROR'
  message?: string
  data?: T
  errors?: { code?: string; message?: string; fieldErrors?: Record<string, string> }[]
}

// The token lives in module memory only: a refresh signs the user out rather than
// leaving a bearer token in web storage.
let token: string | null = null
export const setToken = (t: string | null) => {
  token = t
}

// Called when a signed-in request is refused with 401 (token expired or revoked).
let onUnauthorized: (() => void) | null = null
export const setUnauthorizedHandler = (fn: (() => void) | null) => {
  onUnauthorized = fn
}

export async function call<T = unknown>(method: string, path: string, body?: unknown): Promise<T> {
  let res: Response
  try {
    res = await fetch(`${API}${path}`, {
      method,
      headers: {
        ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiFailure(`Cannot reach the API at ${API}. Is the backend running?`)
  }
  if (res.status === 401 && token) {
    token = null
    onUnauthorized?.()
    throw new ApiFailure('Your session has expired. Please sign in again.', 'SESSION_EXPIRED')
  }
  const json = (await res.json().catch(() => ({}))) as Envelope<T>
  if (!res.ok || json.status === 'ERROR') {
    const err = json.errors?.[0]
    const fields = err?.fieldErrors ? Object.values(err.fieldErrors).join('; ') : ''
    throw new ApiFailure(fields || err?.message || json.message || `Request failed (${res.status})`, err?.code)
  }
  return json.data as T
}

export const soft = <T,>(method: string, path: string): Promise<T | null> =>
  call<T>(method, path).catch(() => null)
