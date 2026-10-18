import { createContext } from 'react'
import type { Session } from '../types/session.types'

export const SessionContext = createContext<Session | null>(null)
