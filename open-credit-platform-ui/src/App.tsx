import { useEffect, useState } from 'react'
import './styles/landing.css'
import { DOCS_URL, ICONS, REPO } from './features/landing/content'
import { Icon } from './features/landing'
import ConsolePage from './pages/ConsolePage'
import LandingPage from './pages/LandingPage'

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


      {consoleView ? <ConsolePage route={route} /> : <LandingPage />}

      <footer>
        <div className="wrap">
          <span>© {new Date().getFullYear()} Open Credit Platform</span>
          <a href={REPO} target="_blank" rel="noreferrer">GitHub</a>
        </div>
      </footer>
    </>
  )
}
