import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { Alert, Box, Button, CircularProgress, Paper, Stack } from '@mui/material'
import { useQueryClient } from '@tanstack/react-query'
import axios from 'axios'
import { z } from 'zod'
import { clearSession, EXPIRES_KEY, http, SESSION_EXPIRED_EVENT, TOKEN_KEY, USER_KEY } from '../../api/http'
import { buildLoginBody, mapUser, PATHS, type SessionUser } from '../../config/contract'
import { useNotify } from '../../shared/Notify'

interface AuthState {
  user: SessionUser | null
  login: (identificador: string, password: string) => Promise<void>
  logout: () => Promise<void>
  clearLocalSession: () => void
  can: (permiso: string) => boolean
}

const AuthCtx = createContext<AuthState | null>(null)
const backendUser = z.object({
  id: z.number().int().positive(),
  username: z.string().min(1),
  correo: z.string(),
  roles: z.array(z.string()),
  permisos: z.array(z.string()),
})
const loginResponse = z.object({
  accessToken: z.string().min(1),
  tokenType: z.literal('Bearer'),
  expiresAt: z.string().min(1),
  usuario: backendUser,
})
type SessionStatus = 'loading' | 'authenticated' | 'anonymous' | 'unavailable'

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(null)
  const [status, setStatus] = useState<SessionStatus>('loading')
  const generation = useRef(0)
  const queryClient = useQueryClient()
  const notify = useNotify()

  const clearLocalSession = useCallback(() => {
    generation.current += 1
    clearSession()
    queryClient.clear()
    setUser(null)
    setStatus('anonymous')
  }, [queryClient])

  const refreshSession = useCallback(async () => {
    const requestGeneration = ++generation.current
    const token = localStorage.getItem(TOKEN_KEY)
    const expires = Date.parse(localStorage.getItem(EXPIRES_KEY) || '')
    if (!token || !Number.isFinite(expires) || expires <= Date.now()) {
      clearLocalSession()
      return
    }
    setUser(null)
    setStatus('loading')
    try {
      const { data } = await http.get(PATHS.me, { timeout: 10_000 })
      if (requestGeneration !== generation.current || localStorage.getItem(TOKEN_KEY) !== token) return
      const fresh = mapUser(backendUser.parse(data))
      localStorage.removeItem(USER_KEY)
      setUser(fresh)
      setStatus('authenticated')
    } catch (error) {
      if (requestGeneration !== generation.current || localStorage.getItem(TOKEN_KEY) !== token) return
      if (axios.isAxiosError(error) && error.response?.status === 401) clearLocalSession()
      else setStatus('unavailable')
    }
  }, [clearLocalSession])

  useEffect(() => {
    const expired = () => clearLocalSession()
    let storageTimer: ReturnType<typeof setTimeout> | undefined
    const storageChanged = (event: StorageEvent) => {
      if (event.storageArea !== localStorage || (event.key !== null && ![TOKEN_KEY, EXPIRES_KEY].includes(event.key))) return
      clearTimeout(storageTimer)
      storageTimer = setTimeout(() => {
        queryClient.clear()
        void refreshSession()
      }, 0)
    }
    window.addEventListener(SESSION_EXPIRED_EVENT, expired)
    window.addEventListener('storage', storageChanged)
    let active = true
    queueMicrotask(() => { if (active) void refreshSession() })
    return () => {
      active = false
      generation.current += 1
      clearTimeout(storageTimer)
      window.removeEventListener(SESSION_EXPIRED_EVENT, expired)
      window.removeEventListener('storage', storageChanged)
    }
  }, [clearLocalSession, queryClient, refreshSession])

  useEffect(() => {
    if (status !== 'authenticated') return
    const token = localStorage.getItem(TOKEN_KEY)
    const expires = Date.parse(localStorage.getItem(EXPIRES_KEY) || '')
    const remaining = expires - Date.now()
    const delay = token && Number.isFinite(remaining) ? Math.max(0, Math.min(remaining, 2_147_483_647)) : 0
    const timer = setTimeout(() => {
      if (localStorage.getItem(TOKEN_KEY) === token) clearLocalSession()
    }, delay)
    return () => clearTimeout(timer)
  }, [status, clearLocalSession])

  const login = useCallback(async (identificador: string, password: string) => {
    const requestGeneration = ++generation.current
    const { data } = await http.post(PATHS.login, buildLoginBody(identificador, password), { timeout: 10_000 })
    const response = loginResponse.parse(data)
    if (!Number.isFinite(Date.parse(response.expiresAt)) || Date.parse(response.expiresAt) <= Date.now()) {
      throw new Error('La sesión recibida no es válida')
    }
    if (requestGeneration !== generation.current) throw new Error('La sesión cambió durante el inicio')
    queryClient.clear()
    localStorage.setItem(EXPIRES_KEY, response.expiresAt)
    localStorage.setItem(TOKEN_KEY, response.accessToken)
    localStorage.removeItem(USER_KEY)
    setUser(mapUser(response.usuario))
    setStatus('authenticated')
  }, [queryClient])

  const logout = useCallback(async () => {
    const token = localStorage.getItem(TOKEN_KEY)
    generation.current += 1
    try {
      await http.post(PATHS.logout, null, { timeout: 10_000 })
    } catch (error) {
      if (!axios.isAxiosError(error) || error.response?.status !== 401) {
        notify('Se cerró la sesión en este navegador, pero no se pudo confirmar su revocación en el servidor.', 'error')
      }
    } finally {
      if (localStorage.getItem(TOKEN_KEY) === token || !localStorage.getItem(TOKEN_KEY)) clearLocalSession()
      else void refreshSession()
    }
  }, [clearLocalSession, notify, refreshSession])

  const value = useMemo<AuthState>(
    () => ({ user, login, logout, clearLocalSession, can: (permission) => !!user && user.permisos.includes(permission) }),
    [user, login, logout, clearLocalSession],
  )

  if (status === 'loading' || status === 'unavailable') {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2 }}>
        <Paper sx={{ p: 3, maxWidth: 520, width: '100%' }}>
          {status === 'loading' ? <Stack sx={{ alignItems: 'center' }} spacing={2}><CircularProgress /><span>Comprobando sesión…</span></Stack> : (
            <Stack spacing={2}>
              <Alert severity="warning">No se pudo comprobar la sesión con el servidor. Revisa la conexión e inténtalo de nuevo.</Alert>
              <Button variant="contained" onClick={() => void refreshSession()}>Reintentar</Button>
              <Button onClick={clearLocalSession}>Salir de esta sesión en este navegador</Button>
            </Stack>
          )}
        </Paper>
      </Box>
    )
  }
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthCtx)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}
