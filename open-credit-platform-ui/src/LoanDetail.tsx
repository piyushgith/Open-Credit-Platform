import { useState } from 'react'
import { ApiFailure, call, dt, money, msg, soft, STAGES, type Disbursement, type Loan, type Offer } from './api'
import { useFetch } from './useFetch'
import { Badge, Card, Facts, Field } from './ui'
import Assessment from './Assessment'
import ApprovalPanel from './ApprovalPanel'
import AuditTimeline from './AuditTimeline'
import KycPanel, { type Doc, type Kyc } from './KycPanel'
import { useRoles } from './session'

type Attempt = { id: string; cycleNumber: number; status: string; active: boolean; startedAt: string; completedAt?: string }
type Sanction = { id: string; sanctionedAmount: number; interestRate: number; tenureMonths: number; monthlyEmi: number; createdAt: string }

async function loadAll(ref: string) {
  const p = `/api/loans/${ref}`
  const loan = await call<Loan>('GET', p)
  const [uw, kyc, docs, offers, sanction, disb] = await Promise.all([
    soft<{ applicationId: string; attempts: Attempt[] }>('GET', `${p}/underwriting`),
    soft<Kyc>('GET', `${p}/kyc`),
    soft<Doc[]>('GET', `${p}/documents`),
    soft<Offer[]>('GET', `${p}/offers`),
    soft<Sanction>('GET', `${p}/sanction`),
    soft<Disbursement>('GET', `${p}/disbursements`),
  ])
  return { loan, uw, kyc, docs: docs ?? [], offers: offers ?? [], sanction, disb }
}

