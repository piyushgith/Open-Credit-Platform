import { useEffect, useState, type FormEvent } from 'react'
import { call, loadStats, msg, STAGES, type Loan, type Session, setToken, setUnauthorizedHandler } from './api'
import { useFetch } from './useFetch'
import Dashboard from './Dashboard'
import StageList from './StageList'
import LoanDetail from './LoanDetail'
import Approvals from './Approvals'
import { SessionContext, useRoles } from './session'
import { Field } from './ui'
import { indicativeRate, monthlyEmi } from './emi'
import { money } from './api'
import './Console.css'

const DEMO_USERS = [
  ['admin', 'admin123'],
  ['alice.maker', 'maker123'],
  ['bob.checker', 'checker123'],
  ['carol.checker', 'checker123'],
]

function Login({ onLogin, notice }: { onLogin: (s: Session) => void; notice?: string }) {
  const [username, setUsername] = useState('admin')
  const [password, setPassword] = useState('admin123')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      onLogin(await call<Session>('POST', '/api/auth/login', { username, password }))
    } catch (err) {
      setError(msg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="box narrow" onSubmit={submit}>
      <h2>Sign in</h2>
      <p className="muted">Use one of the seeded demo users (dev profile only).</p>
      <Field label="Username"><input value={username} onChange={(e) => setUsername(e.target.value)} autoComplete="username" required /></Field>
      <Field label="Password"><input type="password" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" required /></Field>
      {notice && <p className="notice" role="status">{notice}</p>}
      {error && <p className="error" role="alert">{error}</p>}
      <button className="btn primary" disabled={busy}>{busy ? 'Signing in…' : 'Sign in'}</button>
      <div className="chips">
        {DEMO_USERS.map(([u, p]) => (
          <button type="button" key={u} className="chip" onClick={() => { setUsername(u); setPassword(p) }}>{u}</button>
        ))}
      </div>
    </form>
  )
}


const rnd = (n: number) => String(Math.floor(Math.random() * 10 ** n)).padStart(n, '0')

function NewApplication({ onCreated }: { onCreated: (ref: string) => void }) {
  const [f, setF] = useState({
    fullName: 'Asha Verma', email: `asha.${rnd(5)}@example.com`, phoneNumber: `9${rnd(9)}`,
    dateOfBirth: '1992-04-18', panNumber: `DEMOA${rnd(4)}X`,
    productType: 'PERSONAL', requestedAmount: '500000', tenureMonths: '60',
    monthlyIncome: '120000', existingEmi: '15000', employmentType: 'SALARIED',
    vehicleMake: 'Honda', vehiclePrice: '900000', downPayment: '200000', condition: 'NEW',
  })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const rate = indicativeRate(f.productType, f.employmentType, f.condition)
  const emi = monthlyEmi(Number(f.requestedAmount), rate, Number(f.tenureMonths))
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value })

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const customer = await call<{ id: string }>('POST', '/api/customers', {
        fullName: f.fullName, email: f.email, phoneNumber: f.phoneNumber,
        dateOfBirth: f.dateOfBirth, panNumber: f.panNumber.toUpperCase(),
      })
      const base = {
        productType: f.productType, customerId: customer.id,
        requestedAmount: Number(f.requestedAmount), tenureMonths: Number(f.tenureMonths),
      }
      const body = f.productType === 'PERSONAL'
        ? { ...base, monthlyIncome: Number(f.monthlyIncome), existingEmi: Number(f.existingEmi), employmentType: f.employmentType }
        : { ...base, vehicleMake: f.vehicleMake, vehiclePrice: Number(f.vehiclePrice), downPayment: Number(f.downPayment), condition: f.condition }
      const loan = await call<Loan>('POST', '/api/loans/apply', body)
      onCreated(loan.applicationReference)
    } catch (err) {
      setError(msg(err))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form className="box" onSubmit={submit}>
      <h2>New application</h2>
      <h3>Applicant</h3>
      <div className="cols">
        <Field label="Full name"><input value={f.fullName} onChange={set('fullName')} required /></Field>
        <Field label="Email"><input type="email" value={f.email} onChange={set('email')} required /></Field>
        <Field label="Phone"><input value={f.phoneNumber} onChange={set('phoneNumber')} required /></Field>
        <Field label="Date of birth"><input type="date" value={f.dateOfBirth} onChange={set('dateOfBirth')} required /></Field>
        <Field label="PAN (AAAAA9999A)"><input value={f.panNumber} onChange={set('panNumber')} pattern="[A-Za-z]{5}[0-9]{4}[A-Za-z]" required /></Field>
      </div>
      <h3>Loan</h3>
      <div className="cols">
        <Field label="Product">
          <select value={f.productType} onChange={set('productType')}>
            <option value="PERSONAL">Personal</option>
            <option value="VEHICLE">Vehicle</option>
          </select>
        </Field>
        <Field label="Amount"><input type="number" min="1" value={f.requestedAmount} onChange={set('requestedAmount')} required /></Field>
        <Field label="Tenure (months)"><input type="number" min="1" value={f.tenureMonths} onChange={set('tenureMonths')} required /></Field>
        {f.productType === 'PERSONAL' ? (
          <>
            <Field label="Monthly income"><input type="number" min="0" value={f.monthlyIncome} onChange={set('monthlyIncome')} required /></Field>
            <Field label="Existing EMI"><input type="number" min="0" value={f.existingEmi} onChange={set('existingEmi')} required /></Field>
            <Field label="Employment">
              <select value={f.employmentType} onChange={set('employmentType')}>
                <option value="SALARIED">Salaried</option>
                <option value="SELF_EMPLOYED">Self-employed</option>
              </select>
            </Field>
          </>
        ) : (
          <>
            <Field label="Vehicle make"><input value={f.vehicleMake} onChange={set('vehicleMake')} required /></Field>
            <Field label="Vehicle price"><input type="number" min="1" value={f.vehiclePrice} onChange={set('vehiclePrice')} required /></Field>
            <Field label="Down payment"><input type="number" min="0" value={f.downPayment} onChange={set('downPayment')} required /></Field>
            <Field label="Condition">
              <select value={f.condition} onChange={set('condition')}>
                <option value="NEW">New</option>
                <option value="USED">Used</option>
              </select>
            </Field>
          </>
        )}
      </div>
      {emi !== null && (
        <p className="preview">
          Indicative EMI <strong>{money(emi)}</strong> a month at {rate}% over {f.tenureMonths} months.
          <span className="muted"> The final rate, amount and EMI are set when the application is submitted.</span>
        </p>
      )}
      {error && <p className="error" role="alert">{error}</p>}
      <button className="btn primary" disabled={busy}>{busy ? 'Creating…' : 'Create application'}</button>
    </form>
  )
}


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

export default function Console({ route }: { route: string }) {
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
