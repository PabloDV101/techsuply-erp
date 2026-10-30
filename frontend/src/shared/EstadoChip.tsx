import { Chip } from '@mui/material'

const map: Record<string, { label: string; color: 'default' | 'info' | 'success' | 'warning' | 'error' }> = {
  BORRADOR: { label: 'Borrador', color: 'default' },
  APROBADA: { label: 'Aprobada', color: 'info' },
  PARCIAL: { label: 'Recibida parcial', color: 'warning' },
  COMPLETA: { label: 'Completa', color: 'success' },
  CANCELADA: { label: 'Cancelada', color: 'error' },
  CONFIRMADA: { label: 'Confirmada', color: 'success' },
  ANULADA: { label: 'Anulada', color: 'error' },
}

export function EstadoChip({ estado }: { estado: unknown }) {
  const e = map[String(estado)] ?? { label: String(estado ?? '—'), color: 'default' as const }
  return <Chip size="small" label={e.label} color={e.color} variant="outlined" />
}

export const ESTADOS_OC = ['BORRADOR', 'APROBADA', 'PARCIAL', 'COMPLETA', 'CANCELADA']
export const ESTADOS_RECEPCION = ['BORRADOR', 'CONFIRMADA', 'ANULADA']
export const estadoLabel = (e: string) => map[e]?.label ?? e
