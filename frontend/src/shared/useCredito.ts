import { useQuery } from '@tanstack/react-query'
import { http } from '../api/http'
import { PATHS } from '../config/contract'

/** Situación de crédito de un cliente (límite, saldo, disponible y estado). */
export function useCredito(clienteId: string) {
  return useQuery({
    queryKey: ['credito', clienteId],
    enabled: !!clienteId,
    queryFn: async () => (await http.get(`${PATHS.credito}/clientes/${clienteId}`)).data,
  })
}
