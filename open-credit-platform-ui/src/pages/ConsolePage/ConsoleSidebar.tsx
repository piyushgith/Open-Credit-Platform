import { STAGES } from '../../features/loans'
import type { Session } from '../../types/session.types'
import type { View } from './route'

type Props = {
  view: View
  session: Session
  maker: boolean
  counts?: Record<string, number>
  pending?: number
  onLogout: () => void
}

export default function ConsoleSidebar({ view, session, maker, counts, pending, onLogout }: Props) {
  const link = (href: string, active: boolean, label: string, n?: number) => (
    <li key={href}>
      <a href={href} aria-current={active ? 'page' : undefined}>
        <span>{label}</span>
        {n !== undefined && <span className="nav-n">{n.toLocaleString('en-IN')}</span>}
      </a>
    </li>
  )

  return (
    <nav className="side" aria-label="Console">
      <ul>
        {link('#/console', view.kind === 'dashboard', 'Dashboard')}
        {maker && link('#/console/new', view.kind === 'new', 'New application')}
        {link('#/console/search', view.kind === 'search', 'Search')}
        {link('#/console/approvals', view.kind === 'approvals', 'Approvals', pending)}
        {link('#/console/emi', view.kind === 'emi', 'EMI calculator')}
      </ul>
      <p className="side-h">Stages</p>
      <ul>
        {STAGES.map((s) =>
          link(`#/console/stage/${s.status}`, view.kind === 'stage' && view.status === s.status, s.name, counts?.[s.status]))}
      </ul>
      <div className="who">
        <span>{session.username}</span>
        <span className={`badge ${session.role.toLowerCase()}`}>{session.role}</span>
        <button className="btn sm" onClick={onLogout}>Sign out</button>
      </div>
    </nav>
  )
}
