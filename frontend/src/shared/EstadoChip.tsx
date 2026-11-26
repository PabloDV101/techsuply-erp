import { Chip } from '@mui/material'

const map: Record<string, { label: string; color: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  BORRADOR: { label: 'Borrador', color: 'default' },
  PENDIENTE_APROBACION: { label: 'Pendiente de aprobación', color: 'warning' },
  BAJA: { label: 'Baja rotación', color: 'warning' },
  APROBADA: { label: 'Aprobada', color: 'info' },
  PARCIAL: { label: 'Recibida parcial', color: 'warning' },
  COMPLETA: { label: 'Completa', color: 'success' },
  CANCELADA: { label: 'Cancelada', color: 'error' },
  CONFIRMADA: { label: 'Confirmada', color: 'success' },
  ANULADA: { label: 'Anulada', color: 'error' },
  EN_CONTEO: { label: 'En conteo', color: 'info' },
  PROGRAMADO: { label: 'Programado', color: 'info' },
  EN_CAPTURA: { label: 'En captura', color: 'info' },
  CERRADO: { label: 'Cerrado', color: 'success' },
  POSITIVO: { label: 'Ajuste positivo', color: 'success' },
  NEGATIVO: { label: 'Ajuste negativo', color: 'error' },
  APROBADO: { label: 'Aprobado', color: 'info' },
  RECHAZADO: { label: 'Rechazado', color: 'error' },
  APLICADO: { label: 'Aplicado', color: 'success' },
  CANCELADO: { label: 'Cancelado', color: 'error' },
  ENTRADA: { label: 'Entrada', color: 'success' },
  SALIDA: { label: 'Salida', color: 'warning' },
  TRASLADO: { label: 'Traslado', color: 'info' },
  AJUSTE_POSITIVO: { label: 'Ajuste (+)', color: 'success' },
  AJUSTE_NEGATIVO: { label: 'Ajuste (−)', color: 'error' },
  AGOTADO: { label: 'Agotado', color: 'error' },
  CRITICO: { label: 'Crítico', color: 'error' },
  BAJO: { label: 'Bajo mínimo', color: 'warning' },
  OK: { label: 'Normal', color: 'success' },
  ENVIADA: { label: 'Enviada', color: 'info' },
  VIGENTE: { label: 'Vigente', color: 'info' },
  PARCIALMENTE_DESPACHADO: { label: 'Parcialmente despachado', color: 'warning' },
  PARCIALMENTE_RECIBIDA: { label: 'Recibida parcialmente', color: 'warning' },
  RECIBIDA: { label: 'Recibida', color: 'success' },
  PENDIENTE: { label: 'Pendiente', color: 'warning' },
  ACEPTADA: { label: 'Aceptada', color: 'success' },
  RECHAZADA: { label: 'Rechazada', color: 'error' },
  CONVERTIDA: { label: 'Convertida a pedido', color: 'success' },
  CONFIRMADO: { label: 'Confirmado', color: 'info' },
  RETENIDO_CREDITO: { label: 'Retenido por crédito', color: 'warning' },
  DESPACHO_PARCIAL: { label: 'Despacho parcial', color: 'warning' },
  EMITIDA: { label: 'Emitida', color: 'info' },
  PAGO_PARCIAL: { label: 'Pago parcial', color: 'warning' },
  PAGADA: { label: 'Pagada', color: 'success' },
  AL_DIA: { label: 'Al día', color: 'success' },
  EN_RIESGO: { label: 'En riesgo', color: 'warning' },
  EXCEDIDO: { label: 'Límite excedido', color: 'error' },
  BLOQUEADO: { label: 'Bloqueado', color: 'error' },
  ALTA: { label: 'Alta rotación', color: 'success' },
  MEDIA: { label: 'Media', color: 'info' },
  SIN_MOVIMIENTO: { label: 'Sin movimiento', color: 'default' },
  DISPONIBLE: { label: 'Disponible', color: 'success' },
  NO_DISPONIBLE: { label: 'No disponible', color: 'warning' },
}

export function EstadoChip({ estado }: { estado: unknown }) {
  const e = map[String(estado)] ?? { label: String(estado ?? '—'), color: 'default' as const }
  return <Chip size="small" label={e.label} color={e.color} variant="outlined" />
}

export const ESTADOS_OC = ['BORRADOR', 'PENDIENTE_APROBACION', 'APROBADA', 'ENVIADA', 'PARCIALMENTE_RECIBIDA', 'RECIBIDA', 'CANCELADA']
export const ESTADOS_RECEPCION = ['PENDIENTE', 'CONFIRMADA', 'CANCELADA']
export const ESTADOS_CONTEO = ['PROGRAMADO', 'EN_CAPTURA', 'CERRADO', 'CANCELADO']
export const TIPOS_MOVIMIENTO = ['ENTRADA', 'SALIDA']
export const NIVELES_ALERTA = ['AGOTADO', 'CRITICO', 'BAJO']
export const ESTADOS_COTIZACION = ['BORRADOR', 'VIGENTE', 'ACEPTADA', 'VENCIDA', 'CANCELADA']
export const ESTADOS_PEDIDO = ['BORRADOR', 'CONFIRMADO', 'PARCIALMENTE_DESPACHADO', 'DESPACHADO', 'CANCELADO']
export const ESTADOS_DESPACHO = ['BORRADOR', 'CONFIRMADO', 'CANCELADO']
export const ESTADOS_FACTURA = ['EMITIDA']
export const ESTADOS_CREDITO = ['AL_DIA', 'EN_RIESGO', 'EXCEDIDO', 'BLOQUEADO']
export const estadoLabel = (e: string) => map[e]?.label ?? e
