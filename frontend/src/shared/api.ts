import { useQuery } from '@tanstack/react-query'
import { http } from '../api/http'

export type Row = { id: number | string } & Record<string, any>

export interface PageData { rows: Row[]; total: number }

/** Acepta PageResponse ({content,totalElements}) o un arreglo simple. */
export function normalizePage(data: any): PageData {
  if (Array.isArray(data)) return { rows: data, total: data.length }
  const rows = data?.content ?? data?.items ?? []
  return { rows, total: data?.totalElements ?? data?.total ?? rows.length }
}

/** Lista corta para selectores (hasta 200 registros activos). */
export function useLookup(endpoint: string, params: Record<string, unknown> = {}, enabled = true) {
  return useQuery({
    queryKey: ['lookup', endpoint, params],
    enabled: enabled && !!endpoint,
    staleTime: 60_000,
    queryFn: async () =>
      normalizePage((await http.get(endpoint, { params: { page: 0, size: 200, activo: true, ...params } })).data).rows,
  })
}

/** Id numérico si lo parece; si no, tal cual (UUID). */
export const idValue = (v: string): number | string => (/^\d+$/.test(v) ? Number(v) : v)
