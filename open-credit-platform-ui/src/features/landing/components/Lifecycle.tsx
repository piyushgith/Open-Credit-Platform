import { useState } from 'react'
import { LIFECYCLE_STAGES as STAGES, stageCurl } from '../content'

export default function Lifecycle() {
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
