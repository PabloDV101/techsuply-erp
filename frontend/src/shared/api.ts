import { useQuery } from '@tanstack/react-query'
import { http } from '../api/http'

export type Row = { id: number | string } & Record<string, any>

export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface PageData { rows: Row[]; total: number }

/** Acepta la PageResponse del backend o un arreglo usado por reportes agregados. */
export function normalizePage(data: unknown): PageData {
  if (Array.isArray(data)) return { rows: data, total: data.length }
  if (!data || typeof data !== 'object') {
    throw new Error('La respuesta del servidor no tiene el formato de página esperado.')
  }
  const page = data as { content?: unknown; totalElements?: unknown }
  if (!Array.isArray(page.content)) throw new Error('La respuesta del servidor no tiene el formato de página esperado.')
  const total = page.totalElements
  if (typeof total !== 'number' || !Number.isFinite(total) || total < 0) {
    throw new Error('La respuesta paginada no incluye totalElements válido.')
  }
  return { rows: page.content as Row[], total }
}

/** Página acotada para selectores: el backend limita cada consulta a 100 filas. */
export function useLookup(
  endpoint: string,
  params: Record<string, unknown> = {},
  enabled = true,
  activeFilter: { param: string; value: boolean } | null = { param: 'activo', value: true },
) {
  return useQuery({
    queryKey: ['lookup', endpoint, params],
    enabled: enabled && !!endpoint,
    staleTime: 60_000,
    queryFn: async () =>
      normalizePage((await http.get(endpoint, { params: {
        page: 0,
        size: 100,
        ...(activeFilter ? { [activeFilter.param]: activeFilter.value } : {}),
        ...params,
      } })).data).rows,
  })
}

/** Id numérico si lo parece; si no, tal cual (UUID). */
export const idValue = (v: string): number | string => (/^\d+$/.test(v) ? Number(v) : v)
