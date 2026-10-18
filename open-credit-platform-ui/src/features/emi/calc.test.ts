import { describe, expect, it } from 'vitest'
import { amortization, byYear, computeBreakdown, toMonths } from './calc'

describe('toMonths', () => {
  it('converts years and rounds', () => {
    expect(toMonths(5, 'years')).toBe(60)
    expect(toMonths(2.5, 'years')).toBe(30)
    expect(toMonths(18, 'months')).toBe(18)
  })
})

describe('computeBreakdown', () => {
  it('computes the new loan totals (backend reference: 100000 @ 12% / 12m = 8884.88)', () => {
    const b = computeBreakdown({ amount: 100000, ratePct: 12, tenureMonths: 12 })!
    expect(b.newEmi).toBe(8884.88)
    expect(b.newTotalPayable).toBe(106618.56)
    expect(b.newInterest).toBe(6618.56)
    expect(b.existingEmi).toBe(0)
    expect(b.monthlyOutflow).toBe(8884.88)
    expect(b.totalPayableAll).toBe(106618.56)
  })

  it('has zero interest at 0%', () => {
    const b = computeBreakdown({ amount: 120000, ratePct: 0, tenureMonths: 12 })!
    expect(b.newEmi).toBe(10000)
    expect(b.newInterest).toBe(0)
  })

  it('adds existing obligations to the monthly outflow and total payable', () => {
    const b = computeBreakdown({ amount: 100000, ratePct: 12, tenureMonths: 12, existing: { emi: 5000, months: 10 } })!
    expect(b.existingTotal).toBe(50000)
    expect(b.monthlyOutflow).toBe(13884.88)
    expect(b.totalPayableAll).toBe(156618.56)
  })

  it('ignores an incomplete existing EMI', () => {
    const b = computeBreakdown({ amount: 100000, ratePct: 12, tenureMonths: 12, existing: { emi: 5000, months: 0 } })!
    expect(b.existingEmi).toBe(0)
    expect(b.existingTotal).toBe(0)
  })

  it('returns null for invalid new-loan input', () => {
    expect(computeBreakdown({ amount: 0, ratePct: 12, tenureMonths: 12 })).toBeNull()
    expect(computeBreakdown({ amount: 1000, ratePct: 12, tenureMonths: 0 })).toBeNull()
  })
})

describe('amortization', () => {
  it('splits each instalment into principal and interest and repays the loan fully', () => {
    const rows = amortization(100000, 12, 12, 8884.88)
    expect(rows).toHaveLength(12)
    expect(rows[0]).toEqual({ month: 1, principal: 7884.88, interest: 1000 })
    expect(rows.reduce((t, r) => t + r.principal, 0)).toBeCloseTo(100000, 2)
  })

  it('has no interest at 0%', () => {
    const rows = amortization(120000, 0, 12, 10000)
    expect(rows.every((r) => r.interest === 0 && r.principal === 10000)).toBe(true)
  })
})

describe('byYear', () => {
  it('sums instalments per loan year, including a partial last year', () => {
    const rows = amortization(100000, 12, 18, 6000)
    const years = byYear(rows)
    expect(years.map((y) => y.year)).toEqual([1, 2])
    expect(years[0].principal).toBeCloseTo(rows.slice(0, 12).reduce((t, r) => t + r.principal, 0), 2)
    expect(years[1].interest).toBeCloseTo(rows.slice(12).reduce((t, r) => t + r.interest, 0), 2)
  })
})
