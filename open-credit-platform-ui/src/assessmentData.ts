import { call } from './api'

export type Period = { periodLabel: string; periodType: string; startDate: string; endDate: string }
export type Statement = { id: string; applicationId: string; period: Period; lineItems: { lineItemCode: string; value: number }[]; submittedAt: string }
export type Run = {
  id: string
  runAt: string
  facts: { factCode: string; value: number }[]
  ratios: { ratioCode: string; category: string; value: number }[]
  riskIndicators: { indicatorCode: string; triggered: boolean }[]
}
export type Factor = { factorCode: string; value: number; points: number; description: string }
export type Score = { id: string; scorecardName: string; totalScore: number; riskGrade: string; contributingFactors: Factor[]; failedFactors: Factor[]; createdAt: string }
export type Rule = { factorCode: string; operator: string; thresholdValue: number; actualValue: number; severity: string; description: string }
export type Decision = { id: string; policyName: string; outcome: string; requiredAuthority: string; passedRules: Rule[]; failedRules: Rule[]; createdAt: string }
export type Chain = { statement: Statement; run?: Run; score?: Score; decision?: Decision }


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

