import { call } from '../../../services/http'
import type { ApplicationForm, Loan } from '../types'

// Registers the customer, then applies for the loan; returns the application reference.
export async function createApplication(f: ApplicationForm): Promise<string> {
  const customer = await call<{ id: string }>('POST', '/api/customers', {
    fullName: f.fullName, email: f.email, phoneNumber: f.phoneNumber,
    dateOfBirth: f.dateOfBirth, panNumber: f.panNumber.toUpperCase(),
  })
  const base = {
    productType: f.productType, customerId: customer.id,
    requestedAmount: Number(f.requestedAmount), tenureMonths: Number(f.tenureMonths),
  }
  const body = f.productType === 'PERSONAL'
    ? { ...base, monthlyIncome: Number(f.monthlyIncome), existingEmi: Number(f.existingEmi), employmentType: f.employmentType }
    : { ...base, vehicleMake: f.vehicleMake, vehiclePrice: Number(f.vehiclePrice), downPayment: Number(f.downPayment), condition: f.condition }
  const loan = await call<Loan>('POST', '/api/loans/apply', body)
  return loan.applicationReference
}
