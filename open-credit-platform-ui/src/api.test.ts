import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiFailure, call, loadStats, setToken, setUnauthorizedHandler } from './api'

type Reply = { status?: number; body?: unknown }

function mockFetch(...replies: Reply[]) {
  const fn = vi.fn(async () => {
    const r = replies.shift() ?? {}
    const status = r.status ?? 200
    return { ok: status >= 200 && status < 300, status, json: async () => r.body ?? {} } as Response
  })
  vi.stubGlobal('fetch', fn)
  return fn
}

const headersOf = (fn: ReturnType<typeof mockFetch>, i = 0) =>
  ((fn.mock.calls[i] as unknown as [string, RequestInit])[1].headers ?? {}) as Record<string, string>

beforeEach(() => {
  setToken(null)
  setUnauthorizedHandler(null)
})
afterEach(() => vi.unstubAllGlobals())

describe('call', () => {
  it('returns the envelope data and sends the bearer token only when signed in', async () => {
    const fetchMock = mockFetch({ body: { status: 'SUCCESS', data: { n: 1 } } }, { body: { status: 'SUCCESS', data: 2 } })
    expect(await call('GET', '/x')).toEqual({ n: 1 })
    expect(headersOf(fetchMock).Authorization).toBeUndefined()

    setToken('abc')
    await call('GET', '/y')
    expect(headersOf(fetchMock, 1).Authorization).toBe('Bearer abc')
  })

  it('sends JSON content type only when there is a body', async () => {
    const fetchMock = mockFetch({ body: { status: 'SUCCESS' } }, { body: { status: 'SUCCESS' } })
    await call('POST', '/x', { a: 1 })
    await call('POST', '/y')
    expect(headersOf(fetchMock, 0)['Content-Type']).toBe('application/json')
    expect(headersOf(fetchMock, 1)['Content-Type']).toBeUndefined()
  })

  it('throws an ApiFailure carrying the stable error code', async () => {
    mockFetch({ status: 409, body: { status: 'ERROR', errors: [{ code: 'OFFER_ALREADY_SELECTED', message: 'Already selected' }] } })
    await expect(call('POST', '/x')).rejects.toMatchObject({ message: 'Already selected', code: 'OFFER_ALREADY_SELECTED' })
  })

  it('shows field errors in preference to the generic message', async () => {
    mockFetch({ status: 422, body: { status: 'ERROR', message: 'Request validation failed', errors: [{ code: 'VALIDATION_ERROR', message: 'Request validation failed', fieldErrors: { phoneNumber: 'must be a 10-digit number', email: 'must be valid' } }] } })
    await expect(call('POST', '/x')).rejects.toThrow('must be a 10-digit number; must be valid')
  })

  it('reports an unreachable API', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
    const err = (await call('GET', '/x').catch((e: unknown) => e)) as ApiFailure
    expect(err).toBeInstanceOf(ApiFailure)
    expect(err.message).toMatch(/Cannot reach the API/)
  })

  it('signs out on a 401 while signed in', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    setToken('expired')
    const fetchMock = mockFetch({ status: 401, body: { status: 'ERROR' } }, { body: { status: 'SUCCESS' } })

    await expect(call('GET', '/x')).rejects.toMatchObject({ code: 'SESSION_EXPIRED' })
    expect(handler).toHaveBeenCalledOnce()

    await call('GET', '/y')
    expect(headersOf(fetchMock, 1).Authorization).toBeUndefined()
  })

  it('does not treat a failed login (401 without a token) as an expired session', async () => {
    const handler = vi.fn()
    setUnauthorizedHandler(handler)
    mockFetch({ status: 401, body: { status: 'ERROR', errors: [{ code: 'BAD_CREDENTIALS', message: 'Invalid username or password' }] } })
    await expect(call('POST', '/api/auth/login', {})).rejects.toThrow('Invalid username or password')
    expect(handler).not.toHaveBeenCalled()
  })
})

describe('loadStats', () => {
  it('maps the server stats and recent applications', async () => {
    mockFetch(
      {
        body: {
          status: 'SUCCESS',
          data: {
            byStatus: [{ status: 'DRAFT', count: 3, requestedAmount: 300 }, { status: 'OFFERED', count: 1, requestedAmount: 50 }],
            byProduct: [{ productType: 'PERSONAL', count: 4 }],
            createdPerDay: [{ date: '2026-10-18', count: 2 }],
            referredCount: 5,
          },
        },
      },
      { body: { status: 'SUCCESS', data: { content: [{ applicationReference: 'LN-1' }] } } },
    )
    const s = await loadStats()
    expect(s.counts).toEqual({ DRAFT: 3, OFFERED: 1 })
    expect(s.amounts).toEqual({ DRAFT: 300, OFFERED: 50 })
    expect(s.byProduct).toEqual({ PERSONAL: 4 })
    expect(s.perDay).toEqual([{ date: '2026-10-18', count: 2 }])
    expect(s.referred).toBe(5)
    expect(s.recent.map((l) => l.applicationReference)).toEqual(['LN-1'])
  })
})
