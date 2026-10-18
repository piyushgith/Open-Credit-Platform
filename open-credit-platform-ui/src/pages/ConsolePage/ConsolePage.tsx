import { call } from '../../services/http'
import { useFetch } from '../../hooks/useFetch'
import { useRoles } from '../../hooks/useRoles'
import type { Session } from '../../types/session.types'
import { Dashboard, loadStats } from '../../features/dashboard'
import { LoanDetail, NewApplication, StageList } from '../../features/loans'
import { Approvals } from '../../features/approvals'
import { EmiCalculator } from '../../features/emi'
import ConsoleSidebar from './ConsoleSidebar'
import { parseConsoleRoute } from './route'

export default function ConsolePage({ session, route, onLogout }: { session: Session; route: string; onLogout: () => void }) {
  const view = parseConsoleRoute(route)
  const stats = useFetch(loadStats, [])
  const { maker } = useRoles()
  const pending = useFetch(async () => {
    const [a, b] = await Promise.all(['PENDING_MAKER', 'PENDING_CHECKER'].map((st) =>
      call<{ page: { totalElements: number } }>('GET', `/api/approval-cases?status=${st}&size=1`)))
    return a.page.totalElements + b.page.totalElements
  }, [])

  return (
    <div className="shell">
      <ConsoleSidebar view={view} session={session} maker={maker} counts={stats.data?.counts} pending={pending.data} onLogout={onLogout} />
      <div className="main">
        {view.kind === 'dashboard' && (
          <>
            <h1>Dashboard</h1>
            <p className="muted lead-s">Portfolio overview across every stage of the loan lifecycle.</p>
            <Dashboard stats={stats.data} error={stats.error} />
          </>
        )}
        {view.kind === 'approvals' && <Approvals />}
        {view.kind === 'emi' && <EmiCalculator />}
        {view.kind === 'search' && <StageList />}
        {view.kind === 'new' && !maker && <div className="box"><p className="muted">Creating applications needs the MAKER or ADMIN role.</p></div>}
        {view.kind === 'new' && maker && <NewApplication onCreated={(r) => { stats.reload(); location.hash = `#/console/loan/${r}` }} />}
        {view.kind === 'stage' && <StageList key={view.status} status={view.status} />}
        {view.kind === 'loan' && <LoanDetail key={view.ref} reference={view.ref} onChanged={() => { stats.reload(); pending.reload() }} />}
      </div>
    </div>
  )
}
