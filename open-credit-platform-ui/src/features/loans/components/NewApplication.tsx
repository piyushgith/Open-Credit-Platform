import { useState, type FormEvent } from 'react'
import { money, msg } from '../../../utils/format'
import { Field } from '../../../components/common'
import { indicativeRate, monthlyEmi } from '../emi'
import { createApplication } from '../services/loanService'
import type { ApplicationForm } from '../types'

const rnd = (n: number) => String(Math.floor(Math.random() * 10 ** n)).padStart(n, '0')

export default function NewApplication({ onCreated }: { onCreated: (ref: string) => void }) {
  const [f, setF] = useState<ApplicationForm>({
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
      onCreated(await createApplication(f))
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

