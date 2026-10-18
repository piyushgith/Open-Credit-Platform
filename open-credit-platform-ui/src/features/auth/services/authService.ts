import { call } from '../../../services/http'
import type { Session } from '../../../types/session.types'

export const login = (username: string, password: string) =>
  call<Session>('POST', '/api/auth/login', { username, password })
