import { useState, type FormEvent } from 'react'
import { ApiFailure, call, soft } from '../../../services/http'
import { dt, money, msg } from '../../../utils/format'
import { useFetch } from '../../../hooks/useFetch'
import { Badge, Card, Facts, Field } from '../../../components/common'
import { Assessment } from '../../assessment'
import { ApprovalPanel } from '../../approvals'
import { AuditTimeline } from '../../audit'
import { useRoles } from '../../../hooks/useRoles'
import KycPanel, { type Doc, type Kyc } from './KycPanel'
import { STAGES } from '../stages'
import type { Disbursement, Loan, Offer } from '../types'

type Attempt = { id: string; cycleNumber: number; status: string; active: boolean; startedAt: string; completedAt?: string }
type Sanction = { id: string; sanctionedAmount: number; interestRate: number; tenureMonths: number; monthlyEmi: number; createdAt: string }

type Data = Awaited<ReturnType<typeof loadAll>>

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
  const { maker, checker } = useRoles()
  const [log, setLog] = useState<{ ok: boolean; text: string } | null>(null)
  const [busy, setBusy] = useState(false)

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
  const selected = offers.find((o) => o.status === 'SELECTED')
  const auditRefs = buildAuditRefs(data)

  return (
    <div className="detail">
      <a className="back" href={`#/console/stage/${s}`}>← {stage?.name ?? 'Stage'}</a>

      <ActionsBox reference={reference} loan={loan} offers={offers} log={log} busy={busy} act={act} />

      <ApplicationCard loan={loan} />

      <Card title="2 · KYC & documents" state={kyc?.status ?? 'NOT STARTED'}>
        <KycPanel path={p} loanStatus={s} kyc={kyc} docs={docs} busy={busy} act={act} />
      </Card>

      <UnderwritingCard loan={loan} uw={uw} />

      <Card title="4 · Financial analysis, score & decision" state={decisionState}>
        <Assessment reference={reference} onChanged={() => { reload(); onChanged() }}
          renderDecision={(dec) => <ApprovalPanel decision={dec} onChanged={() => { reload(); onChanged() }} />} />
      </Card>

      <OfferCard p={p} status={s} offers={offers} selected={!!selected} canCreate={maker} busy={busy} act={act} />

      <Card title="6 · Sanction" state={sanction ? 'SANCTIONED' : 'NONE'}>
        {sanction ? <Facts items={[['Sanctioned amount', money(sanction.sanctionedAmount)], ['Rate', `${sanction.interestRate}%`], ['Tenure', `${sanction.tenureMonths} months`], ['EMI', money(sanction.monthlyEmi)], ['Sanctioned on', dt(sanction.createdAt)]]} />
          : <p className="muted">Not sanctioned. Requires an offered application with one selected offer and verified KYC.</p>}
      </Card>

      <DisbursementCard p={p} status={s} disb={disb} canDisburse={checker} busy={busy} act={act} />

      {checker && <AuditCard reference={reference} refs={auditRefs} />}
    </div>
  )
}

type Act = (label: string, fn: () => Promise<unknown>) => Promise<void>

function UnderwritingCard({ loan, uw }: { loan: Loan; uw: { attempts: Attempt[] } | null | undefined }) {
  return (
    <Card title="3 · Underwriting" state={loan.decision ?? (uw ? 'IN PROGRESS' : 'NOT STARTED')}>
      {!!loan.reasons?.length && <ul className="reasons">{loan.reasons.map((r) => <li key={r}>{r}</li>)}</ul>}
      {uw?.attempts.length ? (
        <div className="table-wrap"><table><thead><tr><th>Cycle</th><th>Outcome</th><th>Started</th><th>Completed</th><th /></tr></thead>
          <tbody>{uw.attempts.map((a) => <tr key={a.cycleNumber}><td>{a.cycleNumber}</td><td><Badge value={a.status} /></td><td>{dt(a.startedAt)}</td><td>{dt(a.completedAt)}</td><td>{a.active && <span className="muted">current</span>}</td></tr>)}</tbody></table></div>
      ) : <p className="muted">Not submitted for underwriting yet.</p>}
    </Card>
  )
}

type OfferCardProps = { p: string; status: string; offers: Offer[]; selected: boolean; canCreate: boolean; busy: boolean; act: Act }

function OfferCard({ p, status, offers, selected, canCreate, busy, act }: OfferCardProps) {
  const [tenure, setTenure] = useState('60')
  const offered = status === 'OFFERED'
  return (
    <Card title="5 · Offer" state={selected ? 'SELECTED' : offers.length ? 'ACTIVE' : 'NONE'}>
      {canCreate && offered && (
        <form className="inline" onSubmit={(e) => { e.preventDefault(); void act('Create offer', () => call('POST', `${p}/offers`, { tenureMonths: Number(tenure) })) }}>
          <Field label="Offer tenure (months)"><input type="number" min="1" value={tenure} onChange={(e) => setTenure(e.target.value)} /></Field>
          <button type="submit" className="btn" disabled={busy}>Create offer</button>
        </form>
      )}
      {offers.length ? (
        <div className="table-wrap"><table><thead><tr><th>Offer</th><th>Amount</th><th>Rate</th><th>Tenure</th><th>EMI</th><th>Status</th><th /></tr></thead>
          <tbody>{offers.map((o) => (
            <tr key={o.id}><td><code>{o.id.slice(0, 8)}</code></td><td>{money(o.offerAmount)}</td><td>{o.interestRate}%</td><td>{o.tenureMonths}</td><td>{money(o.monthlyEmi)}</td><td><Badge value={o.status} /></td>
              <td>{canCreate && offered && o.status !== 'SELECTED' && <button type="button" className="btn sm" disabled={busy} onClick={() => act('Select offer', () => call('POST', `${p}/offers/${o.id}/select`))}>Select</button>}</td></tr>
          ))}</tbody></table></div>
      ) : <p className="muted">No offers yet.</p>}
    </Card>
  )
}

