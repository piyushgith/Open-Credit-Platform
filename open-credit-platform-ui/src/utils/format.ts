export const money = (n?: number) =>
  n === undefined ? '—' : new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2 }).format(n)

export const msg = (e: unknown) => (e instanceof Error ? e.message : String(e))

export const dt = (s?: string) => (s ? new Date(s).toLocaleString() : '—')
