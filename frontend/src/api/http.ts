import axios from 'axios'

export const TOKEN_KEY = 'erp_token'
export const EXPIRES_KEY = 'erp_expires'
export const USER_KEY = 'erp_user'

export const http = axios.create({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
})

export function clearSession() {
  for (const k of [TOKEN_KEY, EXPIRES_KEY, USER_KEY]) localStorage.removeItem(k)
}

http.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(
  (r) => r,
  (error) => {
    const url = String(error.config?.url ?? '')
    if (error.response?.status === 401 && !url.endsWith('/auth/login')) {
      clearSession()
      window.location.href = '/login'
    }
    return Promise.reject(error)
  },
)

/** Convierte cualquier error de la API (ApiError) en un texto para el usuario. */
export function errorMessage(e: unknown): string {
  if (!axios.isAxiosError(e)) return 'Ocurrió un error inesperado.'
  if (!e.response) return 'No hay conexión con el servidor. Revisa que el backend esté activo.'
  const d = e.response.data
  if (typeof d === 'string' && d) return d
  const detail = d?.errors ?? d?.fieldErrors ?? d?.details
  if (Array.isArray(detail) && detail.length) {
    return detail.map((x: any) => (typeof x === 'string' ? x : [x.field, x.message].filter(Boolean).join(': '))).join(' · ')
  }
  if (d?.message) return d.message
  if (d?.detail) return d.detail
  switch (e.response.status) {
    case 403: return 'No tienes permiso para esta acción.'
    case 404: return 'No se encontró el registro.'
    case 409: return 'La operación entra en conflicto con el estado actual o con datos existentes.'
    case 503: return 'El servidor no está disponible. Intenta de nuevo en unos minutos.'
    default: return `Error ${e.response.status}`
  }
}
