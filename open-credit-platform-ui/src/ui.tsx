import type { ReactNode } from 'react'

export function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
    </label>
  )
}

export function Badge({ value }: { value?: string }) {
  if (!value) return null
  return <span className={`badge ${value.toLowerCase()}`}>{value.replace('_', ' ')}</span>
}

export function Card({ title, state, children }: { title: string; state?: string; children: ReactNode }) {
  return (
    <section className="card-s">
      <header>
        <h3>{title}</h3>
        {state && <Badge value={state} />}
      </header>
      {children}
    </section>
  )
}

export function Facts({ items }: { items: [string, ReactNode][] }) {
  return (
    <dl className="facts">
      {items.map(([k, v]) => (
        <div key={k}><dt>{k}</dt><dd>{v ?? '—'}</dd></div>
      ))}
    </dl>
  )
}