type DisbursementCardProps = { p: string; status: string; disb: Disbursement | null | undefined; canDisburse: boolean; busy: boolean; act: Act }

function DisbursementCard({ p, status, disb, canDisburse, busy, act }: DisbursementCardProps) {
  const [amount, setAmount] = useState('')
  const [trancheRef, setTrancheRef] = useState('')
  const submit = (e: FormEvent) => {
    e.preventDefault()
    void act('Disburse', () => call('POST', `${p}/disbursements`, { amount: Number(amount), requestReference: trancheRef }))
      .then(() => { setAmount(''); setTrancheRef('') })
  }
  return (
    <Card title="7 · Disbursement" state={disb?.status ?? 'NONE'}>
      {canDisburse && status === 'SANCTIONED' && (
        <form className="inline" onSubmit={submit}>
          <Field label="Tranche amount"><input type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} required /></Field>
          <Field label="Request reference"><input value={trancheRef} onChange={(e) => setTrancheRef(e.target.value)} required /></Field>
          <button type="submit" className="btn primary" disabled={busy}>Disburse tranche</button>
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
  )
}

function AuditCard({ reference, refs }: { reference: string; refs: [string, string][] }) {
  const [show, setShow] = useState(false)
  return (
    <Card title="8 · Audit trail">
      {show
        ? <AuditTimeline reference={reference} refs={refs} />
        : <button type="button" className="btn sm" onClick={() => setShow(true)}>Load audit trail</button>}
      <p className="muted small">Business events and data changes for this application, its underwriting attempts, offers, sanction, credit decisions and approval cases. Visible to checkers and admins.</p>
    </Card>
  )
}

type Log = { ok: boolean; text: string } | null

function buildAuditRefs({ uw, kyc, docs, offers, sanction }: Data): [string, string][] {
  const applicationId = uw?.applicationId ?? kyc?.applicationId ?? docs[0]?.applicationId
  return [
    ...(applicationId ? [['LoanApplication', applicationId] as [string, string]] : []),
    ...(uw?.attempts ?? []).map((a): [string, string] => ['UnderwritingAttempt', a.id]),
    ...offers.map((o): [string, string] => ['Offer', o.id]),
    ...(sanction ? [['Sanction', sanction.id] as [string, string]] : []),
  ]
}

type ActionsBoxProps = { reference: string; loan: Loan; offers: Offer[]; log: Log; busy: boolean; act: Act }

function ActionsBox({ reference, loan, offers, log, busy, act }: ActionsBoxProps) {
  return (
    <div className="box">
      <div className="row">
        <h2>{reference}</h2>
        <Badge value={loan.status} />
        <Badge value={loan.decision} />
      </div>
      <h3>Actions</h3>
      <ActionButtons p={`/api/loans/${reference}`} loan={loan} hasSelected={offers.some((o) => o.status === 'SELECTED')} busy={busy} act={act} />
      <RoleHint />
      {log && <p className={log.ok ? 'ok' : 'error'} role="status">{log.text}</p>}
    </div>
  )
}

type ActionButtonsProps = { p: string; loan: Loan; hasSelected: boolean; busy: boolean; act: Act }

function ActionButtons({ p, loan, hasSelected, busy, act }: ActionButtonsProps) {
  const { maker, checker } = useRoles()
  const s = loan.status
  const referred = s === 'UNDERWRITING' && loan.decision === 'REFERRED'
  return (
    <div className="actions">
      {maker && s === 'DRAFT' && <button type="button" className="btn primary" disabled={busy} onClick={() => act('Submit', () => call('POST', `${p}/submit`))}>Submit &amp; underwrite</button>}
      {maker && referred && <button type="button" className="btn" disabled={busy} onClick={() => act('Retry underwriting', () => call('POST', `${p}/underwriting/retry`))}>Retry underwriting</button>}
      {checker && hasSelected && s === 'OFFERED' && <button type="button" className="btn primary" disabled={busy} onClick={() => act('Sanction', () => call('POST', `${p}/sanction`))}>Sanction selected offer</button>}
      {['DECLINED', 'DISBURSED'].includes(s) && <span className="muted">No further actions: this application is {s.toLowerCase()}.</span>}
    </div>
  )
}

function RoleHint() {
  const { maker, checker, role } = useRoles()
  if (maker && checker) return null
  return <p className="muted small">Signed in as {role}: {maker ? 'checker actions (verify KYC, sanction, disburse)' : 'maker actions (submit, start KYC, offers, assessment)'} are hidden because they need the {maker ? 'CHECKER' : 'MAKER'} or ADMIN role.</p>
}

function ApplicationCard({ loan }: { loan: Loan }) {
  return (
    <Card title="1 · Application" state={loan.status}>
      <Facts items={[
        ['Product', loan.productType], ['Approved amount', money(loan.approvedAmount)],
        ['Rate', loan.interestRate !== undefined ? `${loan.interestRate}%` : undefined],
        ['EMI', money(loan.monthlyEmi)], ['Tenure', loan.tenureMonths ? `${loan.tenureMonths} months` : undefined],
        ...(typeof loan.foir === 'number' ? [['FOIR', loan.foir] as [string, number]] : []),
      ]} />
    </Card>
  )
}
