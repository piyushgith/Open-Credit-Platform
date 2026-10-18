export type Loan = {
  applicationReference: string
  status: string
  decision?: string
  approvedAmount?: number
  interestRate?: number
  monthlyEmi?: number
  tenureMonths?: number
  reasons?: string[]
  productType: string
  [k: string]: unknown
}

export type LoanSummary = {
  applicationReference: string
  applicantName: string
  productType: string
  requestedAmount: number
  tenureMonths: number
  status: string
  decision?: string
  createdAt: string
}

export type Offer = {
  id: string
  offerAmount: number
  interestRate: number
  tenureMonths: number
  monthlyEmi: number
  status: string
}

export type Disbursement = {
  sanctionedAmount: number
  disbursedTotal: number
  status: string
  tranches: { trancheNumber: number; amount: number; requestReference: string }[]
}

export type ApplicationForm = {
  fullName: string
  email: string
  phoneNumber: string
  dateOfBirth: string
  panNumber: string
  productType: string
  requestedAmount: string
  tenureMonths: string
  monthlyIncome: string
  existingEmi: string
  employmentType: string
  vehicleMake: string
  vehiclePrice: string
  downPayment: string
  condition: string
}
