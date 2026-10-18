import { useState } from 'react'
import { API } from '../../../config/env'
import { IDLE, SNIPPETS, type Lang } from '../content'

export default function CodePreview() {
  const [lang, setLang] = useState<Lang>('curl')
  const [copied, setCopied] = useState(false)
  const [out, setOut] = useState(IDLE)
  const [state, setState] = useState<'idle' | 'ok' | 'err'>('idle')
  const [busy, setBusy] = useState(false)
  const snip = SNIPPETS[lang]

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(snip.code)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard unavailable */
    }
  }

  const run = async () => {
    setBusy(true)
    try {
      const login = await fetch(`${API}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'alice.maker', password: 'maker123' }),
      })
      const session = await login.json()
      if (!login.ok) throw new Error(session.message ?? `Login failed (${login.status})`)
      const res = await fetch(`${API}/api/loan-products`, {
        headers: { Authorization: `Bearer ${session.data.token}` },
      })
      const json = await res.json()
      setOut(`HTTP ${res.status}\n` + JSON.stringify(json, null, 2))
      setState(res.ok ? 'ok' : 'err')
    } catch (e) {
      setOut(`Could not reach ${API}\n${e instanceof Error ? e.message : e}\n\nStart the backend with the dev profile and retry.`)
      setState('err')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="integ">
      <div className="panel">
        <div className="tabs" role="tablist" aria-label="Language">
          {(Object.keys(SNIPPETS) as Lang[]).map((l) => (
            <button key={l} role="tab" className="tab" aria-selected={lang === l} onClick={() => setLang(l)}>
              {SNIPPETS[l].label}
            </button>
          ))}
        </div>
        <div className="bar">
          <span>{snip.file}</span>
          <button className="copy" onClick={copy} aria-live="polite">{copied ? 'Copied' : 'Copy'}</button>
        </div>
        <pre tabIndex={0}><code>{snip.code}</code></pre>
      </div>
      <div className="panel out">
        <div className="bar"><span>response.json</span></div>
        <pre tabIndex={0} className={state === 'err' ? 'err' : state === 'idle' ? 'idle' : ''} aria-live="polite">{out}</pre>
        <div className="runbar">
          <button className="btn primary" onClick={run} disabled={busy}>{busy ? 'Running…' : 'Run request'}</button>
          <small>Uses a seeded demo user against {API}</small>
        </div>
      </div>
    </div>
  )
}
