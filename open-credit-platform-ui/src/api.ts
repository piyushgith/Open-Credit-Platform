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

export type Session = { token: string; username: string; role: string; expiresAt: string }

export type Loan = {
  applicationReference: string
  status: string
  decision?: string
  approvedAmount?: number
  interestRate?: number
  monthlyEmi?: number
  tenureMonths?: number
  reasons?: string[]
  productType: string
  [k: string]: unknown
}

export type LoanSummary = {
  applicationReference: string
  applicantName: string
  productType: string
  requestedAmount: number
  tenureMonths: number
  status: string
  decision?: string
  createdAt: string
}

export type Offer = {
  id: string
  offerAmount: number
  interestRate: number
  tenureMonths: number
  monthlyEmi: number
  status: string
}

export type Disbursement = {
  sanctionedAmount: number
  disbursedTotal: number
  status: string
  tranches: { trancheNumber: number; amount: number; requestReference: string }[]
}

export const money = (n?: number) =>
  n === undefined ? '—' : new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(n)

export const soft = <T,>(method: string, path: string): Promise<T | null> =>
  call<T>(method, path).catch(() => null)

export const msg = (e: unknown) => (e instanceof Error ? e.message : String(e))

export const STAGES = [
  { status: 'DRAFT', name: 'Draft', blurb: 'Applications captured but not yet submitted. KYC and documents can be prepared here.' },
  { status: 'SUBMITTED', name: 'Submitted', blurb: 'Submitted applications waiting to enter underwriting.' },
  { status: 'UNDERWRITING', name: 'Underwriting', blurb: 'Under review. Referred applications stay here until underwriting is retried.' },
  { status: 'OFFERED', name: 'Offered', blurb: 'Approved applications with offers to create and select, ready for sanction once KYC is verified.' },
  { status: 'SANCTIONED', name: 'Sanctioned', blurb: 'Sanctioned loans awaiting disbursement of the sanctioned amount, in one or more tranches.' },
  { status: 'DISBURSED', name: 'Disbursed', blurb: 'Fully disbursed loans.' },
  { status: 'DECLINED', name: 'Declined', blurb: 'Applications declined by underwriting.' },
] as const

export type StageStatus = (typeof STAGES)[number]['status']

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

export const dt = (s?: string) => (s ? new Date(s).toLocaleString() : '—')
