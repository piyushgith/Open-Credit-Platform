import { useState, type FormEvent, type ReactNode } from 'react'
import { ApiFailure, call, dt, msg } from './api'
import { loadChains, type Decision, type Factor, type Period, type Rule } from './assessmentData'
import { useFetch } from './useFetch'
import { Badge, Field } from './ui'
import { useRoles } from './session'

const ITEMS: [string, string, number][] = [
  ['CURRENT_ASSETS', 'Current assets', 500000], ['NON_CURRENT_ASSETS', 'Non-current assets', 700000],
  ['CURRENT_LIABILITIES', 'Current liabilities', 300000], ['NON_CURRENT_LIABILITIES', 'Non-current liabilities', 200000],
  ['EQUITY', 'Equity', 700000], ['LONG_TERM_DEBT', 'Long-term debt', 150000], ['SHORT_TERM_DEBT', 'Short-term debt', 50000],
  ['INVENTORY', 'Inventory', 100000], ['REVENUE', 'Revenue', 2000000], ['COST_OF_GOODS_SOLD', 'Cost of goods sold', 1100000],
  ['OPERATING_EXPENSES', 'Operating expenses', 300000], ['DEPRECIATION_AMORTIZATION', 'Depreciation & amortization', 50000],
  ['INTEREST_EXPENSE', 'Interest expense', 30000], ['TAX_EXPENSE', 'Tax expense', 100000],
]

const num = (n: number) => new Intl.NumberFormat('en-IN', { maximumFractionDigits: 4 }).format(n)
const ACRONYMS = new Set(['EBITDA', 'ROA', 'ROE', 'DSCR', 'EMI'])
const label = (c: string) =>
  c.split('_').map((w, i) => (ACRONYMS.has(w) ? w : i === 0 ? w[0] + w.slice(1).toLowerCase() : w.toLowerCase())).join(' ')

function StatementForm({ onSubmit, busy }: { onSubmit: (body: unknown) => void; busy: boolean }) {
  const [period, setPeriod] = useState<Period>({ periodLabel: 'FY2024', periodType: 'AUDITED', startDate: '2023-04-01', endDate: '2024-03-31' })
  const [vals, setVals] = useState<Record<string, string>>(Object.fromEntries(ITEMS.map(([c, , v]) => [c, String(v)])))
  const submit = (e: FormEvent) => {
    e.preventDefault()
    onSubmit({ period, lineItems: ITEMS.map(([c]) => ({ lineItemCode: c, value: Number(vals[c]) })) })
  }
  return (
    <form className="stmt-form" onSubmit={submit}>
      <div className="cols">
        <Field label="Period label"><input value={period.periodLabel} onChange={(e) => setPeriod({ ...period, periodLabel: e.target.value })} required /></Field>
        <Field label="Type">
          <select value={period.periodType} onChange={(e) => setPeriod({ ...period, periodType: e.target.value })}>
            {['ACTUAL', 'AUDITED', 'PROVISIONAL', 'PROJECTED'].map((t) => <option key={t}>{t}</option>)}
          </select>
        </Field>
        <Field label="Start"><input type="date" value={period.startDate} onChange={(e) => setPeriod({ ...period, startDate: e.target.value })} required /></Field>
        <Field label="End"><input type="date" value={period.endDate} onChange={(e) => setPeriod({ ...period, endDate: e.target.value })} required /></Field>
      </div>
      <div className="cols items">
        {ITEMS.map(([c, name]) => (
          <Field key={c} label={name}>
            <input type="number" step="0.01" value={vals[c]} onChange={(e) => setVals({ ...vals, [c]: e.target.value })} required />
          </Field>
        ))}
      </div>
      <button className="btn primary" disabled={busy}>Submit statement</button>
    </form>
  )
}

function Rules({ title, rules, ok }: { title: string; rules: Rule[]; ok: boolean }) {
  if (!rules.length) return null
  return (
    <div>
      <h4>{title}</h4>
      <ul className="rules">
        {rules.map((r) => (
          <li key={r.factorCode + r.operator} className={ok ? 'pass' : 'fail'}>
            <span aria-hidden="true">{ok ? '✓' : '✕'}</span>
            <span>{r.description} <span className={`sev ${r.severity.toLowerCase()}`}>{r.severity}</span></span>
          </li>
        ))}
      </ul>
    </div>
  )
}

function Factors({ title, factors }: { title: string; factors: Factor[] }) {
  if (!factors.length) return null
  return (
    <div>
      <h4>{title}</h4>
      <ul className="factors">
        {factors.map((f) => (
          <li key={f.factorCode}>
            <span>{label(f.factorCode)} <span className="muted">· {f.description}</span></span>
            <strong className={f.points < 0 ? 'neg' : 'pos'}>{f.points > 0 ? '+' : ''}{f.points}</strong>
          </li>
        ))}
      </ul>
    </div>
  )
}

