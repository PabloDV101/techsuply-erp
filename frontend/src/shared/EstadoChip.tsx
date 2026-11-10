import { Chip } from '@mui/material'

const map: Record<string, { label: string; color: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  BORRADOR: { label: 'Borrador', color: 'default' },
  BAJA: { label: 'Baja rotación', color: 'warning' },
  APROBADA: { label: 'Aprobada', color: 'info' },
  PARCIAL: { label: 'Recibida parcial', color: 'warning' },
  COMPLETA: { label: 'Completa', color: 'success' },
  CANCELADA: { label: 'Cancelada', color: 'error' },
  CONFIRMADA: { label: 'Confirmada', color: 'success' },
  ANULADA: { label: 'Anulada', color: 'error' },
  EN_CONTEO: { label: 'En conteo', color: 'info' },
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
  ALTA: { label: 'Alta rotación', color: 'success' },
  MEDIA: { label: 'Media', color: 'info' },
  SIN_MOVIMIENTO: { label: 'Sin movimiento', color: 'default' },
}

export function EstadoChip({ estado }: { estado: unknown }) {
  const e = map[String(estado)] ?? { label: String(estado ?? '—'), color: 'default' as const }
  return <Chip size="small" label={e.label} color={e.color} variant="outlined" />
}

export const ESTADOS_OC = ['BORRADOR', 'APROBADA', 'PARCIAL', 'COMPLETA', 'CANCELADA']
export const ESTADOS_RECEPCION = ['BORRADOR', 'CONFIRMADA', 'ANULADA']
export const ESTADOS_CONTEO = ['EN_CONTEO', 'APLICADO', 'CANCELADO']
export const TIPOS_MOVIMIENTO = ['ENTRADA', 'SALIDA', 'TRASLADO', 'AJUSTE_POSITIVO', 'AJUSTE_NEGATIVO']
export const NIVELES_ALERTA = ['AGOTADO', 'CRITICO', 'BAJO']
export const estadoLabel = (e: string) => map[e]?.label ?? e
