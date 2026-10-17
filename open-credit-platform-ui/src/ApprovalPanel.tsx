import { useState, type FormEvent } from 'react'
import { ApiFailure, call, dt, msg, soft } from './api'
import { useFetch } from './useFetch'
import { useRoles } from './session'
import { Badge, Field } from './ui'
import type { Decision } from './assessmentData'

type Case = {
  id: string
  requiredLevel: string
  status: string
  decisions: { role: string; actorUsername: string; actorLevel?: string; outcome: string; comment?: string; decidedAt: string }[]
}

const level = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())

export default function ApprovalPanel({ decision, onChanged }: { decision: Decision; onChanged: () => void }) {
  const { maker, checker, role } = useRoles()
  const { data, loading, reload } = useFetch(() => soft<Case>('GET', `/api/credit-decisions/${decision.id}/approval-case`), [decision.id])
  const [outcome, setOutcome] = useState('APPROVE')
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [log, setLog] = useState<{ ok: boolean; text: string } | null>(null)

  const act = async (what: string, fn: () => Promise<unknown>) => {
    setBusy(true)
    setLog(null)
    try {
      await fn()
      setLog({ ok: true, text: `${what}: done` })
      setComment('')
    } catch (e) {
      setLog({ ok: false, text: `${what}: ${msg(e)}${e instanceof ApiFailure && e.code ? ` (${e.code})` : ''}` })
    }
    reload()
    onChanged()
    setBusy(false)
  }

  if (loading && data === undefined) return <p className="muted">Loading approval case…</p>

  const submit = (who: 'maker' | 'checker') => (e: FormEvent) => {
    e.preventDefault()
    void act(`${who === 'maker' ? 'Maker' : 'Checker'} decision`, () =>
      call('POST', `/api/approval-cases/${data!.id}/${who}-decision`, { outcome, comment: comment || undefined }))
  }

  return (
    <div className="approval">
      <h4>Maker–checker approval</h4>

      {!data && decision.outcome === 'DECLINE' && <p className="muted">A declined decision needs no approval case; the application is declined.</p>}

      {!data && decision.outcome !== 'DECLINE' && (
        <>
          <p className="muted">No approval case yet. Opening one resolves the required authority from the authority matrix. If that is AUTO, the application advances to offered without a case.</p>
          {maker
            ? <button className="btn sm primary" disabled={busy} onClick={() => act('Open approval case', () => call('POST', `/api/credit-decisions/${decision.id}/approval-case`))}>Open approval case</button>
            : <p className="muted">Opening a case needs the MAKER or ADMIN role (you are {role}).</p>}
        </>
      )}

      {data && (
        <>
          <div className="score">
            <Badge value={data.status} />
            <span className="muted">Required level: <b>{level(data.requiredLevel)}</b></span>
          </div>

          {data.decisions.length > 0 && (
            <ol className="timeline">
              {data.decisions.map((d) => (
                <li key={d.role}>
                  <Badge value={d.role} /> <Badge value={d.outcome} />
                  <span> by <b>{d.actorUsername}</b>{d.actorLevel ? ` (${level(d.actorLevel)})` : ''} · {dt(d.decidedAt)}</span>
                  {d.comment && <p className="quote">“{d.comment}”</p>}
                </li>
              ))}
            </ol>
          )}

          {((data.status === 'PENDING_MAKER' && maker) || (data.status === 'PENDING_CHECKER' && checker)) && (
            <form className="inline" onSubmit={submit(data.status === 'PENDING_MAKER' ? 'maker' : 'checker')}>
              <Field label={data.status === 'PENDING_MAKER' ? 'Maker recommendation' : 'Checker decision'}>
                <select value={outcome} onChange={(e) => setOutcome(e.target.value)}>
                  <option value="APPROVE">Approve</option>
                  <option value="REJECT">Reject</option>
                </select>
              </Field>
              <Field label="Comment (optional)"><input value={comment} onChange={(e) => setComment(e.target.value)} maxLength={500} /></Field>
              <button className="btn primary" disabled={busy}>Record decision</button>
            </form>
          )}
          {data.status === 'PENDING_MAKER' && !maker && <p className="muted">Waiting for a maker. Recording the maker recommendation needs the MAKER or ADMIN role.</p>}
          {data.status === 'PENDING_CHECKER' && !checker && <p className="muted">Waiting for a checker. The final decision needs the CHECKER or ADMIN role.</p>}
          {['PENDING_MAKER', 'PENDING_CHECKER'].includes(data.status) && <p className="muted small">The checker must be a different user than the maker.</p>}
        </>
      )}
      {log && <p className={log.ok ? 'ok' : 'error'} role="status">{log.text}</p>}
    </div>
  )
}
