import { PATHS } from '../../../config/contract'

export type DocKind = 'cotizacion' | 'pedido'

export const DOC = {
  cotizacion: { path: PATHS.cotizaciones, singular: 'cotización', dateLabel: 'Vigente hasta', dateKey: 'vigenciaHasta' },
  pedido: { path: PATHS.pedidos, singular: 'pedido de venta', dateLabel: 'Fecha', dateKey: 'fecha' },
} as const
