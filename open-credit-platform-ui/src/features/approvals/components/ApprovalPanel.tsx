import { useState, type FormEvent } from 'react'
import { ApiFailure, call, soft } from '../../../services/http'
import { dt, msg } from '../../../utils/format'
import { useFetch } from '../../../hooks/useFetch'
import { useRoles } from '../../../hooks/useRoles'
import { Badge, Field } from '../../../components/common'
import type { Decision } from '../../assessment'

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

  const openCase = () => act('Open approval case', () => call('POST', `/api/credit-decisions/${decision.id}/approval-case`))

  return (
    <div className="approval">
      <h4>Maker–checker approval</h4>
      {data
        ? <CaseView data={data} maker={maker} checker={checker} busy={busy} outcome={outcome} comment={comment}
            onOutcome={setOutcome} onComment={setComment} onSubmit={submit} />
        : <NoCase declined={decision.outcome === 'DECLINE'} maker={maker} role={role} busy={busy} onOpen={openCase} />}
      {log && <p className={log.ok ? 'ok' : 'error'} role="status">{log.text}</p>}
    </div>
  )
}

function NoCase({ declined, maker, role, busy, onOpen }: { declined: boolean; maker: boolean; role?: string; busy: boolean; onOpen: () => void }) {
  if (declined) return <p className="muted">A declined decision needs no approval case; the application is declined.</p>
  return (
    <>
      <p className="muted">No approval case yet. Opening one resolves the required authority from the authority matrix. If that is AUTO, the application advances to offered without a case.</p>
      {maker
        ? <button type="button" className="btn sm primary" disabled={busy} onClick={onOpen}>Open approval case</button>
        : <p className="muted">Opening a case needs the MAKER or ADMIN role (you are {role}).</p>}
    </>
  )
}

type CaseViewProps = {
  data: Case
  maker: boolean
  checker: boolean
  busy: boolean
  outcome: string
  comment: string
  onOutcome: (v: string) => void
  onComment: (v: string) => void
  onSubmit: (who: 'maker' | 'checker') => (e: FormEvent) => void
}

function CaseView({ data, maker, checker, busy, outcome, comment, onOutcome, onComment, onSubmit }: CaseViewProps) {
  const pending = data.status === 'PENDING_MAKER' || data.status === 'PENDING_CHECKER'
  const isMakerStage = data.status === 'PENDING_MAKER'
  const canAct = isMakerStage ? maker : data.status === 'PENDING_CHECKER' && checker
  return (
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

      {canAct && (
        <form className="inline" onSubmit={onSubmit(isMakerStage ? 'maker' : 'checker')}>
          <Field label={isMakerStage ? 'Maker recommendation' : 'Checker decision'}>
            <select value={outcome} onChange={(e) => onOutcome(e.target.value)}>
              <option value="APPROVE">Approve</option>
              <option value="REJECT">Reject</option>
            </select>
          </Field>
          <Field label="Comment (optional)"><input value={comment} onChange={(e) => onComment(e.target.value)} maxLength={500} /></Field>
          <button type="submit" className="btn primary" disabled={busy}>Record decision</button>
        </form>
      )}
      {!canAct && pending && (
        <p className="muted">
          {isMakerStage
            ? 'Waiting for a maker. Recording the maker recommendation needs the MAKER or ADMIN role.'
            : 'Waiting for a checker. The final decision needs the CHECKER or ADMIN role.'}
        </p>
      )}
      {pending && <p className="muted small">The checker must be a different user than the maker.</p>}
    </>
  )
}
