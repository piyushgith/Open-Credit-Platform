import { DOCS_URL } from '../../../config/env'
import { Icon } from '../../common/Icon'
import { ICONS } from '../../common/Icon/icons'

export function Header({ onHome }: { onHome: () => void }) {
  return (
    <header className="nav">
      <div className="wrap">
        <a className="brand" href="#top" onClick={onHome}>
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
  )
}
