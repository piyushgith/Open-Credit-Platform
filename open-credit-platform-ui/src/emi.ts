// Display-only EMI estimate. The backend (EmiCalculator, BigDecimal) is the source of truth and
// prices the loan on submission; this mirrors its reducing-balance formula so the form can show an
// indication, using the rates hard-coded in PersonalLoanStrategy and VehicleLoanStrategy.

export function monthlyEmi(principal: number, annualRatePercent: number, tenureMonths: number): number | null {
  if (!(principal > 0) || !(tenureMonths > 0) || annualRatePercent < 0) return null
  const r = annualRatePercent / 1200
  const emi = r === 0
    ? principal / tenureMonths
    : (principal * r * (1 + r) ** tenureMonths) / ((1 + r) ** tenureMonths - 1)
  return Math.round(emi * 100) / 100
}

export function indicativeRate(product: string, employmentType: string, condition: string): number {
  if (product === 'PERSONAL') return employmentType === 'SALARIED' ? 11.5 : 13.5
  return condition === 'NEW' ? 9.5 : 12.5
}
