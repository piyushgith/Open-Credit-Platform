import { useState, type FormEvent } from 'react'
import { call } from '../../../services/http'
import { msg } from '../../../utils/format'
import { Field } from '../../../components/ui'
import type { Session } from '../types'

const DEMO_USERS = [
  ['admin', 'admin123'],
  ['alice.maker', 'maker123'],
  ['bob.checker', 'checker123'],
  ['carol.checker', 'checker123'],
]

export default function Login({ onLogin, notice }: { onLogin: (s: Session) => void; notice?: string }) {
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
