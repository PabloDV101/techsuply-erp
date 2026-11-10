import { useQuery } from '@tanstack/react-query'
import { http } from '../api/http'
import { PATHS } from '../config/contract'
import { normalizePage } from './api'

/** Existencia disponible de un producto en un almacén (undefined mientras no haya ambos). */
export function useStock(productoId: string, almacenId: string) {
  return useQuery({
    queryKey: ['stock', productoId, almacenId],
    enabled: !!productoId && !!almacenId,
    queryFn: async () => {
      const rows = normalizePage((await http.get(PATHS.existencias, { params: { productoId, almacenId, page: 0, size: 1 } })).data).rows
      const r = rows[0]
      return { fisica: Number(r?.cantidadFisica ?? 0), disponible: Number(r?.cantidadDisponible ?? r?.cantidadFisica ?? 0) }
    },
  })
}