export default function LoanDetail({ reference, onChanged }: { reference: string; onChanged: () => void }) {
  const { data, error, reload } = useFetch(() => loadAll(reference), [reference])
  const { maker, checker, role } = useRoles()
  const [log, setLog] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [showAudit, setShowAudit] = useState(false)
  const [tenure, setTenure] = useState('60')
  const [amount, setAmount] = useState('')
  const [trancheRef, setTrancheRef] = useState('')

  const act = async (label: string, fn: () => Promise<unknown>) => {
    setBusy(true)
    setLog(null)
    try {
      await fn()
      setLog({ ok: true, text: `${label}: done` })
    } catch (e) {
      setLog({ ok: false, text: `${label}: ${msg(e)}${e instanceof ApiFailure && e.code ? ` (${e.code})` : ''}` })
    }
    reload()
    onChanged()
    setBusy(false)
  }

  if (error && !data) return <div className="box"><p className="error" role="alert">{error}</p></div>
  if (!data) return <div className="box"><p className="muted">Loading…</p></div>

  const { loan, uw, kyc, docs, offers, sanction, disb } = data
  const decisionState = loan.decision ?? 'NONE'
  const s = loan.status
  const p = `/api/loans/${reference}`
  const stage = STAGES.find((x) => x.status === s)
  const referred = s === 'UNDERWRITING' && loan.decision === 'REFERRED'
  const selected = offers.find((o) => o.status === 'SELECTED')
  const applicationId = uw?.applicationId ?? kyc?.applicationId ?? docs[0]?.applicationId
  const auditRefs: [string, string][] = [
    ...(applicationId ? [['LoanApplication', applicationId] as [string, string]] : []),
    ...(uw?.attempts ?? []).map((a): [string, string] => ['UnderwritingAttempt', a.id]),
    ...offers.map((o): [string, string] => ['Offer', o.id]),
    ...(sanction ? [['Sanction', sanction.id] as [string, string]] : []),
  ]

  return (
    <div className="detail">
      <a className="back" href={`#/console/stage/${s}`}>← {stage?.name ?? 'Stage'}</a>

      <div className="box">
        <div className="row">
          <h2>{reference}</h2>
          <Badge value={s} />
          <Badge value={loan.decision} />
        </div>
        <h3>Actions</h3>
        <div className="actions">
          {maker && s === 'DRAFT' && <button className="btn primary" disabled={busy} onClick={() => act('Submit', () => call('POST', `${p}/submit`))}>Submit &amp; underwrite</button>}
          {maker && referred && <button className="btn" disabled={busy} onClick={() => act('Retry underwriting', () => call('POST', `${p}/underwriting/retry`))}>Retry underwriting</button>}
          {checker && selected && s === 'OFFERED' && <button className="btn primary" disabled={busy} onClick={() => act('Sanction', () => call('POST', `${p}/sanction`))}>Sanction selected offer</button>}
          {['DECLINED', 'DISBURSED'].includes(s) && <span className="muted">No further actions: this application is {s.toLowerCase()}.</span>}
        </div>
        {(!maker || !checker) && <p className="muted small">Signed in as {role}: {maker ? 'checker actions (verify KYC, sanction, disburse)' : 'maker actions (submit, start KYC, offers, assessment)'} are hidden because they need the {maker ? 'CHECKER' : 'MAKER'} or ADMIN role.</p>}
        {log && <p className={log.ok ? 'ok' : 'error'} role="status">{log.text}</p>}
      </div>

      <Card title="1 · Application" state={s}>
        <Facts items={[
          ['Product', loan.productType], ['Approved amount', money(loan.approvedAmount)],
          ['Rate', loan.interestRate !== undefined ? `${loan.interestRate}%` : undefined],
          ['EMI', money(loan.monthlyEmi)], ['Tenure', loan.tenureMonths ? `${loan.tenureMonths} months` : undefined],
          ...(typeof loan.foir === 'number' ? [['FOIR', loan.foir] as [string, number]] : []),
        ]} />
      </Card>

      <Card title="2 · KYC & documents" state={kyc?.status ?? 'NOT STARTED'}>
        <KycPanel path={p} loanStatus={s} kyc={kyc} docs={docs} busy={busy} act={act} />
      </Card>

      <Card title="3 · Underwriting" state={loan.decision ?? (uw ? 'IN PROGRESS' : 'NOT STARTED')}>
        {!!loan.reasons?.length && <ul className="reasons">{loan.reasons.map((r) => <li key={r}>{r}</li>)}</ul>}
        {uw?.attempts.length ? (
          <div className="table-wrap"><table><thead><tr><th>Cycle</th><th>Outcome</th><th>Started</th><th>Completed</th><th /></tr></thead>
            <tbody>{uw.attempts.map((a) => <tr key={a.cycleNumber}><td>{a.cycleNumber}</td><td><Badge value={a.status} /></td><td>{dt(a.startedAt)}</td><td>{dt(a.completedAt)}</td><td>{a.active && <span className="muted">current</span>}</td></tr>)}</tbody></table></div>
        ) : <p className="muted">Not submitted for underwriting yet.</p>}
      </Card>

      <Card title="4 · Financial analysis, score & decision" state={decisionState}>
        <Assessment reference={reference} onChanged={() => { reload(); onChanged() }}
          renderDecision={(dec) => <ApprovalPanel decision={dec} onChanged={() => { reload(); onChanged() }} />} />
      </Card>

      <Card title="5 · Offer" state={selected ? 'SELECTED' : offers.length ? 'ACTIVE' : 'NONE'}>
        {maker && s === 'OFFERED' && (
          <form className="inline" onSubmit={(e) => { e.preventDefault(); void act('Create offer', () => call('POST', `${p}/offers`, { tenureMonths: Number(tenure) })) }}>
            <Field label="Offer tenure (months)"><input type="number" min="1" value={tenure} onChange={(e) => setTenure(e.target.value)} /></Field>
            <button className="btn" disabled={busy}>Create offer</button>
          </form>
        )}
        {offers.length ? (
          <div className="table-wrap"><table><thead><tr><th>Offer</th><th>Amount</th><th>Rate</th><th>Tenure</th><th>EMI</th><th>Status</th><th /></tr></thead>
            <tbody>{offers.map((o) => (
              <tr key={o.id}><td><code>{o.id.slice(0, 8)}</code></td><td>{money(o.offerAmount)}</td><td>{o.interestRate}%</td><td>{o.tenureMonths}</td><td>{money(o.monthlyEmi)}</td><td><Badge value={o.status} /></td>
                <td>{maker && s === 'OFFERED' && o.status !== 'SELECTED' && <button className="btn sm" disabled={busy} onClick={() => act('Select offer', () => call('POST', `${p}/offers/${o.id}/select`))}>Select</button>}</td></tr>
            ))}</tbody></table></div>
        ) : <p className="muted">No offers yet.</p>}
      </Card>

      <Card title="6 · Sanction" state={sanction ? 'SANCTIONED' : 'NONE'}>
        {sanction ? <Facts items={[['Sanctioned amount', money(sanction.sanctionedAmount)], ['Rate', `${sanction.interestRate}%`], ['Tenure', `${sanction.tenureMonths} months`], ['EMI', money(sanction.monthlyEmi)], ['Sanctioned on', dt(sanction.createdAt)]]} />
          : <p className="muted">Not sanctioned. Requires an offered application with one selected offer and verified KYC.</p>}
      </Card>

      <Card title="7 · Disbursement" state={disb?.status ?? 'NONE'}>
        {checker && s === 'SANCTIONED' && (
          <form className="inline" onSubmit={(e) => {
            e.preventDefault()
            void act('Disburse', () => call('POST', `${p}/disbursements`, { amount: Number(amount), requestReference: trancheRef }))
              .then(() => { setAmount(''); setTrancheRef('') })
          }}>
            <Field label="Tranche amount"><input type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} required /></Field>
            <Field label="Request reference"><input value={trancheRef} onChange={(e) => setTrancheRef(e.target.value)} required /></Field>
            <button className="btn primary" disabled={busy}>Disburse tranche</button>
          </form>
        )}
        {disb?.tranches.length ? (
          <>
            <p><strong>{money(disb.disbursedTotal)}</strong> of {money(disb.sanctionedAmount)} disbursed</p>
            <div className="table-wrap"><table><thead><tr><th>#</th><th>Amount</th><th>Reference</th></tr></thead>
              <tbody>{disb.tranches.map((t) => <tr key={t.requestReference}><td>{t.trancheNumber}</td><td>{money(t.amount)}</td><td>{t.requestReference}</td></tr>)}</tbody></table></div>
          </>
        ) : <p className="muted">No tranches disbursed.</p>}
      </Card>

      {checker && (
        <Card title="8 · Audit trail">
          {showAudit
            ? <AuditTimeline reference={reference} refs={auditRefs} />
            : <button className="btn sm" onClick={() => setShowAudit(true)}>Load audit trail</button>}
          <p className="muted small">Business events and data changes for this application, its underwriting attempts, offers, sanction, credit decisions and approval cases. Visible to checkers and admins.</p>
        </Card>
      )}
    </div>
  )
}