export default function Assessment({ reference, onChanged, renderDecision }: {
  reference: string
  onChanged: () => void
  renderDecision?: (d: Decision) => ReactNode
}) {
  const { data, error, reload } = useFetch(() => loadChains(reference), [reference])
  const { maker } = useRoles()
  const [adding, setAdding] = useState(false)
  const [busy, setBusy] = useState(false)
  const [log, setLog] = useState<{ ok: boolean; text: string } | null>(null)
  const p = `/api/loans/${reference}/financial-statements`

  const act = async (what: string, fn: () => Promise<unknown>) => {
    setBusy(true)
    setLog(null)
    try {
      await fn()
      setLog({ ok: true, text: `${what}: done` })
    } catch (e) {
      setLog({ ok: false, text: `${what}: ${msg(e)}${e instanceof ApiFailure && e.code ? ` (${e.code})` : ''}` })
    }
    reload()
    onChanged()
    setBusy(false)
  }

  if (error && !data) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="muted">Loading…</p>

  return (
    <div className="assess">
      {!data.length && !adding && <p className="muted">No financial statements for this application.</p>}
      {maker && <div className="actions">
        <button className="btn" onClick={() => setAdding(!adding)}>{adding ? 'Cancel' : 'Add financial statement'}</button>
      </div>}
      {adding && (
        <StatementForm busy={busy} onSubmit={(body) => { void act('Add statement', () => call('POST', p, body)).then(() => setAdding(false)) }} />
      )}
      {log && <p className={log.ok ? 'ok' : 'error'} role="status">{log.text}</p>}

      {data.map(({ statement, run, score, decision }, i) => {
        const base = `${p}/${statement.id}`
        const triggered = run?.riskIndicators.filter((r) => r.triggered) ?? []
        return (
          <details className="stmt" key={statement.id} open={i === 0}>
            <summary>
              <strong>{statement.period.periodLabel}</strong>
              <span className="muted">{statement.period.periodType} · {statement.period.startDate} to {statement.period.endDate}</span>
              <span className="chain">
                <Badge value={run ? 'ANALYZED' : 'NOT ANALYZED'} />
                {score && <Badge value={`GRADE ${score.riskGrade}`} />}
                {decision && <Badge value={decision.outcome} />}
              </span>
            </summary>

            {maker && <div className="steps-row">
              <button className="btn sm" disabled={busy} onClick={() => act('Analyze', () => call('POST', `${base}/analyze`))}>{run ? 'Re-analyze' : 'Analyze'}</button>
              {run && <button className="btn sm" disabled={busy} onClick={() => act('Score', () => call('POST', `${base}/analysis-runs/${run.id}/score`))}>{score ? 'Re-score' : 'Compute score'}</button>}
              {score && run && <button className="btn sm primary" disabled={busy} onClick={() => act('Decision', () => call('POST', `${base}/analysis-runs/${run.id}/score/${score.id}/decision`))}>{decision ? 'Re-run decision' : 'Run decision'}</button>}
            </div>}

            {run && (
              <section>
                <h4>Ratios <span className="muted">· run {dt(run.runAt)}</span></h4>
                <div className="table-wrap"><table>
                  <thead><tr><th>Ratio</th><th>Category</th><th className="num-col">Value</th></tr></thead>
                  <tbody>{run.ratios.map((r) => <tr key={r.ratioCode}><td>{label(r.ratioCode)}</td><td>{label(r.category)}</td><td className="num-col">{num(r.value)}</td></tr>)}</tbody>
                </table></div>
                <div className="chips-row">
                  {run.riskIndicators.map((r) => (
                    <span key={r.indicatorCode} className={`flag ${r.triggered ? 'on' : ''}`}>{r.triggered ? '⚠ ' : ''}{label(r.indicatorCode)}</span>
                  ))}
                  {!triggered.length && <span className="muted">No risk indicators triggered.</span>}
                </div>
              </section>
            )}

            {score && (
              <section>
                <h4>Score <span className="muted">· {score.scorecardName}</span></h4>
                <div className="score">
                  <strong>{score.totalScore}</strong>
                  <Badge value={`GRADE ${score.riskGrade}`} />
                </div>
                <Factors title="Contributing factors" factors={score.contributingFactors} />
                <Factors title="Failed factors" factors={score.failedFactors} />
              </section>
            )}

            {decision && (
              <section>
                <h4>Credit decision <span className="muted">· {decision.policyName}</span></h4>
                <div className="score">
                  <Badge value={decision.outcome} />
                  <span className="muted">Required authority: <b>{label(decision.requiredAuthority)}</b></span>
                </div>
                <Rules title="Passed rules" rules={decision.passedRules} ok />
                <Rules title="Failed rules" rules={decision.failedRules} ok={false} />
                {renderDecision?.(decision)}
              </section>
            )}
          </details>
        )
      })}
    </div>
  )
}
