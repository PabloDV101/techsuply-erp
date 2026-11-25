import { PATHS } from '../../../config/contract'

export type DocKind = 'cotizacion' | 'pedido'

export const DOC = {
  cotizacion: { path: PATHS.cotizaciones, singular: 'cotización', venceLabel: 'Válida hasta', venceKey: 'validaHasta' },
  pedido: { path: PATHS.pedidos, singular: 'pedido de venta', venceLabel: 'Fecha de entrega', venceKey: 'fechaEntrega' },
} as const
