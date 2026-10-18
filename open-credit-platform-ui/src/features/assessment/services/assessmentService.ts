import { call } from '../../../services/http'
import type { Chain, Decision, Run, Score, Statement } from '../types'

const last = <T,>(xs: T[] | null): T | undefined => (xs && xs.length ? xs[xs.length - 1] : undefined)
export async function loadChains(ref: string): Promise<Chain[]> {
  const p = `/api/loans/${ref}/financial-statements`
  const statements = await call<Statement[]>('GET', p)
  const chains = await Promise.all(
    statements.map(async (statement): Promise<Chain> => {
      const runs = await call<Run[]>('GET', `${p}/${statement.id}/analysis`).catch(() => [] as never[])
      const run = [...runs].sort((a, b) => a.runAt.localeCompare(b.runAt)).pop()
      if (!run) return { statement }
      const scores = await call<Score[]>('GET', `${p}/${statement.id}/analysis-runs/${run.id}/score`).catch(() => [] as never[])
      const score = last(scores)
      if (!score) return { statement, run }
      const decisions = await call<Decision[]>('GET', `${p}/${statement.id}/analysis-runs/${run.id}/score/${score.id}/decision`).catch(() => [] as never[])
      return { statement, run, score, decision: last(decisions) }
    }),
  )
  return chains.sort((a, b) => b.statement.submittedAt.localeCompare(a.statement.submittedAt))
}

