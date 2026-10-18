import { REPO } from '../../../config/env'

export function Footer() {
  return (
    <footer>
      <div className="wrap">
        <span>© {new Date().getFullYear()} Open Credit Platform</span>
        <a href={REPO} target="_blank" rel="noreferrer">GitHub</a>
      </div>
    </footer>
  )
}
