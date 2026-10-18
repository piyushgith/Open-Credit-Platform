import { soft } from '../../../services/http'
import { dt } from '../../../utils/format'
import { loadChains } from '../../assessment'
import { useFetch } from '../../../hooks/useFetch'
import { Badge } from '../../../components/ui'

type BusinessEvent = { id: string; eventType: string; entityType: string; entityId: string; actorUsername: string; occurredAt: string; details?: string }
type DataChange = { id: string; entityType: string; entityId: string; field: string; oldValue?: string; newValue?: string; changedBy: string; changedAt: string }
type Entry = { id: string; at: string; actor: string; kind: 'event' | 'change'; entity: string; title: string; detail?: string }

type Ref = [entityType: string, entityId: string]

const label = (s: string) => s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())

async function load(reference: string, base: Ref[]): Promise<Entry[]> {
  // Decisions and approval cases are found through the assessment chain; both are optional.
  const refs: Ref[] = [...base]
  const chains = await loadChains(reference).catch(() => [])
  for (const c of chains) {
    // The loan response hides the application id; statements expose it as a fallback source.
    if (!refs.some(([t]) => t === 'LoanApplication')) refs.push(['LoanApplication', c.statement.applicationId])
    if (!c.decision) continue
    refs.push(['CreditDecision', c.decision.id])
    const kase = await soft<{ id: string }>('GET', `/api/credit-decisions/${c.decision.id}/approval-case`)
    if (kase) refs.push(['ApprovalCase', kase.id])
  }

  const q = ([t, id]: Ref) => `entityType=${encodeURIComponent(t)}&entityId=${encodeURIComponent(id)}`
  const rows = await Promise.all(
    refs.map(async (r) => {
      const [events, changes] = await Promise.all([
        soft<BusinessEvent[]>('GET', `/api/audit/business-events?${q(r)}`),
        soft<DataChange[]>('GET', `/api/audit/data-changes?${q(r)}`),
      ])
      return [...(events ?? []).map((e): Entry => ({
        id: e.id, at: e.occurredAt, actor: e.actorUsername, kind: 'event', entity: e.entityType,
        title: label(e.eventType), detail: e.details,
      })), ...(changes ?? []).map((c): Entry => ({
        id: c.id, at: c.changedAt, actor: c.changedBy, kind: 'change', entity: c.entityType,
        title: `${c.field} changed`, detail: `${c.oldValue ?? '—'} → ${c.newValue ?? '—'}`,
      }))]
    }),
  )
  return rows.flat().sort((a, b) => a.at.localeCompare(b.at))
}

export default function AuditTimeline({ reference, refs }: { reference: string; refs: Ref[] }) {
  const { data, error, loading } = useFetch(() => load(reference, refs), [reference, JSON.stringify(refs)])
  if (error) return <p className="error" role="alert">{error}</p>
  if (!data) return <p className="muted">{loading ? 'Loading audit trail…' : ''}</p>
  if (!data.length) return <p className="muted">No audit entries recorded for this application yet.</p>
  return (
    <ol className="audit">
      {data.map((e) => (
        <li key={e.kind + e.id}>
          <time dateTime={e.at}>{dt(e.at)}</time>
          <div>
            <p><strong>{e.title}</strong> <Badge value={e.kind === 'event' ? 'EVENT' : 'CHANGE'} /> <span className="muted">{e.entity}</span></p>
            {e.detail && <p className="muted">{e.detail}</p>}
            <p className="muted small">by {e.actor}</p>
          </div>
        </li>
      ))}
    </ol>
  )
}
