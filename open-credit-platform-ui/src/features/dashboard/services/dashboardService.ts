import { call } from '../../../services/http'
import type { LoanSummary } from '../../loans'

type ServerStats = {
  byStatus: { status: string; count: number; requestedAmount: number }[]
  byProduct: { productType: string; count: number }[]
  createdPerDay: { date: string; count: number }[]
  referredCount: number
}

export type Stats = {
  counts: Record<string, number>
  amounts: Record<string, number> // requested value per status
  byProduct: Record<string, number>
  perDay: { date: string; count: number }[] // last 14 UTC days
  referred: number
  recent: LoanSummary[]
}

export async function loadStats(): Promise<Stats> {
  const [s, recent] = await Promise.all([
    call<ServerStats>('GET', '/api/loans/stats'),
    call<{ content: LoanSummary[] }>('GET', '/api/loans?size=8'),
  ])
  return {
    counts: Object.fromEntries(s.byStatus.map((x) => [x.status, x.count])),
    amounts: Object.fromEntries(s.byStatus.map((x) => [x.status, x.requestedAmount])),
    byProduct: Object.fromEntries(s.byProduct.map((x) => [x.productType, x.count])),
    perDay: s.createdPerDay,
    referred: s.referredCount,
    recent: recent.content,
  }
}
