import { DOCS_URL, REPO } from '../../config/env'
import { Icon, ICONS } from '../../components/common'
import { CONTROLS, FRAMEWORKS } from '../../features/landing/content'
import { CodePreview, Lifecycle } from '../../features/landing'
export default function LandingPage() {
  return (
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
  )
}
