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
