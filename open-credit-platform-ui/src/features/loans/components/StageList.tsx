import { useEffect, useState } from 'react'
import { call } from '../../../services/http'
import { dt, money } from '../../../utils/format'
import { useFetch } from '../../../hooks/useFetch'
import { Badge } from '../../../components/common'
import { STAGES } from '../stages'
import type { LoanSummary } from '../types'

type Page = { content: LoanSummary[]; page: { number: number; totalElements: number; totalPages: number } }
const SIZE = 20

// Lists applications, newest first. With a `status` it is one stage's list; without, it is the
// cross-stage search. Both filter on the server (reference or applicant name).
export default function StageList({ status }: { status?: string }) {
  const stage = STAGES.find((s) => s.status === status)
  const [page, setPage] = useState(0)
  const [input, setInput] = useState('')
  const [term, setTerm] = useState('')
  const [stageFilter, setStageFilter] = useState('')
  const effective = status ?? stageFilter

  useEffect(() => {
    const t = setTimeout(() => {
      setTerm(input.trim())
      setPage(0)
    }, 300)
    return () => clearTimeout(t)
  }, [input])

  const { data, error, loading } = useFetch(() => {
    const params = new URLSearchParams({ page: String(page), size: String(SIZE) })
    if (effective) params.set('status', effective)
    if (term) params.set('q', term)
    return call<Page>('GET', `/api/loans?${params}`)
  }, [effective, term, page])

  if (status && !stage) return <p className="error">Unknown stage.</p>
  const rows = data?.content ?? []
  const total = data?.page.totalElements ?? 0
  const pages = data?.page.totalPages ?? 0

  return (
    <div className="box">
      <div className="row between">
        <div>
          <h2>{stage ? stage.name : 'Search applications'} <span className="count">{data ? total.toLocaleString('en-IN') : ''}</span></h2>
          <p className="muted">{stage ? stage.blurb : 'Find an application in any stage by reference number or applicant name.'}</p>
        </div>
        <div className="row">
          {!stage && (
            <select className="search" aria-label="Stage" value={stageFilter} onChange={(e) => { setStageFilter(e.target.value); setPage(0) }}>
              <option value="">All stages</option>
              {STAGES.map((s) => <option key={s.status} value={s.status}>{s.name}</option>)}
            </select>
          )}
          <input className="search" type="search" placeholder="Reference or applicant name" aria-label="Search applications" autoFocus={!stage} value={input} onChange={(e) => setInput(e.target.value)} />
        </div>
      </div>
      {error && <p className="error" role="alert">{error}</p>}
      <div className="table-wrap" aria-busy={loading}>
        <table className="clickable">
          <thead><tr><th>Reference</th><th>Applicant</th><th>Product</th><th>Amount</th><th>Tenure</th>{!stage && <th>Stage</th>}<th>Decision</th><th>Created</th></tr></thead>
          <tbody>
            {rows.map((l) => (
              <tr key={l.applicationReference}>
                <td><a href={`#/console/loan/${l.applicationReference}`}>{l.applicationReference}</a></td>
                <td>{l.applicantName}</td><td>{l.productType}</td><td>{money(l.requestedAmount)}</td>
                <td>{l.tenureMonths} mo</td>{!stage && <td><Badge value={l.status} /></td>}<td><Badge value={l.decision} /></td><td>{dt(l.createdAt)}</td>
              </tr>
            ))}
            {data && !rows.length && <tr><td colSpan={8} className="muted">No applications{term ? ' match the search' : ' in this stage'}.</td></tr>}
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
