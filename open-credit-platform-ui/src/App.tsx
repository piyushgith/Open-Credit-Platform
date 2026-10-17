import { useEffect, useState } from 'react'
import './App.css'
import Console from './Console'
import { API } from './api'

const DOCS_URL = import.meta.env.VITE_DOCS_URL ?? `${API}/swagger-ui.html`
const REPO = 'https://github.com/piyushprasad/Open-Credit-Platform'

type Lang = 'curl' | 'javascript' | 'java' | 'python'

const SNIPPETS: Record<Lang, { label: string; file: string; code: string }> = {
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

const IDLE = '// Press "Run request" to call the live API.'

const Icon = ({ d }: { d: string }) => (
  <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
    <path d={d} />
  </svg>
)

const ICONS = {
  shield: 'M12 3l8 3v6c0 4.5-3.2 8.3-8 9-4.8-.7-8-4.5-8-9V6l8-3z',
  users: 'M16 11a4 4 0 10-8 0 4 4 0 008 0zM4 21c0-4 3.6-6 8-6s8 2 8 6',
  log: 'M6 3h9l4 4v14H6V3zM14 3v5h5M9 13h6M9 17h6',
  key: 'M15 7a4 4 0 11-3.9 5H3v-2h2v-2h2v-2h4.1A4 4 0 0115 7z',
  ext: 'M7 17L17 7M8 7h9v9',
}

const CONTROLS = [
  { icon: ICONS.key, title: 'Stateless JWT auth', text: 'Signed bearer tokens with short expiry. Secrets are injected via environment, never defaulted in production.' },
  { icon: ICONS.users, title: 'Maker–checker approval', text: 'Role-separated approvals with an authority matrix; database constraints enforce one decision per approver.' },
  { icon: ICONS.log, title: 'Audit trail', text: 'Every state change is written with request and correlation IDs at the exact point of persistence.' },
  { icon: ICONS.shield, title: 'Deterministic decisions', text: 'Credit decisions are rule-based and explainable. AI never makes or overrides a decision.' },
]

const FRAMEWORKS = [
  { name: 'SOC 2 Type II', note: 'Controls align with the Trust Services Criteria for access control, change management and logging.' },
  { name: 'ISO/IEC 27001', note: 'Controls align with Annex A access, logging and secure development practices.' },
  { name: 'PCI DSS', note: 'No card data is stored or processed by this platform.' },
  { name: 'GDPR', note: 'Data-minimising model with auditable access; a DPA is required for production use.' },
]

type Stage = {
  id: string
  name: string
  status: string
  summary: string
  role: string
  endpoints: [string, string][]
}

const STAGES: Stage[] = [
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

function stageCurl([method, path]: [string, string]) {
  const url = `${API}${path.startsWith('/') ? path : '/api/loans/{reference}' + path.slice(2)}`
  const lines = [`curl${method === 'GET' ? '' : ' -X ' + method} "${url}"`, '  -H "Authorization: Bearer $TOKEN"']
  if (method !== 'GET') lines.push('  -H "Content-Type: application/json"', "  -d '{ ... }'   # see API reference for the schema")
  return lines.join(' \\\n')
}

function Lifecycle() {
  const [i, setI] = useState(0)
  const s = STAGES[i]
  return (
    <div className="life">
      <ol className="steps" aria-label="Loan stages">
        {STAGES.map((st, n) => (
          <li key={st.id}>
            <button className="step" aria-current={n === i ? 'step' : undefined} onClick={() => setI(n)}>
              <span className="num">{n + 1}</span>
              {st.name}
            </button>
          </li>
        ))}
      </ol>
      <div className="stage" aria-live="polite">
        <div className="stage-head">
          <h3>{i + 1}. {s.name}</h3>
          <span className="status">{s.status}</span>
        </div>
        <p>{s.summary}</p>
        <p className="role"><strong>Access:</strong> {s.role}</p>
        <ul className="eps">
          {s.endpoints.map(([m, p]) => (
            <li key={m + p}><span className={`verb ${m.toLowerCase()}`}>{m}</span><code>{p}</code></li>
          ))}
        </ul>
        <div className="panel">
          <div className="bar"><span>primary call</span></div>
          <pre tabIndex={0}><code>{stageCurl(s.endpoints.find(([m]) => m === 'POST') ?? s.endpoints[0])}</code></pre>
        </div>
        <div className="pager">
          <button className="btn" disabled={i === 0} onClick={() => setI(i - 1)}>Previous</button>
          <button className="btn primary" disabled={i === STAGES.length - 1} onClick={() => setI(i + 1)}>Next stage</button>
        </div>
      </div>
    </div>
  )
}

function CodePreview() {
  const [lang, setLang] = useState<Lang>('curl')
  const [copied, setCopied] = useState(false)
  const [out, setOut] = useState(IDLE)
  const [state, setState] = useState<'idle' | 'ok' | 'err'>('idle')
  const [busy, setBusy] = useState(false)
  const snip = SNIPPETS[lang]

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(snip.code)
      setCopied(true)
      setTimeout(() => setCopied(false), 1500)
    } catch {
      /* clipboard unavailable */
    }
  }

  const run = async () => {
    setBusy(true)
    try {
      const login = await fetch(`${API}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ username: 'alice.maker', password: 'maker123' }),
      })
      const session = await login.json()
      if (!login.ok) throw new Error(session.message ?? `Login failed (${login.status})`)
      const res = await fetch(`${API}/api/loan-products`, {
        headers: { Authorization: `Bearer ${session.data.token}` },
      })
      const json = await res.json()
      setOut(`HTTP ${res.status}\n` + JSON.stringify(json, null, 2))
      setState(res.ok ? 'ok' : 'err')
    } catch (e) {
      setOut(`Could not reach ${API}\n${e instanceof Error ? e.message : e}\n\nStart the backend with the dev profile and retry.`)
      setState('err')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="integ">
      <div className="panel">
        <div className="tabs" role="tablist" aria-label="Language">
          {(Object.keys(SNIPPETS) as Lang[]).map((l) => (
            <button key={l} role="tab" className="tab" aria-selected={lang === l} onClick={() => setLang(l)}>
              {SNIPPETS[l].label}
            </button>
          ))}
        </div>
        <div className="bar">
          <span>{snip.file}</span>
          <button className="copy" onClick={copy} aria-live="polite">{copied ? 'Copied' : 'Copy'}</button>
        </div>
        <pre tabIndex={0}><code>{snip.code}</code></pre>
      </div>
      <div className="panel out">
        <div className="bar"><span>response.json</span></div>
        <pre tabIndex={0} className={state === 'err' ? 'err' : state === 'idle' ? 'idle' : ''} aria-live="polite">{out}</pre>
        <div className="runbar">
          <button className="btn primary" onClick={run} disabled={busy}>{busy ? 'Running…' : 'Run request'}</button>
          <small>Uses a seeded demo user against {API}</small>
        </div>
      </div>
    </div>
  )
}

export default function App() {
  const [route, setRoute] = useState(location.hash)
  useEffect(() => {
    const on = () => { setRoute(location.hash); if (location.hash.startsWith('#/console')) window.scrollTo(0, 0) }
    window.addEventListener('hashchange', on)
    return () => window.removeEventListener('hashchange', on)
  }, [])
  const consoleView = route.startsWith('#/console')
  return (
    <>
      <header className="nav">
        <div className="wrap">
          <a className="brand" href="#top" onClick={() => setRoute('')}>
            <Icon d={ICONS.shield} /> Open Credit Platform
          </a>
          <nav className="links" aria-label="Primary">
            <a className="hide-sm" href="#integration">Integration</a>
            <a className="hide-sm" href="#lifecycle">Lifecycle</a>
            <a className="hide-sm" href="#security">Security</a>
            <a href="#docs">Docs</a>
            <a href="#/console">Console</a>
            <a className="btn primary" href={DOCS_URL} target="_blank" rel="noreferrer">API reference</a>
          </nav>
        </div>
      </header>

      {consoleView ? <Console route={route} /> : (
      <main id="top">
        <section className="hero">
          <div className="wrap">
            <span className="eyebrow">Credit decisioning API</span>
            <h1>From application to disbursement, with every decision explained.</h1>
            <p className="lead">
              An open-source platform for loan origination, KYC, underwriting, scoring and
              maker–checker approval — deterministic, auditable and easy to integrate.
            </p>
            <div className="cta">
              <a className="btn primary" href="#/console">Open the console</a>
              <a className="btn" href={DOCS_URL} target="_blank" rel="noreferrer">Read the docs</a>
            </div>
            <dl className="stats">
              <div><dt>REST + OpenAPI</dt><dd>Typed, documented endpoints</dd></div>
              <div><dt>Deterministic</dt><dd>Rule-based, explainable outcomes</dd></div>
              <div><dt>Full audit</dt><dd>Every state change recorded</dd></div>
              <div><dt>Open source</dt><dd>Modular monolith, PostgreSQL</dd></div>
            </dl>
          </div>
        </section>

        <section id="integration" className="alt">
          <div className="wrap">
            <div className="head">
              <h2>Integrate in minutes</h2>
              <p>Authenticate, then call any endpoint with a bearer token. Pick your language, or run the request live.</p>
            </div>
            <CodePreview />
          </div>
        </section>

        <section id="lifecycle">
          <div className="wrap">
            <div className="head">
              <h2>The full loan lifecycle</h2>
              <p>Eleven stages from application to audit. Select a stage to see its endpoints, who can call them and an example request.</p>
            </div>
            <Lifecycle />
          </div>
        </section>

        <section id="security" className="alt">
          <div className="wrap">
            <div className="head">
              <h2>Security and compliance</h2>
              <p>Controls built into the platform, and the frameworks they are designed to support.</p>
            </div>
            <div className="grid">
              {CONTROLS.map((c) => (
                <div className="card" key={c.title}>
                  <Icon d={c.icon} />
                  <h3>{c.title}</h3>
                  <p>{c.text}</p>
                  <span className="status ok">Implemented</span>
                </div>
              ))}
            </div>
            <div className="grid" style={{ marginTop: 20 }}>
              {FRAMEWORKS.map((f) => (
                <div className="card" key={f.name}>
                  <h3>{f.name}</h3>
                  <p>{f.note}</p>
                  <span className="status">Not certified</span>
                </div>
              ))}
            </div>
            <p className="note">
              Open Credit Platform is an open-source project and has not been independently audited or
              certified. Operators are responsible for their own certification when deploying it.
            </p>
          </div>
        </section>

        <section id="docs">
          <div className="wrap">
            <div className="head">
              <h2>Developer documentation</h2>
              <p>Everything you need to build, extend and operate the platform.</p>
            </div>
            <div className="docs">
              <a className="doc" href={DOCS_URL} target="_blank" rel="noreferrer">
                <strong>API reference <Icon d={ICONS.ext} /></strong>
                <span>Interactive Swagger UI for every endpoint.</span>
              </a>
              <a className="doc" href={`${REPO}/blob/main/docs/api/api-overview.md`} target="_blank" rel="noreferrer">
                <strong>API overview <Icon d={ICONS.ext} /></strong>
                <span>Conventions, envelopes, errors and endpoint map.</span>
              </a>
              <a className="doc" href={`${REPO}/tree/main/docs/architecture`} target="_blank" rel="noreferrer">
                <strong>Architecture <Icon d={ICONS.ext} /></strong>
                <span>Module boundaries and transaction design.</span>
              </a>
              <a className="doc" href={`${REPO}/tree/main/docs/adr`} target="_blank" rel="noreferrer">
                <strong>Decision records <Icon d={ICONS.ext} /></strong>
                <span>Why the system is shaped the way it is.</span>
              </a>
            </div>
          </div>
        </section>
      </main>
      )}

      <footer>
        <div className="wrap">
          <span>© {new Date().getFullYear()} Open Credit Platform</span>
          <a href={REPO} target="_blank" rel="noreferrer">GitHub</a>
        </div>
      </footer>
    </>
  )
}
