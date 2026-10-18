import { afterEach, describe, expect, it, vi } from 'vitest'
import { loadStats } from './dashboardService'

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

afterEach(() => vi.unstubAllGlobals())

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
