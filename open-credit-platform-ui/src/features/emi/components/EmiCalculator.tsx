import { useMemo, useState, type FormEvent, type ReactNode } from 'react'
import { Bar, BarChart, CartesianGrid, Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { Field } from '../../../components/common'
import { amortization, byYear, computeBreakdown, toMonths, type Breakdown, type TenureUnit } from '../calc'

type Currency = 'INR' | 'USD'

const NEW_EMI = '#3B82F6'
const NEW_PRINCIPAL = '#93C5FD'
const EXISTING = '#F59E0B'
const OUTFLOW = '#10B981'

const FORMATS: Record<Currency, Intl.NumberFormat> = {
  INR: new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 }),
  USD: new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', maximumFractionDigits: 0 }),
}

// Keeps the raw text while typing so clearing the box or typing "124" never leaves a stray leading 0.
function NumberInput({ value, onChange, min, step = 'any' }: { value: number; onChange: (n: number) => void; min: number; step?: string }) {
  const [draft, setDraft] = useState<string | null>(null)
  return (
    <input type="number" min={min} step={step} value={draft ?? String(value)}
      onChange={(e) => { setDraft(e.target.value); onChange(e.target.value === '' ? 0 : Number(e.target.value)) }}
      onBlur={() => setDraft(null)} />
  )
}

type SegProps<T extends string> = { label: string; className?: string; value: T; options: readonly { id: T; text: string }[]; onChange: (v: T) => void }

// Rectangular segmented toggle (one option always selected).
function Seg<T extends string>({ label, className = '', value, options, onChange }: SegProps<T>) {
  return (
    <div className={`emi-seg ${className}`} role="group" aria-label={label}>
      {options.map((o) => (
        <button key={o.id} type="button" aria-pressed={value === o.id} onClick={() => onChange(o.id)}>{o.text}</button>
      ))}
    </div>
  )
}

type SliderProps = { label: string; toggle?: ReactNode; value: number; min: number; max: number; step: number; onChange: (n: number) => void }

// Number input and slider share one value, so typing moves the slider and dragging fills the box.
function SliderField({ label, toggle, value, min, max, step, onChange }: SliderProps) {
  return (
    <Field label={label}>
      {toggle}
      <NumberInput value={value} min={min} onChange={onChange} />
      <input className="emi-range" type="range" aria-label={`${label} slider`} min={min} max={max} step={step}
        value={Math.min(max, Math.max(min, value))} onChange={(e) => onChange(Number(e.target.value))} />
    </Field>
  )
}

