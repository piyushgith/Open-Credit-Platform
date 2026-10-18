import { useState, type FormEvent } from 'react'
import { call } from '../../../services/http'
import { dt } from '../../../utils/format'
import { useRoles } from '../../../hooks/useRoles'
import { Badge, Facts, Field } from '../../../components/common'

export type Kyc = { applicationId?: string; status: string; remarks?: string; createdAt: string; updatedAt: string }
export type Doc = { id: string; applicationId?: string; documentType: string; fileName: string; storageReference: string; status: string; uploadedAt: string }

const TYPES = ['PAN', 'AADHAAR', 'INCOME_PROOF', 'BANK_STATEMENT', 'ADDRESS_PROOF', 'PHOTOGRAPH', 'OTHER']
const closed = (s: string) => ['DECLINED', 'DISBURSED'].includes(s)

type Props = {
  path: string // /api/loans/{reference}
  loanStatus: string
  kyc: Kyc | null
  docs: Doc[]
  busy: boolean
  act: (label: string, fn: () => Promise<unknown>) => Promise<void>
}

type Act = Props['act']
type DocForm = { documentType: string; fileName: string; storageReference: string }
const EMPTY_DOC: DocForm = { documentType: 'PAN', fileName: '', storageReference: '' }

export default function KycPanel({ path, loanStatus, kyc, docs, busy, act }: Props) {
  const { checker } = useRoles()
  const [adding, setAdding] = useState(false)

  return (
    <>
      <KycActions path={path} loanStatus={loanStatus} kyc={kyc} busy={busy} act={act} adding={adding} onToggleAdd={() => setAdding(!adding)} />

      {kyc && <Facts items={[['Opened', dt(kyc.createdAt)], ['Last update', dt(kyc.updatedAt)], ...(kyc.remarks ? [['Remarks', kyc.remarks] as [string, string]] : [])]} />}

      {adding && <RegisterForm path={path} busy={busy} act={act} onDone={() => setAdding(false)} />}

      <DocumentTable path={path} docs={docs} canReview={checker} busy={busy} act={act} />
    </>
  )
}

type KycActionsProps = Pick<Props, 'path' | 'loanStatus' | 'kyc' | 'busy' | 'act'> & { adding: boolean; onToggleAdd: () => void }

function KycActions({ path, loanStatus, kyc, busy, act, adding, onToggleAdd }: KycActionsProps) {
  const { maker, checker } = useRoles()
  const open = !closed(loanStatus)
  const reviewable = !!kyc && kyc.status !== 'VERIFIED'
  return (
    <>
      <div className="actions">
        {maker && !kyc && open && (
          <button type="button" className="btn sm" disabled={busy} onClick={() => act('Start KYC', () => call('POST', `${path}/kyc`))}>Start KYC</button>
        )}
        {checker && reviewable && (
          <button type="button" className="btn sm primary" disabled={busy} onClick={() => act('Verify KYC', () => call('POST', `${path}/kyc/verify`))}>Verify KYC</button>
        )}
        {maker && open && (
          <button type="button" className="btn sm" onClick={onToggleAdd}>{adding ? 'Cancel' : 'Register document'}</button>
        )}
      </div>
      {checker && reviewable && kyc.status !== 'REJECTED' && <RejectForm path={path} busy={busy} act={act} />}
    </>
  )
}

function RejectForm({ path, busy, act }: { path: string; busy: boolean; act: Act }) {
  const [remarks, setRemarks] = useState('')
  return (
    <form className="inline" onSubmit={(e) => { e.preventDefault(); void act('Reject KYC', () => call('POST', `${path}/kyc/reject`, { remarks: remarks || undefined })).then(() => setRemarks('')) }}>
      <Field label="Rejection remarks"><input value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength={500} /></Field>
      <button type="submit" className="btn sm" disabled={busy}>Reject KYC</button>
    </form>
  )
}

function RegisterForm({ path, busy, act, onDone }: { path: string; busy: boolean; act: Act; onDone: () => void }) {
  const [form, setForm] = useState<DocForm>(EMPTY_DOC)
  const register = (e: FormEvent) => {
    e.preventDefault()
    void act('Register document', () => call('POST', `${path}/documents`, form)).then(() => {
      onDone()
      setForm(EMPTY_DOC)
    })
  }
  return (
    <form className="stmt-form" onSubmit={register}>
      <p className="muted small">This registers a document reference only: the API stores the type, file name and storage reference, not the file contents.</p>
      <div className="cols">
        <Field label="Type">
          <select value={form.documentType} onChange={(e) => setForm({ ...form, documentType: e.target.value })}>
            {TYPES.map((t) => <option key={t} value={t}>{t.replace('_', ' ')}</option>)}
          </select>
        </Field>
        <Field label="File name"><input value={form.fileName} onChange={(e) => setForm({ ...form, fileName: e.target.value })} maxLength={255} required /></Field>
        <Field label="Storage reference"><input value={form.storageReference} placeholder="e.g. s3://bucket/key" onChange={(e) => setForm({ ...form, storageReference: e.target.value })} maxLength={500} required /></Field>
      </div>
      <button type="submit" className="btn primary" disabled={busy}>Register</button>
    </form>
  )
}

function DocumentTable({ path, docs, canReview, busy, act }: { path: string; docs: Doc[]; canReview: boolean; busy: boolean; act: Act }) {
  if (!docs.length) return <p className="muted">No documents registered.</p>
  return (
    <div className="table-wrap">
      <table>
        <thead><tr><th>Type</th><th>File</th><th>Status</th><th>Uploaded</th><th /></tr></thead>
        <tbody>
          {docs.map((d) => (
            <tr key={d.id}>
              <td>{d.documentType.replace('_', ' ')}</td><td>{d.fileName}</td><td><Badge value={d.status} /></td><td>{dt(d.uploadedAt)}</td>
              <td className="row-actions">
                {canReview && d.status === 'UPLOADED' && (
                  <>
                    <button type="button" className="btn sm" disabled={busy} onClick={() => act('Verify document', () => call('POST', `${path}/documents/${d.id}/verify`))}>Verify</button>
                    <button type="button" className="btn sm" disabled={busy} onClick={() => act('Reject document', () => call('POST', `${path}/documents/${d.id}/reject`))}>Reject</button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
