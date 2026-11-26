import { http } from '../../api/http'
import { normalizePage, type Row } from '../../shared/api'

export async function allPages(path: string, extra: Record<string, unknown> = {}): Promise<Row[]> {
  const params = { page: 0, size: 100, ...extra }
  const firstResponse = (await http.get(path, { params })).data
  const first = normalizePage(firstResponse)
  const totalPages = typeof firstResponse.totalPages === 'number' ? firstResponse.totalPages : 1
  const pages = await Promise.all(Array.from({ length: Math.max(0, totalPages - 1) }, async (_, index) =>
    normalizePage((await http.get(path, { params: { ...params, page: index + 1 } })).data).rows))
  return [...first.rows, ...pages.flat()]
}

export const idValue = (value: string): number | string => (/^\d+$/.test(value) ? Number(value) : value)
export const accountLabel = (account: Row) => `${account.codigo} — ${account.nombre}`
