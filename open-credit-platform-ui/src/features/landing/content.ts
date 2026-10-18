import { API } from '../../services/http'

export const DOCS_URL = import.meta.env.VITE_DOCS_URL ?? `${API}/swagger-ui.html`
export const REPO = 'https://github.com/piyushprasad/Open-Credit-Platform'

export type Lang = 'curl' | 'javascript' | 'java' | 'python'

export const SNIPPETS: Record<Lang, { label: string; file: string; code: string }> = {
  curl: {
    label: 'cURL',
    file: 'terminal',
    code: `# 1. Authenticate
curl -X POST ${API}/api/auth/login \\
  -H "Content-Type: application/json" \\
  -d '{"username":"alice.maker","password":"maker123"}'

# 2. Call the API with the bearer token
curl ${API}/api/loan-products \\
  -H "Authorization: Bearer $TOKEN"`,
  },
  javascript: {
    label: 'JavaScript',
    file: 'client.js',
    code: `const base = '${API}'

const { data: session } = await fetch(\`\${base}/api/auth/login\`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username: 'alice.maker', password: 'maker123' }),
}).then((r) => r.json())

const products = await fetch(\`\${base}/api/loan-products\`, {
  headers: { Authorization: \`Bearer \${session.token}\` },
}).then((r) => r.json())`,
  },
  java: {
    label: 'Java',
    file: 'Client.java',
    code: `var http = HttpClient.newHttpClient();

var login = HttpRequest.newBuilder(URI.create("${API}/api/auth/login"))
    .header("Content-Type", "application/json")
    .POST(BodyPublishers.ofString(
        "{\\"username\\":\\"alice.maker\\",\\"password\\":\\"maker123\\"}"))
    .build();
String body = http.send(login, BodyHandlers.ofString()).body();
String token = JsonPath.read(body, "$.data.token");

var products = HttpRequest.newBuilder(URI.create("${API}/api/loan-products"))
    .header("Authorization", "Bearer " + token)
    .build();`,
  },
  python: {
    label: 'Python',
    file: 'client.py',
    code: `import requests

base = "${API}"

session = requests.post(
    f"{base}/api/auth/login",
    json={"username": "alice.maker", "password": "maker123"},
).json()["data"]

products = requests.get(
    f"{base}/api/loan-products",
    headers={"Authorization": f"Bearer {session['token']}"},
).json()`,
  },
}

export const IDLE = '// Press "Run request" to call the live API.'

export const ICONS = {
  shield: 'M12 3l8 3v6c0 4.5-3.2 8.3-8 9-4.8-.7-8-4.5-8-9V6l8-3z',
  users: 'M16 11a4 4 0 10-8 0 4 4 0 008 0zM4 21c0-4 3.6-6 8-6s8 2 8 6',
  log: 'M6 3h9l4 4v14H6V3zM14 3v5h5M9 13h6M9 17h6',
  key: 'M15 7a4 4 0 11-3.9 5H3v-2h2v-2h2v-2h4.1A4 4 0 0115 7z',
  ext: 'M7 17L17 7M8 7h9v9',
}

export const CONTROLS = [
  { icon: ICONS.key, title: 'Stateless JWT auth', text: 'Signed bearer tokens with short expiry. Secrets are injected via environment, never defaulted in production.' },
  { icon: ICONS.users, title: 'Maker–checker approval', text: 'Role-separated approvals with an authority matrix; database constraints enforce one decision per approver.' },
  { icon: ICONS.log, title: 'Audit trail', text: 'Every state change is written with request and correlation IDs at the exact point of persistence.' },
  { icon: ICONS.shield, title: 'Deterministic decisions', text: 'Credit decisions are rule-based and explainable. AI never makes or overrides a decision.' },
]

export const FRAMEWORKS = [
  { name: 'SOC 2 Type II', note: 'Controls align with the Trust Services Criteria for access control, change management and logging.' },
  { name: 'ISO/IEC 27001', note: 'Controls align with Annex A access, logging and secure development practices.' },
  { name: 'PCI DSS', note: 'No card data is stored or processed by this platform.' },
  { name: 'GDPR', note: 'Data-minimising model with auditable access; a DPA is required for production use.' },
]

export type Stage = {
  id: string
  name: string
  status: string
  summary: string
  role: string
  endpoints: [string, string][]
}

