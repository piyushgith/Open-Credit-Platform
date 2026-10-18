import { useState } from 'react'
import { call } from '../../../services/http'
import { dt, money } from '../../../utils/format'
import { useFetch } from '../../../hooks/useFetch'
import { useRoles } from '../../auth'
import { Badge } from '../../../components/ui'

type CaseRow = {
  id: string
  applicationReference: string
  applicantName: string
  productType: string
  requestedAmount: number
  requiredLevel: string
  status: string
  createdAt: string
}
type Page = { content: CaseRow[]; page: { totalElements: number; totalPages: number } }

const TABS = [
  ['PENDING_MAKER', 'Waiting for maker'],
  ['PENDING_CHECKER', 'Waiting for checker'],
  ['APPROVED', 'Approved'],
  ['REJECTED', 'Rejected'],
] as const
const SIZE = 20

export default function Approvals() {
  const { role } = useRoles()
  const [tab, setTab] = useState<string>(role === 'CHECKER' ? 'PENDING_CHECKER' : 'PENDING_MAKER')
  const [page, setPage] = useState(0)
  const counts = useFetch(async () => {
    const r = await Promise.all(TABS.map(([s]) => call<Page>('GET', `/api/approval-cases?status=${s}&size=1`)))
    return Object.fromEntries(TABS.map(([s], i) => [s, r[i].page.totalElements])) as Record<string, number>
  }, [])
  const { data, error, loading } = useFetch(() => call<Page>('GET', `/api/approval-cases?status=${tab}&page=${page}&size=${SIZE}`), [tab, page])
  const pages = data?.page.totalPages ?? 0

  return (
    <div className="box">
      <h2>Approvals</h2>
      <p className="muted">Maker–checker cases by status, oldest first. Open an application to record a decision.</p>
      <div className="tabs-h" role="tablist" aria-label="Approval status">
        {TABS.map(([s, name]) => (
          <button key={s} role="tab" aria-selected={tab === s} onClick={() => { setTab(s); setPage(0) }}>
            {name} <span className="count">{counts.data?.[s] ?? '…'}</span>
          </button>
        ))}
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap" aria-busy={loading}>
        <table className="clickable">
          <thead><tr><th>Application</th><th>Applicant</th><th>Product</th><th>Amount</th><th>Required level</th><th>Status</th><th>Opened</th></tr></thead>
          <tbody>
            {data?.content.map((c) => (
              <tr key={c.id}>
                <td><a href={`#/console/loan/${c.applicationReference}`}>{c.applicationReference}</a></td>
                <td>{c.applicantName}</td><td>{c.productType}</td><td>{money(c.requestedAmount)}</td>
                <td>{c.requiredLevel.replace(/_/g, ' ')}</td><td><Badge value={c.status} /></td><td>{dt(c.createdAt)}</td>
              </tr>
            ))}
            {data && !data.content.length && <tr><td colSpan={7} className="muted">No cases in this status.</td></tr>}
          </tbody>
        </table>
      </div>
      {pages > 1 && (
        <div className="pager">
          <button className="btn sm" disabled={page === 0 || loading} onClick={() => setPage(page - 1)}>Previous</button>
          <span className="muted">Page {page + 1} of {pages}</span>
          <button className="btn sm" disabled={page + 1 >= pages || loading} onClick={() => setPage(page + 1)}>Next</button>
        </div>
      )}
    </div>
  )
}
