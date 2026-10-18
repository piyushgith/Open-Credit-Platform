export const STAGES = [
  { status: 'DRAFT', name: 'Draft', blurb: 'Applications captured but not yet submitted. KYC and documents can be prepared here.' },
  { status: 'SUBMITTED', name: 'Submitted', blurb: 'Submitted applications waiting to enter underwriting.' },
  { status: 'UNDERWRITING', name: 'Underwriting', blurb: 'Under review. Referred applications stay here until underwriting is retried.' },
  { status: 'OFFERED', name: 'Offered', blurb: 'Approved applications with offers to create and select, ready for sanction once KYC is verified.' },
  { status: 'SANCTIONED', name: 'Sanctioned', blurb: 'Sanctioned loans awaiting disbursement of the sanctioned amount, in one or more tranches.' },
  { status: 'DISBURSED', name: 'Disbursed', blurb: 'Fully disbursed loans.' },
  { status: 'DECLINED', name: 'Declined', blurb: 'Applications declined by underwriting.' },
] as const

export type StageStatus = (typeof STAGES)[number]['status']
