import { money, STAGES, type Stats } from './api'
import { Badge } from './ui'

const pct = (n: number) => `${(n * 100).toFixed(1)}%`
const fmt = (n: number) => n.toLocaleString('en-IN')

function Kpi({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className="kpi">
      <span className="kpi-label">{label}</span>
      <strong>{value}</strong>
      {hint && <span className="kpi-hint">{hint}</span>}
    </div>
  )
}

function Last14Days({ days }: { days: Stats['perDay'] }) {
  const max = Math.max(1, ...days.map((d) => d.count))
  return (
    <figure className="bars" aria-label="Applications created per day, last 14 days (UTC)">
      {days.map(({ date, count }) => (
        <div className="bar-col" key={date} title={`${date}: ${count}`}>
          <span className="bar-n">{count || ''}</span>
          <span className="bar-fill" style={{ height: `${(count / max) * 100}%` }} />
          <span className="bar-d">{Number(date.slice(8))}</span>
        </div>
      ))}
    </figure>
  )
}

export default function Dashboard({ stats, error }: { stats?: Stats; error?: string }) {
  if (error) return <p className="error" role="alert">{error}</p>
  if (!stats) return <p className="muted">Loading dashboard…</p>

  const { counts, amounts, byProduct, perDay, referred, recent } = stats
  const c = (s: string) => counts[s] ?? 0
  const total = Object.values(counts).reduce((a, b) => a + b, 0)
  const pipeline = c('DRAFT') + c('SUBMITTED') + c('UNDERWRITING') + c('OFFERED')
  const approved = c('OFFERED') + c('SANCTIONED') + c('DISBURSED')
  const decided = approved + c('DECLINED')
  const maxCount = Math.max(1, ...Object.values(counts))
  const personal = byProduct.PERSONAL ?? 0
  const vehicle = byProduct.VEHICLE ?? 0

  return (
    <>
      <div className="kpis">
        <Kpi label="Total applications" value={fmt(total)} />
        <Kpi label="In pipeline" value={fmt(pipeline)} hint="Draft to offered" />
        <Kpi label="Approval rate" value={decided ? pct(approved / decided) : '—'} hint={`${fmt(approved)} of ${fmt(decided)} decided`} />
        <Kpi label="Sanctioned" value={fmt(c('SANCTIONED') + c('DISBURSED'))} hint="Including disbursed" />
        <Kpi label="Fully disbursed" value={fmt(c('DISBURSED'))} />
        <Kpi label="Declined" value={fmt(c('DECLINED'))} hint={decided ? pct(c('DECLINED') / decided) + ' of decided' : undefined} />
      </div>

      <div className="grid2">
        <section className="box">
          <h3>Pipeline by stage</h3>
          <ul className="funnel">
            {STAGES.map((s) => (
              <li key={s.status}>
                <a href={`#/console/stage/${s.status}`}>
                  <span className="f-name">{s.name}</span>
                  <span className="f-track"><span className={`f-fill ${s.status.toLowerCase()}`} style={{ width: `${(c(s.status) / maxCount) * 100}%` }} /></span>
                  <span className="f-n">{fmt(c(s.status))}</span>
                </a>
              </li>
            ))}
          </ul>
        </section>

        <section className="box">
          <h3>Requested value by stage</h3>
          <table className="plain">
            <thead><tr><th>Stage</th><th className="num-col">Value</th></tr></thead>
            <tbody>
              {STAGES.map((s) => (
                <tr key={s.status}><td>{s.name}</td><td className="num-col">{money(amounts[s.status] ?? 0)}</td></tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>

      <div className="grid2">
        <section className="box">
          <h3>New applications, last 14 days</h3>
          <Last14Days days={perDay} />
          <p className="muted small">Days are in UTC.</p>
        </section>
        <section className="box">
          <h3>Product mix</h3>
          <div className="split-bar" role="img" aria-label={`Personal ${personal}, Vehicle ${vehicle}`}>
            <span style={{ flex: personal || 0.0001 }} className="seg a" />
            <span style={{ flex: vehicle || 0.0001 }} className="seg b" />
          </div>
          <ul className="legend">
            <li><i className="dot a" /> Personal <strong>{fmt(personal)}</strong></li>
            <li><i className="dot b" /> Vehicle <strong>{fmt(vehicle)}</strong></li>
          </ul>
          <h3>Needs attention</h3>
          {referred ? (
            <p>
              <a href="#/console/stage/UNDERWRITING"><strong>{fmt(referred)}</strong> referred application{referred > 1 ? 's' : ''}</a>{' '}
              waiting for an underwriting retry.
            </p>
          ) : <p className="muted">Nothing referred.</p>}
        </section>
      </div>

      <section className="box">
        <h3>Recent applications</h3>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Reference</th><th>Applicant</th><th>Product</th><th>Amount</th><th>Stage</th><th>Created</th></tr></thead>
            <tbody>
              {recent.map((l) => (
                <tr key={l.applicationReference}>
                  <td><a href={`#/console/loan/${l.applicationReference}`}>{l.applicationReference}</a></td>
                  <td>{l.applicantName}</td><td>{l.productType}</td><td>{money(l.requestedAmount)}</td>
                  <td><Badge value={l.status} /></td><td>{new Date(l.createdAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}
