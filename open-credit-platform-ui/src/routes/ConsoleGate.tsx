import { useEffect, useState, type ReactNode } from 'react'
import { setToken, setUnauthorizedHandler } from '../services/http'
import { SessionContext } from '../context/SessionContext'
import { Login } from '../features/auth'
import type { Session } from '../types/session.types'

// Shows the sign-in screen until there is a session; a 401 from the API drops back to it.
export default function ConsoleGate({ children }: { children: (auth: { session: Session; logout: () => void }) => ReactNode }) {
  const [session, setSession] = useState<Session | null>(null)
  const [expired, setExpired] = useState(false)
  useEffect(() => {
    setUnauthorizedHandler(() => { setSession(null); setExpired(true) })
    return () => setUnauthorizedHandler(null)
  }, [])
  const login = (s: Session) => { setToken(s.token); setExpired(false); setSession(s) }
  const logout = () => { setToken(null); setSession(null); window.location.assign('#/console') }

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
      <div className="console">{children({ session, logout })}</div>
    </SessionContext.Provider>
  )
}
