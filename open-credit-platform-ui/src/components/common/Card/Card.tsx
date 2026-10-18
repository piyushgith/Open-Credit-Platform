import type { ReactNode } from 'react'
import { Badge } from '../Badge'

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