export default function EmiCalculator() {
  const [currency, setCurrency] = useState<Currency>('INR')
  const [amount, setAmount] = useState(1_000_000)
  const [rate, setRate] = useState(10.5)
  const [tenure, setTenure] = useState(5)
  const [unit, setUnit] = useState<TenureUnit>('years')
  const [existingEmi, setExistingEmi] = useState(0)
  const [existingMonths, setExistingMonths] = useState(0)
  const [result, setResult] = useState<Breakdown | null>(null)
  const [error, setError] = useState('')
  const [view, setView] = useState<'month' | 'year'>('month')
  const schedule = useMemo(
    () => (result ? amortization(amount, rate, toMonths(tenure, unit), result.newEmi) : []),
    [result, amount, rate, tenure, unit],
  )

  const chartData = useMemo(
    () => (view === 'month'
      ? schedule.map((r) => ({ label: r.month, principal: r.principal, interest: r.interest }))
      : byYear(schedule).map((r) => ({ label: r.year, principal: r.principal, interest: r.interest }))),
    [view, schedule],
  )

  const fmt = (n: number) => FORMATS[currency].format(n)

  const switchUnit = (next: TenureUnit) => {
    if (next === unit) return
    setTenure(next === 'months' ? Math.round(tenure * 12) : Math.round((tenure / 12) * 10) / 10)
    setUnit(next)
  }

  const calculate = (e: FormEvent) => {
    e.preventDefault()
    if (existingEmi > 0 && !(existingMonths > 0)) {
      setResult(null)
      setError('Enter the remaining months for your existing EMI.')
      return
    }
    const r = computeBreakdown({
      amount, ratePct: rate, tenureMonths: toMonths(tenure, unit),
      existing: { emi: existingEmi, months: existingEmi > 0 ? existingMonths : 0 },
    })
    setError(r ? '' : 'Enter a loan amount, a non-negative rate and a tenure of at least one month.')
    setResult(r)
  }

  const donut = result ? [
    { name: 'New principal', value: amount, color: NEW_PRINCIPAL },
    { name: 'New total interest', value: result.newInterest, color: NEW_EMI },
    { name: 'Existing EMI remaining', value: result.existingTotal, color: EXISTING },
  ].filter((d) => d.value > 0) : []

  return (
    <>
      <h1>EMI calculator</h1>
      <p className="muted lead-s">Estimate a new loan's EMI alongside your existing monthly obligations.</p>

      <form className="box" onSubmit={calculate}>
        <div className="emi-currency">
          <Seg label="Currency" value={currency} onChange={setCurrency} options={[{ id: 'INR', text: 'INR' }, { id: 'USD', text: 'USD' }]} />
        </div>
        <h2>New loan</h2>
        <div className="cols">
          <SliderField label="Loan amount" value={amount} min={10_000} max={10_000_000} step={10_000} onChange={setAmount} />
          <SliderField label="Interest rate (% p.a.)" value={rate} min={1} max={30} step={0.1} onChange={setRate} />
          <SliderField label={`Tenure (${unit})`} value={tenure}
            toggle={<Seg label="Tenure unit" className="corner" value={unit} onChange={switchUnit} options={[{ id: 'years', text: 'Years' }, { id: 'months', text: 'Months' }]} />}
            min={unit === 'years' ? 1 : 6} max={unit === 'years' ? 30 : 360} step={unit === 'years' ? 0.5 : 1} onChange={setTenure} />
        </div>

        <h3>Existing obligations</h3>
        <p className="muted">Leave at 0 if you have no running EMI; the remaining months are asked only when there is one.</p>
        <div className="cols">
          <Field label="Existing EMI (per month)">
            <NumberInput value={existingEmi} min={0} onChange={setExistingEmi} />
          </Field>
          {existingEmi > 0 && <Field label="Remaining months">
            <NumberInput value={existingMonths} min={1} step="1" onChange={setExistingMonths} />
          </Field>}
        </div>

        {error && <p className="error" role="alert">{error}</p>}
        <button type="submit" className="btn primary">Calculate</button>
      </form>

      {result && (
        <>
          <div className="kpis" aria-live="polite">
            <div className="kpi">
              <span className="kpi-label">New loan EMI</span>
              <strong style={{ color: NEW_EMI }}>{fmt(result.newEmi)}</strong>
              <span className="kpi-hint">per month</span>
            </div>
            <div className="kpi">
              <span className="kpi-label">Existing EMI</span>
              <strong style={{ color: EXISTING }}>{fmt(result.existingEmi)}</strong>
              <span className="kpi-hint">per month</span>
            </div>
            <div className="kpi">
              <span className="kpi-label">Total monthly outflow</span>
              <strong style={{ color: OUTFLOW }}>{fmt(result.monthlyOutflow)}</strong>
              <span className="kpi-hint">new + existing</span>
            </div>
          </div>

          <div className="grid2">
            <section className="box">
              <h2>Where the money goes</h2>
              <div style={{ height: 280 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie data={donut} dataKey="value" nameKey="name" innerRadius="58%" outerRadius="85%" paddingAngle={2} stroke="none">
                      {donut.map((d) => <Cell key={d.name} fill={d.color} />)}
                    </Pie>
                    <Tooltip formatter={(v) => fmt(Number(v))} />
                    <Legend />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <dl className="facts">
                <div><dt>Interest (new)</dt><dd>{fmt(result.newInterest)}</dd></div>
                <div><dt>Payable (new)</dt><dd>{fmt(result.newTotalPayable)}</dd></div>
                <div><dt>Existing remaining</dt><dd>{fmt(result.existingTotal)}</dd></div>
                <div><dt>Total payable</dt><dd style={{ color: OUTFLOW }}>{fmt(result.totalPayableAll)}</dd></div>
              </dl>
            </section>

            <section className="box">
              <h2>Monthly outflow: new vs existing</h2>
              <div style={{ height: 120 }}>
                <ResponsiveContainer width="100%" height="100%">
                  <BarChart data={[{ name: 'Monthly', new: result.newEmi, existing: result.existingEmi }]} layout="vertical" margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
                    <XAxis type="number" hide domain={[0, result.monthlyOutflow]} />
                    <YAxis type="category" dataKey="name" hide />
                    <Tooltip formatter={(v) => fmt(Number(v))} cursor={false} />
                    <Legend />
                    <Bar dataKey="new" name="New loan EMI" stackId="m" fill={NEW_EMI} barSize={36} />
                    <Bar dataKey="existing" name="Existing EMI" stackId="m" fill={EXISTING} barSize={36} />
                  </BarChart>
                </ResponsiveContainer>
              </div>
              <p className="muted small">
                {result.monthlyOutflow > 0 ? Math.round((result.newEmi / result.monthlyOutflow) * 100) : 0}% of the monthly outflow is the new loan.
                Indicative estimate; the final rate and EMI are set when an application is submitted.
              </p>
            </section>
          </div>

          <section className="box">
            <div className="row between">
              <h2>{view === 'month' ? 'Month-wise' : 'Year-wise'} principal and interest</h2>
              <Seg label="Chart grouping" value={view} onChange={setView} options={[{ id: 'month', text: 'Monthly' }, { id: 'year', text: 'Yearly' }]} />
            </div>
            <div className="emi-scroll">
             <div style={{ height: 320, minWidth: chartData.length * (view === 'month' ? 32 : 64) }}>
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="label" tickLine={false} minTickGap={16} />
                  <YAxis tickFormatter={(v) => fmt(Number(v))} width={84} tickLine={false} axisLine={false} />
                  <Tooltip formatter={(v) => fmt(Number(v))} labelFormatter={(l) => `${view === 'month' ? 'Month' : 'Year'} ${l}`} />
                  <Legend />
                  <Bar dataKey="principal" name="Principal" stackId="s" fill={NEW_PRINCIPAL} />
                  <Bar dataKey="interest" name="Interest" stackId="s" fill={NEW_EMI} />
                </BarChart>
              </ResponsiveContainer>
             </div>
            </div>
            <p className="muted small">Scroll sideways to see every {view}. Each bar is one {view} of the new loan, split into principal and interest.</p>
          </section>
        </>
      )}
    </>
  )
}
