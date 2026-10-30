import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { clearSession, EXPIRES_KEY, http, TOKEN_KEY, USER_KEY } from '../../api/http'
import { buildLoginBody, mapUser, PATHS, SUPER_ROLES, type SessionUser } from '../../config/contract'

interface AuthState {
  user: SessionUser | null
  login: (identificador: string, password: string) => Promise<void>
  logout: () => Promise<void>
  can: (permiso: string) => boolean
}

const AuthCtx = createContext<AuthState | null>(null)

function readUser(): SessionUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY)
    const token = localStorage.getItem(TOKEN_KEY)
    const exp = localStorage.getItem(EXPIRES_KEY)
    if (!raw || !token) return null
    if (exp && new Date(exp).getTime() <= Date.now()) {
      clearSession()
      return null
    }
    return JSON.parse(raw) as SessionUser
  } catch {
    return null
  }
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<SessionUser | null>(readUser)

  // Refresca roles y permisos con /auth/me al abrir la app (si el token ya no vale, el interceptor cierra la sesión).
  useEffect(() => {
    if (!localStorage.getItem(TOKEN_KEY)) return
    http.get(PATHS.me).then(({ data }) => {
      const fresh = mapUser(data)
      localStorage.setItem(USER_KEY, JSON.stringify(fresh))
      setUser(fresh)
    }).catch(() => {})
  }, [])

  const login = useCallback(async (identificador: string, password: string) => {
    const { data } = await http.post(PATHS.login, buildLoginBody(identificador, password))
    const u = mapUser(data.usuario)
    localStorage.setItem(TOKEN_KEY, data.accessToken)
    if (data.expiresAt) localStorage.setItem(EXPIRES_KEY, data.expiresAt)
    localStorage.setItem(USER_KEY, JSON.stringify(u))
    setUser(u)
  }, [])

  const logout = useCallback(async () => {
    try { await http.post(PATHS.logout) } catch { /* se limpia igual */ }
    clearSession()
    setUser(null)
  }, [])

  const value = useMemo<AuthState>(
    () => ({
      user, login, logout,
      can: (p) => !!user && (SUPER_ROLES.some((r) => user.roles.includes(r)) || user.permisos.includes(p)),
    }),
    [user, login, logout],
  )
  return <AuthCtx.Provider value={value}>{children}</AuthCtx.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthCtx)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}
