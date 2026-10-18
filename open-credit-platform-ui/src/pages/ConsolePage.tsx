import { useEffect, useState } from 'react'
import { call, setToken, setUnauthorizedHandler } from '../services/http'
import { useFetch } from '../hooks/useFetch'
import { Login, SessionContext, useRoles, type Session } from '../features/auth'
import { Dashboard, loadStats } from '../features/dashboard'
import { LoanDetail, NewApplication, StageList, STAGES } from '../features/loans'
import { Approvals } from '../features/approvals'
import '../styles/console.css'

/* ---------- shell ---------- */
type View = { kind: 'dashboard' } | { kind: 'new' } | { kind: 'approvals' } | { kind: 'search' } | { kind: 'stage'; status: string } | { kind: 'loan'; ref: string }

function parse(route: string): View {
  const m = route.replace(/^#\/console\/?/, '').split('/')
  if (m[0] === 'stage' && STAGES.some((s) => s.status === m[1])) return { kind: 'stage', status: m[1] }
  if (m[0] === 'loan' && m[1]) return { kind: 'loan', ref: decodeURIComponent(m[1]) }
  if (m[0] === 'new') return { kind: 'new' }
  if (m[0] === 'approvals') return { kind: 'approvals' }
  if (m[0] === 'search') return { kind: 'search' }
  return { kind: 'dashboard' }
}

function Workspace({ session, route, onLogout }: { session: Session; route: string; onLogout: () => void }) {
  const view = parse(route)
  const stats = useFetch(loadStats, [])
  const { maker } = useRoles()
  const pending = useFetch(async () => {
    const [a, b] = await Promise.all(['PENDING_MAKER', 'PENDING_CHECKER'].map((st) =>
      call<{ page: { totalElements: number } }>('GET', `/api/approval-cases?status=${st}&size=1`)))
    return a.page.totalElements + b.page.totalElements
  }, [])
  const counts = stats.data?.counts

  const link = (href: string, active: boolean, label: string, n?: number) => (
    <li key={href}>
      <a href={href} aria-current={active ? 'page' : undefined}>
        <span>{label}</span>
        {n !== undefined && <span className="nav-n">{n.toLocaleString('en-IN')}</span>}
      </a>
    </li>
  )

  return (
    <div className="shell">
      <nav className="side" aria-label="Console">
        <ul>
          {link('#/console', view.kind === 'dashboard', 'Dashboard')}
          {maker && link('#/console/new', view.kind === 'new', 'New application')}
          {link('#/console/search', view.kind === 'search', 'Search')}
          {link('#/console/approvals', view.kind === 'approvals', 'Approvals', pending.data)}
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
      <div className="main">
        {view.kind === 'dashboard' && (
          <>
            <h1>Dashboard</h1>
            <p className="muted lead-s">Portfolio overview across every stage of the loan lifecycle.</p>
            <Dashboard stats={stats.data} error={stats.error} />
          </>
        )}
        {view.kind === 'approvals' && <Approvals />}
        {view.kind === 'search' && <StageList />}
        {view.kind === 'new' && !maker && <div className="box"><p className="muted">Creating applications needs the MAKER or ADMIN role.</p></div>}
        {view.kind === 'new' && maker && <NewApplication onCreated={(r) => { stats.reload(); location.hash = `#/console/loan/${r}` }} />}
        {view.kind === 'stage' && <StageList key={view.status} status={view.status} />}
        {view.kind === 'loan' && <LoanDetail key={view.ref} reference={view.ref} onChanged={() => { stats.reload(); pending.reload() }} />}
      </div>
    </div>
  )
}

export default function ConsolePage({ route }: { route: string }) {
  const [session, setSession] = useState<Session | null>(null)
  const [expired, setExpired] = useState(false)
  useEffect(() => {
    setUnauthorizedHandler(() => { setSession(null); setExpired(true) })
    return () => setUnauthorizedHandler(null)
  }, [])
  const login = (s: Session) => { setToken(s.token); setExpired(false); setSession(s) }
  const logout = () => { setToken(null); setSession(null); location.hash = '#/console' }

  if (!session) {
    return (
      <div className="console"><div className="wrap">
        <div className="console-head"><div><h1>Loan console</h1><p className="muted">Sign in to view the portfolio and work applications through every stage.</p></div></div>
        <Login onLogin={login} notice={expired ? 'Your session has expired. Please sign in again.' : undefined} />
      </div></div>
    )
  }
  return (
    <SessionContext.Provider value={session}>
      <div className="console"><Workspace session={session} route={route} onLogout={logout} /></div>
    </SessionContext.Provider>
  )
}
