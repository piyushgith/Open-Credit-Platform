import { STAGES } from '../../features/loans'

export type View =
  | { kind: 'dashboard' }
  | { kind: 'new' }
  | { kind: 'approvals' }
  | { kind: 'search' }
  | { kind: 'stage'; status: string }
  | { kind: 'loan'; ref: string }

export function parseConsoleRoute(route: string): View {
  const m = route.replace(/^#\/console\/?/, '').split('/')
  if (m[0] === 'stage' && STAGES.some((s) => s.status === m[1])) return { kind: 'stage', status: m[1] }
  if (m[0] === 'loan' && m[1]) return { kind: 'loan', ref: decodeURIComponent(m[1]) }
  if (m[0] === 'new') return { kind: 'new' }
  if (m[0] === 'approvals') return { kind: 'approvals' }
  if (m[0] === 'search') return { kind: 'search' }
  return { kind: 'dashboard' }
}
