import { useState, type FormEvent } from 'react'
import { call, dt } from './api'
import { useRoles } from './session'
import { Badge, Facts, Field } from './ui'

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

export default function KycPanel({ path, loanStatus, kyc, docs, busy, act }: Props) {
  const { maker, checker } = useRoles()
  const [adding, setAdding] = useState(false)
  const [form, setForm] = useState({ documentType: 'PAN', fileName: '', storageReference: '' })
  const [remarks, setRemarks] = useState('')

  const register = (e: FormEvent) => {
    e.preventDefault()
    void act('Register document', () => call('POST', `${path}/documents`, form)).then(() => {
      setAdding(false)
      setForm({ documentType: 'PAN', fileName: '', storageReference: '' })
    })
  }

  return (
    <>
      <div className="actions">
        {maker && !kyc && !closed(loanStatus) && (
          <button className="btn sm" disabled={busy} onClick={() => act('Start KYC', () => call('POST', `${path}/kyc`))}>Start KYC</button>
        )}
        {checker && kyc && kyc.status !== 'VERIFIED' && (
          <button className="btn sm primary" disabled={busy} onClick={() => act('Verify KYC', () => call('POST', `${path}/kyc/verify`))}>Verify KYC</button>
        )}
        {maker && !closed(loanStatus) && (
          <button className="btn sm" onClick={() => setAdding(!adding)}>{adding ? 'Cancel' : 'Register document'}</button>
        )}
      </div>

      {checker && kyc && kyc.status !== 'VERIFIED' && kyc.status !== 'REJECTED' && (
        <form className="inline" onSubmit={(e) => { e.preventDefault(); void act('Reject KYC', () => call('POST', `${path}/kyc/reject`, { remarks: remarks || undefined })).then(() => setRemarks('')) }}>
          <Field label="Rejection remarks"><input value={remarks} onChange={(e) => setRemarks(e.target.value)} maxLength={500} /></Field>
          <button className="btn sm" disabled={busy}>Reject KYC</button>
        </form>
      )}

      {kyc && <Facts items={[['Opened', dt(kyc.createdAt)], ['Last update', dt(kyc.updatedAt)], ...(kyc.remarks ? [['Remarks', kyc.remarks] as [string, string]] : [])]} />}

      {adding && (
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
          <button className="btn primary" disabled={busy}>Register</button>
        </form>
      )}

      {docs.length ? (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Type</th><th>File</th><th>Status</th><th>Uploaded</th><th /></tr></thead>
            <tbody>
              {docs.map((d) => (
                <tr key={d.id}>
                  <td>{d.documentType.replace('_', ' ')}</td><td>{d.fileName}</td><td><Badge value={d.status} /></td><td>{dt(d.uploadedAt)}</td>
                  <td className="row-actions">
                    {checker && d.status === 'UPLOADED' && (
                      <>
                        <button className="btn sm" disabled={busy} onClick={() => act('Verify document', () => call('POST', `${path}/documents/${d.id}/verify`))}>Verify</button>
                        <button className="btn sm" disabled={busy} onClick={() => act('Reject document', () => call('POST', `${path}/documents/${d.id}/reject`))}>Reject</button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : <p className="muted">No documents registered.</p>}
    </>
  )
}
