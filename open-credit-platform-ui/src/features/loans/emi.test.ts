import { describe, expect, it } from 'vitest'
import { indicativeRate, monthlyEmi } from './emi'

// Expected values come from the backend's EmiCalculatorTest, which is the source of truth.
describe('monthlyEmi', () => {
  it.each([
    [100000, 12, 12, 8884.88],
    [120000, 0, 12, 10000],
    [10000, 12, 1, 10100],
    [500000, 11.5, 60, 10996.3],
  ])('matches the backend for %d at %d%% over %d months', (principal, rate, months, expected) => {
    expect(monthlyEmi(principal, rate, months)).toBe(expected)
  })

  it.each([
    [0, 12, 12],
    [-1, 12, 12],
    [100000, 12, 0],
    [100000, -1, 12],
    [Number.NaN, 12, 12],
  ])('returns null for invalid input (%d, %d, %d)', (principal, rate, months) => {
    expect(monthlyEmi(principal, rate, months)).toBeNull()
  })
})

describe('indicativeRate', () => {
  it('uses the personal loan rates by employment type', () => {
    expect(indicativeRate('PERSONAL', 'SALARIED', 'NEW')).toBe(11.5)
    expect(indicativeRate('PERSONAL', 'SELF_EMPLOYED', 'NEW')).toBe(13.5)
  })

  it('uses the vehicle loan rates by condition', () => {
    expect(indicativeRate('VEHICLE', 'SALARIED', 'NEW')).toBe(9.5)
    expect(indicativeRate('VEHICLE', 'SALARIED', 'USED')).toBe(12.5)
  })
})
