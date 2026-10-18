import { monthlyEmi } from '../loans'

// Display-only estimate (see loans/emi.ts); the backend remains the source of truth for pricing.

export type TenureUnit = 'years' | 'months'

export type Breakdown = {
  newEmi: number
  newTotalPayable: number
  newInterest: number
  existingEmi: number
  existingTotal: number
  monthlyOutflow: number
  totalPayableAll: number
}

export const toMonths = (value: number, unit: TenureUnit) => Math.round(unit === 'years' ? value * 12 : value)

const round2 = (n: number) => Math.round(n * 100) / 100

export function computeBreakdown(input: {
  amount: number
  ratePct: number
  tenureMonths: number
  existing?: { emi: number; months: number }
}): Breakdown | null {
  const newEmi = monthlyEmi(input.amount, input.ratePct, input.tenureMonths)
  if (newEmi === null) return null
  const newTotalPayable = round2(newEmi * input.tenureMonths)
  const ex = input.existing
  const existingEmi = ex && ex.emi > 0 && ex.months > 0 ? ex.emi : 0
  const existingTotal = existingEmi > 0 ? round2(existingEmi * ex!.months) : 0
  return {
    newEmi,
    newTotalPayable,
    newInterest: round2(newTotalPayable - input.amount),
    existingEmi,
    existingTotal,
    monthlyOutflow: round2(newEmi + existingEmi),
    totalPayableAll: round2(newTotalPayable + existingTotal),
  }
}

export type ScheduleRow = { month: number; principal: number; interest: number }

// Reducing-balance split of each instalment; the last month absorbs rounding so principal sums to the loan amount.
export function amortization(amount: number, ratePct: number, tenureMonths: number, emi: number): ScheduleRow[] {
  const r = ratePct / 1200
  const rows: ScheduleRow[] = []
  let balance = amount
  for (let month = 1; month <= tenureMonths; month++) {
    const interest = round2(balance * r)
    const principal = month === tenureMonths ? round2(balance) : round2(emi - interest)
    balance = round2(balance - principal)
    rows.push({ month, principal, interest })
  }
  return rows
}

export type YearRow = { year: number; principal: number; interest: number }

// Groups instalments into loan years (months 1-12 = year 1, ...).
export function byYear(rows: ScheduleRow[]): YearRow[] {
  const years: YearRow[] = []
  for (const r of rows) {
    const year = Math.ceil(r.month / 12)
    const y = years[year - 1] ?? (years[year - 1] = { year, principal: 0, interest: 0 })
    y.principal = round2(y.principal + r.principal)
    y.interest = round2(y.interest + r.interest)
  }
  return years
}