export const LIFECYCLE_STAGES: Stage[] = [
  { id: 'apply', name: 'Apply', status: 'DRAFT', role: 'Authenticated user', summary: 'Create a customer and capture a loan application for a product type. The application starts as a draft and is identified by a human-readable reference.',
    endpoints: [['POST', '/api/customers'], ['GET', '/api/loan-products'], ['POST', '/api/loans/apply'], ['GET', '/api/loans/{reference}']] },
  { id: 'kyc', name: 'Documents & KYC', status: 'DRAFT', role: 'Maker / Checker', summary: 'Upload supporting documents and run identity verification. Documents and the KYC case are each verified or rejected explicitly.',
    endpoints: [['POST', '/api/loans/{reference}/documents'], ['POST', '/api/loans/{reference}/documents/{id}/verify'], ['POST', '/api/loans/{reference}/kyc'], ['POST', '/api/loans/{reference}/kyc/verify']] },
  { id: 'submit', name: 'Submit & underwrite', status: 'SUBMITTED → UNDERWRITING', role: 'Authenticated user', summary: 'Submitting moves the application through underwriting in one call. The outcome is approved, declined, or referred for a later retry.',
    endpoints: [['POST', '/api/loans/{reference}/submit'], ['POST', '/api/loans/{reference}/underwriting/retry'], ['GET', '/api/loans/{reference}/underwriting']] },
  { id: 'financial', name: 'Financial analysis', status: 'UNDERWRITING', role: 'Authenticated user', summary: 'Record financial statements and run analysis to produce versioned ratios, kept as runs for traceability.',
    endpoints: [['POST', '/api/loans/{reference}/financial-statements'], ['POST', '/api/loans/{reference}/financial-statements/{id}/analyze'], ['GET', '/api/loans/{reference}/financial-statements/{id}/analysis']] },
  { id: 'scoring', name: 'Credit scoring', status: 'UNDERWRITING', role: 'Admin configures scorecards', summary: 'Apply a configurable scorecard to an analysis run. Each rule contribution is stored so the score can be explained.',
    endpoints: [['POST', '.../analysis-runs/{runId}/score'], ['GET', '.../analysis-runs/{runId}/score'], ['GET', '/api/scorecards']] },
  { id: 'decision', name: 'Credit decision', status: 'UNDERWRITING', role: 'Admin configures policies', summary: 'A deterministic policy engine turns the score and ratios into a decision with reasons. No AI is involved.',
    endpoints: [['POST', '.../score/{scoreId}/decision'], ['GET', '.../score/{scoreId}/decision'], ['GET', '/api/credit-policies']] },
  { id: 'approval', name: 'Maker–checker approval', status: 'UNDERWRITING', role: 'Maker, then Checker', summary: 'A maker proposes and a checker confirms, routed by the authority matrix. One decision per approver is enforced by the database.',
    endpoints: [['POST', '/api/credit-decisions/{id}/approval-case'], ['POST', '/api/approval-cases/{id}/maker-decision'], ['POST', '/api/approval-cases/{id}/checker-decision']] },
  { id: 'offer', name: 'Offer', status: 'OFFERED', role: 'Authenticated user', summary: 'Approved applications receive one or more offers. The customer selects exactly one.',
    endpoints: [['POST', '/api/loans/{reference}/offers'], ['GET', '/api/loans/{reference}/offers'], ['POST', '/api/loans/{reference}/offers/{id}/select']] },
  { id: 'sanction', name: 'Sanction', status: 'SANCTIONED', role: 'Authenticated user', summary: 'Sanction the selected offer. Requires the application to be offered with exactly one selected offer.',
    endpoints: [['POST', '/api/loans/{reference}/sanction'], ['GET', '/api/loans/{reference}/sanction']] },
  { id: 'disburse', name: 'Disbursement', status: 'DISBURSED', role: 'Authenticated user', summary: 'Release funds in one or more tranches. Requests are idempotent by reference, and the loan is disbursed once the sanctioned amount is reached.',
    endpoints: [['POST', '/api/loans/{reference}/disbursements'], ['GET', '/api/loans/{reference}/disbursements']] },
  { id: 'audit', name: 'Audit', status: 'All stages', role: 'Authenticated user', summary: 'Every business event and data change is queryable by entity, tagged with request and correlation IDs.',
    endpoints: [['GET', '/api/audit/business-events'], ['GET', '/api/audit/data-changes']] },
]

export function stageCurl([method, path]: [string, string]) {
  const url = `${API}${path.startsWith('/') ? path : '/api/loans/{reference}' + path.slice(2)}`
  const lines = [`curl${method === 'GET' ? '' : ' -X ' + method} "${url}"`, '  -H "Authorization: Bearer $TOKEN"']
  if (method !== 'GET') lines.push('  -H "Content-Type: application/json"', "  -d '{ ... }'   # see API reference for the schema")
  return lines.join(' \\\n')
}
