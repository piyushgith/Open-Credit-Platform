import { useContext } from 'react'
import { SessionContext } from '../context/SessionContext'

// Mirrors the backend's @PreAuthorize rules: makers prepare and submit, checkers verify, sanction
// and disburse, and ADMIN can do both. The server remains the authority; this only hides buttons
// the signed-in role would be refused.
export function useRoles() {
  const role = useContext(SessionContext)?.role
  return {
    role,
    maker: role === 'MAKER' || role === 'ADMIN',
    checker: role === 'CHECKER' || role === 'ADMIN',
  }
}
