import { createContext, useCallback, useContext, useState, type ReactNode } from 'react'
import { Alert, Snackbar } from '@mui/material'

type Severity = 'success' | 'error' | 'info'
const Ctx = createContext<(msg: string, severity?: Severity) => void>(() => {})
export const useNotify = () => useContext(Ctx)

export function NotifyProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<{ msg: string; severity: Severity } | null>(null)
  const notify = useCallback((msg: string, severity: Severity = 'success') => setState({ msg, severity }), [])
  return (
    <Ctx.Provider value={notify}>
      {children}
      <Snackbar
        open={!!state}
        autoHideDuration={4000}
        onClose={() => setState(null)}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
      >
        {state ? <Alert severity={state.severity} variant="filled" onClose={() => setState(null)}>{state.msg}</Alert> : undefined}
      </Snackbar>
    </Ctx.Provider>
  )
}
